package com.doxaiz.netoptimizer.core.net

/** Invalid or missing battery readings fail closed. The threshold is exactly 20%. */
object BatteryPolicy {
    fun canMeasure(level: Int, scale: Int): Boolean =
        scale > 0 && level in 0..scale && level.toLong() * 100 >= scale.toLong() * 20
}
