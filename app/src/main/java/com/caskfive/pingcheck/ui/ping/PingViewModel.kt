package com.caskfive.pingcheck.ui.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import com.caskfive.pingcheck.data.geolocation.GeoLocationService
import com.caskfive.pingcheck.data.preferences.PreferencesManager
import com.caskfive.pingcheck.domain.ping.PingConfig
import com.caskfive.pingcheck.domain.ping.PingEngine
import com.caskfive.pingcheck.domain.ping.PingEvent
import com.caskfive.pingcheck.data.db.FavoriteEntity
import com.caskfive.pingcheck.repository.FavoritesRepository
import com.caskfive.pingcheck.repository.PingRepository
import com.caskfive.pingcheck.service.PingServiceManager
import com.caskfive.pingcheck.service.PingServiceState
import com.caskfive.pingcheck.ui.components.IpInfoState
import com.caskfive.pingcheck.util.DnsUtils
import com.caskfive.pingcheck.util.NetworkChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.abs

data class PingScreenState(
    val targetHost: String = "",
    val isRunning: Boolean = false,
    val isResolving: Boolean = false,
    val resolvedIp: String? = null,
    val isIpv6: Boolean = false,
    val results: List<PingResultDisplay> = emptyList(),
    val stats: PingStats = PingStats(),
    val error: PingError? = null,
    val showAdvancedSettings: Boolean = false,
    val count: Int = 4,
    val interval: Float = 1.0f,
    val packetSize: Int = 56,
    val timeout: Int = 10,
    val sessionId: Long? = null,
    val ipInfo: IpInfoState = IpInfoState(),
    val favorites: List<FavoriteDisplay> = emptyList(),
    val isFavorite: Boolean = false,
    val defaultGateway: String? = null,
)

data class FavoriteDisplay(
    val id: Long,
    val host: String,
    val displayName: String?,
)

data class PingResultDisplay(
    val sequenceNumber: Int,
    val rttMs: Float?,
    val ttl: Int?,
    val bytes: Int?,
    val isSuccess: Boolean,
    val text: String,
)

data class PingStats(
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val packetLossPct: Float = 0f,
    val minRtt: Float? = null,
    val avgRtt: Float? = null,
    val maxRtt: Float? = null,
    val stddevRtt: Float? = null,
    val jitter: Float? = null,
)

sealed class PingError(val message: String) {
    data class DnsFailure(val host: String) : PingError("Could not resolve $host")
    data object NoNetwork : PingError("No network connection")
    data object NoPingBinary : PingError("Ping not available on this device")
    data class General(val msg: String) : PingError(msg)
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pingEngine: PingEngine,
    private val pingRepository: PingRepository,
    private val preferencesManager: PreferencesManager,
    private val geoLocationService: GeoLocationService,
    private val favoritesRepository: FavoritesRepository,
    private val serviceManager: PingServiceManager,
    private val networkChecker: NetworkChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(PingScreenState())
    val state: StateFlow<PingScreenState> = _state.asStateFlow()

    private var pingJob: Job? = null

    // Thread-safe resultBatch protected by Mutex
    private val resultBatchMutex = Mutex()
    private val resultBatch = mutableListOf<PingResultEntity>()

    private var lastRtt: Float? = null

    // Running accumulators for O(1) incremental stats
    private var rttSum: Double = 0.0
    private var rttSumOfSquares: Double = 0.0

    init {
        viewModelScope.launch {
            val prefs = preferencesManager.preferences.first()
            _state.update {
                it.copy(
                    count = prefs.defaultCount,
                    interval = prefs.defaultInterval,
                    packetSize = prefs.defaultPacketSize,
                    timeout = prefs.defaultTimeout,
                )
            }
        }
        // Observe favorites
        viewModelScope.launch {
            favoritesRepository.observeAll().collect { favorites ->
                val defaults = listOf(
                    FavoriteDisplay(id = -1, host = "8.8.8.8", displayName = "Google DNS"),
                    FavoriteDisplay(id = -2, host = "1.1.1.1", displayName = "Cloudflare"),
                )
                val userFavs = favorites.map {
                    FavoriteDisplay(id = it.id, host = it.host, displayName = it.displayName)
                }
                _state.update { state ->
                    val isFav = favorites.any { it.host == state.targetHost }
                    state.copy(favorites = defaults + userFavs, isFavorite = isFav)
                }
            }
        }
        // Observe foreground service state to update UI isRunning
        viewModelScope.launch {
            serviceManager.serviceState.collect { serviceState ->
                val isServiceRunning = serviceState == PingServiceState.RUNNING
                _state.update { state ->
                    // If service just stopped and we were in continuous mode, update isRunning
                    if (!isServiceRunning && state.isRunning && state.count == 0) {
                        state.copy(isRunning = false)
                    } else if (isServiceRunning && state.count == 0) {
                        state.copy(isRunning = true)
                    } else {
                        state
                    }
                }
            }
        }
        // Detect default gateway on init
        viewModelScope.launch {
            val gateway = networkChecker.getDefaultGateway()
            if (gateway != null) {
                _state.update { it.copy(defaultGateway = gateway) }
            }
        }
    }

