package com.caskfive.pingcheck.util

object InputValidator {
    private val HOST_PATTERN = Regex("^[a-zA-Z0-9._:\\-]+$")

    fun isValidHost(host: String): Boolean {
        // Reject null-byte characters, empty, or blank input
        if (host.isBlank() || host.contains('\u0000')) return false

        // Max DNS name length is 253 characters
        if (host.length > 253) return false

        // Basic charset validation
        if (!HOST_PATTERN.matches(host)) return false

        // Structural validation: no leading/trailing dots or hyphens
        if (host.startsWith('.') || host.endsWith('.')) return false
        if (host.startsWith('-') || host.endsWith('-')) return false

        // Individual labels must not start or end with a hyphen
        val labels = host.split('.')
        for (label in labels) {
            if (label.isEmpty()) return false // consecutive dots
            if (label.startsWith('-') || label.endsWith('-')) return false
        }

        return true
    }
}
