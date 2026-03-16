package com.caskfive.pingcheck.domain.ping

data class PingConfig(
    val host: String,
    val count: Int = 4,
    val intervalSeconds: Float = 1.0f,
    val packetSizeBytes: Int = 56,
    val timeoutSeconds: Int = 10,
) {
    val isContinuous: Boolean get() = count == 0
}
