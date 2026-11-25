package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class NetworkConditionTest {
    private val good = NetworkConditions(
        validated = true, captivePortal = false, notCongested = true, notSuspended = true,
        notRoaming = true, temporarilyUnmetered = false, metered = true,
        downstreamKbps = 12_000, upstreamKbps = 3_000
    )

    @Test fun documentedCapabilitiesDecideTheLevel() {
        val verdict = NetworkConditionPolicy.verdict(good)
        assertEquals(NetworkConditionLevel.GOOD, verdict.level)
        assertTrue(verdict.headline.contains("не сообщает"))

        assertEquals(NetworkConditionLevel.UNUSABLE, NetworkConditionPolicy.verdict(good.copy(validated = false)).level)
        assertEquals(NetworkConditionLevel.UNUSABLE, NetworkConditionPolicy.verdict(good.copy(captivePortal = true)).level)
        assertEquals(NetworkConditionLevel.UNUSABLE, NetworkConditionPolicy.verdict(good.copy(notSuspended = false)).level)
        assertEquals(NetworkConditionLevel.CAUTION, NetworkConditionPolicy.verdict(good.copy(notCongested = false)).level)
        assertEquals(NetworkConditionLevel.CAUTION, NetworkConditionPolicy.verdict(good.copy(notRoaming = false)).level)
        assertEquals(NetworkConditionLevel.CAUTION,
            NetworkConditionPolicy.verdict(good.copy(temporarilyUnmetered = true, metered = true)).level)
    }

    @Test fun captivePortalIsUnusableEvenWhenValidatedFlagArrivesLate() {
        val verdict = NetworkConditionPolicy.verdict(good.copy(captivePortal = true, validated = true))
        assertEquals(NetworkConditionLevel.UNUSABLE, verdict.level)
        assertTrue(verdict.details.any { it.contains("captive portal") })
    }

    @Test fun missingBandwidthIsUnknownNotZero() {
        val verdict = NetworkConditionPolicy.verdict(good.copy(downstreamKbps = 0, upstreamKbps = 0))
        val text = verdict.details.single { it.contains("Оценка полосы") }
        assertTrue(text.contains("недоступна"))
        assertFalse(text.contains("0 кбит/с"))
        assertEquals(NetworkConditionLevel.GOOD, verdict.level)

        val oneWay = NetworkConditionPolicy.verdict(good.copy(downstreamKbps = 0, upstreamKbps = 3_000))
        val oneWayText = oneWay.details.single { it.contains("Оценка полосы") }
        assertTrue(oneWayText.contains("↑ 3000 кбит/с"))
        assertTrue(oneWayText.contains("не равно нулю"))
    }

    @Test fun cableBandwidthIsDescribedAsAnEstimate() {
        val text = NetworkConditionPolicy.verdict(good).details.single { it.contains("Оценка полосы") }
        assertTrue(text.contains("12000"))
        assertTrue(text.contains("оценка системы"))
        assertTrue(NetworkConditionPolicy.verdict(good).details.none { it.contains("скорость канала измерена") })
    }

    @Test fun congestionWordingDoesNotBlameAppsOrProveCausality() {
        val verdict = NetworkConditionPolicy.verdict(good.copy(notCongested = false))
        val text = verdict.details.single { it.contains("перегруженной") }
        assertTrue(text.contains("не доказывает причину"))
    }
}
