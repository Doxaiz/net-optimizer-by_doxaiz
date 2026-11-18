package com.doxaiz.netoptimizer.core.cellular

/** Prefer newer modem timestamps; unknown times are not treated as newer than known times. */
internal object CellSourceChoice {
    fun useRefresh(cachePresent: Boolean, cacheMs: Long?, refreshPresent: Boolean, refreshMs: Long?, nowMs: Long): Boolean {
        if (!refreshPresent) return false
        if (!cachePresent) return true
        fun valid(timestamp: Long?): Long? = timestamp?.takeIf { it > 0 && it <= nowMs }
        val cached = valid(cacheMs)
        val refreshed = valid(refreshMs)
        return when {
            refreshed == null && cached != null -> false
            refreshed != null && cached != null -> refreshed >= cached
            else -> true // usable refresh with known time, or two unknown times
        }
    }
}
