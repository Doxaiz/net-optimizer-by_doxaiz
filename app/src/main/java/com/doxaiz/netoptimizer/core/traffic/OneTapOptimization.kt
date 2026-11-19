package com.doxaiz.netoptimizer.core.traffic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** A one-time, user-confirmed action. Never turns a pre-existing setting off. */
internal enum class OneTapResult { ENABLED, ALREADY_ENABLED, UNAVAILABLE, INTERRUPTED, UNCONFIRMED }

internal interface OneTapOperations {
    fun canContinue(): Boolean
    suspend fun readGlobalSetting(): Boolean?
    suspend fun enableDataSaver(): Boolean
}

internal object OneTapOptimization {
    suspend fun apply(operations: OneTapOperations): OneTapResult {
        currentCoroutineContext().ensureActive()
        if (!operations.canContinue()) return OneTapResult.INTERRUPTED
        val before = operations.readGlobalSetting()
        currentCoroutineContext().ensureActive()
        if (!operations.canContinue()) return OneTapResult.INTERRUPTED
        if (before == null) return OneTapResult.UNAVAILABLE
        if (before) return OneTapResult.ALREADY_ENABLED
        // Re-read immediately before the change; never claim another actor's change as ours.
        val current = operations.readGlobalSetting()
        currentCoroutineContext().ensureActive()
        if (!operations.canContinue()) return OneTapResult.INTERRUPTED
        if (current == null) return OneTapResult.UNAVAILABLE
        if (current) return OneTapResult.ALREADY_ENABLED
        val sent = operations.enableDataSaver()
        currentCoroutineContext().ensureActive()
        var after = operations.readGlobalSetting()
        repeat(3) {
            if (sent && after != true) {
                delay(250)
                after = operations.readGlobalSetting()
            }
        }
        currentCoroutineContext().ensureActive()
        if (!sent || after != true) return OneTapResult.UNCONFIRMED
        return if (operations.canContinue()) OneTapResult.ENABLED else OneTapResult.INTERRUPTED
    }
}