    fun onTargetHostChanged(host: String) {
        _state.update { state ->
            val isFav = state.favorites.any { it.host == host }
            state.copy(targetHost = host, error = null, isFavorite = isFav)
        }
    }

    fun toggleFavorite() {
        val host = _state.value.targetHost.trim()
        if (host.isBlank()) return

        viewModelScope.launch {
            val existing = favoritesRepository.getByHost(host)
            if (existing != null) {
                favoritesRepository.delete(existing.id)
            } else {
                val sortOrder = favoritesRepository.getNextSortOrder()
                favoritesRepository.add(
                    FavoriteEntity(
                        host = host,
                        sortOrder = sortOrder,
                        lastCount = _state.value.count,
                        lastInterval = _state.value.interval,
                        lastPacketSize = _state.value.packetSize,
                        lastTimeout = _state.value.timeout,
                    )
                )
            }
        }
    }

    fun selectFavorite(host: String) {
        _state.update { it.copy(targetHost = host, error = null) }
        startPing()
    }

    fun onCountChanged(count: Int) {
        _state.update { it.copy(count = count) }
    }

    fun onIntervalChanged(interval: Float) {
        _state.update { it.copy(interval = interval) }
    }

    fun onPacketSizeChanged(size: Int) {
        _state.update { it.copy(packetSize = size) }
    }

    fun onTimeoutChanged(timeout: Int) {
        _state.update { it.copy(timeout = timeout) }
    }

    fun toggleAdvancedSettings() {
        _state.update { it.copy(showAdvancedSettings = !it.showAdvancedSettings) }
    }

    // Edit a favorite's display name
    fun editFavorite(id: Long, newDisplayName: String) {
        viewModelScope.launch {
            val favorites = favoritesRepository.observeAll().first()
            val favorite = favorites.firstOrNull { it.id == id } ?: return@launch
            favoritesRepository.updateFavorite(
                favorite.copy(displayName = newDisplayName.ifBlank { null })
            )
        }
    }

    // Delete a favorite by id
    fun deleteFavorite(id: Long) {
        viewModelScope.launch {
            favoritesRepository.deleteFavorite(id)
        }
    }

    fun startPing() {
        val host = _state.value.targetHost.trim()
        if (host.isBlank()) return

        stopPing()

        // Validate and coerce PingConfig parameters
        val validatedCount = _state.value.count.coerceIn(0, 10000)
        val validatedInterval = _state.value.interval.coerceIn(0.2f, 60f)
        val validatedPacketSize = _state.value.packetSize.coerceIn(8, 1472)
        val validatedTimeout = _state.value.timeout.coerceIn(1, 60)

        _state.update {
            it.copy(
                isRunning = true,
                isResolving = true,
                resolvedIp = null,
                results = emptyList(),
                stats = PingStats(),
                error = null,
                sessionId = null,
                count = validatedCount,
                interval = validatedInterval,
                packetSize = validatedPacketSize,
                timeout = validatedTimeout,
            )
        }

        lastRtt = null
        rttSum = 0.0
        rttSumOfSquares = 0.0
        viewModelScope.launch {
            resultBatchMutex.withLock { resultBatch.clear() }
        }

        val config = PingConfig(
            host = host,
            count = validatedCount,
            intervalSeconds = validatedInterval,
            packetSizeBytes = validatedPacketSize,
            timeoutSeconds = validatedTimeout,
        )

        // If continuous mode (count == 0), delegate to foreground service
        if (config.isContinuous) {
            serviceManager.startContinuousPing(config)
            // isRunning is managed by observing serviceManager.serviceState
            _state.update { it.copy(isRunning = true, isResolving = false) }
            return
        }

        // Non-continuous mode: run in viewModelScope as before
        pingJob = viewModelScope.launch {
            // Create session in DB
            val session = PingSessionEntity(
                targetHost = host,
                startTime = System.currentTimeMillis(),
                countSetting = config.count,
                intervalSetting = config.intervalSeconds,
                packetSizeSetting = config.packetSizeBytes,
                timeoutSetting = config.timeoutSeconds,
            )
            val sessionId = pingRepository.createSession(session)
            _state.update { it.copy(sessionId = sessionId) }

            pingEngine.ping(config).collect { event ->
                handlePingEvent(event, sessionId)
            }

            // Flush any remaining batched results
            flushResultBatch(sessionId)

            // Update session end time
            pingRepository.getSession(sessionId)?.let { existing ->
                val s = _state.value.stats
                pingRepository.updateSession(
                    existing.copy(
                        endTime = System.currentTimeMillis(),
                        packetsSent = s.packetsSent,
                        packetsReceived = s.packetsReceived,
                        packetLossPct = s.packetLossPct,
                        minRtt = s.minRtt,
                        avgRtt = s.avgRtt,
                        maxRtt = s.maxRtt,
                        stddevRtt = s.stddevRtt,
                        jitterRtt = s.jitter,
                    )
                )
            }

            _state.update { it.copy(isRunning = false) }
        }
    }

