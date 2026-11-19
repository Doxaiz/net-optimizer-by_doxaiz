package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficRankingTest {
    private fun app(uid: Int, total: Long, outside: Long?) = AppTraffic(uid, "App $uid", listOf("org.example.app$uid"), total, 0, false, outside)

    @Test fun totalsUseAllBytesAndDeterministicTies() {
        val apps = listOf(app(3, 10, null), app(2, 20, 1), app(1, 20, 0))
        assertEquals(listOf(1, 2, 3), TrafficRanking.sort(apps, TrafficSort.TOTAL).map { it.uid })
    }

    @Test fun unavailableStateComesAfterKnownZeroAndKnownNonzero() {
        val apps = listOf(app(4, 1000, null), app(3, 100, 0), app(2, 40, 20), app(1, 50, 20))
        assertEquals(listOf(1, 2, 3, 4), TrafficRanking.sort(apps, TrafficSort.OUTSIDE_FOREGROUND).map { it.uid })
        assertEquals(emptyList<Int>(), TrafficRanking.sort(emptyList(), TrafficSort.OUTSIDE_FOREGROUND).map { it.uid })
    }
}
