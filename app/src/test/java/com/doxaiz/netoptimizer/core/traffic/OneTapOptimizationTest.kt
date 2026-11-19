package com.doxaiz.netoptimizer.core.traffic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OneTapOptimizationTest {
    private class Fake(
        val reads: MutableList<Boolean?> = mutableListOf(),
        val checks: MutableList<Boolean> = mutableListOf(true, true, true, true),
        var sent: Boolean = true
    ) : OneTapOperations {
        var calls = 0
        var changed = 0
        override fun canContinue(): Boolean = checks.removeAt(0)
        override suspend fun readGlobalSetting(): Boolean? { calls++; return reads.removeAt(0) }
        override suspend fun enableDataSaver(): Boolean { changed++; return sent }
    }

    @Test fun enablesOnlyAfterCheckingAndVerifiesActualState() = runBlocking {
        val f = Fake(mutableListOf(false, false, true))
        assertEquals(OneTapResult.ENABLED, OneTapOptimization.apply(f))
        assertEquals(1, f.changed)
        assertEquals(3, f.calls)
    }

    @Test fun doesNotOverwriteAlreadyEnabledOrUnknownSetting() = runBlocking {
        val enabled = Fake(mutableListOf(true))
        assertEquals(OneTapResult.ALREADY_ENABLED, OneTapOptimization.apply(enabled))
        assertEquals(0, enabled.changed)
        val unknown = Fake(mutableListOf(null))
        assertEquals(OneTapResult.UNAVAILABLE, OneTapOptimization.apply(unknown))
        assertEquals(0, unknown.changed)
        val changedExternally = Fake(mutableListOf(false, true))
        assertEquals(OneTapResult.ALREADY_ENABLED, OneTapOptimization.apply(changedExternally))
        assertEquals(0, changedExternally.changed)
    }

    @Test fun stopsWhenNetworkOrScreenChangesBeforeCommand() = runBlocking {
        val f = Fake(mutableListOf(false), mutableListOf(true, false))
        assertEquals(OneTapResult.INTERRUPTED, OneTapOptimization.apply(f))
        assertEquals(0, f.changed)
    }

    @Test fun doesNotClaimSuccessWhenCommandOrReadbackFails() = runBlocking {
        val f = Fake(mutableListOf(false, false, true), sent = false)
        assertEquals(OneTapResult.UNCONFIRMED, OneTapOptimization.apply(f))
        assertEquals(1, f.changed)
    }

    @Test fun interruptionAfterCommandIsNotPresentedAsACompletedComparison() = runBlocking {
        val f = Fake(mutableListOf(false, false, true), mutableListOf(true, true, true, false))
        assertEquals(OneTapResult.INTERRUPTED, OneTapOptimization.apply(f))
        assertEquals(1, f.changed)
    }
}
