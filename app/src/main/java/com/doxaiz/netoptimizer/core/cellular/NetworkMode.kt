package com.doxaiz.netoptimizer.core.cellular

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import rikka.shizuku.Shizuku

/** Сохранённые предпочтения не применяются автоматически: проверка VoLTE не реализована. */
enum class NetworkMode(val label: String, val typeValue: Int) {
    AUTO("Авто (4G/5G)", 10),
    LTE_ONLY("Только 4G (LTE)", 11),
    NR_ONLY("Только 5G (NR)", 23),
    NR_LTE("NR/LTE (5G приоритет)", 24)
}

/** Сохранение желаемого режима сети (переживает перезагрузку) */
object NetworkModePrefs {
    private const val PREFS = "doxaiz_network_mode"
    private const val KEY = "mode"

    fun save(context: Context, mode: NetworkMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    fun load(context: Context): NetworkMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return name?.let { runCatching { NetworkMode.valueOf(it) }.getOrNull() } ?: NetworkMode.AUTO
    }
}

/**
 * Применение режима сети через Shizuku (нужен установленный Shizuku + разрешение).
 * Все обращения к API Shizuku и скрытым классам — через рефлексию,
 * чтобы сборка не зависела от конкретной версии Shizuku API.
 */
object NetworkModeApplier {

    fun isShizukuReady(): Boolean = try {
        ShizukuManager.shizukuRunning() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /** Получить binder сервиса "phone" через Shizuku */
    private fun phoneBinder(): IBinder? {
        try {
            val helper = Class.forName("rikka.shizuku.SystemServiceHelper")
            val b = helper.getMethod("getSystemService", String::class.java).invoke(null, "phone")
            if (b is IBinder) return b
        } catch (_: Exception) { }
        return try {
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val serviceBinder = shizukuCls.getMethod("getBinder").invoke(null) ?: return null
            val wrapperCls = Class.forName("rikka.shizuku.ShizukuBinderWrapper")
            wrapperCls.getConstructor(IBinder::class.java).newInstance(serviceBinder) as? IBinder
        } catch (_: Exception) {
            null
        }
    }

    /** Fail closed until VoLTE, emergency-call and device compatibility checks exist. */
    fun apply(mode: NetworkMode): Boolean = false

    /** Текущий режим сети (через Shizuku). -1, если недоступно. */
    fun currentType(): Int {
        if (!isShizukuReady()) return -1
        return try {
            val binder = phoneBinder() ?: return -1
            val stubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val telephony = asInterface.invoke(null, binder) ?: return -1
            val getMethod = telephony.javaClass.getMethod(
                "getPreferredNetworkTypeForUser",
                Int::class.javaPrimitiveType
            )
            getMethod.invoke(telephony, 0) as? Int ?: -1
        } catch (_: Exception) {
            -1
        }
    }
}
