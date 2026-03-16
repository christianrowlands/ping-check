package com.caskfive.pingcheck.domain.ping

sealed class PingEvent {
    data class Started(val resolvedIp: String, val isIpv6: Boolean = false) : PingEvent()

    data class PacketReceived(
        val sequenceNumber: Int,
        val rttMs: Float,
        val ttl: Int,
        val bytes: Int,
    ) : PingEvent()

    data class PacketLost(val sequenceNumber: Int) : PingEvent()

    data class Summary(
        val packetsSent: Int,
        val packetsReceived: Int,
        val packetLossPct: Float,
        val minRtt: Float?,
        val avgRtt: Float?,
        val maxRtt: Float?,
        val stddevRtt: Float?,
    ) : PingEvent()

    data class DnsResolutionFailed(val host: String) : PingEvent()

    data class Error(val message: String) : PingEvent()
}
