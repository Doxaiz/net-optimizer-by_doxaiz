package com.doxaiz.netoptimizer.core.traffic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicitly chosen entries only. The historical traffic list is not a live activity list. */
internal data class StopTarget(val uid: Int, val packageName: String, val label: String)
internal enum class StopOutcome { SENT, FAILED, PROTECTED_OR_CHANGED }
internal data class StopResult(val target: StopTarget, val outcome: StopOutcome)

internal object StopPolicy {
    const val MAX_TARGETS = 10
    private val packagePattern = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    fun eligible(target: StopTarget, myUid: Int, currentUid: Int?, packages: List<String>, system: Boolean, protected: Set<String>): Boolean =
        target.uid >= 0 && target.uid % 100_000 in 10_000..19_999 && target.uid != myUid && target.uid == currentUid &&
            packagePattern.matches(target.packageName) && packages.size == 1 && packages.single() == target.packageName &&
            !system && target.packageName !in protected
}

/** Every target is revalidated immediately before sending; cancellation prevents later commands. */
internal object BatchStop {
    suspend fun run(
        targets: List<StopTarget>,
        eligible: suspend (StopTarget) -> Boolean,
        send: suspend (StopTarget) -> Boolean,
        onResult: (StopResult) -> Unit
    ) {
        require(targets.size in 1..StopPolicy.MAX_TARGETS)
        require(targets.map { it.packageName }.toSet().size == targets.size && targets.map { it.uid }.toSet().size == targets.size)
        for (target in targets) {
            currentCoroutineContext().ensureActive()
            val allowed = eligible(target)
            currentCoroutineContext().ensureActive()
            val outcome = if (!allowed) StopOutcome.PROTECTED_OR_CHANGED else {
                if (send(target)) StopOutcome.SENT else StopOutcome.FAILED
            }
            currentCoroutineContext().ensureActive()
            onResult(StopResult(target, outcome))
        }
    }
}
