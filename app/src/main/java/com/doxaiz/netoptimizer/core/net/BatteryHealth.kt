package com.doxaiz.netoptimizer.core.net

/** Values reported by Android; null means unavailable, not zero. Temperature is in tenths of °C. */
data class BatterySnapshot(val percent: Int?, val temperatureTenths: Int?)

enum class BatteryWarning { UNKNOWN_CHARGE, LOW_CHARGE, HIGH_TEMPERATURE }

object BatteryHealth {
    const val MAX_READING_AGE_MS = 15_000L

    fun fromRaw(level: Int, scale: Int, temperatureTenths: Int): BatterySnapshot {
        val percent = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null
        val temperature = temperatureTenths.takeIf { it in -400..1000 }
        return BatterySnapshot(percent, temperature)
    }

    fun isFresh(observedAtMs: Long?, foregroundSinceMs: Long?, nowMs: Long): Boolean =
        observedAtMs != null && foregroundSinceMs != null && observedAtMs >= foregroundSinceMs &&
            nowMs >= observedAtMs && nowMs - observedAtMs <= MAX_READING_AGE_MS

    fun warnings(snapshot: BatterySnapshot): List<BatteryWarning> = buildList {
        if (snapshot.percent == null) add(BatteryWarning.UNKNOWN_CHARGE)
        else if (snapshot.percent < 20) add(BatteryWarning.LOW_CHARGE)
        if (snapshot.temperatureTenths != null && snapshot.temperatureTenths > 420) add(BatteryWarning.HIGH_TEMPERATURE)
    }
}
