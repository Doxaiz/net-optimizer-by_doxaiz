package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

/** Цель для пинг-теста: игра, сервис или свой IP/домен */
data class PingTarget(val name: String, val host: String, val port: Int, val hint: String)

data class PingResult(
    val target: PingTarget,
    val avgMs: Int,
    val jitterMs: Int,
    val lossPercent: Int,
    val ok: Boolean
)

object PingCatalog {
    val targets = listOf(
        PingTarget("Standoff 2", "standoff2.com", 443, "мобильная игра"),
        PingTarget("PUBG Mobile", "pubgmobile.com", 443, "мобильная игра"),
        PingTarget("Mobile Legends", "m.mobilelegends.com", 443, "мобильная игра"),
        PingTarget("Brawl Stars", "supercell.com", 443, "мобильная игра"),
        PingTarget("Counter-Strike 2", "store.steampowered.com", 443, "ПК-игра · для USB-модема"),
        PingTarget("Dota 2", "store.steampowered.com", 443, "ПК-игра · EU East"),
        PingTarget("Valorant", "playvalorant.com", 443, "ПК-игра · Frankfurt"),
        PingTarget("Мир Танков", "worldoftanks.ru", 443, "ПК-игра · RU"),
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
            val network = CellularNetwork.active(context)
                ?: return@withContext PingResult(target, -1, 0, 100, false)
            val times = mutableListOf<Int>()
            var failed = 0
            repeat(attempts) {
                val t = connectOnce(network, target.host, target.port)
                if (t >= 0) times += t else failed++
            }
            val avg = if (times.isEmpty()) -1 else times.average().roundToInt()
            val jitter = if (times.size < 2) 0 else {
                val diffs = times.zipWithNext { a, b -> abs(a - b) }
                diffs.average().roundToInt()
            }
            val loss = (failed * 100 / attempts)
            PingResult(target, avg, jitter, loss, avg in 1..150 && loss == 0)
        }

    /** Параллельный тест всех целей (быстро даже на плохой сети) */
    suspend fun testAll(targets: List<PingTarget>): Map<String, PingResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) { target.name to test(target) }
        }.awaitAll().toMap()
    }

    private fun connectOnce(network: android.net.Network, host: String, port: Int): Int {
        val socket = Socket()
        return try {
            val address = network.getAllByName(host).first()
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
