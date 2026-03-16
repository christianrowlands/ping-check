package com.caskfive.pingcheck.domain.ping

import app.cash.turbine.test
import com.caskfive.pingcheck.domain.process.ManagedProcess
import com.caskfive.pingcheck.domain.process.ProcessExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class PingEngineTest {

    private fun createMockProcess(stdout: String, stderr: String = ""): ManagedProcess {
        val process = mockk<ManagedProcess>(relaxed = true)
        every { process.stdout } returns BufferedReader(StringReader(stdout))
        every { process.stderr } returns BufferedReader(StringReader(stderr))
        every { process.isAlive() } returns false
        every { process.waitFor() } returns 0
        return process
    }

    private fun createEngine(stdout: String, stderr: String = ""): PingEngine {
        val executor = mockk<ProcessExecutor>()
        every { executor.execute(any()) } returns createMockProcess(stdout, stderr)
        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns null
        return PingEngine(executor, binaryProvider)
    }

    @Test
    fun `parse standard ping reply`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=24.3 ms
64 bytes from 8.8.8.8: icmp_seq=2 ttl=118 time=23.1 ms

--- 8.8.8.8 ping statistics ---
2 packets transmitted, 2 received, 0% packet loss, time 1001ms
rtt min/avg/max/mdev = 23.1/23.7/24.3/0.6 ms
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "8.8.8.8", count = 2)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is PingEvent.Started)
            assertEquals("8.8.8.8", (started as PingEvent.Started).resolvedIp)

            val pkt1 = awaitItem()
            assertTrue("Expected PacketReceived, got $pkt1", pkt1 is PingEvent.PacketReceived)
            pkt1 as PingEvent.PacketReceived
            assertEquals(1, pkt1.sequenceNumber)
            assertEquals(24.3f, pkt1.rttMs)
            assertEquals(118, pkt1.ttl)
            assertEquals(64, pkt1.bytes)

            val pkt2 = awaitItem()
            assertTrue("Expected PacketReceived, got $pkt2", pkt2 is PingEvent.PacketReceived)
            pkt2 as PingEvent.PacketReceived
            assertEquals(2, pkt2.sequenceNumber)
            assertEquals(23.1f, pkt2.rttMs)

            val summary1 = awaitItem()
            assertTrue("Expected Summary, got $summary1", summary1 is PingEvent.Summary)
            summary1 as PingEvent.Summary
            assertEquals(2, summary1.packetsSent)
            assertEquals(2, summary1.packetsReceived)
            assertEquals(0f, summary1.packetLossPct)

            val summary2 = awaitItem()
            assertTrue("Expected Summary with RTT, got $summary2", summary2 is PingEvent.Summary)
            summary2 as PingEvent.Summary
            assertEquals(23.1f, summary2.minRtt)
            assertEquals(23.7f, summary2.avgRtt)
            assertEquals(24.3f, summary2.maxRtt)
            assertEquals(0.6f, summary2.stddevRtt)

            awaitComplete()
        }
    }

    @Test
    fun `parse hostname resolution`() = runTest {
        val output = """
PING google.com (142.250.80.46) 56(84) bytes of data.
64 bytes from lax17s61-in-f14.1e100.net (142.250.80.46): icmp_seq=1 ttl=118 time=5.12 ms

--- google.com ping statistics ---
1 packets transmitted, 1 received, 0% packet loss, time 0ms
rtt min/avg/max/mdev = 5.12/5.12/5.12/0.0 ms
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "google.com", count = 1)).test {
            val started = awaitItem() as PingEvent.Started
            assertEquals("142.250.80.46", started.resolvedIp)

            val pkt = awaitItem() as PingEvent.PacketReceived
            assertEquals(5.12f, pkt.rttMs)

            awaitItem() // summary packets
            awaitItem() // summary rtt
            awaitComplete()
        }
    }

    @Test
    fun `parse packet loss with sequence gap`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=24 ms
64 bytes from 8.8.8.8: icmp_seq=3 ttl=118 time=25 ms

--- 8.8.8.8 ping statistics ---
3 packets transmitted, 2 received, 33% packet loss, time 2002ms
rtt min/avg/max/mdev = 24.0/24.5/25.0/0.5 ms
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "8.8.8.8", count = 3)).test {
            awaitItem() // Started
            awaitItem() // seq=1
            val lost = awaitItem()
            assertTrue("Expected PacketLost, got $lost", lost is PingEvent.PacketLost)
            assertEquals(2, (lost as PingEvent.PacketLost).sequenceNumber)
            awaitItem() // seq=3
            awaitItem() // summary packets
            awaitItem() // summary rtt
            awaitComplete()
        }
    }

    @Test
    fun `parse integer time value`() = runTest {
        val output = """
PING 192.168.1.1 (192.168.1.1) 56(84) bytes of data.
64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=1 ms

--- 192.168.1.1 ping statistics ---
1 packets transmitted, 1 received, 0% packet loss, time 0ms
rtt min/avg/max/mdev = 1.0/1.0/1.0/0.0 ms
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "192.168.1.1", count = 1)).test {
            awaitItem() // Started
            val pkt = awaitItem() as PingEvent.PacketReceived
            assertEquals(1f, pkt.rttMs)
            awaitItem() // summary
            awaitItem() // rtt summary
            awaitComplete()
        }
    }

    @Test
    fun `invalid host is rejected`() = runTest {
        val engine = createEngine("")
        engine.ping(PingConfig(host = "bad host; rm -rf /", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected Error, got $event", event is PingEvent.Error)
            awaitComplete()
        }
    }

    @Test
    fun `dns failure detected from output`() = runTest {
        val output = "ping: unknown host nonexistent.invalid"
        val engine = createEngine(output)
        engine.ping(PingConfig(host = "nonexistent.invalid", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected DnsResolutionFailed, got $event", event is PingEvent.DnsResolutionFailed)
            awaitComplete()
        }
    }

    // ---- Additional test cases ----

    @Test
    fun `REQUEST_TIMEOUT_REGEX parses explicit timeout line`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
Request timeout for icmp_seq 5
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "8.8.8.8", count = 1)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is PingEvent.Started)

            val lost = awaitItem()
            assertTrue("Expected PacketLost, got $lost", lost is PingEvent.PacketLost)
            assertEquals(5, (lost as PingEvent.PacketLost).sequenceNumber)

            awaitComplete()
        }
    }

    @Test
    fun `REQUEST_TIMEOUT_REGEX parses no answer yet variant`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
