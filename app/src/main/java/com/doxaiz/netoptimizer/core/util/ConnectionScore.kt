package com.doxaiz.netoptimizer.core.util

/** Rough indicator based only on a successful TCP measurement and a valid RSRP reading. */
object ConnectionScore {
    fun calculate(ping: Int?, rsrp: Int): Int? {
        if (ping == null || ping < 0 || rsrp !in -160..-30) return null
        val p = when { ping <= 30 -> 40; ping <= 50 -> 32; ping <= 80 -> 24; ping <= 120 -> 15; else -> 5 }
        val s = when { rsrp >= -80 -> 40; rsrp >= -90 -> 32; rsrp >= -100 -> 22; rsrp >= -110 -> 12; else -> 4 }
        return (p + s + 10).coerceIn(5, 98)
    }
}
