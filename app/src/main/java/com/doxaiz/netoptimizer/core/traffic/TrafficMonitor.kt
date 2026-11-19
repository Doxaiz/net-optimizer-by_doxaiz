package com.doxaiz.netoptimizer.core.traffic

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** One row per UID: shared UIDs are deliberately never attributed to one arbitrary app. */
data class AppTraffic(
    val uid: Int,
    val label: String,
    val packageNames: List<String>,
    val rxBytes: Long,
    val txBytes: Long,
    val systemProtected: Boolean,
    val outsideForegroundBytes: Long? = null
) {
    val totalBytes: Long get() = saturatedAdd(rxBytes, txBytes)
}

enum class TrafficSummaryStatus { READY, NO_ACCESS, UNAVAILABLE }
data class TrafficSummary(val status: TrafficSummaryStatus, val apps: List<AppTraffic> = emptyList(), val window: TrafficWindow? = null)

class TrafficMonitor(private val context: Context) {
    private val protectedPackages = setOf("com.android.phone", "com.android.systemui", "com.google.android.dialer", "com.samsung.android.dialer")
    private val prefs = context.getSharedPreferences("protected_traffic_apps", Context.MODE_PRIVATE)

    fun protectedByUser(): Set<String> = prefs.getStringSet("packages", emptySet())?.toSet() ?: emptySet()
    fun setProtected(pkg: String, protect: Boolean) {
        val updated = protectedByUser().toMutableSet()
        if (protect) updated.add(pkg) else updated.remove(pkg)
        prefs.edit().putStringSet("packages", updated).apply()
    }

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            when (appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
                else -> false
            }
        } catch (_: Exception) { false }
    }

    /** Mobile interface counters, not per-app rates and never Wi-Fi counters. */
    fun observeMobileRate(): Flow<MobileRate?> = flow {
        var previous: MobileCounters? = null
        while (true) {
            val network = CellularNetwork.active(context)
            val current = if (network != null) {
                val rx = TrafficStats.getMobileRxBytes()
                val tx = TrafficStats.getMobileTxBytes()
                val now = SystemClock.elapsedRealtime()
                if (rx >= 0 && tx >= 0 && CellularNetwork.active(context) == network)
                    MobileCounters(network.networkHandle, now, rx, tx) else null
            } else null
            emit(current?.let { TrafficRate.between(previous, it) })
            previous = current
            delay(2_000)
        }
    }.flowOn(Dispatchers.IO)

    /** Historical mobile bytes for a calendar period; Android may report with a delay. */
    suspend fun summary(period: TrafficPeriod): TrafficSummary = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext TrafficSummary(TrafficSummaryStatus.NO_ACCESS)
        val manager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return@withContext TrafficSummary(TrafficSummaryStatus.UNAVAILABLE)
        val window = TrafficPeriods.window(period, System.currentTimeMillis(), ZoneId.systemDefault())
        try {
            @Suppress("DEPRECATION")
            val stats = manager.querySummary(ConnectivityManager.TYPE_MOBILE, null, window.startMs, window.endMs)
                ?: return@withContext TrafficSummary(TrafficSummaryStatus.UNAVAILABLE)
            val totals = TrafficTotals()
            stats.use {
                val bucket = NetworkStats.Bucket()
                while (it.hasNextBucket()) {
                    currentCoroutineContext().ensureActive()
                    if (!it.getNextBucket(bucket)) return@withContext TrafficSummary(TrafficSummaryStatus.UNAVAILABLE)
                    val state = when (bucket.state) {
                        NetworkStats.Bucket.STATE_DEFAULT -> TrafficBucketState.DEFAULT
                        NetworkStats.Bucket.STATE_FOREGROUND -> TrafficBucketState.FOREGROUND
                        else -> TrafficBucketState.UNKNOWN
                    }
                    if (!totals.add(bucket.uid, bucket.rxBytes, bucket.txBytes, state)) return@withContext TrafficSummary(TrafficSummaryStatus.UNAVAILABLE)
                }
            }
            currentCoroutineContext().ensureActive()
            if (!hasUsageAccess()) return@withContext TrafficSummary(TrafficSummaryStatus.NO_ACCESS)
            val pm = context.packageManager
            val apps = totals.snapshot().mapNotNull { (uid, amount) ->
                currentCoroutineContext().ensureActive()
                if (amount.rx == 0L && amount.tx == 0L) return@mapNotNull null
                val packages = if (uid >= 0) (pm.getPackagesForUid(uid)?.toList() ?: emptyList()).sorted() else emptyList()
                val info = if (packages.size == 1) try { pm.getApplicationInfo(packages.first(), 0) } catch (_: Exception) { null } else null
                val label = when {
                    uid == NetworkStats.Bucket.UID_TETHERING -> "Раздача интернета"
                    uid == NetworkStats.Bucket.UID_REMOVED -> "Удалённые приложения"
                    packages.size > 1 -> "Общий UID $uid · ${packages.size} приложений"
                    info != null -> try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { packages.first() }
                    else -> "Система или неизвестное приложение · UID $uid"
                }
                val automaticProtection = packages.size != 1 || info == null || uid == Process.myUid() ||
                    packages.any { it in protectedPackages } || (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                AppTraffic(uid, label, packages, amount.rx, amount.tx, automaticProtection, amount.outsideForeground)
            }.sortedByDescending { it.totalBytes }
            TrafficSummary(TrafficSummaryStatus.READY, apps, window)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            TrafficSummary(if (hasUsageAccess()) TrafficSummaryStatus.UNAVAILABLE else TrafficSummaryStatus.NO_ACCESS)
        }
    }

    /** User-installed apps available for a manual settings action, independent of usage access.
     * This list is not evidence that an app is active or consuming network traffic. */
    internal suspend fun manualStopTargets(): List<StopTarget>? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledApplications(0)
            val targets = mutableListOf<StopTarget>()
            for (info in installed) {
                currentCoroutineContext().ensureActive()
                if (!info.enabled || info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
                val target = StopTarget(info.uid, info.packageName, try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName })
                if (canStopNow(target)) targets.add(target)
            }
            targets.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { null }
    }

    /** Recheck package identity and protection immediately before each user-confirmed stop. */
    internal suspend fun canStopNow(target: StopTarget): Boolean = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(target.packageName, 0)
            val packages = pm.getPackagesForUid(target.uid)?.toList().orEmpty()
            val dialer = try { context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage } catch (_: Exception) { null }
            val sms = try { android.provider.Telephony.Sms.getDefaultSmsPackage(context) } catch (_: Exception) { null }
            val launcher = try {
                pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val protected = protectedByUser() + protectedPackages + listOfNotNull(dialer, sms, launcher)
            StopPolicy.eligible(target, Process.myUid(), info.uid, packages,
                info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0, protected)
        } catch (_: Exception) { false }
    }

    fun openUsageAccess(): Boolean = open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    /** This opens a user-controlled Android screen; it does not change or verify policy. */
    internal fun openBackgroundDataSettings(packageName: String, launch: ((Intent) -> Unit)? = null): ManualSettingsDestination =
        ManualSettingsPolicy.openBackgroundSettings { destination ->
            when (destination) {
                ManualSettingsDestination.DATA_USAGE -> open(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }, launch)
                ManualSettingsDestination.APP_DETAILS -> openAppSettings(packageName, launch)
                ManualSettingsDestination.UNAVAILABLE -> false
            }
        }

    fun openAppSettings(packageName: String, launch: ((Intent) -> Unit)? = null): Boolean = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.parse("package:$packageName")
    }, launch)
    private fun open(intent: Intent, launch: ((Intent) -> Unit)? = null): Boolean = try {
        if (launch != null) launch(intent) else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }
}
