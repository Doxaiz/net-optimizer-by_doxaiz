package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TrafficPeriodTest {
    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()

    @Test fun todayUsesLocalMidnightNotUtc() {
        val now = millis("2026-11-17T14:32:00Z")
        val window = TrafficPeriods.window(TrafficPeriod.TODAY, now, ZoneId.of("Asia/Yekaterinburg"))
        assertEquals(millis("2026-11-16T19:00:00Z"), window.startMs)
        assertEquals(now, window.endMs)
        assertEquals("Asia/Yekaterinburg", window.zoneId)
    }

    @Test fun sevenDaysIncludesCurrentDayAndCrossesYearBoundary() {
        val now = millis("2026-01-02T14:00:00Z")
        val window = TrafficPeriods.window(TrafficPeriod.LAST_SEVEN_DAYS, now, ZoneId.of("UTC"))
        assertEquals(millis("2025-12-27T00:00:00Z"), window.startMs)
        assertEquals(now, window.endMs)
    }

    @Test fun monthStartsOnFirstIncludingLeapYear() {
        val now = millis("2024-02-29T23:59:00Z")
        val window = TrafficPeriods.window(TrafficPeriod.MONTH_TO_DATE, now, ZoneId.of("UTC"))
        assertEquals(millis("2024-02-01T00:00:00Z"), window.startMs)
        assertEquals(now, window.endMs)
    }

    @Test fun daylightSavingUsesCalendarDaysRatherThanFixedHours() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.parse("2026-03-10T12:00:00-04:00[America/New_York]").toInstant().toEpochMilli()
        val window = TrafficPeriods.window(TrafficPeriod.LAST_SEVEN_DAYS, now, zone)
        assertEquals(millis("2026-03-04T05:00:00Z"), window.startMs)
        assertEquals(millis("2026-03-10T16:00:00Z"), window.endMs)
        assertTrue(window.startMs <= window.endMs)
    }

    @Test fun firstOfMonthMatchesToday() {
        val now = millis("2026-04-01T12:00:00Z")
        val zone = ZoneId.of("UTC")
        assertEquals(TrafficPeriods.window(TrafficPeriod.TODAY, now, zone), TrafficPeriods.window(TrafficPeriod.MONTH_TO_DATE, now, zone))
    }
}
