package com.doxaiz.netoptimizer.core.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doxaiz.netoptimizer.core.dns.DnsSamples
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs on Android with the packaged JNI library, never only on the host JVM. */
@RunWith(AndroidJUnit4::class)
class NativeMeasurementsInstrumentedTest {
    @Test fun nativeLibraryIsActuallyLoaded() {
        assertTrue("Rust JNI library did not load", NativeMeasurements.available)
        assertNotNull("TCP path silently fell back to Kotlin", NativeMeasurements.tcp(listOf(10, -1, 20, 25)))
        assertEquals(20, NativeMeasurements.median(listOf(10, 20, 30), 3))
    }

    @Test fun tcpMatchesKotlinAcrossEdgeCasesAndRandomInputs() {
        val cases = listOf(emptyList(), listOf(-1), listOf(0), listOf(0, Int.MAX_VALUE),
            listOf(10, -1, 20, 25), List(4096) { if (it % 2 == 0) Int.MAX_VALUE else 0 })
        val random = Random(1234567)
        val randomCases = List(1200) {
            List(random.nextInt(64)) { if (random.nextInt(4) == 0) -1 else random.nextInt(Int.MAX_VALUE) }
        }
        (cases + randomCases).forEach { samples ->
            val native = NativeMeasurements.tcp(samples)
            assertNotNull("Native TCP path unavailable for ${samples.size} samples", native)
            assertEquals(TcpProbeSummary.fromKotlin(samples), native)
            assertEquals(native, TcpProbeSummary.from(samples))
        }
        val oversized = List(4097) { 1 }
        assertNull(NativeMeasurements.tcp(oversized))
        assertEquals(TcpProbeSummary.fromKotlin(oversized), TcpProbeSummary.from(oversized))
    }

    @Test fun medianMatchesReferenceAndHandlesFailures() {
        val random = Random(2026)
        repeat(1500) {
            val samples = List(random.nextInt(60)) { if (random.nextInt(4) == 0) -1 else random.nextInt(Int.MAX_VALUE) }
            val minimum = 1 + random.nextInt(8)
            val sorted = samples.filter { it >= 0 }.sorted()
            val expected = if (sorted.size < minimum) -1 else if (sorted.size % 2 == 1) sorted[sorted.size / 2] else
                ((sorted[sorted.size / 2 - 1].toLong() + sorted[sorted.size / 2]) / 2).toInt()
            assertEquals(expected, NativeMeasurements.median(samples, minimum))
        }
        assertEquals(-1, NativeMeasurements.median(emptyList(), 3))
        assertEquals(Int.MAX_VALUE - 1, NativeMeasurements.median(listOf(Int.MAX_VALUE - 1, Int.MAX_VALUE), 2))
        assertNull(NativeMeasurements.median(List(4097) { 1 }, 3))
        assertNull(NativeMeasurements.median(listOf(1), 0))
    }

    @Test fun concurrentCallsAndMaximumMedianMatchReference() {
        assertTrue("Rust JNI library did not load", NativeMeasurements.available)
        val maximum = List(4096) { if (it % 3 == 0) -1 else 4096 - it }
        val sorted = maximum.filter { it >= 0 }.sorted()
        val midpoint = sorted.size / 2
        val expectedMedian = if (sorted.size % 2 == 1) sorted[midpoint] else
            ((sorted[midpoint - 1].toLong() + sorted[midpoint]) / 2).toInt()
        assertEquals(expectedMedian, NativeMeasurements.median(maximum, 1))
        assertEquals(-1, NativeMeasurements.median(maximum, 4096))

        val executor = Executors.newFixedThreadPool(6)
        try {
            val tasks = (0 until 6).map { worker ->
                Callable {
                    val random = Random(9000L + worker)
                    repeat(120) {
                        val samples = List(random.nextInt(65)) {
                            if (random.nextInt(4) == 0) -1 else random.nextInt(Int.MAX_VALUE)
                        }
                        assertEquals(TcpProbeSummary.fromKotlin(samples), NativeMeasurements.tcp(samples))
                        val valid = samples.filter { it >= 0 }.sorted()
                        val median = if (valid.isEmpty()) -1 else if (valid.size % 2 == 1) valid[valid.size / 2] else
                            ((valid[valid.size / 2 - 1].toLong() + valid[valid.size / 2]) / 2).toInt()
                        assertEquals(median, NativeMeasurements.median(samples, 1))
                    }
                }
            }
            executor.invokeAll(tasks, 60, TimeUnit.SECONDS).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun actualConsumersUseSameConventions() {
        assertEquals(20, DnsSamples.summarize(listOf(10, 20, 30)).medianMs)
        assertNull(DnsSamples.summarize(listOf(10, 20)).medianMs)
        val loaded = LoadedLatencySummary.from(listOf(10, -1, 20, 30), listOf(20, 40, 60, 80))
        assertEquals(20, loaded.idleMedianMs)
        assertEquals(50, loaded.loadedMedianMs)
        assertEquals(30, loaded.differenceMs)
    }
}
