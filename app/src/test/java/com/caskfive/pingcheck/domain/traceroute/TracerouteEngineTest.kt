package com.caskfive.pingcheck.domain.traceroute

import app.cash.turbine.test
import com.caskfive.pingcheck.domain.ping.PingBinaryProvider
import com.caskfive.pingcheck.domain.process.ManagedProcess
import com.caskfive.pingcheck.domain.process.ProcessExecutor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class TracerouteEngineTest {

    private fun createMockProcess(stdout: String, stderr: String = ""): ManagedProcess {
        val process = mockk<ManagedProcess>(relaxed = true)
        every { process.stdout } returns BufferedReader(StringReader(stdout))
        every { process.stderr } returns BufferedReader(StringReader(stderr))
        every { process.isAlive() } returns false
        every { process.waitFor() } returns 0
        return process
    }

    private fun createEngine(
        stdoutPerHop: Map<Int, String>,
        binaryPath: String = "/system/bin/ping",
    ): TracerouteEngine {
        val executor = mockk<ProcessExecutor>()
        stdoutPerHop.forEach { (ttl, stdout) ->
            every {
                executor.execute(match { it.contains(ttl.toString()) && it.indexOf(ttl.toString()) == it.indexOf("-t") + 1 })
            } returns createMockProcess(stdout)
        }
        // Fallback for any TTL not explicitly mapped -- return empty output (timeout)
        every { executor.execute(any()) } returns createMockProcess("")

        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns binaryPath
        every { binaryProvider.findPing6Binary() } returns null
        return TracerouteEngine(executor, binaryProvider)
    }

    private fun createEngineSequential(
        stdoutSequence: List<String>,
        binaryPath: String = "/system/bin/ping",
    ): TracerouteEngine {
        val executor = mockk<ProcessExecutor>()
        val processes = stdoutSequence.map { createMockProcess(it) }
        val callRef = object { var idx = 0 }
        every { executor.execute(any()) } answers {
            val i = callRef.idx
            callRef.idx++
            if (i < processes.size) processes[i] else createMockProcess("")
        }

        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns binaryPath
        every { binaryProvider.findPing6Binary() } returns null
        return TracerouteEngine(executor, binaryProvider)
    }

    @Test
    fun `standard multi-hop trace reaching destination`() = runTest {
        val hop1 = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
From 192.168.1.1: icmp_seq=1 Time to live exceeded
        """.trimIndent()

        val hop2 = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
From 10.0.0.1: icmp_seq=1 Time to live exceeded
        """.trimIndent()

        val hop3 = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=56 time=12.3 ms
        """.trimIndent()

        val engine = createEngineSequential(listOf(hop1, hop2, hop3))
        engine.trace(TraceConfig(host = "8.8.8.8", maxHops = 30)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is TracerouteEvent.Started)
            assertEquals("8.8.8.8", (started as TracerouteEvent.Started).resolvedIp)

            val h1 = awaitItem()
            assertTrue("Expected HopResult, got $h1", h1 is TracerouteEvent.HopResult)
            h1 as TracerouteEvent.HopResult
            assertEquals(1, h1.hopNumber)
            assertEquals("192.168.1.1", h1.ipAddress)
            assertEquals(false, h1.isTimeout)
            assertEquals(false, h1.isDestination)

            val h2 = awaitItem()
            assertTrue("Expected HopResult, got $h2", h2 is TracerouteEvent.HopResult)
            h2 as TracerouteEvent.HopResult
            assertEquals(2, h2.hopNumber)
            assertEquals("10.0.0.1", h2.ipAddress)
            assertEquals(false, h2.isTimeout)
            assertEquals(false, h2.isDestination)

            val h3 = awaitItem()
            assertTrue("Expected HopResult, got $h3", h3 is TracerouteEvent.HopResult)
            h3 as TracerouteEvent.HopResult
            assertEquals(3, h3.hopNumber)
            assertEquals("8.8.8.8", h3.ipAddress)
            assertEquals(12.3f, h3.rttMs)
            assertEquals(false, h3.isTimeout)
            assertEquals(true, h3.isDestination)

            val completed = awaitItem()
            assertTrue("Expected Completed, got $completed", completed is TracerouteEvent.Completed)

            awaitComplete()
        }
    }

    @Test
    fun `timeout hop emits timeout event`() = runTest {
        // Hop 1 returns no useful output (simulates * * *)
        val hop1empty = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
        """.trimIndent()

        val hop2destination = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=64 time=5.0 ms
        """.trimIndent()

        val engine = createEngineSequential(listOf(hop1empty, hop2destination))
        engine.trace(TraceConfig(host = "8.8.8.8", maxHops = 30)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is TracerouteEvent.Started)

            val h1 = awaitItem()
            assertTrue("Expected HopResult, got $h1", h1 is TracerouteEvent.HopResult)
            h1 as TracerouteEvent.HopResult
            assertEquals(1, h1.hopNumber)
            assertEquals(true, h1.isTimeout)
            assertEquals(false, h1.isDestination)

            val h2 = awaitItem()
            assertTrue("Expected HopResult, got $h2", h2 is TracerouteEvent.HopResult)
            h2 as TracerouteEvent.HopResult
            assertEquals(2, h2.hopNumber)
            assertEquals(false, h2.isTimeout)
            assertEquals(true, h2.isDestination)

            val completed = awaitItem()
            assertTrue("Expected Completed, got $completed", completed is TracerouteEvent.Completed)

            awaitComplete()
        }
    }

    @Test
    fun `DNS failure produces error event`() = runTest {
        val dnsFailOutput = "ping: unknown host nonexistent.invalid"

        val engine = createEngineSequential(listOf(dnsFailOutput))
        engine.trace(TraceConfig(host = "nonexistent.invalid", maxHops = 30)).test {
            val error = awaitItem()
            assertTrue("Expected Error, got $error", error is TracerouteEvent.Error)
            assertEquals(
                "Could not resolve nonexistent.invalid",
                (error as TracerouteEvent.Error).message,
            )

            // After DNS error on hop 1, the engine continues looping through remaining hops
            // (timeouts) and eventually emits Completed. Cancel and ignore those events.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid host is rejected immediately`() = runTest {
        val engine = createEngineSequential(listOf(""))
        engine.trace(TraceConfig(host = ";ls -la", maxHops = 30)).test {
            val error = awaitItem()
            assertTrue("Expected Error, got $error", error is TracerouteEvent.Error)
            assertTrue(
                "Error message should mention invalid hostname",
                (error as TracerouteEvent.Error).message.contains("Invalid hostname"),
            )
            awaitComplete()
        }
    }

    @Test
    fun `max hops reached without finding destination`() = runTest {
        val maxHops = 3
        val ttlExceeded1 = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
From 10.0.0.1: icmp_seq=1 Time to live exceeded
        """.trimIndent()

        val ttlExceeded2 = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
From 10.0.0.2: icmp_seq=1 Time to live exceeded
        """.trimIndent()

        val timeoutHop = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
        """.trimIndent()

        val engine = createEngineSequential(listOf(ttlExceeded1, ttlExceeded2, timeoutHop))
        engine.trace(TraceConfig(host = "8.8.8.8", maxHops = maxHops)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is TracerouteEvent.Started)

            val h1 = awaitItem() as TracerouteEvent.HopResult
            assertEquals(1, h1.hopNumber)
            assertEquals("10.0.0.1", h1.ipAddress)
            assertEquals(false, h1.isDestination)

            val h2 = awaitItem() as TracerouteEvent.HopResult
            assertEquals(2, h2.hopNumber)
            assertEquals("10.0.0.2", h2.ipAddress)
            assertEquals(false, h2.isDestination)

            val h3 = awaitItem() as TracerouteEvent.HopResult
            assertEquals(3, h3.hopNumber)
            assertEquals(true, h3.isTimeout)
            assertEquals(false, h3.isDestination)

            val completed = awaitItem()
            assertTrue("Expected Completed, got $completed", completed is TracerouteEvent.Completed)

            awaitComplete()
        }
    }

    @Test
    fun `ping binary not available produces error`() = runTest {
        val executor = mockk<ProcessExecutor>()
        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns null
        every { binaryProvider.findPing6Binary() } returns null

        val engine = TracerouteEngine(executor, binaryProvider)
        engine.trace(TraceConfig(host = "8.8.8.8")).test {
            val error = awaitItem()
            assertTrue("Expected Error, got $error", error is TracerouteEvent.Error)
            assertTrue(
                "Error should mention ping binary",
                (error as TracerouteEvent.Error).message.contains("Ping binary"),
            )
            awaitComplete()
        }
    }

    @Test
    fun `process execution exception produces error`() = runTest {
        val executor = mockk<ProcessExecutor>()
        every { executor.execute(any()) } throws SecurityException("Permission denied")
        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns null

        val engine = TracerouteEngine(executor, binaryProvider)
        engine.trace(TraceConfig(host = "8.8.8.8", maxHops = 3)).test {
            val error = awaitItem()
            assertTrue("Expected Error, got $error", error is TracerouteEvent.Error)
            assertEquals("Permission denied", (error as TracerouteEvent.Error).message)

            val completed = awaitItem()
            assertTrue("Expected Completed, got $completed", completed is TracerouteEvent.Completed)

            awaitComplete()
        }
    }
}
