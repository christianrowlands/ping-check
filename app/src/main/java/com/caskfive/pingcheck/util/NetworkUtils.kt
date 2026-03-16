package com.caskfive.pingcheck.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet6Address
import java.net.NetworkInterface
import javax.inject.Inject

interface NetworkChecker {
    fun isNetworkAvailable(): Boolean
    fun getDefaultGateway(): String?
    fun getLocalIpAddress(): String?
}

class SystemNetworkChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkChecker {

    override fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun getDefaultGateway(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val linkProperties = cm.getLinkProperties(network) ?: return null
        return linkProperties.routes
            .firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.hostAddress
    }

    override fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it !is Inet6Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
