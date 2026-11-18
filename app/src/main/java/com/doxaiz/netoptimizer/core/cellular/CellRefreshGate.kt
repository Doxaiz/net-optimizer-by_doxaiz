package com.doxaiz.netoptimizer.core.cellular

/** Coordinates asynchronous modem refreshes; Android cannot cancel an outstanding request. */
internal class CellRefreshGate<T>(private val intervalMs: Long = 15_000, private val timeoutMs: Long = 45_000) {
    private data class Request(val id: Long, val started: Long, val generation: Long)
    private var active = false
    private var generation = 0L
    private var nextId = 0L
    private var lastStarted: Long? = null
    private var inFlight: Request? = null
    private var pending: T? = null

    init { require(intervalMs > 0 && timeoutMs >= intervalMs) }

    @Synchronized fun start() { active = true; generation++; pending = null }
    @Synchronized fun stop() { active = false; generation++; pending = null }

    @Synchronized fun tryStart(now: Long): Long? {
        if (!active || now < 0) return null
        val last = lastStarted
        if (last != null && (now < last || now - last < intervalMs)) return null
        val existing = inFlight
        if (existing != null && (now < existing.started || now - existing.started < timeoutMs)) return null
        val id = ++nextId
        inFlight = Request(id, now, generation)
        lastStarted = now
        return id
    }

    @Synchronized fun complete(id: Long, value: T?) {
        val request = inFlight
        if (request?.id != id) return
        inFlight = null
        if (active && request.generation == generation && value != null) pending = value
    }

    @Synchronized fun take(): T? = pending.also { pending = null }
}
