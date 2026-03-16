package com.caskfive.pingcheck.ui.traceroute

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caskfive.pingcheck.util.InputValidator

@Composable
fun TracerouteScreen(
    viewModel: TracerouteViewModel = hiltViewModel(),
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
        // Target input
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
            enabled = !state.isRunning,
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
                    if (!state.isRunning) viewModel.startTrace()
                }
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Settings toggle
        TextButton(onClick = viewModel::toggleSettings) {
            Text(if (state.showSettings) "Hide Settings" else "Settings")
            Icon(
                if (state.showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (state.showSettings) "Collapse settings" else "Expand settings",
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = state.showSettings) {
            TracerouteSettings(state, viewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Start/Stop button
        Button(
            onClick = { if (state.isRunning) viewModel.stopTrace() else viewModel.startTrace() },
            modifier = Modifier.fillMaxWidth(),
            colors = if (state.isRunning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Icon(
                if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (state.isRunning) "Stop traceroute" else "Start traceroute",
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isRunning) "STOP" else "TRACE")
        }

        // Error display
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            TracerouteErrorCard(error, onRetry = { viewModel.startTrace() })
        }

        // Resolved IP
        state.resolvedIp?.let { ip ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tracing route to ${state.targetHost} ($ip)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Copy/Share buttons
        if (state.hops.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            CopyShareButtons(
                hops = state.hops,
                targetHost = state.targetHost,
                resolvedIp = state.resolvedIp,
                context = context,
            )
        }

        // Hop list
        if (state.hops.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HopHeader()
            HopList(
                hops = state.hops,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Take remaining space even when no hops
            Spacer(modifier = Modifier.weight(1f))
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
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
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

@Composable
private fun HopHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "#",
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "IP Address",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "RTT",
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HopList(
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
        modifier = modifier.fillMaxWidth(),
    ) {
        items(hops, key = { it.hopNumber }) { hop ->
            SelectionContainer {
                HopRow(hop)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun HopRow(hop: HopDisplay) {
    val textColor = when {
        hop.isDestination -> MaterialTheme.colorScheme.primary
        hop.isTimeout -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onBackground
    }

    val backgroundColor = if (hop.isDestination) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hop number
        Text(
            text = hop.hopNumber.toString(),
            modifier = Modifier.width(32.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )

        // IP / hostname / timeout
        Column(modifier = Modifier.weight(1f)) {
            if (hop.isTimeout) {
                Text(
                    text = "* * *",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = textColor,
                )
            } else {
                Text(
                    text = hop.ipAddress ?: "unknown",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = textColor,
                )
                hop.hostname?.let { hostname ->
                    Text(
                        text = hostname,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                hop.countryCode?.let { cc ->
                    Text(
                        text = cc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // RTT
        Text(
            text = when {
                hop.isTimeout -> "-"
                hop.rttMs != null -> "%.1f ms".format(hop.rttMs)
                else -> "-"
            },
            modifier = Modifier.width(72.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = textColor,
        )
    }
}
