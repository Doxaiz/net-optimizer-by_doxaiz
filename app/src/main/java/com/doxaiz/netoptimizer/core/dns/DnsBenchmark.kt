package com.doxaiz.netoptimizer.core.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import java.util.concurrent.atomic.AtomicBoolean
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Бесплатные DNS-серверы для бенчмарка */
data class DnsServer(val name: String, val ip: String, val hint: String, val ms: Int = -1)

object DnsCatalog {
    val servers = listOf(
        DnsServer("Яндекс DNS", "77.88.8.8", "Публичный DNS"),
        DnsServer("Cloudflare 1.1.1.1", "1.1.1.1", "DoH/DoT · шифрованный"),
        DnsServer("AdGuard DNS", "94.140.14.14", "+ анти-реклама"),
        DnsServer("Google Public DNS", "8.8.8.8", "DoH/DoT"),
        DnsServer("Quad9", "9.9.9.9", "Защита от вредоносных доменов"),
        DnsServer("OpenDNS", "208.67.222.222", "Классика")
    )
}

/**
 * Реальный замер DNS: отправляет настоящий DNS-запрос (UDP/53) на сервер
 * и меряет время ответа. Медиана минимум 3 из 5 попыток. Серверы замеряются параллельно.
 */
class DnsBenchmark(private val context: android.content.Context) {

    suspend fun benchmarkAll(): List<DnsServer> = withContext(Dispatchers.IO) {
        val unavailable = DnsCatalog.servers.map { it.copy(ms = -1) }
        val network = CellularNetwork.active(context) ?: return@withContext unavailable
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@withContext unavailable
        val changed = AtomicBoolean(false)
        val lowBattery = AtomicBoolean(false)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) { if (available != network) changed.set(true) }
            override fun onLost(lost: Network) { if (lost == network) changed.set(true) }
            override fun onCapabilitiesChanged(current: Network, caps: NetworkCapabilities) {
                if (current == network && (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))) changed.set(true)
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            return@withContext unavailable
        }
        fun safe(): Boolean {
            if (CellularNetwork.active(context) != network) changed.set(true)
            if (!CellularNetwork.batteryOk(context)) lowBattery.set(true)
            return !changed.get() && !lowBattery.get()
        }
        try {
            if (!safe()) return@withContext unavailable
            val measured = coroutineScope {
                DnsCatalog.servers.map { server ->
                    async(Dispatchers.IO) {
                        val times = mutableListOf<Int>()
                        repeat(5) {
                            currentCoroutineContext().ensureActive()
                            if (!safe()) return@async server.copy(ms = -1)
                            val time = measureOnce(network, server.ip)
                            currentCoroutineContext().ensureActive()
                            if (!safe()) return@async server.copy(ms = -1)
                            if (time >= 0) times.add(time)
                        }
                        server.copy(ms = if (times.size < 3) -1 else times.sorted()[times.size / 2])
                    }
                }.awaitAll()
            }
            if (!safe()) unavailable else measured.sortedBy { if (it.ms < 0) Int.MAX_VALUE else it.ms }
        } finally {
            try { manager.unregisterNetworkCallback(callback) } catch (_: Exception) { }
        }
    }

    private fun measureOnce(network: android.net.Network, ip: String): Int {
        val socket = try { DatagramSocket() } catch (_: Exception) { return -1 }
        return try {
            network.bindSocket(socket)
            socket.soTimeout = 1500
            val query = DnsWire.query()
            socket.connect(InetSocketAddress(ip, 53))
            val start = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, InetSocketAddress(ip, 53)))
            val buf = ByteArray(512)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            if (!DnsWire.validResponse(query, buf, response.length)) return -1
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            socket.close()
        }
    }

}
