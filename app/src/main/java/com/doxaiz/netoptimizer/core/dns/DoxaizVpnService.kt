package com.doxaiz.netoptimizer.core.dns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Локальный VPN-сервис Doxaiz (без root, без серверов):
 * 1) Перехватывает только DNS-запросы (UDP/53) и отвечает через выбранный DNS —
 *    основной трафик идёт напрямую на полной скорости оператора.
 * 2) Ведёт лог DNS-запросов и блокирует рекламные/трекинговые домены (NXDOMAIN).
 * 3) Блокирует сеть отдельным приложениям через addDisallowedApplication.
 *
 * Полный TCP-туннель не нужен: DNS-only режим не режет скорость и не ест батарею.
 */
class DoxaizVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null

    companion object {
        var currentDns: String = "77.88.8.8"
        var blockedApps: Set<String> = emptySet()
        var running: Boolean = false

        fun start(context: android.content.Context, dns: String, blocked: Set<String>) {
            currentDns = dns
            blockedApps = blocked
            val intent = Intent(context, DoxaizVpnService::class.java)
            context.startService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, DoxaizVpnService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Перестраиваем интерфейс при каждом старте, чтобы применились
        // новые DNS и список заблокированных приложений
        job?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        val builder = Builder()
            .setSession("Doxaiz DNS")
            .setMtu(1500)
            .addAddress("10.111.222.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(currentDns)
            .allowBypass()
        blockedApps.forEach { pkg ->
            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
        }
        try { builder.addAllowedApplication(packageName) } catch (_: Exception) {}

        val iface = builder.establish()
        if (iface == null) {
            // VPN не разрешён пользователем — корректно завершаемся
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        vpnInterface = iface
        running = true
        startForegroundNotification()
        job = scope.launch { runLoop() }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "doxaiz_vpn"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Doxaiz DNS", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Doxaiz DNS активен")
            .setContentText("Быстрый DNS и блокировка трекеров работают в фоне")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(2, notification)
    }

    private fun runLoop() {
        val iface = vpnInterface ?: return
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buf = ByteArray(2048)
        while (true) {
            val n = try { input.read(buf) } catch (_: Exception) { -1 }
            if (n <= 0) break
            val packet = buf.copyOf(n)
            scope.launch { handlePacket(packet, output) }
        }
    }

    /** Разбирает IPv4/UDP; DNS-запросы логирует, трекеры блокирует, остальные пересылает на DNS */
    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        try {
            if (packet.size < 28) return
            val version = (packet[0].toInt() shr 4) and 0x0F
            if (version != 4) return
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (packet[9].toInt() != 17) return // не UDP
            val srcIp = packet.copyOfRange(12, 16)
            val dstIp = packet.copyOfRange(16, 20)
            val udpStart = ihl
            val srcPort = ((packet[udpStart].toInt() and 0xFF) shl 8) or (packet[udpStart + 1].toInt() and 0xFF)
            val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or (packet[udpStart + 3].toInt() and 0xFF)
            if (dstPort != 53) return // только DNS
            val dnsStart = udpStart + 8
            val dnsQuery = packet.copyOfRange(dnsStart, packet.size)

            // Лог DNS + блокировка трекеров
            val domain = parseQName(dnsQuery)
            if (domain != null) {
                val blocked = DnsLog.isBlocked(domain)
                DnsLog.log(domain, blocked)
                if (blocked) {
                    val nx = buildNxDomain(dnsQuery)
                    val response = buildUdpResponse(srcIp, dstIp, dstPort, srcPort, nx)
                    synchronized(output) { output.write(response) }
                    return
                }
            }

            // Запрос к выбранному DNS (сокет обязательно закрываем)
            val socket = DatagramSocket()
            try {
                socket.soTimeout = 3000
                socket.send(DatagramPacket(dnsQuery, dnsQuery.size, InetSocketAddress(currentDns, 53)))
                val respBuf = ByteArray(2048)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)
                val response = buildUdpResponse(srcIp, dstIp, dstPort, srcPort, respBuf.copyOf(respPacket.length))
                synchronized(output) { output.write(response) }
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }

        } catch (_: Exception) {
            // Пакет не DNS или ошибка сети — молча пропускаем
        }
    }

    /** Извлекает доменное имя (QNAME) из DNS-запроса */
    private fun parseQName(query: ByteArray): String? {
        if (query.size < 13) return null
        var pos = 12
        val sb = StringBuilder()
        while (pos < query.size) {
            val len = query[pos].toInt() and 0xFF
            if (len == 0) break
            if (len > 63) return null // указатель сжатия — не запрос
            pos++
            if (pos + len > query.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(query, pos, len, Charsets.US_ASCII))
            pos += len
        }
        return sb.toString().ifEmpty { null }
    }

    /** Строит NXDOMAIN-ответ (домен не найден) для заблокированных трекеров */
    private fun buildNxDomain(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        resp[2] = (resp[2].toInt() or 0x80).toByte() // QR = 1 (ответ)
        resp[3] = (resp[3].toInt() or 0x03).toByte() // RCODE = 3 (NXDOMAIN)
        resp[6] = 0; resp[7] = 0                       // ANCOUNT = 0
        return resp
    }

    private fun buildUdpResponse(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val totalLen = 20 + 8 + payload.size
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x45)
        buf.put(0)
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x4000)
        buf.put(64)
        buf.put(17)
        buf.putShort(0)
        buf.put(dstIp)
        buf.put(srcIp)
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort((8 + payload.size).toShort())
        buf.putShort(0)
        buf.put(payload)
        return buf.array()
    }

    override fun onDestroy() {
        job?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        running = false
        super.onDestroy()
    }
}
