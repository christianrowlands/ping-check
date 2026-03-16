package com.caskfive.pingcheck.domain.traceroute

sealed class TracerouteEvent {
    data class Started(val resolvedIp: String) : TracerouteEvent()

    data class HopResult(
        val hopNumber: Int,
        val ipAddress: String? = null,
        val hostname: String? = null,
        val rttMs: Float? = null,
        val countryCode: String? = null,
        val asn: String? = null,
        val orgName: String? = null,
        val isTimeout: Boolean = false,
        val isDestination: Boolean = false,
    ) : TracerouteEvent()

    data object Completed : TracerouteEvent()

    data class Error(val message: String) : TracerouteEvent()
}
