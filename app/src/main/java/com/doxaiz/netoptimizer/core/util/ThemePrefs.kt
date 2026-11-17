package com.doxaiz.netoptimizer.core.util

import android.content.Context
import com.doxaiz.netoptimizer.ui.theme.DoxaizThemeKind

/** Сохранение выбранной темы оформления */
object ThemePrefs {
    private const val PREFS = "doxaiz_theme"
    private const val KEY = "kind"

    fun save(context: Context, kind: DoxaizThemeKind) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, kind.name).apply()
    }

    fun load(context: Context): DoxaizThemeKind {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return name?.let { runCatching { DoxaizThemeKind.valueOf(it) }.getOrNull() } ?: DoxaizThemeKind.AURORA
    }
}
