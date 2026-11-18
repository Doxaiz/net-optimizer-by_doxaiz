package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.Socket

/** Цель для пинг-теста: игра, сервис или свой IP/домен */
data class PingTarget(val name: String, val host: String, val port: Int, val hint: String)

enum class PingStatus { COMPLETED, NO_CELLULAR, NETWORK_CHANGED, RESOLUTION_FAILED, LOW_BATTERY }

data class PingResult(
    val target: PingTarget,
    val avgMs: Int,
    val jitterMs: Int, // -1: fewer than two successful connections
    val lossPercent: Int, // failed TCP attempts, -1 when no measurement was made
    val ok: Boolean,
    val attemptsMade: Int,
    val successfulAttempts: Int,
    val status: PingStatus
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

    suspend fun test(target: PingTarget, attempts: Int = 6): PingResult =
        withContext(Dispatchers.IO) {
            require(attempts > 0) { "attempts must be positive" }
            val network = CellularNetwork.active(context)
                ?: return@withContext result(target, emptyList(), PingStatus.NO_CELLULAR)
            if (!CellularNetwork.batteryOk(context)) return@withContext result(target, emptyList(), PingStatus.LOW_BATTERY)
            val address = try { network.getAllByName(target.host).firstOrNull() } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (CellularNetwork.active(context) != network) {
                return@withContext result(target, emptyList(), PingStatus.NETWORK_CHANGED)
            }
            if (address == null) return@withContext result(target, emptyList(), PingStatus.RESOLUTION_FAILED)
            val samples = mutableListOf<Int>()
            repeat(attempts) {
                currentCoroutineContext().ensureActive()
                if (CellularNetwork.active(context) != network) {
                    return@withContext result(target, samples, PingStatus.NETWORK_CHANGED)
                }
                if (!CellularNetwork.batteryOk(context)) return@withContext result(target, samples, PingStatus.LOW_BATTERY)
                samples += connectOnce(network, address, target.port)
            }
            val status = when {
                CellularNetwork.active(context) != network -> PingStatus.NETWORK_CHANGED
                !CellularNetwork.batteryOk(context) -> PingStatus.LOW_BATTERY
                else -> PingStatus.COMPLETED
            }
            result(target, samples, status)
        }

    /** Параллельный тест всех целей (быстро даже на плохой сети) */
    suspend fun testAll(targets: List<PingTarget>): Map<String, PingResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) { target.name to test(target) }
        }.awaitAll().toMap()
    }

    private fun result(target: PingTarget, samples: List<Int>, status: PingStatus): PingResult {
        val summary = TcpProbeSummary.from(samples)
        // Measurements from interrupted network transitions must not be presented as a complete test.
        return PingResult(target,
            if (status == PingStatus.COMPLETED) summary.averageMs else -1,
            if (status == PingStatus.COMPLETED) summary.jitterMs else -1,
            if (status == PingStatus.COMPLETED) summary.failedPercent else -1,
            status == PingStatus.COMPLETED && summary.averageMs in 0..150 && summary.failedPercent == 0,
            summary.attempts, summary.successes, status)
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
