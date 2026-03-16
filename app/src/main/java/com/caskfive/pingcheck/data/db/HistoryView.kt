package com.caskfive.pingcheck.data.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView(
    viewName = "history_view",
    value = """
        SELECT id, 'ping' AS type, target_host, resolved_ip, start_time, end_time,
            packets_received || '/' || packets_sent || ' pkts, ' ||
            COALESCE(ROUND(avg_rtt, 1) || 'ms avg', 'N/A') AS summary
        FROM ping_sessions
        UNION ALL
        SELECT id, 'traceroute' AS type, target_host, resolved_ip, start_time, end_time,
            CASE WHEN is_complete THEN 'Complete' ELSE 'Incomplete' END AS summary
        FROM traceroute_sessions
        ORDER BY start_time DESC
    """
)
data class HistoryViewItem(
    val id: Long,
    val type: String,
    @ColumnInfo(name = "target_host") val targetHost: String,
    @ColumnInfo(name = "resolved_ip") val resolvedIp: String?,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    val summary: String?,
)
