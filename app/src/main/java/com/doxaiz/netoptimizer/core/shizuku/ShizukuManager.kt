package com.doxaiz.netoptimizer.core.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.doxaiz.netoptimizer.core.traffic.DataSaverPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

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

    /** Commands use fixed arguments, not shell interpolation. */
    private fun newProcess(vararg cmd: String): Process? = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        val method = cls.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        method.invoke(null, cmd, null, null) as? Process
    } catch (error: Exception) {
        Log.w("NetOptShizuku", "Command creation failed: ${error.javaClass.simpleName}")
        null
    }

    private fun runCommand(vararg cmd: String): Boolean {
        val process = newProcess(*cmd) ?: return false
        return try {
            process.waitFor(6, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) { false }
        finally { try { process.destroy() } catch (_: Exception) { } }
    }

    /** The fixed netpolicy query has short output; unknown/vendor responses are not success. */
    private fun readGlobalDataSaver(): Boolean? {
        val process = newProcess("cmd", "netpolicy", "get", "restrict-background") ?: return null
        return try {
            if (!process.waitFor(6, TimeUnit.SECONDS) || process.exitValue() != 0) {
                Log.w("NetOptShizuku", "Status command did not complete successfully")
                null
            } else DataSaverPolicy.parseGlobal(process.inputStream.bufferedReader().use { it.readText() }).also {
                if (it == null) Log.w("NetOptShizuku", "Status command returned an unrecognized response")
            }
        } catch (error: Exception) {
            Log.w("NetOptShizuku", "Status query failed: ${error.javaClass.simpleName}")
            null
        } finally { try { process.destroy() } catch (_: Exception) { } }
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

    /** Open the installed app if possible, otherwise its official store or GitHub page. */
    fun openAppOrStore(context: Context): Boolean {
        val installed = try { context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") } catch (_: Exception) { null }
        val intents = listOfNotNull(installed,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku")))
        for (intent in intents) {
            try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) { }
        }
        return false
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

    /** Null means Shizuku or the global system status could not be verified. */
    suspend fun globalDataSaverEnabled(): Boolean? = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext null
        readGlobalDataSaver()
    }

    /** Only call after explicit confirmation: this persistent setting affects the whole device. */
    suspend fun setDataSaver(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext false
        runCommand("cmd", "netpolicy", "set", "restrict-background", if (enabled) "true" else "false")
    }

    /** Disabled: toggling mobile data does not guarantee a different cell and can break tethering. */
    suspend fun resetCell(): Boolean = false

    /** Disabled: never apply saved network modes without an explicit, verified safe action. */
    fun applySavedNetworkMode(context: Context) = Unit
}
