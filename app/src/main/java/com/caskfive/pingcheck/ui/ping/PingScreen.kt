package com.caskfive.pingcheck.ui.ping

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caskfive.pingcheck.ui.components.IpInfoCard
import com.caskfive.pingcheck.ui.components.LatencyChart
import com.caskfive.pingcheck.ui.components.LatencyDataPoint
import com.caskfive.pingcheck.util.InputValidator

@Composable
fun PingScreen(
    viewModel: PingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasInteracted by remember { mutableStateOf(false) }


    // Validation state
    val isValidationError = hasInteracted &&
            state.targetHost.isNotEmpty() &&
            !InputValidator.isValidHost(state.targetHost)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // === Fixed top section: input area + StatsCard ===

        // Target input with star toggle
        OutlinedTextField(
            value = state.targetHost,
            onValueChange = {
                hasInteracted = true
                viewModel.onTargetHostChanged(it)
            },
            modifier = Modifier
                .fillMaxWidth(),
            label = { Text("Host or IP address") },
            placeholder = { Text("8.8.8.8 or google.com") },
            singleLine = true,
            isError = isValidationError,
            supportingText = if (isValidationError) {
                { Text("Invalid hostname or IP address") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(
                onGo = {
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

        // Favorites chips row
        if (state.favorites.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.favorites) { fav ->
                    AssistChip(
                        onClick = { viewModel.onTargetHostChanged(fav.host) },
                        label = { Text(fav.displayName ?: fav.host, maxLines = 1) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advanced settings toggle
        TextButton(onClick = viewModel::toggleAdvancedSettings) {
            Text(if (state.showAdvancedSettings) "Hide Settings" else "Advanced Settings")
            Icon(
                if (state.showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (state.showAdvancedSettings) "Collapse settings" else "Expand settings",
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = state.showAdvancedSettings) {
            AdvancedSettings(state, viewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Start/Stop button
        Button(
            onClick = { if (state.isRunning) viewModel.stopPing() else viewModel.startPing() },
            modifier = Modifier.fillMaxWidth(),
            colors = if (state.isRunning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Icon(
                if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (state.isRunning) "Stop ping" else "Start ping",
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isRunning) "STOP" else "START")
        }

        // Error display
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            ErrorCard(error, onRetry = { viewModel.startPing() })
        }

        // Stats card (sticky - stays in fixed top section)
        if (state.results.isNotEmpty() || state.isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            StatsCard(state.stats)
        }

        // === Scrollable bottom section: chart + IP info + copy/share + ResultsList ===
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Latency chart
            if (state.results.isNotEmpty()) {
                item(key = "chart") {
                    Spacer(modifier = Modifier.height(8.dp))
                    LatencyChart(
                        dataPoints = state.results.map { result ->
                            LatencyDataPoint(
                                sequenceNumber = result.sequenceNumber,
                                rttMs = result.rttMs,
                            )
                        },
                    )
                }
            }

            // IP Info card
            if (state.ipInfo.resolvedIp != null) {
                item(key = "ipinfo") {
                    Spacer(modifier = Modifier.height(8.dp))
                    IpInfoCard(ipInfo = state.ipInfo)
                }
            }

            // Copy/Share buttons
            if (state.results.isNotEmpty()) {
                item(key = "copy_share") {
                    Spacer(modifier = Modifier.height(8.dp))
                    CopyShareButtons(
                        resultsText = state.results.joinToString("\n") { it.text },
                        context = context,
                    )
                }
            }

            // Results list (inline items)
            if (state.results.isNotEmpty()) {
                item(key = "results_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.results, key = { "result_${it.sequenceNumber}" }) { result ->
                    SelectionContainer {
                        Text(
                            text = result.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = if (result.isSuccess) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    // Auto-scroll to bottom when new results arrive
    // (handled via the LazyColumn containing everything in one list)
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
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
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
private fun StatsCard(stats: PingStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Sent", stats.packetsSent.toString())
                StatItem("Recv", stats.packetsReceived.toString())
                StatItem("Loss", "%.1f%%".format(stats.packetLossPct))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Min", stats.minRtt?.let { "%.1f ms".format(it) } ?: "-")
                StatItem("Avg", stats.avgRtt?.let { "%.1f ms".format(it) } ?: "-")
                StatItem("Max", stats.maxRtt?.let { "%.1f ms".format(it) } ?: "-")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("StdDev", stats.stddevRtt?.let { "%.1f ms".format(it) } ?: "-")
                StatItem("Jitter", stats.jitter?.let { "%.1f ms".format(it) } ?: "-")
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ErrorCard(error: PingError, onRetry: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                is PingError.NoNetwork -> {
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

                is PingError.NoPingBinary -> {
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
