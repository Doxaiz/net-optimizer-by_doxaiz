package com.doxaiz.netoptimizer.core.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** Last uncaught Java/Kotlin exception; never sent off the device automatically. */
object CrashLog {
    private const val FILE_NAME = "crash.log"
    private const val MAX_CHARS = 128 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = ("Time: ${Date()}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nThread: ${thread.name}\n\n" + trace).take(MAX_CHARS)
                // Internal copy stays readable even if external storage is unavailable.
                File(appContext.filesDir, FILE_NAME).writeText(report)
                appContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME).writeText(report) }
            } catch (_: Throwable) {
                // Never prevent Android's normal crash handling if logging fails.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun read(context: Context): String? = try {
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()
    } catch (_: Exception) { null }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) { }
        try { context.getExternalFilesDir(null)?.let { File(it, FILE_NAME).delete() } } catch (_: Exception) { }
    }
}
