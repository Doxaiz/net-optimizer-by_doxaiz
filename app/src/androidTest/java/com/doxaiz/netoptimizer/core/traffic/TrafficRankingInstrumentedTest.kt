package com.doxaiz.netoptimizer.core.traffic

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic data only; this does not exercise Android's historical statistics service. */
@RunWith(AndroidJUnit4::class)
class TrafficRankingInstrumentedTest {
    @Test fun ranksHistoricalDirectionsAndKeepsSharedUidIntact() {
        val apps = listOf(
            AppTraffic(9, "Upload", listOf("org.example.one"), 1, 90, false),
            AppTraffic(3, "Shared", listOf("org.example.two", "org.example.three"), 100, 2, true),
            AppTraffic(2, "Download", listOf("org.example.four"), 100, 2, false)
        )
        assertEquals(listOf(9, 2, 3), TrafficRanking.sort(apps, TrafficSort.SENT).map { it.uid })
        val downloaded = TrafficRanking.sort(apps, TrafficSort.RECEIVED)
        assertEquals(listOf(2, 3, 9), downloaded.map { it.uid })
        assertTrue(downloaded[1].systemProtected)
        assertEquals(listOf("org.example.two", "org.example.three"), downloaded[1].packageNames)
    }
}
