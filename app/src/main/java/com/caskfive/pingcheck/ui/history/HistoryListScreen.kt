package com.caskfive.pingcheck.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.caskfive.pingcheck.ui.TRACEROUTE_ENABLED
import com.caskfive.pingcheck.ui.components.LatencyChart
import com.caskfive.pingcheck.ui.components.LatencyDataPoint
import com.caskfive.pingcheck.ui.theme.LatencyGoodDark
import com.caskfive.pingcheck.ui.theme.LatencyModerateDark
import com.caskfive.pingcheck.ui.theme.LatencyPoorDark
import com.caskfive.pingcheck.ui.theme.MutedDark
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
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryFilterType.entries
                .filter { TRACEROUTE_ENABLED || it != HistoryFilterType.TRACEROUTE }
                .forEach { filterType ->
                FilterChip(
                    selected = state.filterType == filterType,
                    onClick = { viewModel.onFilterTypeChanged(filterType) },
                    label = { Text(filterType.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                IconButton(onClick = viewModel::showDeleteAllConfirmation) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "Delete all history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    sparklineData = state.sparklineData,
                    hopPathData = state.hopPathData,
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

    // Delete all confirmation dialog
    if (state.showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAllConfirmation,
            title = { Text("Delete all history?") },
            text = {
                Text(
                    if (TRACEROUTE_ENABLED) "This will permanently delete all ping and traceroute history. This action cannot be undone."
                    else "This will permanently delete all ping history. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteAllConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(
    items: List<HistoryViewItem>,
    sparklineData: Map<String, List<Float>>,
    hopPathData: Map<String, List<Boolean>>,
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
                val key = "${item.type}_${item.id}"
                HistoryItemCard(
                    item = item,
                    sparklineValues = sparklineData[key],
                    hopTimeoutFlags = hopPathData[key],
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

/**
 * Determines the quality color for a ping session based on latency and packet loss.
 */
private fun getPingQualityColor(avgRtt: Float?, packetLossPct: Float?): Color {
    val rtt = avgRtt ?: return MutedDark
    val loss = packetLossPct ?: 0f
    return when {
        rtt > 100f || loss > 25f -> LatencyPoorDark
        rtt >= 50f || loss >= 5f -> LatencyModerateDark
        else -> LatencyGoodDark
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryViewItem,
    sparklineValues: List<Float>?,
    hopTimeoutFlags: List<Boolean>?,
    onClick: () -> Unit,
) {
    val isPing = item.type == "ping"
    val qualityColor = if (isPing) {
        getPingQualityColor(item.avgRtt, item.packetLossPct)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
            // Quality indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(qualityColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Main content
            Column(modifier = Modifier.weight(1f)) {
                // Primary line: target host + key metric
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.targetHost,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPing) {
                            item.avgRtt?.let { "${"%.1f".format(it)} ms" } ?: "N/A"
                        } else {
                            item.hopCount?.let { "$it hops" } ?: "N/A"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = qualityColor,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Secondary line: resolved IP + relative time on left, mini viz + packet count on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: resolved IP + relative timestamp
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        val secondaryParts = mutableListOf<String>()
                        item.resolvedIp?.let { secondaryParts.add("($it)") }
                        secondaryParts.add(
                            HistoryViewModel.formatRelativeTimestamp(item.startTime)
                        )
                        Text(
                            text = secondaryParts.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: mini visualization + packet count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isPing && sparklineValues != null && sparklineValues.isNotEmpty()) {
                            MiniSparkline(
                                values = sparklineValues,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = 48.dp, height = 20.dp),
                            )
                        } else if (!isPing && hopTimeoutFlags != null && hopTimeoutFlags.isNotEmpty()) {
                            MiniHopPath(
                                timeoutFlags = hopTimeoutFlags,
                                totalHopCount = item.hopCount ?: hopTimeoutFlags.size,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = 48.dp, height = 20.dp),
                            )
                        }

                        if (isPing && item.packetsSent != null && item.packetsReceived != null) {
                            Text(
                                text = "${item.packetsReceived}/${item.packetsSent}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mini sparkline bar chart for ping sessions.
 * Shows 4 bars representing recent RTT values, with height relative to the session min/max range.
 */
@Composable
private fun MiniSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val barColor = color.copy(alpha = 0.6f)

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        val barCount = 4
        val gap = 2.dp.toPx()
        val cornerRadius = 2.dp.toPx()
        val totalGaps = (barCount - 1) * gap
        val barWidth = (size.width - totalGaps) / barCount

        // Bucket the values into 4 groups and average each
        val bucketSize = values.size.toFloat() / barCount
        val bucketAverages = (0 until barCount).map { i ->
            val startIdx = (i * bucketSize).toInt()
            val endIdx = ((i + 1) * bucketSize).toInt().coerceAtMost(values.size)
            if (startIdx < endIdx) {
                values.subList(startIdx, endIdx).average().toFloat()
            } else {
                values.lastOrNull() ?: 0f
            }
        }

        val minVal = bucketAverages.min()
        val maxVal = bucketAverages.max()
        val range = (maxVal - minVal).coerceAtLeast(0.1f)

        bucketAverages.forEachIndexed { index, avg ->
            // Normalize to 0.15..1.0 range so even the smallest bar is visible
            val normalizedHeight = ((avg - minVal) / range).coerceIn(0f, 1f)
            val barHeight = (0.15f + normalizedHeight * 0.85f) * size.height
            val x = index * (barWidth + gap)
            val y = size.height - barHeight

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )
        }
    }
}

/**
 * Mini hop path visualization for traceroute sessions.
 * Shows dots connected by lines. Timeout hops are hollow, normal hops are filled.
 * If more than 6 hops, shows first 2 + gap indicator + last 3.
 */
@Composable
private fun MiniHopPath(
    timeoutFlags: List<Boolean>,
    totalHopCount: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (timeoutFlags.isEmpty()) return@Canvas

        val dotRadius = 2.5.dp.toPx()
        val lineWidth = 1.dp.toPx()
        val centerY = size.height / 2f

        // Determine which dots to show
        val showFlags: List<Boolean?>
        val needsGap = totalHopCount > 6 && timeoutFlags.size > 5

        if (needsGap) {
            // Show first 2, gap (null), last 3
            val first2 = timeoutFlags.take(2)
            val last3 = timeoutFlags.takeLast(3)
            showFlags = first2 + listOf(null) + last3
        } else {
            showFlags = timeoutFlags.map { it as Boolean? }
        }

        val dotCount = showFlags.size
        if (dotCount <= 1) {
            // Single dot
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(size.width / 2f, centerY),
            )
            return@Canvas
        }

        val spacing = (size.width - dotRadius * 2) / (dotCount - 1).coerceAtLeast(1)

        showFlags.forEachIndexed { index, isTimeout ->
            val cx = dotRadius + index * spacing
            val cy = centerY

            // Draw connecting line to previous dot (if not the first)
            if (index > 0) {
                val prevCx = dotRadius + (index - 1) * spacing
                val prevIsGap = showFlags[index - 1] == null
                val currentIsGap = isTimeout == null

                if (!prevIsGap && !currentIsGap) {
                    drawLine(
                        color = color.copy(alpha = 0.4f),
                        start = Offset(prevCx + dotRadius, cy),
                        end = Offset(cx - dotRadius, cy),
                        strokeWidth = lineWidth,
                    )
                } else if (prevIsGap || currentIsGap) {
                    // Dashed/gap indicator: draw small dots
                    val midX = (prevCx + cx) / 2f
                    drawCircle(
                        color = color.copy(alpha = 0.3f),
                        radius = 1.dp.toPx(),
                        center = Offset(midX, cy),
                    )
                }
            }

            when (isTimeout) {
                null -> {
                    // Gap indicator: draw a small ellipsis dot
                    drawCircle(
                        color = color.copy(alpha = 0.3f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
                true -> {
                    // Timeout: hollow dot
                    drawCircle(
                        color = color,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = lineWidth),
                    )
                }
                false -> {
                    // Normal: filled dot
                    drawCircle(
                        color = color,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                    )
                }
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
