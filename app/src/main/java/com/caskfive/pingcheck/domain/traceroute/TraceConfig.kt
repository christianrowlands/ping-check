package com.caskfive.pingcheck.domain.traceroute

data class TraceConfig(
    val host: String,
    val maxHops: Int = 30,
    val timeoutSeconds: Int = 3,
)
