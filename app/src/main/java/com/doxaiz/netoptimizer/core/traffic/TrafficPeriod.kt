package com.doxaiz.netoptimizer.core.traffic

import java.time.Instant
import java.time.ZoneId

enum class TrafficPeriod(val label: String) {
    TODAY("Сегодня"),
    LAST_SEVEN_DAYS("7 дней"),
    MONTH_TO_DATE("Этот месяц")
}

/** Calendar periods in the device's selected time zone, ending at the query time. */
data class TrafficWindow(val startMs: Long, val endMs: Long, val zoneId: String)

object TrafficPeriods {
    fun window(period: TrafficPeriod, nowMs: Long, zone: ZoneId): TrafficWindow {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val startDate = when (period) {
            TrafficPeriod.TODAY -> date
            TrafficPeriod.LAST_SEVEN_DAYS -> date.minusDays(6)
            TrafficPeriod.MONTH_TO_DATE -> date.withDayOfMonth(1)
        }
        return TrafficWindow(startDate.atStartOfDay(zone).toInstant().toEpochMilli(), nowMs, zone.id)
    }
}
