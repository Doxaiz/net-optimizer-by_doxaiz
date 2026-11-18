package com.doxaiz.netoptimizer.core.dns

import org.junit.Assert.*
import org.junit.Test

class PrivateDnsTest {
    @Test fun providersHaveValidUniqueHostnames() {
        assertEquals(PrivateDns.providers.size, PrivateDns.providers.map { it.hostname }.distinct().size)
        PrivateDns.providers.forEach { assertEquals(it.hostname, PrivateDns.hostname(it.hostname)) }
    }

    @Test fun normalizesDnsNamesWithoutLookingThemUp() {
        assertEquals("dns.google", PrivateDns.hostname("  DNS.Google.  "))
        assertEquals("xn--e1afmkfd.xn--p1ai", PrivateDns.hostname("пример.рф"))
        assertEquals("one.one.one.one", PrivateDns.hostname("one.one.one.one"))
    }

    @Test fun rejectsIpsPortsUrlsAndMalformedNames() {
        listOf("", "1.1.1.1", "2001:db8::1", "[::1]", "https://dns.google/dns-query",
            "tls://dns.google", "dns.google:853", "dns.google/path", "dns.google?foo=1",
            "bad host.com", "example..com", "-bad.example", "bad-.example", "localhost", "a.123", "a".repeat(64) + ".com").forEach {
            assertNull(it, PrivateDns.hostname(it))
        }
    }
}
