package com.doxaiz.netoptimizer.core.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku: опциональные «супер-фичи» без root.
 *
 * Без Shizuku приложение полностью работоспособно — просто часть действий
 * открывает системные настройки вместо выполнения в 1 тап.
 *
 * Все вызовы API Shizuku идут через рефлексию: в разных версиях API
 * (11.x / 12.x / 13.x) набор статических методов отличается.
 */
object ShizukuManager {

    enum class Status(val label: String) {
        NOT_INSTALLED("Shizuku не установлен"),
        NOT_RUNNING("Shizuku установлен, но не запущен"),
        NO_PERMISSION("Shizuku запущен, нужно разрешение"),
        READY("Shizuku готов — супер-фичи активны")
    }

    /** Shizuku запущен? (pingBinder через рефлексию — в API 13.x нет isInstalled/isRunning) */
    fun shizukuRunning(): Boolean = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        cls.getMethod("pingBinder").invoke(null) as? Boolean ?: false
    } catch (_: Exception) {
        try {
            Class.forName("rikka.shizuku.Shizuku").getMethod("getBinder").invoke(null) != null
        } catch (_: Exception) { false }
    }

    /** Выполнить shell-команду через Shizuku (newProcess — private в API 13.x) */
    private fun runCommand(vararg cmd: String): Boolean = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        val m = cls.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val process = m.invoke(null, cmd, null, null) ?: return false
        val pcls = process.javaClass
        pcls.getMethod("waitFor").invoke(process)
        val code = pcls.getMethod("exitValue").invoke(process)
        code == 0
    } catch (_: Exception) {
        false
    }

    fun status(): Status = try {
        when {
            !shizukuRunning() -> Status.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NO_PERMISSION
            else -> Status.READY
        }
    } catch (_: Exception) {
        Status.NOT_INSTALLED
    }

    fun isReady(): Boolean = status() == Status.READY

    /** Открыть страницу Shizuku в Play Market (или GitHub, если нет Play) */
    fun openShizukuStore(context: Context) {
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku"))
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) { }
        }
    }

    /** Запросить разрешение Shizuku (нужен запущенный Shizuku) */
    fun requestPermission(requestCode: Int = 1001) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Exception) { }
    }

    /** Добавить слушатель изменения статуса Shizuku */
    fun addListener(listener: Shizuku.OnBinderReceivedListener) {
        try { Shizuku.addBinderReceivedListenerSticky(listener) } catch (_: Exception) { }
    }

    fun removeListener(listener: Shizuku.OnBinderReceivedListener) {
        try { Shizuku.removeBinderReceivedListener(listener) } catch (_: Exception) { }
    }

    /**
     * Принудительная остановка приложения через Shizuku (без открытия настроек).
     * Работает, если Shizuku готов. Возвращает true при успехе.
     */
    suspend fun forceStopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext false
        runCommand("am", "force-stop", packageName)
    }

    /**
     * Перерегистрация на ближнюю соту: переключение мобильных данных через Shizuku.
     * Wi-Fi не трогаем — иначе прервётся связь при USB-модеме.
     */
    suspend fun resetCell(): Boolean = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext false
        runCommand("svc", "data", "disable")
        delay(1500)
        runCommand("svc", "data", "enable")
        true
    }

    /** Применить сохранённый режим сети (при получении разрешения Shizuku) */
    fun applySavedNetworkMode(context: Context) {
        if (!isReady()) return
        try {
            val mode = com.doxaiz.netoptimizer.core.cellular.NetworkModePrefs.load(context)
            com.doxaiz.netoptimizer.core.cellular.NetworkModeApplier.apply(mode)
        } catch (_: Exception) { }
    }
}
