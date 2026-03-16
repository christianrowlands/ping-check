package com.caskfive.pingcheck.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.caskfive.pingcheck.domain.ping.PingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable state representing whether the foreground ping service is running.
 */
enum class PingServiceState {
    IDLE,
    RUNNING,
}

/**
 * Abstraction over starting/stopping [PingForegroundService] so that ViewModels
 * do not need a [Context] reference.
 */
interface PingServiceManager {
    val serviceState: StateFlow<PingServiceState>
    fun startContinuousPing(config: PingConfig)
    fun stopPing()
}

@Singleton
class PingServiceManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PingServiceManager {

    private val _serviceState = MutableStateFlow(PingServiceState.IDLE)
    override val serviceState: StateFlow<PingServiceState> = _serviceState.asStateFlow()

    override fun startContinuousPing(config: PingConfig) {
        val intent = PingForegroundService.startIntent(
            context = context,
            host = config.host,
            count = config.count,
            interval = config.intervalSeconds,
            packetSize = config.packetSizeBytes,
            timeout = config.timeoutSeconds,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        _serviceState.value = PingServiceState.RUNNING
    }

    override fun stopPing() {
        val intent = PingForegroundService.stopIntent(context)
        context.startService(intent)
        _serviceState.value = PingServiceState.IDLE
    }
}
