package com.caskfive.pingcheck.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.caskfive.pingcheck.MainActivity
import com.caskfive.pingcheck.R
import com.caskfive.pingcheck.data.db.PingResultEntity
import com.caskfive.pingcheck.data.db.PingSessionEntity
import com.caskfive.pingcheck.domain.ping.PingConfig
import com.caskfive.pingcheck.domain.ping.PingEngine
import com.caskfive.pingcheck.domain.ping.PingEvent
import com.caskfive.pingcheck.repository.PingRepository
import com.caskfive.pingcheck.util.InputValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class PingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ping_channel"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_HOST = "extra_host"
        const val EXTRA_COUNT = "extra_count"
        const val EXTRA_INTERVAL = "extra_interval"
        const val EXTRA_PACKET_SIZE = "extra_packet_size"
        const val EXTRA_TIMEOUT = "extra_timeout"

        const val ACTION_STOP = "com.caskfive.pingcheck.STOP_PING"

        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 5000L
        private const val MAX_RESULTS = 50_000

        fun startIntent(
            context: Context,
            host: String,
            count: Int = 0,
            interval: Float = 1.0f,
            packetSize: Int = 56,
            timeout: Int = 10,
        ): Intent {
            return Intent(context, PingForegroundService::class.java).apply {
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_COUNT, count)
                putExtra(EXTRA_INTERVAL, interval)
                putExtra(EXTRA_PACKET_SIZE, packetSize)
                putExtra(EXTRA_TIMEOUT, timeout)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, PingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    @Inject
    lateinit var pingEngine: PingEngine

    @Inject
    lateinit var pingRepository: PingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pingJob: Job? = null
    private var sessionId: Long? = null

    // Stats tracking
    private var host: String = ""
    private var packetsSent: Int = 0
    private var packetsReceived: Int = 0
    private var totalRtt: Float = 0f
    private var lastRtt: Float? = null
    private var lastNotificationUpdate: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val intentHost = intent?.getStringExtra(EXTRA_HOST)
        if (intentHost.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!InputValidator.isValidHost(intentHost)) {
            stopSelf()
            return START_NOT_STICKY
        }

        host = intentHost
        val count = intent.getIntExtra(EXTRA_COUNT, 0)
        val interval = intent.getFloatExtra(EXTRA_INTERVAL, 1.0f)
        val packetSize = intent.getIntExtra(EXTRA_PACKET_SIZE, 56)
        val timeout = intent.getIntExtra(EXTRA_TIMEOUT, 10)

        val notification = buildNotification("Pinging $host...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPinging(
            PingConfig(
                host = host,
                count = count,
                intervalSeconds = interval,
                packetSizeBytes = packetSize,
                timeoutSeconds = timeout,
            )
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pingJob?.cancel()
        runBlocking {
            finishSession()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPinging(config: PingConfig) {
        pingJob?.cancel()
        packetsSent = 0
        packetsReceived = 0
        totalRtt = 0f
        lastRtt = null

        pingJob = serviceScope.launch {
            // Create DB session
            val session = PingSessionEntity(
                targetHost = config.host,
                startTime = System.currentTimeMillis(),
                countSetting = config.count,
                intervalSetting = config.intervalSeconds,
                packetSizeSetting = config.packetSizeBytes,
                timeoutSetting = config.timeoutSeconds,
            )
            val id = pingRepository.createSession(session)
            sessionId = id

            pingEngine.ping(config).collect { event ->
                handleEvent(event, id)
            }

            // Ping completed naturally
            finishSession()
            stopSelf()
        }
    }

    private suspend fun handleEvent(event: PingEvent, sessionId: Long) {
        when (event) {
            is PingEvent.Started -> {
                pingRepository.getSession(sessionId)?.let { existing ->
                    pingRepository.updateSession(
                        existing.copy(
                            resolvedIp = event.resolvedIp,
                            isIpv6 = event.isIpv6,
                        )
                    )
                }
            }

            is PingEvent.PacketReceived -> {
                packetsSent++
                packetsReceived++
                totalRtt += event.rttMs
                lastRtt = event.rttMs

                val result = PingResultEntity(
                    sessionId = sessionId,
                    sequenceNumber = event.sequenceNumber,
                    rttMs = event.rttMs,
                    ttl = event.ttl,
                    bytes = event.bytes,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = true,
                )
                pingRepository.insertResult(result)
                enforceResultCap(sessionId)
                maybeUpdateNotification()
            }

            is PingEvent.PacketLost -> {
                packetsSent++

                val result = PingResultEntity(
                    sessionId = sessionId,
                    sequenceNumber = event.sequenceNumber,
                    rttMs = null,
                    ttl = null,
                    bytes = null,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = false,
                )
                pingRepository.insertResult(result)
                enforceResultCap(sessionId)
                maybeUpdateNotification()
            }

            is PingEvent.Summary -> {
                // Use the final summary stats if provided
                if (event.packetsSent > 0) packetsSent = event.packetsSent
                if (event.packetsReceived > 0) packetsReceived = event.packetsReceived
            }

            is PingEvent.DnsResolutionFailed -> {
                updateNotification("Pinging $host - DNS resolution failed")
            }

            is PingEvent.Error -> {
                updateNotification("Pinging $host - Error: ${event.message}")
            }
        }
    }

    private fun maybeUpdateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate >= NOTIFICATION_UPDATE_INTERVAL_MS) {
            lastNotificationUpdate = now

            val avgRtt = if (packetsReceived > 0) totalRtt / packetsReceived else 0f
            val lossPct = if (packetsSent > 0) {
                ((packetsSent - packetsReceived).toFloat() / packetsSent) * 100f
            } else 0f

            val text = "Pinging $host — Avg: ${"%.1f".format(avgRtt)}ms, Loss: ${"%.0f".format(lossPct)}%"
            updateNotification(text)
        }
    }

    private suspend fun enforceResultCap(sessionId: Long) {
        val count = pingRepository.getResultCount(sessionId)
        if (count > MAX_RESULTS) {
            pingRepository.deleteOldestResults(sessionId, count - MAX_RESULTS)
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun finishSession() {
        val id = sessionId ?: return
        pingRepository.getSession(id)?.let { existing ->
            val avgRtt = if (packetsReceived > 0) totalRtt / packetsReceived else null
            val lossPct = if (packetsSent > 0) {
                ((packetsSent - packetsReceived).toFloat() / packetsSent) * 100f
            } else 0f

            pingRepository.updateSession(
                existing.copy(
                    endTime = System.currentTimeMillis(),
                    packetsSent = packetsSent,
                    packetsReceived = packetsReceived,
                    packetLossPct = lossPct,
                    avgRtt = avgRtt,
                )
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ping Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Continuous ping monitoring"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        // Tap intent -> open MainActivity
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Stop action
        val stopIntent = Intent(this, PingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PingCheck")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tapPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent,
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
