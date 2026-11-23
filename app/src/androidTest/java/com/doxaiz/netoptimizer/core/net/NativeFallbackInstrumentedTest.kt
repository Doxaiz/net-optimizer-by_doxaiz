package com.doxaiz.netoptimizer.core.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doxaiz.netoptimizer.core.dns.DnsSamples
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only against a deliberately built -PnativeRust=false APK. */
@RunWith(AndroidJUnit4::class)
class NativeFallbackInstrumentedTest {
    @Test fun missingLibraryUsesKotlin() {
        assertFalse("Fallback APK unexpectedly contains the Rust library", NativeMeasurements.available)
        val samples = listOf(10, -1, 20, 25)
        assertNull(NativeMeasurements.tcp(samples))
        assertNull(NativeMeasurements.median(samples, 3))
        assertEquals(TcpProbeSummary.fromKotlin(samples), TcpProbeSummary.from(samples))
        assertEquals(20, DnsSamples.summarize(listOf(10, 20, 30)).medianMs)
        assertEquals(20, LoadedLatencySummary.from(listOf(10, 20, 30), emptyList()).idleMedianMs)
        assertEquals(TcpLatencyDistribution(20, 500), TcpLatencyDistributions.from(listOf(10, -1, 20, 500)))
        assertNull(TcpLatencyDistributions.from(listOf(10, -1, 20)))
    }
}
