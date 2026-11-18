package com.doxaiz.netoptimizer.core.session

import org.junit.Assert.*
import org.junit.Test

class SessionProgressTest {
    @Test fun pausedTimeIsNotCountedAndResumeDoesNotResetSamples() {
        val started = SessionProgress().resume(1_000).addCompleted(40, 6, 5)
        val paused = started.pause(4_500)
        assertEquals(3_500L, paused.elapsedAt(20_000))
        assertEquals(40, paused.averageLatency())
        val resumed = paused.resume(20_000).addCompleted(60, 6, 6).pause(22_000)
        assertEquals(5_500L, resumed.elapsedMs)
        assertEquals(listOf(40, 60), resumed.history)
        assertEquals(50, resumed.averageLatency())
        assertEquals(8, resumed.failedPercent())
    }

    @Test fun historyIsBoundedButStatisticsIncludeEarlierMeasurements() {
        var progress = SessionProgress()
        repeat(121) { progress = progress.addCompleted(it, 6, 6) }
        assertEquals(121, progress.sampleCount)
        assertEquals(120, progress.history.size)
        assertEquals(1, progress.history.first())
        assertEquals(60, progress.averageLatency())
        assertEquals(120, progress.maxLatency)
    }

    @Test fun failedTestsDoNotInventLatency() {
        val progress = SessionProgress().addCompleted(-1, 6, 0)
        assertEquals(-1, progress.averageLatency())
        assertEquals(-1, progress.maxLatency)
        assertEquals(100, progress.failedPercent())
        assertTrue(progress.history.isEmpty())
    }

    @Test fun repeatedPauseAndResumeDoNotDoubleCountTime() {
        val running = SessionProgress().resume(1_000).resume(2_000)
        val paused = running.pause(3_000).pause(9_000)
        assertEquals(2_000L, paused.elapsedMs)
        assertEquals(2_000L, paused.elapsedAt(12_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteStatisticsCannotBeAdded() {
        SessionProgress().addCompleted(50, 6, 0)
    }
}
