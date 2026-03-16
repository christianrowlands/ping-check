package com.caskfive.pingcheck.domain.ping

import com.caskfive.pingcheck.domain.process.ProcessExecutor
import com.caskfive.pingcheck.util.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PingEngine @Inject constructor(
    private val processExecutor: ProcessExecutor,
    private val binaryProvider: PingBinaryProvider,
) {
    companion object {
        private val PING_REPLY_REGEX = Regex(
            """(\d+)\s+bytes\s+from\s+([^:]+):\s+icmp_seq=(\d+)\s+ttl=(\d+)\s+time=(\d+\.?\d*)\s*ms"""
        )
        private val PING_STARTED_REGEX = Regex(
            """PING\s+\S+\s+\(([^)]+)\)"""
        )
        private val SUMMARY_PACKETS_REGEX = Regex(
            """(\d+)\s+packets?\s+transmitted,\s+(\d+)\s+(?:packets?\s+)?received.*?(\d+\.?\d*)%\s+packet\s+loss"""
        )
        private val SUMMARY_RTT_REGEX = Regex(
            """(?:rtt|round-trip)\s+min/avg/max/(?:mdev|stddev)\s+=\s+(\d+\.?\d*)/(\d+\.?\d*)/(\d+\.?\d*)/(\d+\.?\d*)\s*ms"""
        )
        private val REQUEST_TIMEOUT_REGEX = Regex(
            """(?:Request timeout|no answer yet) for icmp_seq[= ](\d+)"""
        )
        private val DNS_FAILURE_REGEX = Regex(
            """(?:unknown host|Name or service not known|Could not resolve hostname|bad address)""",
            RegexOption.IGNORE_CASE,
        )
    }

    fun ping(config: PingConfig): Flow<PingEvent> = callbackFlow {
        if (!InputValidator.isValidHost(config.host)) {
            trySend(PingEvent.Error("Invalid hostname: ${config.host}"))
            close()
            return@callbackFlow
        }

        val pingBinary = binaryProvider.findPingBinary()
        if (pingBinary == null) {
            trySend(PingEvent.Error("Ping binary not available on this device"))
            close()
            return@callbackFlow
        }

        val isIpv6 = config.host.contains(":")
        val command = buildPingCommand(config, pingBinary, isIpv6)

        val process = try {
            processExecutor.execute(command)
        } catch (e: Exception) {
            trySend(PingEvent.Error(e.message ?: "Failed to start ping"))
            close()
            return@callbackFlow
        }

        // Drain stderr in background
        launch(Dispatchers.IO) {
            try {
                process.stderr.forEachLine { /* drain */ }
            } catch (_: Exception) { }
        }

        // Read stdout on IO dispatcher
        launch(Dispatchers.IO) {
            var lastSeq = -1
            var emittedStarted = false
            var emittedDnsFailure = false

            try {
                process.stdout.forEachLine { line ->
                    if (!isActive) return@forEachLine

                    val result = parseLine(line, config, isIpv6, emittedStarted, lastSeq)
                    if (result != null) {
                        emittedStarted = emittedStarted || result.startedEmitted
                        lastSeq = result.lastSeq
                        result.events.forEach { event ->
                            if (event is PingEvent.DnsResolutionFailed) emittedDnsFailure = true
                            trySend(event)
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed on cancellation
            }

            if (!emittedStarted && !emittedDnsFailure) {
                trySend(PingEvent.DnsResolutionFailed(config.host))
            }

            close()
        }

        awaitClose {
            process.destroy()
        }
    }

    private data class ParseResult(
        val events: List<PingEvent>,
        val lastSeq: Int,
        val startedEmitted: Boolean,
    )

    private fun parseLine(
        line: String,
        config: PingConfig,
        isIpv6: Boolean,
        alreadyStarted: Boolean,
        lastSeq: Int,
    ): ParseResult? {
        val events = mutableListOf<PingEvent>()
        var newLastSeq = lastSeq

        if (!alreadyStarted) {
            val startMatch = PING_STARTED_REGEX.find(line)
            if (startMatch != null) {
                events.add(PingEvent.Started(startMatch.groupValues[1], isIpv6))
                return ParseResult(events, newLastSeq, true)
            }
        }

        val replyMatch = PING_REPLY_REGEX.find(line)
        if (replyMatch != null) {
            val bytes = replyMatch.groupValues[1].toInt()
            val seq = replyMatch.groupValues[3].toInt()
            val ttl = replyMatch.groupValues[4].toInt()
            val rtt = replyMatch.groupValues[5].toFloat()

            if (lastSeq >= 0) {
                for (missedSeq in (lastSeq + 1) until seq) {
                    events.add(PingEvent.PacketLost(missedSeq))
                }
            }
            newLastSeq = seq
            events.add(PingEvent.PacketReceived(seq, rtt, ttl, bytes))
            return ParseResult(events, newLastSeq, false)
        }

        val timeoutMatch = REQUEST_TIMEOUT_REGEX.find(line)
        if (timeoutMatch != null) {
            val seq = timeoutMatch.groupValues[1].toInt()
            events.add(PingEvent.PacketLost(seq))
            return ParseResult(events, seq, false)
        }

        val packetMatch = SUMMARY_PACKETS_REGEX.find(line)
        if (packetMatch != null) {
            events.add(
                PingEvent.Summary(
                    packetsSent = packetMatch.groupValues[1].toInt(),
                    packetsReceived = packetMatch.groupValues[2].toInt(),
                    packetLossPct = packetMatch.groupValues[3].toFloat(),
                    minRtt = null, avgRtt = null, maxRtt = null, stddevRtt = null,
                )
            )
            return ParseResult(events, newLastSeq, false)
        }

        val rttMatch = SUMMARY_RTT_REGEX.find(line)
        if (rttMatch != null) {
            events.add(
                PingEvent.Summary(
                    packetsSent = 0, packetsReceived = 0, packetLossPct = 0f,
                    minRtt = rttMatch.groupValues[1].toFloat(),
                    avgRtt = rttMatch.groupValues[2].toFloat(),
                    maxRtt = rttMatch.groupValues[3].toFloat(),
                    stddevRtt = rttMatch.groupValues[4].toFloat(),
                )
            )
            return ParseResult(events, newLastSeq, false)
        }

        if (DNS_FAILURE_REGEX.containsMatchIn(line)) {
            events.add(PingEvent.DnsResolutionFailed(config.host))
            return ParseResult(events, newLastSeq, false)
        }

        return null
    }

    private fun buildPingCommand(
        config: PingConfig,
        pingBinary: String,
        isIpv6: Boolean,
    ): List<String> {
        val cmd = mutableListOf<String>()

        if (isIpv6) {
            val ping6 = binaryProvider.findPing6Binary()
            if (ping6 != null) {
                cmd.add(ping6)
            } else {
                cmd.add(pingBinary)
                cmd.add("-6")
            }
        } else {
            cmd.add(pingBinary)
        }

        if (!config.isContinuous) {
            cmd.add("-c")
            cmd.add(config.count.toString())
        }

        cmd.add("-i")
        cmd.add(config.intervalSeconds.toString())
        cmd.add("-s")
        cmd.add(config.packetSizeBytes.toString())
        cmd.add("-W")
        cmd.add(config.timeoutSeconds.toString())
        cmd.add(config.host)

        return cmd
    }
}
