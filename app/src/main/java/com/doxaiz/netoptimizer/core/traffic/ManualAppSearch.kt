package com.doxaiz.netoptimizer.core.traffic

/** Searches installed app names and package identifiers; never infers current traffic. */
internal object ManualAppSearch {
    fun matches(target: StopTarget, query: String): Boolean {
        val term = query.trim()
        return term.isEmpty() || target.label.contains(term, ignoreCase = true) ||
            target.packageName.contains(term, ignoreCase = true)
    }
}
