package com.caskfive.pingcheck.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
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

@OptIn(FlowPreview::class)
@Composable
private fun LatencyChartContent(
    dataPoints: List<LatencyDataPoint>,
    maxDisplayPoints: Int,
) {
    val configuration = LocalConfiguration.current
    val chartHeight = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 120.dp else 200.dp

    val modelProducer = remember { CartesianChartModelProducer() }

    val chartColor = MaterialTheme.colorScheme.primary
    val showPoints = dataPoints.count { it.rttMs != null } <= 50

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

    val scrollState = rememberVicoScrollState(
        initialScroll = Scroll.Absolute.End,
        autoScroll = Scroll.Absolute.End,
        autoScrollCondition = AutoScrollCondition.OnModelGrowth,
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(chartColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(chartColor.copy(alpha = 0.15f))
                        ),
                        pointProvider = if (showPoints) {
                            LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.Point(
                                    rememberShapeComponent(Fill(chartColor), CircleShape)
                                )
                            )
                        } else {
                            null
                        },
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        scrollState = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight),
    )
}
