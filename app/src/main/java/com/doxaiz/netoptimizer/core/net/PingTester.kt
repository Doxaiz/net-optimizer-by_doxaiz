package com.doxaiz.netoptimizer.core.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.doxaiz.netoptimizer.core.session.SessionNetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket

/** Цель для пинг-теста: игра, сервис или свой IP/домен */
data class PingTarget(val name: String, val host: String, val port: Int, val hint: String)

enum class PingStatus { COMPLETED, NO_CELLULAR, NETWORK_CHANGED, NETWORK_CHECK_UNAVAILABLE, RESOLUTION_FAILED, LOW_BATTERY }

data class PingResult(
    val target: PingTarget,
    val avgMs: Int,
    val jitterMs: Int, // -1: fewer than two successful connections
    val lossPercent: Int, // failed TCP attempts, -1 when no measurement was made
    val ok: Boolean,
    val attemptsMade: Int,
    val successfulAttempts: Int,
    val status: PingStatus,
    // Only published for a completed series with at least three successes.
    val medianMs: Int? = null,
    val maximumMs: Int? = null
)

object PingCatalog {
    val targets = listOf(
        PingTarget("Standoff 2", "standoff2.com", 443, "мобильная игра"),
        PingTarget("PUBG Mobile", "pubgmobile.com", 443, "мобильная игра"),
        PingTarget("Mobile Legends", "m.mobilelegends.com", 443, "мобильная игра"),
        PingTarget("Brawl Stars", "supercell.com", 443, "мобильная игра"),
        PingTarget("Counter-Strike 2", "store.steampowered.com", 443, "ПК-игра · для USB-модема"),
        PingTarget("Dota 2", "store.steampowered.com", 443, "сайт Steam"),
        PingTarget("Valorant", "playvalorant.com", 443, "сайт игры"),
        PingTarget("Мир Танков", "worldoftanks.ru", 443, "сайт игры"),
        PingTarget("Telegram", "telegram.org", 443, "мессенджер"),
        PingTarget("Яндекс", "yandex.ru", 443, "сервис РФ")
    )
}

/**
 * TCP-пинг: меряет время установки соединения (без root ICMP недоступен).
 * Считает средний пинг, джиттер и потери (неудачные попытки).
 */
class PingTester(private val context: android.content.Context) {

    suspend fun test(
        target: PingTarget,
        attempts: Int = 6,
        intervalMs: Long = 0,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): PingResult =
        withContext(Dispatchers.IO) {
            require(attempts > 0) { "attempts must be positive" }
            require(intervalMs in 0..10_000L) { "intervalMs must be between 0 and 10000" }
            val network = CellularNetwork.active(context)
                ?: return@withContext PingMeasurementPlan.result(target, emptyList(), PingStatus.NO_CELLULAR)
            if (!CellularNetwork.batteryOk(context)) return@withContext PingMeasurementPlan.result(target, emptyList(), PingStatus.LOW_BATTERY)
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return@withContext PingMeasurementPlan.result(target, emptyList(), PingStatus.NETWORK_CHECK_UNAVAILABLE)
            val guard = SessionNetworkGuard(network)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(available: Network) { guard.available(available) }
                override fun onLost(lost: Network) { guard.lost(lost) }
                override fun onCapabilitiesChanged(current: Network, caps: NetworkCapabilities) {
                    guard.capabilities(current, caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }
            }
            try {
                manager.registerDefaultNetworkCallback(callback)
            } catch (_: Exception) {
                return@withContext PingMeasurementPlan.result(target, emptyList(), PingStatus.NETWORK_CHECK_UNAVAILABLE)
            }
            try {
                PingMeasurementPlan.run(target, attempts,
                    sameNetwork = { guard.valid(CellularNetwork.active(context)) },
                    batteryOk = { CellularNetwork.batteryOk(context) },
                    resolve = {
                        try { network.getAllByName(target.host).firstOrNull() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { null }
                    },
                    connect = { address -> connectOnce(network, address, target.port) },
                    betweenAttempts = { if (intervalMs > 0) delay(intervalMs) },
                    onProgress = onProgress)
            } finally {
                try { manager.unregisterNetworkCallback(callback) } catch (_: Exception) { }
            }
        }

    /** Параллельный тест всех целей (быстро даже на плохой сети) */
    suspend fun testAll(targets: List<PingTarget>): Map<String, PingResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) { target.name to test(target) }
        }.awaitAll().toMap()
    }

    private fun connectOnce(network: android.net.Network, address: java.net.InetAddress, port: Int): Int {
        val socket = Socket()
        return try {
            network.bindSocket(socket)
            val start = System.nanoTime()
            socket.connect(java.net.InetSocketAddress(address, port), 2500)
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
