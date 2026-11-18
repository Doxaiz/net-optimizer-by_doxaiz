package com.doxaiz.netoptimizer.core.net

import java.net.IDN
import java.net.URI

/** Validates input locally, without making a DNS request. */
object CustomTarget {
    fun parse(hostInput: String, portInput: String): PingTarget? {
        val port = portInput.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val input = hostInput.trim()
        if (input.isEmpty() || input.length > 253 || input.any { it.isWhitespace() } ||
            input.any { it == '/' || it == '?' || it == '#' || it == '@' || it == '%' }) return null

        val host = if (input.contains(':')) {
            val address = if (input.startsWith('[') && input.endsWith(']')) input.substring(1, input.length - 1) else input
            try {
                // URI validates IPv6 syntax without resolving a hostname.
                URI("http://[$address]/").host ?: return null
                address
            } catch (_: IllegalArgumentException) { return null }
              catch (_: java.net.URISyntaxException) { return null }
        } else {
            if (input.contains('[') || input.contains(']')) return null
            val ascii = try { IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES) } catch (_: IllegalArgumentException) { return null }
            if (ascii.length > 253 || ascii.isEmpty()) return null
            val labels = ascii.removeSuffix(".").split('.')
            if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
            if (ascii.all { it.isDigit() || it == '.' }) {
                if (labels.size != 4 || labels.any { it.length > 3 || (it.toIntOrNull() ?: 256) > 255 }) return null
            }
            ascii
        }
        return PingTarget("Своя цель", host, port, "TCP")
    }
}
