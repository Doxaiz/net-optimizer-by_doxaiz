package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class CustomTargetTest {
    @Test fun acceptsDomainsIpv4AndIpv6() {
        assertEquals("example.com", CustomTarget.parse(" example.com ", "443")?.host)
        assertEquals("1.1.1.1", CustomTarget.parse("1.1.1.1", "53")?.host)
        assertEquals("2001:db8::1", CustomTarget.parse("[2001:db8::1]", "65535")?.host)
        assertEquals("2001:db8::1", CustomTarget.parse("2001:db8::1", "1")?.host)
        assertEquals("xn--e1afmkfd.xn--p1ai", CustomTarget.parse("пример.рф", "443")?.host)
    }

    @Test fun rejectsInvalidAddressesAndPortsWithoutLookingUpDns() {
        listOf("", "https://example.com", "example.com/path", "999.1.1.1", "1.2.3", "-bad.example", "bad host", "[not-ipv6]", "2001:db8::gg", "fe80::1%wlan0").forEach {
            assertNull(it, CustomTarget.parse(it, "443"))
        }
        listOf("", "0", "65536", "-1", "abc").forEach {
            assertNull(it, CustomTarget.parse("example.com", it))
        }
    }
}
