package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** SENT means the OS accepted the datagram, not that the server received it. */
enum class KeepAliveStatus { STOPPED, STARTING, SENT, PAUSED_NO_CELLULAR, PAUSED_BATTERY, SEND_FAILED }

internal class KeepAliveLoop(private val pulse: suspend () -> KeepAliveStatus) {
    private val lock = Any()
    private var generation = 0L
    private var job: Job? = null
    private val mutableState = MutableStateFlow(KeepAliveStatus.STOPPED)
    val state: StateFlow<KeepAliveStatus> = mutableState

    fun start(scope: CoroutineScope, intervalMs: Long) {
        require(intervalMs > 0)
        synchronized(lock) {
            generation++
            val id = generation
            job?.cancel()
            mutableState.value = KeepAliveStatus.STARTING
            val next = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                while (true) {
                    val result = try { pulse() } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { KeepAliveStatus.SEND_FAILED }
                    coroutineContext.ensureActive()
                    synchronized(lock) { if (id == generation) mutableState.value = result }
                    delay(intervalMs)
                }
            }
            job = next
            next.invokeOnCompletion {
                synchronized(lock) {
                    if (id == generation) {
                        job = null
                        mutableState.value = KeepAliveStatus.STOPPED
                    }
                }
            }
            next.start()
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            job?.cancel()
            job = null
            mutableState.value = KeepAliveStatus.STOPPED
        }
    }
}
