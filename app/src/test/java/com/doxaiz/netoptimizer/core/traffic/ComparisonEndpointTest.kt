package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class ComparisonEndpointTest {
    @Test fun parsesAndLabelsSelectedHostAndPort() {
        val domain = ComparisonEndpoint.parse(" пример.рф ", "8443")!!
        assertEquals("xn--e1afmkfd.xn--p1ai:8443", ComparisonEndpoint.label(domain))
        assertEquals("192.0.2.1:1", ComparisonEndpoint.label(ComparisonEndpoint.parse("192.0.2.1", "1")!!))
        assertEquals("[2001:db8::1]:65535", ComparisonEndpoint.label(ComparisonEndpoint.parse("[2001:db8::1]", "65535")!!))
        assertEquals("[2001:db8::1]:53", ComparisonEndpoint.label(ComparisonEndpoint.parse("2001:db8::1", "53")!!))
        assertEquals("yandex.ru:443", ComparisonEndpoint.label(ComparisonEndpoint.parse(ComparisonEndpoint.DEFAULT_HOST, ComparisonEndpoint.DEFAULT_PORT)!!))
    }

    @Test fun neverSubstitutesDefaultForInvalidInput() {
        for ((host, port) in listOf("" to "443", "example.com" to "", "https://example.com" to "443", "999.1.1.1" to "443", "[2001:db8::gg]" to "443", "example.com" to "0", "example.com" to "65536")) {
            assertNull("$host:$port", ComparisonEndpoint.parse(host, port))
        }
    }
}
