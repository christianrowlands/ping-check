package com.caskfive.pingcheck.ui.ping

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caskfive.pingcheck.ui.components.IpInfoCard
import com.caskfive.pingcheck.ui.components.LatencyChart
import com.caskfive.pingcheck.ui.components.LatencyDataPoint
import com.caskfive.pingcheck.ui.theme.HeroStatStyle
import com.caskfive.pingcheck.ui.theme.LatencyGoodDark
import com.caskfive.pingcheck.ui.theme.ResultRowStyle
import com.caskfive.pingcheck.ui.theme.StatsLabelStyle
import com.caskfive.pingcheck.ui.theme.StatsValueStyle
import com.caskfive.pingcheck.util.InputValidator

@Composable
fun PingScreen(
    viewModel: PingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var hasInteracted by remember { mutableStateOf(false) }

    // Reset IpInfoCard collapse when a new ping starts (results cleared + running)
    val ipInfoCollapseReset = state.results.isEmpty() && state.isRunning

    // Validation state
    val isValidationError = hasInteracted &&
            state.targetHost.isNotEmpty() &&
            !InputValidator.isValidHost(state.targetHost)

    // Animate button color between primary and error
    val buttonColor by animateColorAsState(
        targetValue = if (state.isRunning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "startStopButtonColor",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // === 1. Input row — host field + Start/Stop button side-by-side ===
        item(key = "input_row") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.targetHost,
                onValueChange = {
                    hasInteracted = true
                    viewModel.onTargetHostChanged(it)
                },
                modifier = Modifier.weight(1f),
                label = { Text("Host or IP address") },
                placeholder = { Text("8.8.8.8 or google.com") },
                singleLine = true,
                isError = isValidationError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboardController?.hide()
                        if (state.isRunning) viewModel.stopPing() else viewModel.startPing()
                    }
                ),
                leadingIcon = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (state.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                trailingIcon = {
                    if (state.isResolving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                },
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    if (state.isRunning) viewModel.stopPing() else viewModel.startPing()
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (state.isRunning) "Stop ping" else "Start ping",
                )
            }
        }
        }

        if (isValidationError) {
            item(key = "validation_error") {
                Text(
                    text = "Invalid hostname or IP address",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }

        // === 2. Favorites + Settings chips row ===
        if (state.favorites.isNotEmpty()) {
            item(key = "favorites") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.favorites) { fav ->
                    AssistChip(
                        onClick = { viewModel.onTargetHostChanged(fav.host) },
                        label = { Text(fav.displayName ?: fav.host, maxLines = 1) },
                    )
                }
                item(key = "settings_divider") {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                item(key = "settings_chip") {
                    val countDisplay = if (state.count == 0) "\u221E" else state.count.toString()
                    FilterChip(
                        selected = state.showAdvancedSettings,
                        onClick = viewModel::toggleAdvancedSettings,
                        label = {
                            Text(
                                "$countDisplay \u00D7 ${state.interval}s",
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (state.showAdvancedSettings) Icons.Default.Check else Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            }
        }

        // Advanced settings panel
        item(key = "advanced_settings") {
            AnimatedVisibility(visible = state.showAdvancedSettings) {
                AdvancedSettings(state, viewModel)
            }
        }

        // === 3. Error card ===
        state.error?.let { error ->
            item(key = "error") {
                Spacer(modifier = Modifier.height(12.dp))
                ErrorCard(error, onRetry = { viewModel.startPing() })
            }
        }

            // === 4. Dashboard card ===
            if (state.results.isNotEmpty() || state.isRunning) {
                item(key = "dashboard") {
                    Spacer(modifier = Modifier.height(12.dp))
                    DashboardCard(
                        results = state.results,
                        stats = state.stats,
                    )
                }
            }

            // === 5. IP Info bar ===
            if (state.ipInfo.resolvedIp != null) {
                item(key = "ipinfo") {
                    Spacer(modifier = Modifier.height(12.dp))
                    IpInfoCard(
                        ipInfo = state.ipInfo,
                        collapsible = true,
                        onCollapseReset = ipInfoCollapseReset,
                    )
                }
            }

            // === 6. Copy/Share buttons ===
            if (state.results.isNotEmpty()) {
                item(key = "copy_share") {
                    Spacer(modifier = Modifier.height(12.dp))
                    CopyShareButtons(
                        resultsText = state.results.joinToString("\n") { it.text },
                        context = context,
                    )
                }
            }

            // === 7. Results list — virtualized as individual items ===
            if (state.results.isNotEmpty()) {
                item(key = "results_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                itemsIndexed(
                    items = state.results,
                    key = { _, result -> result.sequenceNumber },
                ) { index, result ->
                    PingResultRow(result = result, showTopDivider = index > 0)
                }
            }
            if (state.isRunning && state.ipInfo.resolvedIp != null) {
                item(key = "results_waiting") {
                    if (state.results.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    WaitingRow(
                        nextSequence = state.results.lastOrNull()?.sequenceNumber?.plus(1) ?: 1,
                    )
                }
            }

            // === Completion summary ===
            if (!state.isRunning && state.results.isNotEmpty()) {
                item(key = "completion_summary") {
                    Spacer(modifier = Modifier.height(12.dp))
                    CompletionSummaryCard(state.stats)
                }
            }
    }
}

@Composable
private fun DashboardCard(
    results: List<PingResultDisplay>,
    stats: PingStats,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Hero row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: large avg latency
                val isAllLoss = stats.avgRtt == null && stats.packetsSent > 0
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (isAllLoss) "No response" else stats.avgRtt?.let { "%.1f".format(it) } ?: "\u2014",
                        style = HeroStatStyle,
                        color = if (isAllLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (!isAllLoss) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ms avg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }

                // Right: status badge (only show when packets have been sent)
                if (stats.packetsSent > 0) {
                    val badgeColor = when {
                        stats.packetsReceived == 0 ->
                            MaterialTheme.colorScheme.errorContainer
                        stats.packetLossPct > 0f ->
                            MaterialTheme.colorScheme.tertiaryContainer
                        else ->
                            MaterialTheme.colorScheme.primaryContainer
                    }
                    val badgeContentColor = when {
                        stats.packetsReceived == 0 ->
                            MaterialTheme.colorScheme.onErrorContainer
                        stats.packetLossPct > 0f ->
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else ->
                            MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    val badgeSymbol = if (stats.packetsReceived == 0) "\u2717" else "\u2713"

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "${stats.packetsReceived}/${stats.packetsSent} $badgeSymbol",
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeContentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Latency chart (memoize data point mapping)
            if (results.isNotEmpty()) {
                val chartDataPoints = remember(results) {
                    results.map { result ->
                        LatencyDataPoint(
                            sequenceNumber = result.sequenceNumber,
                            rttMs = result.rttMs,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LatencyChart(
                    dataPoints = chartDataPoints,
                    collapsible = false,
                )
            }

            // Divider between chart and stats
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    label = "MIN",
                    value = stats.minRtt?.let { "%.1f".format(it) } ?: "-",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                StatItem(
                    label = "AVG",
                    value = stats.avgRtt?.let { "%.1f".format(it) } ?: "-",
                    color = MaterialTheme.colorScheme.primary,
                )
                StatItem(
                    label = "MAX",
                    value = stats.maxRtt?.let { "%.1f".format(it) } ?: "-",
                    color = MaterialTheme.colorScheme.secondary,
                )
                StatItem(
                    label = "LOSS",
                    value = if (stats.packetsSent > 0) "%.1f%%".format(stats.packetLossPct) else "-",
                    color = if (stats.packetLossPct == 0f || stats.packetsSent == 0) {
                        LatencyGoodDark
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                StatItem(
                    label = "JITTER",
                    value = stats.jitter?.let { "%.1f".format(it) } ?: "-",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatItem(
                    label = "STDDEV",
                    value = stats.stddevRtt?.let { "%.1f".format(it) } ?: "-",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = StatsLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = StatsValueStyle,
            color = color,
        )
    }
}

@Composable
private fun PingResultRow(result: PingResultDisplay, showTopDivider: Boolean) {
    Column {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        SelectionContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${result.sequenceNumber}",
                    style = ResultRowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = result.ttl?.let { "ttl=$it" } ?: "",
                    style = ResultRowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (result.isSuccess && result.rttMs != null) {
                        "%.1f ms".format(result.rttMs)
                    } else {
                        "timeout"
                    },
                    style = ResultRowStyle,
                    color = if (result.isSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun WaitingRow(nextSequence: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$nextSequence",
            style = ResultRowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "waiting...",
            style = ResultRowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletionSummaryCard(stats: PingStats) {
    val isAllLoss = stats.packetsReceived == 0 && stats.packetsSent > 0
    val isHighLoss = stats.packetLossPct > 50f && !isAllLoss

    if (!isAllLoss && !isHighLoss) return

    val containerColor = if (isAllLoss) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isAllLoss) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val message = if (isAllLoss) {
        "Host unreachable \u2014 all ${stats.packetsSent} packets lost. The host may be down, blocking ICMP, or behind a firewall."
    } else {
        "High packet loss (${"%.1f".format(stats.packetLossPct)}%). The connection may be unstable."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = message,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CopyShareButtons(
    resultsText: String,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Ping Results", resultsText)
            clipboard.setPrimaryClip(clip)
        }) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy results",
            )
        }
        IconButton(onClick = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, resultsText)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Results"))
        }) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Share results",
            )
        }
    }
}

@Composable
private fun AdvancedSettings(state: PingScreenState, viewModel: PingViewModel) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = if (state.count == 0) "\u221e" else state.count.toString(),
                onValueChange = { viewModel.onCountChanged(it.toIntOrNull() ?: 4) },
                modifier = Modifier.weight(1f),
                label = { Text("Count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.interval.toString(),
                onValueChange = { viewModel.onIntervalChanged(it.toFloatOrNull() ?: 1.0f) },
                modifier = Modifier.weight(1f),
                label = { Text("Interval (s)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.packetSize.toString(),
                onValueChange = { viewModel.onPacketSizeChanged(it.toIntOrNull() ?: 56) },
                modifier = Modifier.weight(1f),
                label = { Text("Size (bytes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.timeout.toString(),
                onValueChange = { viewModel.onTimeoutChanged(it.toIntOrNull() ?: 10) },
                modifier = Modifier.weight(1f),
                label = { Text("Timeout (s)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun ErrorCard(error: PingError, onRetry: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Error-specific guidance
            when (error) {
                PingError.NoNetwork -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check your Wi-Fi or mobile data connection.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Text("Open Network Settings")
                    }
                }

                is PingError.DnsFailure -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please verify the hostname is spelled correctly and try again.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                PingError.NoPingBinary -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The ping binary is not available on this device. This can happen on some manufacturer-customized Android builds. Try restarting the app or device.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is PingError.General -> {
                    // No additional guidance for general errors
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
