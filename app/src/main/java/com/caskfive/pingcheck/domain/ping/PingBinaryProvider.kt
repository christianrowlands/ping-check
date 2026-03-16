package com.caskfive.pingcheck.domain.ping

import java.io.File
import javax.inject.Inject

interface PingBinaryProvider {
    fun findPingBinary(): String?
    fun findPing6Binary(): String?
}

class SystemPingBinaryProvider @Inject constructor() : PingBinaryProvider {
    companion object {
        private val PING_PATHS = listOf("/system/bin/ping", "/usr/bin/ping", "/bin/ping")
        private val PING6_PATHS = listOf("/system/bin/ping6", "/usr/bin/ping6", "/bin/ping6")
    }

    override fun findPingBinary(): String? {
        return PING_PATHS.firstOrNull { File(it).exists() && File(it).canExecute() }
    }

    override fun findPing6Binary(): String? {
        return PING6_PATHS.firstOrNull { File(it).exists() && File(it).canExecute() }
    }
}
