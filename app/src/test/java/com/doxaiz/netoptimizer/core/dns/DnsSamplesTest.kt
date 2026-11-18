package com.doxaiz.netoptimizer.core.dns

import org.junit.Assert.*
import org.junit.Test

class DnsSamplesTest {
    @Test fun requiresThreeValidatedResponsesAndCountsFailures() {
        assertEquals(DnsSampleSummary(0, null), DnsSamples.summarize(emptyList()))
        assertEquals(DnsSampleSummary(2, null), DnsSamples.summarize(listOf(2, 30)))
        assertEquals(DnsSampleSummary(3, 20), DnsSamples.summarize(listOf(30, 10, 20)))
        assertEquals(DnsSampleSummary(5, 30), DnsSamples.summarize(listOf(50, 10, 40, 20, 30)))
    }

    @Test fun evenMedianUsesBothMiddleValuesWithoutOverflow() {
        assertEquals(DnsSampleSummary(4, 25), DnsSamples.summarize(listOf(40, 10, 30, 20)))
        assertEquals(DnsSampleSummary(4, Int.MAX_VALUE), DnsSamples.summarize(List(4) { Int.MAX_VALUE }))
    }

    @Test fun rejectsInvalidSampleLists() {
        assertThrows(IllegalArgumentException::class.java) { DnsSamples.summarize(listOf(-1)) }
        assertThrows(IllegalArgumentException::class.java) { DnsSamples.summarize(List(6) { 1 }) }
    }
}
