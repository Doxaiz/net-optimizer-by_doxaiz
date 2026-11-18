package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeepAliveLoopTest {
    @Test fun reportsPauseThenRetryWithoutClaimingDelivery() = runBlocking {
        val attempts = AtomicInteger()
        val retry = CompletableDeferred<Unit>()
        val loop = KeepAliveLoop {
            if (attempts.incrementAndGet() == 1) KeepAliveStatus.PAUSED_BATTERY
            else { retry.await(); KeepAliveStatus.SENT }
        }
        loop.start(this, 10)
        withTimeout(2_000) { loop.state.first { it == KeepAliveStatus.PAUSED_BATTERY } }
        retry.complete(Unit)
        withTimeout(2_000) { loop.state.first { it == KeepAliveStatus.SENT } }
        loop.stop()
        assertEquals(KeepAliveStatus.STOPPED, loop.state.value)
    }

    @Test fun stopCancelsInFlightOperationAndStaleResultCannotRestartIt() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loop = KeepAliveLoop {
            entered.complete(Unit)
            // Simulate a blocking platform call that cannot be cancelled immediately.
            withContext(NonCancellable) { release.await() }
            KeepAliveStatus.SENT
        }
        loop.start(this, 1000)
        withTimeout(2_000) { entered.await() }
        loop.stop()
        release.complete(Unit)
        delay(50)
        assertEquals(KeepAliveStatus.STOPPED, loop.state.value)
    }

    @Test fun restartDoesNotLetOldGenerationOverwriteNewStatus() = runBlocking {
        val count = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loop = KeepAliveLoop {
            if (count.incrementAndGet() == 1) {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                KeepAliveStatus.SENT
            } else KeepAliveStatus.PAUSED_NO_CELLULAR
        }
        loop.start(this, 10_000)
        withTimeout(2_000) { entered.await() }
        loop.start(this, 10_000)
        withTimeout(2_000) { loop.state.first { it == KeepAliveStatus.PAUSED_NO_CELLULAR } }
        release.complete(Unit)
        delay(50)
        assertEquals(KeepAliveStatus.PAUSED_NO_CELLULAR, loop.state.value)
        loop.stop()
    }

    @Test fun pulseFailureIsReportedAndStopIsIdempotent() = runBlocking {
        val loop = KeepAliveLoop { throw IllegalStateException() }
        loop.start(this, 10_000)
        withTimeout(2_000) { loop.state.first { it == KeepAliveStatus.SEND_FAILED } }
        loop.stop()
        loop.stop()
        assertEquals(KeepAliveStatus.STOPPED, loop.state.value)
    }
}
