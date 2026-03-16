package com.caskfive.pingcheck.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
) {
    if (ipInfo.resolvedIp == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "IP Information",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

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
