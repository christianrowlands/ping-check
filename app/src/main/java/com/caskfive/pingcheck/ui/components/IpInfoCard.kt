package com.caskfive.pingcheck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class IpInfoState(
    val resolvedIp: String? = null,
    val reverseDns: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val asn: String? = null,
    val orgName: String? = null,
    val publicIp: String? = null,
)

@Composable
fun IpInfoCard(
    ipInfo: IpInfoState,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    onCollapseReset: Boolean = false,
) {
    if (ipInfo.resolvedIp == null) return

    var isExpanded by remember { mutableStateOf(!collapsible) }

    // Reset to collapsed when onCollapseReset changes to true
    LaunchedEffect(onCollapseReset) {
        if (onCollapseReset && collapsible) {
            isExpanded = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (collapsible) Modifier.clickable { isExpanded = !isExpanded }
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (collapsible && !isExpanded) {
                // Collapsed single-line view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildCollapsedSummary(ipInfo),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Expanded view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "IP Information",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (collapsible) {
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = "Collapse",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AnimatedVisibility(visible = true) {
                    Column {
                        InfoRow("IP", ipInfo.resolvedIp)
                        ipInfo.reverseDns?.let { InfoRow("Host", it) }
                        ipInfo.countryCode?.let { cc ->
                            InfoRow("Country", ipInfo.countryName?.let { "$it ($cc)" } ?: cc)
                        }
                        ipInfo.asn?.let { InfoRow("ASN", it) }
                        ipInfo.orgName?.let { InfoRow("Org", it) }
                        ipInfo.publicIp?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            InfoRow("Public IP", it)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds a single-line summary of available IP info fields separated by " · ".
 */
private fun buildCollapsedSummary(ipInfo: IpInfoState): String {
    return listOfNotNull(
        ipInfo.resolvedIp,
        ipInfo.reverseDns,
        ipInfo.countryCode,
        ipInfo.orgName,
    ).joinToString(" \u00B7 ")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
