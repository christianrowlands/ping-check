package com.caskfive.pingcheck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

data class LatencyDataPoint(
    val sequenceNumber: Int,
    val rttMs: Float?,
)

@Composable
fun LatencyChart(
    dataPoints: List<LatencyDataPoint>,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    maxDisplayPoints: Int = 500,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (collapsible) {
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text("Latency Chart")
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }

        AnimatedVisibility(visible = isExpanded || !collapsible) {
            if (dataPoints.any { it.rttMs != null }) {
                Column {
                    LatencyChartContent(
                        dataPoints = dataPoints,
                        maxDisplayPoints = maxDisplayPoints,
                    )
                    // Legend row showing min/avg/max values
                    LatencyStatsLegend(dataPoints = dataPoints)
                }
            } else {
                Text(
                    text = "No data yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * Legend row displaying min, avg, and max RTT values below the chart.
 */
@Composable
private fun LatencyStatsLegend(dataPoints: List<LatencyDataPoint>) {
    val rtts = dataPoints.mapNotNull { it.rttMs }
    if (rtts.isEmpty()) return

    val minRtt = rtts.min()
    val avgRtt = rtts.sum() / rtts.size
    val maxRtt = rtts.max()
    val lossCount = dataPoints.count { it.rttMs == null }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendItem(label = "Min", value = "${"%.1f".format(minRtt)} ms", color = MaterialTheme.colorScheme.tertiary)
        LegendItem(label = "Avg", value = "${"%.1f".format(avgRtt)} ms", color = MaterialTheme.colorScheme.primary)
        LegendItem(label = "Max", value = "${"%.1f".format(maxRtt)} ms", color = MaterialTheme.colorScheme.secondary)
        if (lossCount > 0) {
            LegendItem(label = "Loss", value = "$lossCount pkt", color = Color.Red)
        }
    }
}

@Composable
private fun LegendItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun LatencyChartContent(
    dataPoints: List<LatencyDataPoint>,
    maxDisplayPoints: Int,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val chartColor = MaterialTheme.colorScheme.primary

    // Wrap parameter in State so snapshotFlow can observe changes
    val currentDataPoints by rememberUpdatedState(dataPoints)

    LaunchedEffect(Unit) {
        snapshotFlow { currentDataPoints }
            .debounce(250L)
            .collect { points ->
                val windowedData = if (points.size > maxDisplayPoints) {
                    points.takeLast(maxDisplayPoints)
                } else {
                    points
                }

                val successfulPoints = windowedData.filter { it.rttMs != null }

                if (successfulPoints.isEmpty()) return@collect

                modelProducer.runTransaction {
                    lineSeries {
                        series(
                            x = successfulPoints.map { it.sequenceNumber.toDouble() },
                            y = successfulPoints.map { it.rttMs!!.toDouble() },
                        )
                    }
                }
            }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(chartColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(chartColor.copy(alpha = 0.15f))
                        ),
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    )
}
