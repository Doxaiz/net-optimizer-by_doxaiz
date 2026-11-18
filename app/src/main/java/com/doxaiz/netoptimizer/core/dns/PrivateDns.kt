package com.doxaiz.netoptimizer.core.dns

import java.net.IDN
import java.util.Locale

/** These are hostnames for Android's system Private DNS, not addresses for the UDP benchmark. */
data class PrivateDnsProvider(val name: String, val hostname: String, val description: String)

object PrivateDns {
    // Hostnames published by the respective providers. Availability and filtering are not guaranteed.
    val providers = listOf(
        PrivateDnsProvider("Яндекс", "common.dot.dns.yandex.net", "Базовый DNS без дополнительной фильтрации"),
        PrivateDnsProvider("Cloudflare", "one.one.one.one", "Обычный DNS без фильтра рекламы"),
        PrivateDnsProvider("AdGuard", "dns.adguard-dns.com", "Фильтрует часть рекламных и отслеживающих доменов"),
        PrivateDnsProvider("Google", "dns.google", "Обычный DNS без фильтра рекламы"),
        PrivateDnsProvider("Quad9", "dns.quad9.net", "Фильтрует известные вредоносные домены")
    )

    /** Normalize a DNS-over-TLS hostname without resolving it or changing Android settings. */
    fun hostname(input: String): String? {
        val value = input.trim().removeSuffix(".")
        if (value.isEmpty() || value.length > 253 || value.any { it.isWhitespace() } ||
            value.any { it in "/:?#@%[]\\" }) return null
        val ascii = try { IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES) }
            catch (_: IllegalArgumentException) { return null }
        if (ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
        // Numeric IP literals cannot be used as a Private DNS provider hostname.
        if (labels.last().none { it.isLetter() }) return null
        return ascii.lowercase(Locale.ROOT)
    }
}