no answer yet for icmp_seq=3
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "8.8.8.8", count = 1)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is PingEvent.Started)

            val lost = awaitItem()
            assertTrue("Expected PacketLost, got $lost", lost is PingEvent.PacketLost)
            assertEquals(3, (lost as PingEvent.PacketLost).sequenceNumber)

            awaitComplete()
        }
    }

    @Test
    fun `process execution throwing SecurityException emits error`() = runTest {
        val executor = mockk<ProcessExecutor>()
        every { executor.execute(any()) } throws SecurityException("Permission denied")
        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns null

        val engine = PingEngine(executor, binaryProvider)
        engine.ping(PingConfig(host = "8.8.8.8", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected Error, got $event", event is PingEvent.Error)
            assertEquals("Permission denied", (event as PingEvent.Error).message)
            awaitComplete()
        }
    }

    @Test
    fun `IPv6 host uses ping6 binary when available`() = runTest {
        val output = """
PING 2001:db8::1 (2001:db8::1) 56 data bytes.
64 bytes from 2001:db8::1: icmp_seq=1 ttl=64 time=1.5 ms

--- 2001:db8::1 ping statistics ---
1 packets transmitted, 1 received, 0% packet loss, time 0ms
rtt min/avg/max/mdev = 1.5/1.5/1.5/0.0 ms
        """.trimIndent()

        val executor = mockk<ProcessExecutor>()
        val commandSlot = slot<List<String>>()
        every { executor.execute(capture(commandSlot)) } returns createMockProcess(output)

        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns "/system/bin/ping6"

        val engine = PingEngine(executor, binaryProvider)
        engine.ping(PingConfig(host = "2001:db8::1", count = 1)).test {
            val started = awaitItem()
            assertTrue("Expected Started, got $started", started is PingEvent.Started)
            assertTrue(
                "Started should indicate IPv6",
                (started as PingEvent.Started).isIpv6,
            )

            awaitItem() // PacketReceived
            awaitItem() // Summary packets
            awaitItem() // Summary rtt
            awaitComplete()
        }

        // Verify ping6 binary was used
        assertEquals("/system/bin/ping6", commandSlot.captured[0])
    }

    @Test
    fun `IPv6 host falls back to ping -6 when ping6 not available`() = runTest {
        val output = """
PING 2001:db8::1 (2001:db8::1) 56 data bytes.
64 bytes from 2001:db8::1: icmp_seq=1 ttl=64 time=1.5 ms

--- 2001:db8::1 ping statistics ---
1 packets transmitted, 1 received, 0% packet loss, time 0ms
rtt min/avg/max/mdev = 1.5/1.5/1.5/0.0 ms
        """.trimIndent()

        val executor = mockk<ProcessExecutor>()
        val commandSlot = slot<List<String>>()
        every { executor.execute(capture(commandSlot)) } returns createMockProcess(output)

        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns null

        val engine = PingEngine(executor, binaryProvider)
        engine.ping(PingConfig(host = "2001:db8::1", count = 1)).test {
            awaitItem() // Started
            awaitItem() // PacketReceived
            awaitItem() // Summary packets
            awaitItem() // Summary rtt
            awaitComplete()
        }

        // Should use ping binary with -6 flag
        assertEquals("/system/bin/ping", commandSlot.captured[0])
        assertTrue("Command should contain -6 flag", commandSlot.captured.contains("-6"))
    }

    @Test
    fun `findPingBinary returning null emits NoPingBinary error`() = runTest {
        val executor = mockk<ProcessExecutor>()
        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns null
        every { binaryProvider.findPing6Binary() } returns null

        val engine = PingEngine(executor, binaryProvider)
        engine.ping(PingConfig(host = "8.8.8.8", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected Error, got $event", event is PingEvent.Error)
            assertTrue(
                "Error should mention ping binary",
                (event as PingEvent.Error).message.contains("Ping binary"),
            )
            awaitComplete()
        }
    }

    @Test
    fun `round-trip keyword variant in summary parsing`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=24 ms

--- 8.8.8.8 ping statistics ---
1 packets transmitted, 1 received, 0% packet loss, time 0ms
round-trip min/avg/max/stddev = 24.0/24.0/24.0/0.0 ms
        """.trimIndent()

        val engine = createEngine(output)
        engine.ping(PingConfig(host = "8.8.8.8", count = 1)).test {
            awaitItem() // Started
            awaitItem() // PacketReceived

            val summary1 = awaitItem()
            assertTrue("Expected Summary, got $summary1", summary1 is PingEvent.Summary)

            val summary2 = awaitItem()
            assertTrue("Expected Summary with RTT, got $summary2", summary2 is PingEvent.Summary)
            summary2 as PingEvent.Summary
            assertEquals(24.0f, summary2.minRtt)
            assertEquals(24.0f, summary2.avgRtt)
            assertEquals(24.0f, summary2.maxRtt)
            assertEquals(0.0f, summary2.stddevRtt)

            awaitComplete()
        }
    }

    @Test
    fun `dns failure - Name or service not known`() = runTest {
        val output = "ping: Name or service not known"
        val engine = createEngine(output)
        engine.ping(PingConfig(host = "nonexistent.invalid", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected DnsResolutionFailed, got $event", event is PingEvent.DnsResolutionFailed)
            assertEquals("nonexistent.invalid", (event as PingEvent.DnsResolutionFailed).host)
            awaitComplete()
        }
    }

    @Test
    fun `dns failure - not known`() = runTest {
        val output = "ping: host not known"
        val engine = createEngine(output)
        engine.ping(PingConfig(host = "nonexistent.invalid", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected DnsResolutionFailed, got $event", event is PingEvent.DnsResolutionFailed)
            awaitComplete()
        }
    }

    @Test
    fun `dns failure - bad address`() = runTest {
        val output = "ping: bad address 'nonexistent.invalid'"
        val engine = createEngine(output)
        engine.ping(PingConfig(host = "nonexistent.invalid", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected DnsResolutionFailed, got $event", event is PingEvent.DnsResolutionFailed)
            awaitComplete()
        }
    }

    @Test
    fun `dns failure - Could not resolve hostname`() = runTest {
        val output = "ping: Could not resolve hostname nonexistent.invalid"
        val engine = createEngine(output)
        engine.ping(PingConfig(host = "nonexistent.invalid", count = 1)).test {
            val event = awaitItem()
            assertTrue("Expected DnsResolutionFailed, got $event", event is PingEvent.DnsResolutionFailed)
            awaitComplete()
        }
    }

    @Test
    fun `continuous mode does not include -c flag`() = runTest {
        val output = """
PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=24 ms
        """.trimIndent()

        val executor = mockk<ProcessExecutor>()
        val commandSlot = slot<List<String>>()
        every { executor.execute(capture(commandSlot)) } returns createMockProcess(output)

        val binaryProvider = mockk<PingBinaryProvider>()
        every { binaryProvider.findPingBinary() } returns "/system/bin/ping"
        every { binaryProvider.findPing6Binary() } returns null

        val engine = PingEngine(executor, binaryProvider)
        // count=0 means continuous mode
        engine.ping(PingConfig(host = "8.8.8.8", count = 0)).test {
            awaitItem() // Started
            awaitItem() // PacketReceived
            awaitComplete()
        }

        assertFalse(
            "Continuous mode command should not contain -c flag",
            commandSlot.captured.contains("-c"),
        )
    }
}
