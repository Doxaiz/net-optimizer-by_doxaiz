package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DownloadLatencyProbeTest {
    @Test fun connectsDuringDataReadsAndStopsSamplingAfterDownload() {
        val entered = Semaphore(0)
        val release = Semaphore(0)
        val calls = AtomicInteger()
        val probe = DownloadLatencyProbe(AtomicBoolean(false), {}, {
            if (calls.incrementAndGet() <= 4) 20 else {
                entered.release()
                check(release.tryAcquire(3, TimeUnit.SECONDS))
                60
            }
        }, sampleIntervalMs = 0)
        probe.measureIdle()
        probe.onBytes(100)
        repeat(3) {
            assertTrue(entered.tryAcquire(3, TimeUnit.SECONDS))
            probe.onBytes(100)
            release.release()
        }
        // Beginning the next attempt proves the preceding sample has already been recorded.
        assertTrue(entered.tryAcquire(3, TimeUnit.SECONDS))
        release.release()
        val result = probe.finish()
        assertEquals(4, result.idleSuccesses)
        assertTrue(result.loadedSuccesses >= 3)
        assertEquals(20, result.idleMedianMs)
        assertEquals(60, result.loadedMedianMs)
        assertEquals(40, result.differenceMs)
    }

    @Test fun noLoadedSamplingWhenIdleProbesFail() {
        val calls = AtomicInteger()
        val probe = DownloadLatencyProbe(AtomicBoolean(false), {}, { calls.incrementAndGet(); -1 }, 0)
        probe.measureIdle()
        probe.onBytes(100)
        val result = probe.finish()
        assertEquals(4, calls.get())
        assertEquals(0, result.loadedSuccesses)
        assertNull(result.differenceMs)
    }
}
