package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualAppListTest {
    private val alpha = StopTarget(10001, "org.example.alpha", "Alpha")
    private val beta = StopTarget(10002, "org.example.beta", "Beta")
    private val gamma = StopTarget(10003, "org.example.gamma", "Gamma")

    @Test fun pinnedAppsFirstThenSortedWithoutInventingStaleTargets() {
        assertEquals(listOf(beta, gamma, alpha), ManualAppList.search(
            listOf(gamma, alpha, beta), "", setOf(beta.packageName, gamma.packageName, "org.missing.app")))
    }

    @Test fun searchAppliesToPinnedAndOtherApps() {
        assertEquals(listOf(beta), ManualAppList.search(listOf(alpha, beta), "  BETA  ", setOf(alpha.packageName)))
        assertEquals(emptyList<StopTarget>(), ManualAppList.search(listOf(alpha), "gamma", setOf(gamma.packageName)))
    }

    @Test fun equalNamesHaveStablePackageTieBreak() {
        val a = StopTarget(10010, "org.example.a", "NAME")
        val b = StopTarget(10011, "org.example.b", "name")
        assertEquals(listOf(a, b), ManualAppList.search(listOf(b, a), "", emptySet()))
    }
}
