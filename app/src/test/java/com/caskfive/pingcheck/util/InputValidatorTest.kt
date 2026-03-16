package com.caskfive.pingcheck.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    // ---- Valid inputs ----

    @Test
    fun `simple hostname is valid`() {
        assertTrue(InputValidator.isValidHost("google.com"))
    }

    @Test
    fun `dotted subdomain is valid`() {
        assertTrue(InputValidator.isValidHost("sub.domain.com"))
    }

    @Test
    fun `IPv4 address is valid`() {
        assertTrue(InputValidator.isValidHost("192.168.1.1"))
    }

    @Test
    fun `IPv6 address with colons is valid`() {
        assertTrue(InputValidator.isValidHost("2001:db8::1"))
    }

    @Test
    fun `hyphenated hostname is valid`() {
        assertTrue(InputValidator.isValidHost("my-host.example.com"))
    }

    // ---- Invalid inputs ----

    @Test
    fun `empty string is invalid`() {
        assertFalse(InputValidator.isValidHost(""))
    }

    @Test
    fun `blank whitespace-only string is invalid`() {
        assertFalse(InputValidator.isValidHost("   "))
    }

    @Test
    fun `command injection with semicolon is invalid`() {
        assertFalse(InputValidator.isValidHost(";ls"))
    }

    @Test
    fun `command injection with pipe is invalid`() {
        assertFalse(InputValidator.isValidHost("|cat /etc/passwd"))
    }

    @Test
    fun `command injection with dollar-paren is invalid`() {
        assertFalse(InputValidator.isValidHost("\$(rm -rf /)"))
    }

    @Test
    fun `command injection with backticks is invalid`() {
        assertFalse(InputValidator.isValidHost("`whoami`"))
    }

    @Test
    fun `string with spaces is invalid`() {
        assertFalse(InputValidator.isValidHost("bad host"))
    }

    @Test
    fun `over-length string is invalid`() {
        val longHost = "a".repeat(254)
        assertFalse(InputValidator.isValidHost(longHost))
    }

    @Test
    fun `exactly 253 chars is valid`() {
        val maxHost = "a".repeat(253)
        assertTrue(InputValidator.isValidHost(maxHost))
    }

    @Test
    fun `null-byte character is invalid`() {
        assertFalse(InputValidator.isValidHost("host\u0000.com"))
    }

    @Test
    fun `leading dot is rejected`() {
        assertFalse(InputValidator.isValidHost(".example.com"))
    }

    @Test
    fun `trailing dot is rejected`() {
        assertFalse(InputValidator.isValidHost("example.com."))
    }

    @Test
    fun `leading hyphen is rejected`() {
        assertFalse(InputValidator.isValidHost("-example.com"))
    }

    @Test
    fun `trailing hyphen is rejected`() {
        assertFalse(InputValidator.isValidHost("example.com-"))
    }

    @Test
    fun `ampersand is rejected`() {
        assertFalse(InputValidator.isValidHost("host&whoami"))
    }

    @Test
    fun `newline is rejected`() {
        assertFalse(InputValidator.isValidHost("host\ninjection"))
    }

    @Test
    fun `slash is rejected`() {
        assertFalse(InputValidator.isValidHost("host/path"))
    }
}
