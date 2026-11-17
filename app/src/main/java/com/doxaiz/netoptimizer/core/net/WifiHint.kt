package com.doxaiz.netoptimizer.core.net

import android.content.Context

/**
 * Подсказка «Wi-Fi медленнее 4G» (идея failover из Speedify):
 * запоминает последнюю скорость мобильного интернета и сравнивает с Wi-Fi.
 */
object WifiHint {

    private const val PREFS = "doxaiz_wifi_hint"
    private const val KEY_CELL_SPEED = "last_cellular_mbps"

    fun saveCellularSpeed(context: Context, mbps: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_CELL_SPEED, mbps.toFloat()).apply()
    }

    fun lastCellularSpeed(context: Context): Double {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_CELL_SPEED, 0f)
        return v.toDouble()
    }

    private const val KEY_LAST_CHECK = "last_wifi_check"

    /** Не чаще раза в 5 минут (экономия трафика) */
    fun shouldCheckWifi(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > 5 * 60 * 1000
    }

    fun markWifiChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }
}
