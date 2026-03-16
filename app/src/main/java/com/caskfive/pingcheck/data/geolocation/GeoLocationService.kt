package com.caskfive.pingcheck.data.geolocation

import android.content.Context
import com.maxmind.db.CHMCache
import com.maxmind.db.Reader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class CountryInfo(
    val countryCode: String,
    val countryName: String?,
)

data class AsnInfo(
    val asn: Long,
    val asnOrg: String?,
)

@Singleton
class GeoLocationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var countryReader: Reader? = null
    private var asnReader: Reader? = null
    private val initMutex = Mutex()
    private var initialized = false

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                countryReader = openDbFromAssets("GeoLite2-Country.mmdb")
                asnReader = openDbFromAssets("GeoLite2-ASN.mmdb")
            }
            initialized = true
        }
    }

    private fun openDbFromAssets(assetName: String): Reader? {
        return try {
            val dbFile = File(context.filesDir, assetName)
            if (!dbFile.exists()) {
                context.assets.open(assetName).use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Reader(dbFile, CHMCache())
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getCountry(ip: String): CountryInfo? {
        ensureInitialized()
        val reader = countryReader ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(ip)
                val record = reader.get(address, Map::class.java) ?: return@withContext null
                val country = record["country"] as? Map<String, Any?> ?: return@withContext null
                val isoCode = country["iso_code"] as? String ?: return@withContext null
                val names = country["names"] as? Map<String, String>
                CountryInfo(
                    countryCode = isoCode,
                    countryName = names?.get("en"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getAsn(ip: String): AsnInfo? {
        ensureInitialized()
        val reader = asnReader ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(ip)
                val record = reader.get(address, Map::class.java) ?: return@withContext null
                val asn = (record["autonomous_system_number"] as? Number)?.toLong()
                    ?: return@withContext null
                val org = record["autonomous_system_organization"] as? String
                AsnInfo(asn = asn, asnOrg = org)
            } catch (_: Exception) {
                null
            }
        }
    }
}
