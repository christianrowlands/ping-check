package com.caskfive.pingcheck.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

interface DnsResolver {
    suspend fun resolve(hostname: String): String?
    suspend fun reverseLookup(ip: String): String?
}

class SystemDnsResolver @Inject constructor() : DnsResolver {
    override suspend fun resolve(hostname: String): String? = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(hostname).hostAddress
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun reverseLookup(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname != ip) hostname else null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Kept for backward compatibility with existing call sites that reference DnsUtils directly.
 * New code should inject [DnsResolver] instead.
 */
object DnsUtils {
    suspend fun resolve(hostname: String): String? = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(hostname).hostAddress
        } catch (_: Exception) {
            null
        }
    }

    suspend fun reverseLookup(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname != ip) hostname else null
        } catch (_: Exception) {
            null
        }
    }
}
