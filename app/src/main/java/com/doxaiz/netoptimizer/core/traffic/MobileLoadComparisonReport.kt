package com.doxaiz.netoptimizer.core.traffic

/** Testable wording for the comparison card; the screen only renders these values. */
internal data class MobileLoadComparisonReport(val headline: String, val explanation: String)

internal object MobileLoadComparisonReportBuilder {
    fun build(before: MobileLoadSample, after: MobileLoadSample, nowElapsedMs: Long): MobileLoadComparisonReport {
        val difference = MobileLoadComparison.difference(before, after, nowElapsedMs)
        if (difference != null) {
            return MobileLoadComparisonReport(
                "Изменение средней передачи: ↓ ${signed(difference.rxBytesPerSecond)} · ↑ ${signed(difference.txBytesPerSecond)}",
                "Разница доступна менее 10 минут после первого замера при совпадении сети. Это не доказательство причины или ускорения: счётчики общие, а одинаковая сеть не гарантирует непрерывную связь и одинаковые условия между замерами."
            )
        }
        val reason = when (MobileLoadComparison.status(before, after, nowElapsedMs)) {
            MobileLoadComparisonStatus.DIFFERENT_NETWORK -> "мобильные сети различаются"
            MobileLoadComparisonStatus.COUNTERS_RESET -> "счётчики могли сброситься между замерами"
            MobileLoadComparisonStatus.EXPIRED -> "прошло не менее 10 минут после первого замера"
            MobileLoadComparisonStatus.INVALID_TIME -> "порядок времени недостоверен"
            MobileLoadComparisonStatus.INVALID_SAMPLE -> "один из замеров недостоверен"
            MobileLoadComparisonStatus.READY -> "нет данных"
        }
        return MobileLoadComparisonReport("Разница недоступна: $reason. Для сравнения повторите замеры.", "")
    }

    /** Byte values are descriptive; zero stays zero instead of being hidden as "no data". */
    fun signed(bytesPerSecond: Double): String = when {
        kotlin.math.abs(bytesPerSecond) < 0.5 -> "≈0 Б/с"
        bytesPerSecond > 0 -> "+" + format(bytesPerSecond)
        else -> "−" + format(-bytesPerSecond)
    }

    private fun format(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1_000_000 -> "%.2f МБ/с".format(java.util.Locale.ROOT, bytesPerSecond / 1_000_000)
        bytesPerSecond >= 1_000 -> "%.1f КБ/с".format(java.util.Locale.ROOT, bytesPerSecond / 1_000)
        else -> "%.0f Б/с".format(java.util.Locale.ROOT, bytesPerSecond)
    }
}
