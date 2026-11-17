package com.doxaiz.netoptimizer.core.util

import android.content.Context

/** Флаг прохождения онбординга при первом запуске */
object OnboardingPrefs {
    private const val PREFS = "doxaiz_onboarding"
    private const val KEY = "done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    private const val KEY_MODE = "start_mode"

    fun saveMode(context: Context, modeName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, modeName).apply()
    }

    fun loadMode(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
}
