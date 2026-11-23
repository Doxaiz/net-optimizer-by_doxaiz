package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficRankingTest {
    private fun app(uid: Int, total: Long, outside: Long?) = AppTraffic(uid, "App $uid", listOf("org.example.app$uid"), total, 0, false, outside)

    @Test fun totalsUseAllBytesAndDeterministicTies() {
        val apps = listOf(app(3, 10, null), app(2, 20, 1), app(1, 20, 0))
        assertEquals(listOf(1, 2, 3), TrafficRanking.sort(apps, TrafficSort.TOTAL).map { it.uid })
    }

    @Test fun directionsAreIndependentOfTotalAndRemainDeterministic() {
        val apps = listOf(
            AppTraffic(10, "Upload", listOf("a"), 5, 100, false),
            AppTraffic(3, "Download", listOf("b"), 200, 10, false),
            AppTraffic(2, "Shared", listOf("c", "d"), 200, 10, true),
            AppTraffic(-5, "Tethering", emptyList(), 0, 0, true)
        )
        assertEquals(listOf(2, 3, 10, -5), TrafficRanking.sort(apps, TrafficSort.RECEIVED).map { it.uid })
        assertEquals(listOf(10, 2, 3, -5), TrafficRanking.sort(apps, TrafficSort.SENT).map { it.uid })
        assertEquals(listOf("c", "d"), TrafficRanking.sort(apps, TrafficSort.SENT)[1].packageNames)
        assertEquals(emptyList<Int>(), TrafficRanking.sort(emptyList(), TrafficSort.SENT).map { it.uid })
    }

    @Test fun unavailableStateComesAfterKnownZeroAndKnownNonzero() {
        val apps = listOf(app(4, 1000, null), app(3, 100, 0), app(2, 40, 20), app(1, 50, 20))
        assertEquals(listOf(1, 2, 3, 4), TrafficRanking.sort(apps, TrafficSort.OUTSIDE_FOREGROUND).map { it.uid })
        assertEquals(emptyList<Int>(), TrafficRanking.sort(emptyList(), TrafficSort.OUTSIDE_FOREGROUND).map { it.uid })
    }
}
