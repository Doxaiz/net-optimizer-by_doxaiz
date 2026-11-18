package com.doxaiz.netoptimizer.core.net

import android.content.Context
import com.doxaiz.netoptimizer.core.dns.DnsWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

enum class KeepAliveProfile(val intervalMs: Long, val label: String) {
    ECO(30_000, "Эко (от батареи)"),
    BALANCE(10_000, "Баланс"),
    MAX(3_000, "Макс. отклик (игры и USB)")
}

/** Experimental foreground UDP traffic. It cannot guarantee modem state or improve latency. */
class KeepAliveEngine(private val context: Context) {
    private val loop = KeepAliveLoop { pulse() }
    val state = loop.state

    fun start(scope: CoroutineScope, profile: KeepAliveProfile) = loop.start(scope, profile.intervalMs)
    fun stop() = loop.stop()

    private suspend fun pulse(): KeepAliveStatus {
        currentCoroutineContext().ensureActive()
        if (!CellularNetwork.batteryOk(context)) return KeepAliveStatus.PAUSED_BATTERY
        val network = CellularNetwork.active(context) ?: return KeepAliveStatus.PAUSED_NO_CELLULAR
        return try {
            val address = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
            val data = DnsWire.query() // example.com A query, response deliberately not read
            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                currentCoroutineContext().ensureActive()
                if (CellularNetwork.active(context) != network) return KeepAliveStatus.PAUSED_NO_CELLULAR
                socket.send(DatagramPacket(data, data.size, address, 53))
            }
            currentCoroutineContext().ensureActive()
            if (CellularNetwork.active(context) != network) KeepAliveStatus.PAUSED_NO_CELLULAR else KeepAliveStatus.SENT
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { KeepAliveStatus.SEND_FAILED }
    }
}

fun KeepAliveStatus.description(): String = when (this) {
    KeepAliveStatus.STOPPED -> "Выключен"
    KeepAliveStatus.STARTING -> "Запускается"
    KeepAliveStatus.SENT -> "Пакет отправлен системой; доставка не проверяется"
    KeepAliveStatus.PAUSED_NO_CELLULAR -> "Пауза: нет активной мобильной сети"
    KeepAliveStatus.PAUSED_BATTERY -> "Пауза: заряд ниже 20% или неизвестен"
    KeepAliveStatus.SEND_FAILED -> "Не удалось отправить пакет; будет повторная попытка"
}
