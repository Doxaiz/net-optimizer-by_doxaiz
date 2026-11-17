package com.doxaiz.netoptimizer.core.traffic

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Живая статистика одного приложения */
data class AppTraffic(
    val packageName: String,
    val label: String,
    val uid: Int,
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val totalRxBytes: Long,
    val networkBlocked: Boolean,
    val whitelisted: Boolean
)

/**
 * Монитор трафика: живая скорость каждого приложения через NetworkStatsManager.
 * Белый список защищает звонки, банки и системный тетеринг от случайного отключения.
 *
 * queryDetailsForUid вызывается по каждому UID отдельно (5 параметров, API 29+),
 * байты суммируются по всем бакетам — получаем кумулятивный объём,
 * а скорость = дельта между двумя снимками.
 */
class TrafficMonitor(private val context: Context) {

    private val statsManager: android.app.usage.NetworkStatsManager =
        context.getSystemService(android.app.usage.NetworkStatsManager::class.java)

    private val connectivity: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val whitelist = setOf(
        "com.android.phone", "com.android.systemui",
        "com.google.android.dialer", "com.samsung.android.dialer"
    )

    fun observe(blocked: Set<String>): Flow<List<AppTraffic>> = flow {
        var prev = mutableMapOf<Int, Pair<Long, Long>>()
        while (true) {
            val list = snapshot(prev, blocked)
            emit(list)
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    private fun snapshot(
        prev: MutableMap<Int, Pair<Long, Long>>,
        blocked: Set<String>
    ): List<AppTraffic> {
        val network = connectivity.activeNetwork ?: return emptyList()
        val caps = connectivity.getNetworkCapabilities(network) ?: return emptyList()
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 0 // TRANSPORT_CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1      // TRANSPORT_WIFI
            else -> return emptyList()
        }
        val pm = context.packageManager
        val end = System.currentTimeMillis()

        @Suppress("DEPRECATION")
        val installed = try {
            pm.getInstalledPackages(0)
        } catch (_: Exception) {
            emptyList<android.content.pm.PackageInfo>()
        }

        val latest = mutableMapOf<Int, Pair<Long, Long>>()
        for (info in installed) {
            val uid = info.applicationInfo?.uid ?: continue
            if (uid == Process.myUid()) continue
            val stats = try {
                statsManager.queryDetailsForUid(transport, "", 0L, end, uid)
            } catch (_: Exception) { null } ?: continue
            val bucket = android.app.usage.NetworkStats.Bucket()
            var rx = 0L
            var tx = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                rx += bucket.rxBytes
                tx += bucket.txBytes
            }
            try { stats.close() } catch (_: Exception) { }
            if (rx + tx > 0L) latest[uid] = rx to tx
        }

        val result = mutableListOf<AppTraffic>()
        for ((uid, totals) in latest) {
            val (rx, tx) = totals
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
            val old = prev[uid]
            val rxSpeed = if (old != null && rx >= old.first) (rx - old.first) / 2 else 0L
            val txSpeed = if (old != null && tx >= old.second) (tx - old.second) / 2 else 0L
            prev[uid] = rx to tx
            val label = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) { pkg }
            result += AppTraffic(
                packageName = pkg,
                label = label,
                uid = uid,
                rxBytesPerSec = rxSpeed,
                txBytesPerSec = txSpeed,
                totalRxBytes = rx,
                networkBlocked = blocked.contains(pkg),
                whitelisted = whitelist.contains(pkg)
            )
        }
        if (prev.size > 500) prev.clear() // защита от роста памяти за длинную сессию
        return result.sortedByDescending { it.rxBytesPerSec + it.txBytesPerSec }
    }

    /** Открыть системную карточку приложения (кнопка «Остановить» без root) */
    fun openAppSettings(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
