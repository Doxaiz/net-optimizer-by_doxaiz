package com.doxaiz.netoptimizer.core.net

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Runs TCP connects before and during one transfer, accepting only samples overlapping progress. */
internal class DownloadLatencyProbe(
    private val cancelled: AtomicBoolean,
    private val check: () -> Unit,
    private val connect: () -> Int,
    private val sampleIntervalMs: Long = 150
) {
    private val active = AtomicBoolean(false)
    private val bytes = AtomicLong(0)
    private val loaded = mutableListOf<Int>() // Accessed only by sampler until it is joined.
    private var sampler: Thread? = null
    private var idle = emptyList<Int>()

    fun measureIdle() {
        idle = List(4) {
            check()
            connect()
        }
        check()
    }

    fun onBytes(count: Int) {
        if (count <= 0) return
        bytes.addAndGet(count.toLong())
        if (sampler == null && idle.count { it >= 0 } >= LoadedLatencySummary.MIN_SUCCESS) {
            active.set(true)
            sampler = Thread({ sampleWhileDownloading() }, "download-latency-probe").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun sampleWhileDownloading() {
        try {
            repeat(8) {
                if (!active.get() || cancelled.get()) return
                check()
                val startActive = active.get()
                val before = bytes.get()
                val ms = connect()
                val after = bytes.get()
                val endActive = active.get()
                LoadedLatencySummary.overlappingSample(startActive, endActive, before, after, ms)?.let { loaded.add(it) }
                if (!active.get() || cancelled.get()) return
                Thread.sleep(sampleIntervalMs)
            }
        } catch (_: Exception) {
            // The owner verifies battery and network again before accepting any result.
        }
    }

    /** True if a timed-out sampler still owns the shared probe socket. */
    fun samplerRunning(): Boolean = sampler?.isAlive == true

    /** Called after the transfer or on error. Never read the list while the sampler is alive. */
    fun finish(): LoadedLatency {
        active.set(false)
        val thread = sampler
        if (thread != null) {
            try {
                thread.join(2_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CancellationException()
            }
        }
        return LoadedLatencySummary.from(idle, if (thread?.isAlive == true) emptyList() else loaded.toList())
    }
}
