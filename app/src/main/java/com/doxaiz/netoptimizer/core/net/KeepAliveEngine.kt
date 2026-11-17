package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

enum class KeepAliveProfile(val intervalMs: Long, val label: String) {
    ECO(30_000, "Эко (от батареи)"),
    BALANCE(10_000, "Баланс"),
    MAX(3_000, "Макс. отклик (игры и USB)")
}

/**
 * Анти-сон модема: не даёт радиомодулю уходить в RRC_IDLE между пакетами.
 * Сверхлёгкие UDP-импульсы (32 байта) — убирает стартовую задержку 150–300 мс
 * на первом пакете и «замирание» 4G.
 */
class KeepAliveEngine {

    private var job: Job? = null

    fun start(scope: CoroutineScope, profile: KeepAliveProfile) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            while (true) {
                pulse()
                delay(profile.intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun pulse() {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1000
                val data = ByteArray(32) { 0 }
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName("1.1.1.1"), 53))
            }
        } catch (_: Exception) {
            // Нет сети — просто пропускаем импульс
        }
    }
}