    fun stopPing() {
        // If continuous mode, stop the foreground service
        if (_state.value.count == 0 && serviceManager.serviceState.value == PingServiceState.RUNNING) {
            serviceManager.stopPing()
            _state.update { it.copy(isRunning = false) }
            return
        }

        pingJob?.cancel()
        pingJob = null
        if (_state.value.isRunning) {
            _state.update { it.copy(isRunning = false) }
            // Flush remaining results
            _state.value.sessionId?.let { sessionId ->
                viewModelScope.launch { flushResultBatch(sessionId) }
            }
        }
    }

    private suspend fun handlePingEvent(event: PingEvent, sessionId: Long) {
        when (event) {
            is PingEvent.Started -> {
                _state.update {
                    it.copy(
                        isResolving = false,
                        resolvedIp = event.resolvedIp,
                        isIpv6 = event.isIpv6,
                        ipInfo = IpInfoState(resolvedIp = event.resolvedIp),
                    )
                }
                pingRepository.getSession(sessionId)?.let { existing ->
                    pingRepository.updateSession(
                        existing.copy(
                            resolvedIp = event.resolvedIp,
                            isIpv6 = event.isIpv6,
                        )
                    )
                }
                // Lookup IP info in background
                lookupIpInfo(event.resolvedIp)
            }

            is PingEvent.PacketReceived -> {
                val jitter = lastRtt?.let { prev -> abs(event.rttMs - prev) }
                lastRtt = event.rttMs

                val display = PingResultDisplay(
                    sequenceNumber = event.sequenceNumber,
                    rttMs = event.rttMs,
                    ttl = event.ttl,
                    bytes = event.bytes,
                    isSuccess = true,
                    text = "${event.bytes} bytes: seq=${event.sequenceNumber} ttl=${event.ttl} time=${event.rttMs} ms"
                )

                _state.update { state ->
                    val newResults = state.results + display
                    val stats = computeStatsIncremental(event.rttMs, jitter, state.stats)
                    state.copy(results = newResults, stats = stats)
                }

                val resultEntity = PingResultEntity(
                    sessionId = sessionId,
                    sequenceNumber = event.sequenceNumber,
                    rttMs = event.rttMs,
                    ttl = event.ttl,
                    bytes = event.bytes,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = true,
                )
                batchOrInsertResult(resultEntity, sessionId)
            }

            is PingEvent.PacketLost -> {
                val display = PingResultDisplay(
                    sequenceNumber = event.sequenceNumber,
                    rttMs = null,
                    ttl = null,
                    bytes = null,
                    isSuccess = false,
                    text = "Request timeout for seq ${event.sequenceNumber}"
                )
                _state.update { state ->
                    val newResults = state.results + display
                    val sent = state.stats.packetsSent + 1
                    val lossPct = if (sent > 0) {
                        ((sent - state.stats.packetsReceived).toFloat() / sent) * 100f
                    } else 0f
                    state.copy(
                        results = newResults,
                        stats = state.stats.copy(packetsSent = sent, packetLossPct = lossPct),
                    )
                }

                val resultEntity = PingResultEntity(
                    sessionId = sessionId,
                    sequenceNumber = event.sequenceNumber,
                    rttMs = null,
                    ttl = null,
                    bytes = null,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = false,
                )
                batchOrInsertResult(resultEntity, sessionId)
            }

            is PingEvent.Summary -> {
                _state.update { state ->
                    val currentStats = state.stats
                    state.copy(
                        stats = currentStats.copy(
                            packetsSent = if (event.packetsSent > 0) event.packetsSent else currentStats.packetsSent,
                            packetsReceived = if (event.packetsReceived > 0) event.packetsReceived else currentStats.packetsReceived,
                            packetLossPct = if (event.packetLossPct > 0f) event.packetLossPct else currentStats.packetLossPct,
                            minRtt = event.minRtt ?: currentStats.minRtt,
                            avgRtt = event.avgRtt ?: currentStats.avgRtt,
                            maxRtt = event.maxRtt ?: currentStats.maxRtt,
                            stddevRtt = event.stddevRtt ?: currentStats.stddevRtt,
                        )
                    )
                }
            }

            is PingEvent.DnsResolutionFailed -> {
                _state.update {
                    it.copy(
                        isResolving = false,
                        error = PingError.DnsFailure(event.host),
                    )
                }
            }

            is PingEvent.Error -> {
                val error = when {
                    event.message.contains("not available") -> PingError.NoPingBinary
                    event.message.contains("network", ignoreCase = true) -> PingError.NoNetwork
                    else -> PingError.General(event.message)
                }
                _state.update {
                    it.copy(isResolving = false, error = error)
                }
            }
        }
    }

