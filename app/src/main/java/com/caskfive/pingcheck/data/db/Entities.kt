package com.caskfive.pingcheck.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ping_sessions",
    indices = [Index("start_time")]
)
data class PingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "target_host") val targetHost: String,
    @ColumnInfo(name = "resolved_ip") val resolvedIp: String? = null,
    @ColumnInfo(name = "is_ipv6") val isIpv6: Boolean = false,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "count_setting") val countSetting: Int,
    @ColumnInfo(name = "interval_setting") val intervalSetting: Float,
    @ColumnInfo(name = "packet_size_setting") val packetSizeSetting: Int,
    @ColumnInfo(name = "timeout_setting") val timeoutSetting: Int,
    @ColumnInfo(name = "packets_sent") val packetsSent: Int = 0,
    @ColumnInfo(name = "packets_received") val packetsReceived: Int = 0,
    @ColumnInfo(name = "packet_loss_pct") val packetLossPct: Float = 0f,
    @ColumnInfo(name = "min_rtt") val minRtt: Float? = null,
    @ColumnInfo(name = "avg_rtt") val avgRtt: Float? = null,
    @ColumnInfo(name = "max_rtt") val maxRtt: Float? = null,
    @ColumnInfo(name = "stddev_rtt") val stddevRtt: Float? = null,
    @ColumnInfo(name = "jitter_rtt") val jitterRtt: Float? = null,
)

@Entity(
    tableName = "ping_results",
    foreignKeys = [
        ForeignKey(
            entity = PingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class PingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Int,
    @ColumnInfo(name = "rtt_ms") val rttMs: Float? = null,
    val ttl: Int? = null,
    val bytes: Int? = null,
    val timestamp: Long,
    @ColumnInfo(name = "is_success") val isSuccess: Boolean,
)

@Entity(
    tableName = "traceroute_sessions",
    indices = [Index("start_time")]
)
data class TracerouteSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "target_host") val targetHost: String,
    @ColumnInfo(name = "resolved_ip") val resolvedIp: String? = null,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "max_hops") val maxHops: Int = 30,
    @ColumnInfo(name = "timeout_setting") val timeoutSetting: Int = 3,
    @ColumnInfo(name = "is_complete") val isComplete: Boolean = false,
)

@Entity(
    tableName = "traceroute_hops",
    foreignKeys = [
        ForeignKey(
            entity = TracerouteSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class TracerouteHopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "hop_number") val hopNumber: Int,
    @ColumnInfo(name = "ip_address") val ipAddress: String? = null,
    val hostname: String? = null,
    @ColumnInfo(name = "rtt_ms") val rttMs: Float? = null,
    @ColumnInfo(name = "country_code") val countryCode: String? = null,
    val asn: String? = null,
    @ColumnInfo(name = "org_name") val orgName: String? = null,
    @ColumnInfo(name = "is_timeout") val isTimeout: Boolean = false,
    @ColumnInfo(name = "is_destination") val isDestination: Boolean = false,
)

@Entity(
    tableName = "favorites",
    indices = [Index("sort_order")]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "last_count") val lastCount: Int? = null,
    @ColumnInfo(name = "last_interval") val lastInterval: Float? = null,
    @ColumnInfo(name = "last_packet_size") val lastPacketSize: Int? = null,
    @ColumnInfo(name = "last_timeout") val lastTimeout: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
