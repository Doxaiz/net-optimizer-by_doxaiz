package com.doxaiz.netoptimizer.core.traffic

import android.content.Context

/** User-chosen shortcuts, not a record of Android's policy or current activity. */
internal class ManualAppPins(context: Context) {
    private val preferences = context.getSharedPreferences("manual_app_pins", Context.MODE_PRIVATE)

    fun packages(): Set<String> = preferences.getStringSet("packages", emptySet())?.toSet().orEmpty()

    fun setPinned(packageName: String, pinned: Boolean) {
        val updated = packages().toMutableSet()
        if (pinned) updated.add(packageName) else updated.remove(packageName)
        preferences.edit().putStringSet("packages", updated).apply()
    }

    fun clear() { preferences.edit().remove("packages").apply() }
}

/** Search only the currently eligible list; stale or protected bookmarks never create targets. */
internal object ManualAppList {
    fun search(apps: List<StopTarget>, query: String, pinned: Set<String>): List<StopTarget> =
        apps.filter { ManualAppSearch.matches(it, query) }
            .sortedWith(compareByDescending<StopTarget> { it.packageName in pinned }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName })
}
