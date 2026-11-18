package com.doxaiz.netoptimizer.core.cellular

/** The modem timestamp is approximate; thresholds are display safeguards, not a guarantee. */
enum class SignalFreshnessStatus { FRESH, UNKNOWN_MODEM_TIME, STALE_MODEM, STALE_POLL }

object SignalFreshness {
    const val MAX_POLL_AGE_MS = 15_000L
    const val MAX_MODEM_AGE_MS = 30_000L

    fun status(pollMs: Long?, modemMs: Long?, foregroundSinceMs: Long?, nowMs: Long): SignalFreshnessStatus {
        if (foregroundSinceMs == null || pollMs == null || pollMs < foregroundSinceMs ||
            foregroundSinceMs < 0 || nowMs < pollMs || nowMs - pollMs > MAX_POLL_AGE_MS
        ) return SignalFreshnessStatus.STALE_POLL
        if (modemMs == null || modemMs <= 0 || modemMs > pollMs || modemMs > nowMs) {
            return SignalFreshnessStatus.UNKNOWN_MODEM_TIME
        }
        return if (nowMs - modemMs > MAX_MODEM_AGE_MS) SignalFreshnessStatus.STALE_MODEM else SignalFreshnessStatus.FRESH
    }

    fun canDisplay(status: SignalFreshnessStatus): Boolean =
        status == SignalFreshnessStatus.FRESH || status == SignalFreshnessStatus.UNKNOWN_MODEM_TIME
}
