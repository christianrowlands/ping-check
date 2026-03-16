package com.caskfive.pingcheck.ui.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caskfive.pingcheck.data.db.TracerouteHopEntity
import com.caskfive.pingcheck.data.db.TracerouteSessionEntity
import com.caskfive.pingcheck.domain.traceroute.TraceConfig
import com.caskfive.pingcheck.domain.traceroute.TracerouteEngine
import com.caskfive.pingcheck.domain.traceroute.TracerouteEvent
import com.caskfive.pingcheck.repository.TracerouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// TracerouteError sealed class mirroring PingError pattern
sealed class TracerouteError(val message: String) {
    class NoNetwork : TracerouteError("No network connection")
    class DnsFailure(host: String) : TracerouteError("Could not resolve $host")
    class NoPingBinary : TracerouteError("Ping/traceroute not available on this device")
    class General(msg: String) : TracerouteError(msg)
}

data class TracerouteScreenState(
    val targetHost: String = "",
    val isRunning: Boolean = false,
    val resolvedIp: String? = null,
    val hops: List<HopDisplay> = emptyList(),
    // Kept as String? for backward compatibility with TracerouteScreen UI
    val error: String? = null,
    // Typed error for programmatic use
    val typedError: TracerouteError? = null,
    val showSettings: Boolean = false,
    val maxHops: Int = 30,
    val timeout: Int = 3,
    val sessionId: Long? = null,
)

data class HopDisplay(
    val hopNumber: Int,
    val ipAddress: String? = null,
    val hostname: String? = null,
    val rttMs: Float? = null,
    val countryCode: String? = null,
    val isTimeout: Boolean = false,
    val isDestination: Boolean = false,
)

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteEngine: TracerouteEngine,
    private val tracerouteRepository: TracerouteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TracerouteScreenState())
    val state: StateFlow<TracerouteScreenState> = _state.asStateFlow()

    private var traceJob: Job? = null

    fun onTargetHostChanged(host: String) {
        _state.update { it.copy(targetHost = host, error = null, typedError = null) }
    }

    fun onMaxHopsChanged(maxHops: Int) {
        _state.update { it.copy(maxHops = maxHops.coerceIn(1, 64)) }
    }

    fun onTimeoutChanged(timeout: Int) {
        _state.update { it.copy(timeout = timeout.coerceIn(1, 30)) }
    }

    fun toggleSettings() {
        _state.update { it.copy(showSettings = !it.showSettings) }
    }

    fun startTrace() {
        val host = _state.value.targetHost.trim()
        if (host.isBlank()) return

        stopTrace()

        _state.update {
            it.copy(
                isRunning = true,
                resolvedIp = null,
                hops = emptyList(),
                error = null,
                typedError = null,
                sessionId = null,
            )
        }

        val config = TraceConfig(
            host = host,
            maxHops = _state.value.maxHops,
            timeoutSeconds = _state.value.timeout,
        )

        traceJob = viewModelScope.launch {
            // Create session in DB
            val session = TracerouteSessionEntity(
                targetHost = host,
                startTime = System.currentTimeMillis(),
                maxHops = config.maxHops,
                timeoutSetting = config.timeoutSeconds,
            )
            val sessionId = tracerouteRepository.createSession(session)
            _state.update { it.copy(sessionId = sessionId) }

            tracerouteEngine.trace(config).collect { event ->
                handleEvent(event, sessionId)
            }

            // Update session end time
            tracerouteRepository.getSession(sessionId)?.let { existing ->
                tracerouteRepository.updateSession(
                    existing.copy(
                        endTime = System.currentTimeMillis(),
                        isComplete = true,
                    )
                )
            }

            _state.update { it.copy(isRunning = false) }
        }
    }

    fun stopTrace() {
        traceJob?.cancel()
        traceJob = null
        if (_state.value.isRunning) {
            _state.update { it.copy(isRunning = false) }
        }
    }

    private suspend fun handleEvent(event: TracerouteEvent, sessionId: Long) {
        when (event) {
            is TracerouteEvent.Started -> {
                _state.update { it.copy(resolvedIp = event.resolvedIp) }
                tracerouteRepository.getSession(sessionId)?.let { existing ->
                    tracerouteRepository.updateSession(
                        existing.copy(resolvedIp = event.resolvedIp)
                    )
                }
            }

            is TracerouteEvent.HopResult -> {
                val display = HopDisplay(
                    hopNumber = event.hopNumber,
                    ipAddress = event.ipAddress,
                    hostname = event.hostname,
                    rttMs = event.rttMs,
                    countryCode = event.countryCode,
                    isTimeout = event.isTimeout,
                    isDestination = event.isDestination,
                )
                _state.update { state ->
                    state.copy(hops = state.hops + display)
                }

                // Save hop to DB
                val hopEntity = TracerouteHopEntity(
                    sessionId = sessionId,
                    hopNumber = event.hopNumber,
                    ipAddress = event.ipAddress,
                    hostname = event.hostname,
                    rttMs = event.rttMs,
                    countryCode = event.countryCode,
                    asn = event.asn,
                    orgName = event.orgName,
                    isTimeout = event.isTimeout,
                    isDestination = event.isDestination,
                )
                tracerouteRepository.insertHop(hopEntity)
            }

            is TracerouteEvent.Completed -> {
                // Handled after collect finishes
            }

            is TracerouteEvent.Error -> {
                // Map error messages to typed TracerouteError
                val tracerouteError = when {
                    event.message.contains("not available", ignoreCase = true) ->
                        TracerouteError.NoPingBinary()
                    event.message.contains("network", ignoreCase = true) ->
                        TracerouteError.NoNetwork()
                    event.message.contains("resolve", ignoreCase = true) ||
                        event.message.contains("dns", ignoreCase = true) ->
                        TracerouteError.DnsFailure(_state.value.targetHost)
                    else -> TracerouteError.General(event.message)
                }
                // Set both error (String for UI) and typedError (TracerouteError for logic)
                _state.update {
                    it.copy(
                        error = tracerouteError.message,
                        typedError = tracerouteError,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTrace()
    }
}
