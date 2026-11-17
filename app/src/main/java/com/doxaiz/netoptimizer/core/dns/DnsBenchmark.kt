package com.doxaiz.netoptimizer.core.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.Random
import kotlin.math.roundToInt

/** Бесплатные DNS-серверы для бенчмарка */
data class DnsServer(val name: String, val ip: String, val hint: String, val ms: Int = 0)

object DnsCatalog {
    val servers = listOf(
        DnsServer("Яндекс DNS", "77.88.8.8", "Оптимум по РФ и Уралу"),
        DnsServer("Cloudflare 1.1.1.1", "1.1.1.1", "DoH/DoT · шифрованный"),
        DnsServer("AdGuard DNS", "94.140.14.14", "+ анти-реклама"),
        DnsServer("Google Public DNS", "8.8.8.8", "DoH/DoT"),
        DnsServer("Quad9", "9.9.9.9", "Защита от вредоносных доменов"),
        DnsServer("OpenDNS", "208.67.222.222", "Классика")
    )
}

/**
 * Реальный замер DNS: отправляет настоящий DNS-запрос (UDP/53) на сервер
 * и меряет время ответа. Медиана из 5 попыток. Серверы замеряются параллельно.
 */
class DnsBenchmark(private val context: android.content.Context) {

    suspend fun measure(server: DnsServer): Int = withContext(Dispatchers.IO) {
        val network = com.doxaiz.netoptimizer.core.net.CellularNetwork.active(context) ?: return@withContext -1
        val times = mutableListOf<Int>()
        repeat(5) {
            val t = measureOnce(network, server.ip)
            if (t >= 0) times += t
        }
        if (times.isEmpty()) -1 else times.sorted()[times.size / 2]
    }

    suspend fun benchmarkAll(): List<DnsServer> = coroutineScope {
        DnsCatalog.servers.map { server ->
            async(Dispatchers.IO) { server.copy(ms = measure(server)) }
        }.awaitAll().sortedBy { if (it.ms < 0) Int.MAX_VALUE else it.ms }
    }

    private fun measureOnce(network: android.net.Network, ip: String): Int {
        val socket = try { DatagramSocket() } catch (_: Exception) { return -1 }
        return try {
            network.bindSocket(socket)
            socket.soTimeout = 1500
            val query = buildDnsQuery(randomDomain())
            socket.connect(InetSocketAddress(ip, 53))
            val start = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, InetSocketAddress(ip, 53)))
            val buf = ByteArray(512)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            if (response.length < 12 || buf[0] != query[0] || buf[1] != query[1] || (buf[2].toInt() and 0x80) == 0) return -1
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            socket.close()
        }
    }

    private fun randomDomain(): String {
        val rnd = Random()
        val tlds = listOf("ru", "com", "net", "org", "io")
        return "d${rnd.nextInt(1_000_000)}.${tlds[rnd.nextInt(tlds.size)]}"
    }

    /** Минимальный DNS-запрос A-записи */
    private fun buildDnsQuery(domain: String): ByteArray {
        val labels = domain.split(".")
        val qnameLen = labels.sumOf { it.length + 1 } + 1
        val buf = ByteArray(12 + qnameLen + 4)
        buf[0] = (Random().nextInt(256)).toByte()
        buf[1] = (Random().nextInt(256)).toByte()
        buf[2] = 0x01 // Recursion Desired
        buf[5] = 0x01 // QDCOUNT = 1
        var pos = 12
        for (label in labels) {
            buf[pos++] = label.length.toByte()
            for (c in label) buf[pos++] = c.code.toByte()
        }
        buf[pos++] = 0 // root
        buf[pos++] = 0; buf[pos++] = 1 // QTYPE = A
        buf[pos++] = 0; buf[pos++] = 1 // QCLASS = IN
        return buf
    }
}
