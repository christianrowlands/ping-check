package com.caskfive.pingcheck.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caskfive.pingcheck.data.db.HistoryViewItem
import com.caskfive.pingcheck.ui.TRACEROUTE_ENABLED
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import com.caskfive.pingcheck.data.db.TracerouteHopEntity
import com.caskfive.pingcheck.data.db.TracerouteSessionEntity
import com.caskfive.pingcheck.data.preferences.PreferencesManager
import com.caskfive.pingcheck.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class HistoryFilterType(val label: String) {
    ALL("All"),
    PING("Ping"),
    TRACEROUTE("Traceroute"),
}

data class HistoryScreenState(
    val items: List<HistoryViewItem> = emptyList(),
    val searchQuery: String = "",
    val filterType: HistoryFilterType = HistoryFilterType.ALL,
    val isLoading: Boolean = true,
    val detailItem: HistoryDetailState? = null,
    val csvExportContent: CsvExportData? = null,
    val sparklineData: Map<String, List<Float>> = emptyMap(),
    val hopPathData: Map<String, List<Boolean>> = emptyMap(),
    val showDeleteAllConfirmation: Boolean = false,
)

// Data class to hold CSV export content ready for sharing
data class CsvExportData(
    val fileName: String,
    val csvContent: String,
)

sealed class HistoryDetailState {
    data class PingDetail(
        val session: PingSessionEntity,
        val results: List<PingResultEntity>,
    ) : HistoryDetailState()

    data class TracerouteDetail(
        val session: TracerouteSessionEntity,
        val hops: List<TracerouteHopEntity>,
    ) : HistoryDetailState()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryScreenState())
    val state: StateFlow<HistoryScreenState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        performCleanup()
        observeHistory()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        observeHistory()
    }

    fun onFilterTypeChanged(filterType: HistoryFilterType) {
        _state.update { it.copy(filterType = filterType) }
        observeHistory()
    }

    fun deleteItem(item: HistoryViewItem) {
        viewModelScope.launch {
            historyRepository.deleteSession(item.id, item.type)
        }
    }

    fun showDeleteAllConfirmation() {
        _state.update { it.copy(showDeleteAllConfirmation = true) }
    }

    fun dismissDeleteAllConfirmation() {
        _state.update { it.copy(showDeleteAllConfirmation = false) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            historyRepository.deleteAll()
            _state.update { it.copy(showDeleteAllConfirmation = false) }
        }
    }

    fun bulkDeleteBefore(beforeTimestamp: Long) {
        viewModelScope.launch {
            historyRepository.deleteSessionsBefore(beforeTimestamp)
        }
    }

    fun showDetail(item: HistoryViewItem) {
        viewModelScope.launch {
            val detail = when (item.type) {
                "ping" -> {
                    val session = historyRepository.getPingSession(item.id)
                    val results = historyRepository.getPingResults(item.id)
                    if (session != null) {
                        HistoryDetailState.PingDetail(session, results)
                    } else null
                }
                "traceroute" -> {
                    val session = historyRepository.getTracerouteSession(item.id)
                    val hops = historyRepository.getTracerouteHops(item.id)
                    if (session != null) {
                        HistoryDetailState.TracerouteDetail(session, hops)
                    } else null
                }
                else -> null
            }
            _state.update { it.copy(detailItem = detail) }
        }
    }

    fun dismissDetail() {
        _state.update { it.copy(detailItem = null) }
    }

    // Generate CSV export content for a ping session
    fun exportPingSessionCsv(session: PingSessionEntity, results: List<PingResultEntity>) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date(session.startTime))
        val fileName = "pingcheck_${session.targetHost}_$timestamp.csv"

        val csv = buildString {
            // Header
            appendLine("seq,timestamp,rtt_ms,ttl,bytes,status")
            // Data rows
            results.forEach { result ->
                val timestampStr = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.getDefault()
                ).format(Date(result.timestamp))
                val rttStr = result.rttMs?.let { "%.2f".format(it) } ?: ""
                val ttlStr = result.ttl?.toString() ?: ""
                val bytesStr = result.bytes?.toString() ?: ""
                val statusStr = if (result.isSuccess) "ok" else "timeout"
                appendLine("${result.sequenceNumber},$timestampStr,$rttStr,$ttlStr,$bytesStr,$statusStr")
            }
        }

        _state.update { it.copy(csvExportContent = CsvExportData(fileName, csv)) }
    }

    // Clear CSV export data after it has been shared
    fun clearCsvExport() {
        _state.update { it.copy(csvExportContent = null) }
    }

    private fun observeHistory() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val query = _state.value.searchQuery.trim()
            val filter = _state.value.filterType

            val flow = when {
                query.isNotEmpty() -> historyRepository.search(query)
                filter == HistoryFilterType.PING -> historyRepository.observeByType("ping")
                filter == HistoryFilterType.TRACEROUTE -> historyRepository.observeByType("traceroute")
                else -> historyRepository.observeAll()
            }

            flow.collectLatest { items ->
                val filtered = if (query.isNotEmpty() && filter != HistoryFilterType.ALL) {
                    items.filter { it.type == filter.name.lowercase() }
                } else {
                    items
                }.let { list ->
                    if (!TRACEROUTE_ENABLED) list.filter { it.type != "traceroute" } else list
                }

                // Fetch sparkline / hop path data for visible items
                val sparklineMap = mutableMapOf<String, List<Float>>()
                val hopPathMap = mutableMapOf<String, List<Boolean>>()

                val deferreds = filtered.map { item ->
                    val key = "${item.type}_${item.id}"
                    async {
                        when (item.type) {
                            "ping" -> {
                                val rttValues = historyRepository.getRecentRttValues(item.id, 4)
                                val nonNull = rttValues.filterNotNull()
                                if (nonNull.isNotEmpty()) {
                                    synchronized(sparklineMap) {
                                        sparklineMap[key] = nonNull.reversed()
                                    }
                                }
                            }
                            "traceroute" -> {
                                val flags = historyRepository.getHopTimeoutFlags(item.id, 6)
                                if (flags.isNotEmpty()) {
                                    synchronized(hopPathMap) {
                                        hopPathMap[key] = flags
                                    }
                                }
                            }
                        }
                    }
                }
                deferreds.awaitAll()

                _state.update {
                    it.copy(
                        items = filtered,
                        isLoading = false,
                        sparklineData = sparklineMap,
                        hopPathData = hopPathMap,
                    )
                }
            }
        }
    }

    private fun performCleanup() {
        viewModelScope.launch {
            val prefs = preferencesManager.preferences.first()
            val retentionDays = prefs.historyRetentionDays
            if (retentionDays > 0) {
                val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
                historyRepository.deleteSessionsBefore(cutoff)
            }
        }
    }

    companion object {
        fun formatRelativeTimestamp(timestampMs: Long): String {
            val now = System.currentTimeMillis()
            val diffMs = now - timestampMs
            val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

            return when {
                diffMinutes < 1 -> "just now"
                diffMinutes < 60 -> "$diffMinutes min ago"
                diffHours < 24 -> "$diffHours hr ago"
                diffDays < 7 -> "$diffDays days ago"
                else -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                    val monthFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                    monthFormat.format(cal.time)
                }
            }
        }
    }
}
