package com.doxaiz.netoptimizer.core.cellular

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После перезагрузки телефона повторно применяет сохранённый режим сети
 * (если доступен Shizuku). Без Shizuku режим нужно выставить вручную —
 * приложение напомнит об этом карточкой на главном экране.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val mode = NetworkModePrefs.load(context)
        if (mode != NetworkMode.AUTO) {
            // Пытаемся применить через Shizuku (если он запустился к этому моменту)
            NetworkModeApplier.apply(mode)
        }
    }
}
