package com.caskfive.pingcheck.domain.traceroute

import com.caskfive.pingcheck.domain.ping.PingBinaryProvider
import com.caskfive.pingcheck.domain.process.ManagedProcess
import com.caskfive.pingcheck.domain.process.ProcessExecutor
import com.caskfive.pingcheck.util.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TracerouteEngine @Inject constructor(
    private val processExecutor: ProcessExecutor,
    private val binaryProvider: PingBinaryProvider,
) {
    companion object {
        // "From 10.0.0.1: icmp_seq=1 Time to live exceeded"
        private val TTL_EXCEEDED_REGEX = Regex(
            """From\s+(\S+?):?\s+icmp_seq=\d+\s+Time to live exceeded""",
            RegexOption.IGNORE_CASE,
        )

        // Standard ping reply: "64 bytes from 8.8.8.8: icmp_seq=1 ttl=56 time=12.3 ms"
        private val PING_REPLY_REGEX = Regex(
            """\d+\s+bytes\s+from\s+([^:]+):\s+icmp_seq=\d+\s+ttl=\d+\s+time=(\d+\.?\d*)\s*ms"""
        )

        // PING header: "PING google.com (142.250.80.46) 56(84) bytes of data."
        private val PING_STARTED_REGEX = Regex(
            """PING\s+\S+\s+\(([^)]+)\)"""
        )

        private val DNS_FAILURE_REGEX = Regex(
            """(?:unknown host|Name or service not known|Could not resolve hostname|bad address)""",
            RegexOption.IGNORE_CASE,
        )
    }

    fun trace(config: TraceConfig): Flow<TracerouteEvent> = callbackFlow {
        if (!InputValidator.isValidHost(config.host)) {
            trySend(TracerouteEvent.Error("Invalid hostname: ${config.host}"))
            close()
            return@callbackFlow
        }

        val pingBinary = binaryProvider.findPingBinary()
        if (pingBinary == null) {
            trySend(TracerouteEvent.Error("Ping binary not available on this device"))
            close()
            return@callbackFlow
        }

        var currentProcess: ManagedProcess? = null
        var stderrJob: Job? = null

        val traceJob = launch(Dispatchers.IO) {
            var resolvedIp: String? = null

            for (ttl in 1..config.maxHops) {
                if (!isActive) break

                val command = buildCommand(pingBinary, config, ttl)

                val process = try {
                    processExecutor.execute(command)
                } catch (e: Exception) {
                    trySend(TracerouteEvent.Error(e.message ?: "Failed to execute ping"))
                    break
                }

                currentProcess = process

                try {
                    // Drain stderr in a scoped background job
                    stderrJob = launch(Dispatchers.IO) {
                        try {
                            process.stderr.forEachLine { /* drain */ }
                        } catch (_: Exception) { }
                    }

                    var hopEmitted = false
                    var reachedDestination = false

                    process.stdout.forEachLine { line ->
                        if (!isActive) return@forEachLine

                        // Check for DNS failure
                        if (DNS_FAILURE_REGEX.containsMatchIn(line)) {
                            trySend(TracerouteEvent.Error("Could not resolve ${config.host}"))
                            hopEmitted = true
                            return@forEachLine
                        }

                        // Check for PING header to get resolved IP (only on TTL 1)
                        if (resolvedIp == null) {
                            val startMatch = PING_STARTED_REGEX.find(line)
                            if (startMatch != null) {
                                resolvedIp = startMatch.groupValues[1]
                                trySend(TracerouteEvent.Started(resolvedIp!!))
                            }
                        }

                        // Check for TTL exceeded (intermediate hop)
                        val ttlMatch = TTL_EXCEEDED_REGEX.find(line)
                        if (ttlMatch != null && !hopEmitted) {
                            val hopIp = ttlMatch.groupValues[1]
                            trySend(
                                TracerouteEvent.HopResult(
                                    hopNumber = ttl,
                                    ipAddress = hopIp,
                                    isTimeout = false,
                                    isDestination = false,
                                )
                            )
                            hopEmitted = true
                            return@forEachLine
                        }

                        // Check for standard reply (destination reached)
                        val replyMatch = PING_REPLY_REGEX.find(line)
                        if (replyMatch != null && !hopEmitted) {
                            val hopIp = replyMatch.groupValues[1].trim()
                            val rtt = replyMatch.groupValues[2].toFloatOrNull()
                            trySend(
                                TracerouteEvent.HopResult(
                                    hopNumber = ttl,
                                    ipAddress = hopIp,
                                    rttMs = rtt,
                                    isTimeout = false,
                                    isDestination = true,
                                )
                            )
                            hopEmitted = true
                            reachedDestination = true
                            return@forEachLine
                        }
                    }

                    // Wait for process to finish
                    process.waitFor()
                    stderrJob?.join()

                    // If no useful output was parsed, it's a timeout hop
                    if (!hopEmitted) {
                        trySend(
                            TracerouteEvent.HopResult(
                                hopNumber = ttl,
                                isTimeout = true,
                                isDestination = false,
                            )
                        )
                    }

                    if (reachedDestination) {
                        break
                    }
                } finally {
                    process.destroy()
                    currentProcess = null
                    stderrJob?.cancel()
                    stderrJob = null
                }
            }

            if (isActive) {
                trySend(TracerouteEvent.Completed)
            }

            close()
        }

        awaitClose {
            traceJob.cancel()
            currentProcess?.destroy()
        }
    }

    private fun buildCommand(
        pingBinary: String,
        config: TraceConfig,
        ttl: Int,
    ): List<String> {
        return listOf(
            pingBinary,
            "-c", "1",
            "-t", ttl.toString(),
            "-W", config.timeoutSeconds.toString(),
            config.host,
        )
    }
}
