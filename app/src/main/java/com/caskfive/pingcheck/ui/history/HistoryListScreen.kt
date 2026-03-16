package com.caskfive.pingcheck.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caskfive.pingcheck.data.db.HistoryViewItem
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import com.caskfive.pingcheck.data.db.TracerouteHopEntity
import com.caskfive.pingcheck.data.db.TracerouteSessionEntity
import com.caskfive.pingcheck.ui.components.LatencyChart
import com.caskfive.pingcheck.ui.components.LatencyDataPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryListScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle CSV export when content is ready
    state.csvExportContent?.let { csvData ->
        LaunchedEffect(csvData) {
            shareCsv(context, csvData.fileName, csvData.csvContent)
            viewModel.clearCsvExport()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by host or IP") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryFilterType.entries.forEach { filterType ->
                FilterChip(
                    selected = state.filterType == filterType,
                    onClick = { viewModel.onFilterTypeChanged(filterType) },
                    label = { Text(filterType.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.searchQuery.isNotEmpty()) {
                            "No results found"
                        } else {
                            "No history yet"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                HistoryList(
                    items = state.items,
                    onItemClick = viewModel::showDetail,
                    onItemDismissed = viewModel::deleteItem,
                )
            }
        }
    }

    // Detail dialog
    state.detailItem?.let { detail ->
        HistoryDetailDialog(
            detail = detail,
            onDismiss = viewModel::dismissDetail,
            onExportCsv = { session, results ->
                viewModel.exportPingSessionCsv(session, results)
            },
            context = context,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(
    items: List<HistoryViewItem>,
    onItemClick: (HistoryViewItem) -> Unit,
    onItemDismissed: (HistoryViewItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { "${it.type}_${it.id}" }) { item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        onItemDismissed(item)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color by animateColorAsState(
                        targetValue = when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        label = "dismissBackground",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
                enableDismissFromStartToEnd = false,
            ) {
                HistoryItemCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryViewItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.type == "ping") Icons.Default.NetworkPing else Icons.Default.Route,
                contentDescription = item.type,
                modifier = Modifier.size(28.dp),
                tint = if (item.type == "ping") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.targetHost,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.resolvedIp?.let { ip ->
                    Text(
                        text = ip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatTimestamp(item.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            item.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailDialog(
    detail: HistoryDetailState,
    onDismiss: () -> Unit,
    onExportCsv: (PingSessionEntity, List<PingResultEntity>) -> Unit,
    context: Context,
) {
    val detailText = when (detail) {
        is HistoryDetailState.PingDetail -> buildPingDetailText(detail.session, detail.results)
        is HistoryDetailState.TracerouteDetail -> buildTracerouteDetailText(detail.session, detail.hops)
    }

    val title = when (detail) {
        is HistoryDetailState.PingDetail -> "Ping: ${detail.session.targetHost}"
        is HistoryDetailState.TracerouteDetail -> "Traceroute: ${detail.session.targetHost}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Show latency chart for ping session detail
                if (detail is HistoryDetailState.PingDetail && detail.results.isNotEmpty()) {
                    val chartDataPoints = detail.results.map { result ->
                        LatencyDataPoint(
                            sequenceNumber = result.sequenceNumber,
                            rttMs = result.rttMs,
                        )
                    }
                    LatencyChart(
                        dataPoints = chartDataPoints,
                        collapsible = false,
                        maxDisplayPoints = 500,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = detailText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            Row {
                // CSV export button for ping sessions
                if (detail is HistoryDetailState.PingDetail) {
                    IconButton(onClick = {
                        onExportCsv(detail.session, detail.results)
                    }) {
                        Icon(Icons.Default.Description, contentDescription = "Export CSV")
                    }
                }
                IconButton(onClick = {
                    copyToClipboard(context, title, detailText)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
                IconButton(onClick = {
                    shareText(context, title, detailText)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

private fun buildPingDetailText(
    session: PingSessionEntity,
    results: List<PingResultEntity>,
): String {
    val sb = StringBuilder()
    sb.appendLine("Target: ${session.targetHost}")
    session.resolvedIp?.let { sb.appendLine("IP: $it") }
    sb.appendLine("Started: ${formatTimestamp(session.startTime)}")
    session.endTime?.let { sb.appendLine("Ended: ${formatTimestamp(it)}") }
    sb.appendLine("Config: count=${session.countSetting}, interval=${session.intervalSetting}s, size=${session.packetSizeSetting}B, timeout=${session.timeoutSetting}s")
    sb.appendLine()

    // Stats summary
    sb.appendLine("--- Statistics ---")
    sb.appendLine("Packets: ${session.packetsSent} sent, ${session.packetsReceived} received, ${"%.1f".format(session.packetLossPct)}% loss")
    session.minRtt?.let { sb.append("Min: ${"%.1f".format(it)} ms  ") }
    session.avgRtt?.let { sb.append("Avg: ${"%.1f".format(it)} ms  ") }
    session.maxRtt?.let { sb.append("Max: ${"%.1f".format(it)} ms") }
    if (session.minRtt != null) sb.appendLine()
    session.stddevRtt?.let { sb.append("StdDev: ${"%.1f".format(it)} ms  ") }
    session.jitterRtt?.let { sb.append("Jitter: ${"%.1f".format(it)} ms") }
    if (session.stddevRtt != null || session.jitterRtt != null) sb.appendLine()
    sb.appendLine()

    // Individual results
    sb.appendLine("--- Results ---")
    results.forEach { result ->
        if (result.isSuccess) {
            sb.appendLine("seq=${result.sequenceNumber} ttl=${result.ttl ?: "-"} time=${"%.1f".format(result.rttMs)} ms")
        } else {
            sb.appendLine("seq=${result.sequenceNumber} Request timeout")
        }
    }

    return sb.toString()
}

private fun buildTracerouteDetailText(
    session: TracerouteSessionEntity,
    hops: List<TracerouteHopEntity>,
): String {
    val sb = StringBuilder()
    sb.appendLine("Target: ${session.targetHost}")
    session.resolvedIp?.let { sb.appendLine("IP: $it") }
    sb.appendLine("Started: ${formatTimestamp(session.startTime)}")
    session.endTime?.let { sb.appendLine("Ended: ${formatTimestamp(it)}") }
    sb.appendLine("Max hops: ${session.maxHops}, Timeout: ${session.timeoutSetting}s")
    sb.appendLine("Status: ${if (session.isComplete) "Complete" else "Incomplete"}")
    sb.appendLine()

    // Hop list
    sb.appendLine("--- Hops ---")
    hops.forEach { hop ->
        val hopNum = "%2d".format(hop.hopNumber)
        if (hop.isTimeout) {
            sb.appendLine("$hopNum  * * *")
        } else {
            val host = hop.hostname ?: hop.ipAddress ?: "???"
            val ip = if (hop.hostname != null && hop.ipAddress != null) " (${hop.ipAddress})" else ""
            val rtt = hop.rttMs?.let { "${"%.1f".format(it)} ms" } ?: "*"
            val org = hop.orgName?.let { " [$it]" } ?: ""
            sb.appendLine("$hopNum  $host$ip  $rtt$org")
        }
    }

    return sb.toString()
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share results"))
}

// Share CSV content via text/csv intent
private fun shareCsv(context: Context, fileName: String, csvContent: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TEXT, csvContent)
    }
    context.startActivity(Intent.createChooser(intent, "Export CSV"))
}
