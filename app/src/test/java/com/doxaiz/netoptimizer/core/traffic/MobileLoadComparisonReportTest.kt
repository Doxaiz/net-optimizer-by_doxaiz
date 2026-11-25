package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class MobileLoadComparisonReportTest {
    private fun sample(start: Long, network: Long = 42, rx: Long = 100, tx: Long = 200,
                       rxStep: Long = 500, txStep: Long = 100, interval: Long = 2_000): MobileLoadSample =
        MobileLoadWindow.result((0..5).map { index ->
            MobileCounters(network, start + interval * index, rx + rxStep * index, tx + txStep * index)
        })!!

    private val before = sample(1_000)

    @Test fun reportsSignedDifferencesWithDirectionAndLimit() {
        val after = sample(12_000, rx = 3_000, tx = 1_000, rxStep = 100, txStep = 200)
        val report = MobileLoadComparisonReportBuilder.build(before, after, 22_000)
        assertEquals("Изменение средней передачи: ↓ −200 Б/с · ↑ +50 Б/с", report.headline)
        assertTrue(report.explanation.contains("не доказательство причины"))
    }

    @Test fun zeroDifferenceIsShownNotHiddenAsMissingData() {
        val idleBefore = sample(1_000, rxStep = 0, txStep = 0)
        val idleAfter = sample(12_000, rx = 100, tx = 200, rxStep = 0, txStep = 0)
        val report = MobileLoadComparisonReportBuilder.build(idleBefore, idleAfter, 22_000)
        assertEquals("Изменение средней передачи: ↓ ≈0 Б/с · ↑ ≈0 Б/с", report.headline)
    }

    @Test fun incompatibleSamplesExplainWhyWithoutNumbers() {
        val otherNetwork = sample(12_000, network = 77)
        val network = MobileLoadComparisonReportBuilder.build(before, otherNetwork, 22_000)
        assertTrue(network.headline.startsWith("Разница недоступна: мобильные сети различаются"))
        assertEquals("", network.explanation)
        val expired = MobileLoadComparisonReportBuilder.build(before, sample(12_000, rx = 3_000, tx = 1_000), 611_000)
        assertTrue(expired.headline.startsWith("Разница недоступна: прошло не менее 10 минут"))
        val reset = MobileLoadComparisonReportBuilder.build(before, sample(12_000, rx = 1), 22_000)
        assertTrue(reset.headline.startsWith("Разница недоступна: счётчики могли сброситься"))
        val broken = MobileLoadComparisonReportBuilder.build(before.copy(durationMs = 0), sample(12_000), 22_000)
        assertTrue(broken.headline.startsWith("Разница недоступна: один из замеров недостоверен"))
        val time = MobileLoadComparisonReportBuilder.build(before, sample(12_000), 21_999)
        assertTrue(time.headline.startsWith("Разница недоступна: порядок времени недостоверен"))
    }

    @Test fun unreliableNetworkHidesTheDifferenceEvenForCompatibleSamples() {
        val after = sample(12_000, rx = 3_000, tx = 1_000, rxStep = 100, txStep = 200)
        assertNotNull(MobileLoadComparison.difference(before, after, 22_000))
        // A suspended, captive-portalled or unvalidated network makes any difference meaningless.
        assertNull(MobileLoadComparison.difference(before, after, 22_000, reliableNetwork = false))
        val report = MobileLoadComparisonReportBuilder.build(before, after, 22_000, reliableNetwork = false)
        assertEquals("", report.explanation)
        assertTrue(report.headline.startsWith("Разница недоступна: Android сообщает об ограничениях текущей сети"))
        // Timing and network-identity problems are reported before the reliability hint.
        assertEquals(MobileLoadComparisonStatus.DIFFERENT_NETWORK,
            MobileLoadComparison.status(before, sample(12_000, network = 9), 22_000, reliableNetwork = false))
    }

    @Test fun largeValuesUseReadableUnitsWithoutLocaleDecimalsLeaking() {
        val bigBefore = sample(1_000, rx = 0, tx = 0, rxStep = 0, txStep = 0)
        val bigAfter = sample(12_000, rx = 0, tx = 0, rxStep = 2_000_000, txStep = 5_000, interval = 2_000)
        val report = MobileLoadComparisonReportBuilder.build(bigBefore, bigAfter, 22_000)
        assertTrue(report.headline.contains("МБ/с"))
        assertTrue(report.headline.contains("КБ/с"))
        assertFalse(report.headline.contains(","))
    }
}