    /**
     * O(1) incremental stats update for each successful packet.
     * Uses running accumulators (rttSum, rttSumOfSquares) instead of
     * iterating the full results list on every packet.
     */
    private fun computeStatsIncremental(
        newRtt: Float,
        latestJitter: Float?,
        currentStats: PingStats,
    ): PingStats {
        val sent = currentStats.packetsSent + 1
        val received = currentStats.packetsReceived + 1
        val lossPct = ((sent - received).toFloat() / sent) * 100f

        rttSum += newRtt.toDouble()
        rttSumOfSquares += newRtt.toDouble() * newRtt.toDouble()

        val newMin = currentStats.minRtt?.let { minOf(it, newRtt) } ?: newRtt
        val newMax = currentStats.maxRtt?.let { maxOf(it, newRtt) } ?: newRtt
        val newAvg = (rttSum / received).toFloat()
        val newStddev = if (received > 1) {
            val mean = rttSum / received
            val variance = (rttSumOfSquares / received) - (mean * mean)
            if (variance > 0) kotlin.math.sqrt(variance).toFloat() else 0f
        } else null

        // Running jitter: use exponential moving average
        val jitter = if (latestJitter != null) {
            val prevJitter = currentStats.jitter ?: latestJitter
            prevJitter * 0.8f + latestJitter * 0.2f
        } else currentStats.jitter

        return PingStats(
            packetsSent = sent,
            packetsReceived = received,
            packetLossPct = lossPct,
            minRtt = newMin,
            avgRtt = newAvg,
            maxRtt = newMax,
            stddevRtt = newStddev,
            jitter = jitter,
        )
    }

    // Thread-safe batch access using Mutex
    private suspend fun batchOrInsertResult(result: PingResultEntity, sessionId: Long) {
        val shouldBatch = _state.value.interval < 0.5f
        if (shouldBatch) {
            val toFlush: List<PingResultEntity>?
            resultBatchMutex.withLock {
                resultBatch.add(result)
                toFlush = if (resultBatch.size >= 10) {
                    val copy = resultBatch.toList()
                    resultBatch.clear()
                    copy
                } else {
                    null
                }
            }
            if (toFlush != null) {
                pingRepository.insertResults(toFlush)
            }
        } else {
            pingRepository.insertResult(result)
        }

        // Ring buffer: cap at 50,000 results per session
        val count = pingRepository.getResultCount(sessionId)
        if (count > 50_000) {
            pingRepository.deleteOldestResults(sessionId, count - 50_000)
        }
    }

    // Thread-safe flush using Mutex
    private suspend fun flushResultBatch(sessionId: Long) {
        val toFlush: List<PingResultEntity>
        resultBatchMutex.withLock {
            toFlush = resultBatch.toList()
            resultBatch.clear()
        }
        if (toFlush.isNotEmpty()) {
            pingRepository.insertResults(toFlush)
        }
    }

    private fun lookupIpInfo(ip: String) {
        viewModelScope.launch {
            val reverseDns = DnsUtils.reverseLookup(ip)
            val country = geoLocationService.getCountry(ip)
            val asn = geoLocationService.getAsn(ip)

            _state.update { state ->
                state.copy(
                    ipInfo = state.ipInfo.copy(
                        reverseDns = reverseDns,
                        countryCode = country?.countryCode,
                        countryName = country?.countryName,
                        asn = asn?.let { "AS${it.asn}" },
                        orgName = asn?.asnOrg,
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPing()
    }
}
