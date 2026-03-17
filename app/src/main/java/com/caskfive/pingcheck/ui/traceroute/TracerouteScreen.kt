package com.caskfive.pingcheck.ui.traceroute

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caskfive.pingcheck.ui.theme.LatencyGoodDark
import com.caskfive.pingcheck.ui.theme.MutedDark
import com.caskfive.pingcheck.ui.theme.ResultRowStyle
import com.caskfive.pingcheck.util.InputValidator

@Composable
fun TracerouteScreen(
    viewModel: TracerouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var hasInteracted by remember { mutableStateOf(false) }

    // Animate button color between primary and error
    val buttonColor by animateColorAsState(
        targetValue = if (state.isRunning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "startStopButtonColor",
    )

    // Validation state
    val isValidationError = hasInteracted &&
            state.targetHost.isNotEmpty() &&
            !InputValidator.isValidHost(state.targetHost)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // === 1. Input row — same pattern as Ping screen ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                enabled = !state.isRunning,
                isError = isValidationError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboardController?.hide()
                        if (!state.isRunning) viewModel.startTrace()
                    }
                ),
            )

            Button(
                onClick = {
                    keyboardController?.hide()
                    if (state.isRunning) viewModel.stopTrace() else viewModel.startTrace()
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (state.isRunning) "Stop traceroute" else "Start traceroute",
                )
            }
        }

        if (isValidationError) {
            Text(
                text = "Invalid hostname or IP address",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 2. Settings chip ===
        FilterChip(
            selected = state.showSettings,
            onClick = viewModel::toggleSettings,
            label = {
                Text("${state.maxHops} hops \u00B7 ${state.timeout}s timeout")
            },
            leadingIcon = {
                Icon(
                    if (state.showSettings) Icons.Default.Check else Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        AnimatedVisibility(visible = state.showSettings) {
            TracerouteSettings(state, viewModel)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 3. Error card ===
        state.error?.let { error ->
            TracerouteErrorCard(error, onRetry = { viewModel.startTrace() })
            Spacer(modifier = Modifier.height(12.dp))
        }

        // === 4. Summary bar (shown when hops exist) ===
        if (state.hops.isNotEmpty()) {
            SummaryBar(
                targetHost = state.targetHost,
                resolvedIp = state.resolvedIp,
                hops = state.hops,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // === 5. Visual hop path ===
        if (state.hops.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                HopPathList(
                    hops = state.hops,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            }
        } else {
            // Take remaining space even when no hops
            Spacer(modifier = Modifier.weight(1f))
        }

        // === 6. Copy/Share buttons ===
        if (state.hops.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            CopyShareButtons(
                hops = state.hops,
                targetHost = state.targetHost,
                resolvedIp = state.resolvedIp,
                context = context,
            )
        }
    }
}

@Composable
private fun SummaryBar(
    targetHost: String,
    resolvedIp: String?,
    hops: List<HopDisplay>,
) {
    val lastNonTimeoutHop = hops.lastOrNull { !it.isTimeout && it.rttMs != null }
    val totalHops = hops.size
    val finalRtt = lastNonTimeoutHop?.rttMs

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: target -> resolvedIp
            Text(
                text = buildString {
                    append(targetHost)
                    resolvedIp?.let {
                        append(" \u2192 $it")
                    }
                },
                style = ResultRowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Right: badge "N hops . Xms"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = buildString {
                        append("$totalHops hops")
                        finalRtt?.let {
                            append(" \u00B7 ${"%.0f".format(it)}ms")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = ResultRowStyle,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HopPathList(
    hops: List<HopDisplay>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(hops.size) {
        if (hops.isNotEmpty()) {
            listState.animateScrollToItem(hops.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        items(hops, key = { it.hopNumber }) { hop ->
            val isLastHop = hop.hopNumber == hops.last().hopNumber
            SelectionContainer {
                HopPathRow(
                    hop = hop,
                    isLastHop = isLastHop,
                )
            }
        }
    }
}

@Composable
private fun HopPathRow(
    hop: HopDisplay,
    isLastHop: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Node indicator column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Dot
            val dotSize = if (hop.isDestination) 16.dp else 12.dp
            if (hop.isTimeout) {
                // Hollow circle for timeout
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .border(2.dp, MutedDark, CircleShape),
                )
            } else {
                // Filled circle
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }

            // Connecting line (not for last hop)
            if (!isLastHop) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                )
            }
        }

        // Content column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 12.dp, top = 2.dp),
        ) {
            if (hop.isTimeout) {
                // Timeout row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${hop.hopNumber}  * * *",
                        style = ResultRowStyle,
                        color = MutedDark,
                    )
                    Text(
                        text = "\u2014",
                        style = ResultRowStyle,
                        color = MutedDark,
                    )
                }
            } else {
                // IP + RTT row
                val textColor = if (hop.isDestination) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = hop.ipAddress ?: "unknown",
                        style = ResultRowStyle,
                        color = textColor,
                    )

                    // RTT with color coding
                    val rttText = hop.rttMs?.let { "%.1f ms".format(it) } ?: "\u2014"
                    val rttColor = when {
                        hop.isDestination -> MaterialTheme.colorScheme.primary
                        hop.rttMs == null -> MutedDark
                        hop.rttMs < 10f -> LatencyGoodDark
                        hop.rttMs < 50f -> MaterialTheme.colorScheme.primary
                        hop.rttMs < 100f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }

                    Text(
                        text = rttText,
                        style = ResultRowStyle,
                        fontWeight = FontWeight.Medium,
                        color = rttColor,
                    )
                }

                // Hostname (if available)
                hop.hostname?.let { hostname ->
                    Text(
                        text = hostname,
                        fontSize = 10.sp,
                        color = if (hop.isDestination) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MutedDark
                        },
                    )
                }

                // Country code (if available)
                hop.countryCode?.let { cc ->
                    Text(
                        text = cc,
                        fontSize = 10.sp,
                        color = if (hop.isDestination) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MutedDark
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyShareButtons(
    hops: List<HopDisplay>,
    targetHost: String,
    resolvedIp: String?,
    context: Context,
) {
    val resultsText = buildString {
        appendLine("Traceroute to $targetHost${resolvedIp?.let { " ($it)" } ?: ""}")
        hops.forEach { hop ->
            val ip = if (hop.isTimeout) "* * *" else (hop.ipAddress ?: "unknown")
            val rtt = when {
                hop.isTimeout -> "-"
                hop.rttMs != null -> "%.1f ms".format(hop.rttMs)
                else -> "-"
            }
            appendLine("${hop.hopNumber}\t$ip\t$rtt")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Traceroute Results", resultsText)
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
private fun TracerouteSettings(state: TracerouteScreenState, viewModel: TracerouteViewModel) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.maxHops.toString(),
                onValueChange = { viewModel.onMaxHopsChanged(it.toIntOrNull() ?: 30) },
                modifier = Modifier.weight(1f),
                label = { Text("Max Hops") },
                singleLine = true,
                enabled = !state.isRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.timeout.toString(),
                onValueChange = { viewModel.onTimeoutChanged(it.toIntOrNull() ?: 3) },
                modifier = Modifier.weight(1f),
                label = { Text("Timeout (s)") },
                singleLine = true,
                enabled = !state.isRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun TracerouteErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
