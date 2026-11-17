package com.doxaiz.netoptimizer.core.net

import android.content.Context
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.Socket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

/** Foreground-only, bounded transfers to Cloudflare, bound to the original cellular network. */
class SpeedTester(private val context: Context) {
    suspend fun test(): SpeedResult = suspendCancellableCoroutine { continuation ->
        val cancelled = AtomicBoolean(false)
        val connection = AtomicReference<HttpURLConnection?>(null)
        val probeSocket = AtomicReference<Socket?>(null)
        continuation.invokeOnCancellation {
            cancelled.set(true)
            // Disconnect off the main thread: a vendor's socket implementation can block.
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
                try { probeSocket.get()?.close() } catch (_: Exception) { }
                connection.get()?.disconnect()
            })
        }
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            try {
                val network = CellularNetwork.active(context)
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val changed = AtomicBoolean(false)
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(available: Network) {
                        if (available != network) changed.set(true)
                    }
                    override fun onLost(lost: Network) {
                        if (lost == network) changed.set(true)
                    }
                    override fun onCapabilitiesChanged(current: Network, caps: NetworkCapabilities) {
                        if (current == network &&
                            (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                             !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))) changed.set(true)
                    }
                }
                var registered = false
                val latency = AtomicReference<LoadedLatency?>(null)
                val result = try {
                    if (network != null && manager != null) {
                        manager.registerDefaultNetworkCallback(callback)
                        registered = true
                    }
                    SpeedTestPlan.run(
                        hasInitialNetwork = network != null && manager != null,
                        sameNetwork = { network != null && !changed.get() && CellularNetwork.active(context) == network },
                        batteryOk = { CellularNetwork.batteryOk(context) },
                        cancelled = { cancelled.get() },
                        transfer = { direction, check ->
                            transfer(requireNotNull(network), direction, connection, probeSocket, cancelled, check, latency)
                        }
                    )
                } finally {
                    if (registered) manager?.unregisterNetworkCallback(callback)
                }
                val completed = if (result.status == SpeedStatus.COMPLETED && result.downloadMbps != null)
                    result.copy(downloadLatency = latency.get()) else result
                if (continuation.isActive) continuation.resume(completed)
            } catch (_: CancellationException) {
                // The caller cancelled; no result should be shown.
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
        })
    }

    private fun transfer(
        network: Network,
        direction: SpeedDirection,
        current: AtomicReference<HttpURLConnection?>,
        probeSocket: AtomicReference<Socket?>,
        cancelled: AtomicBoolean,
        check: () -> Unit,
        latency: AtomicReference<LoadedLatency?>
    ): Double? {
        val probe = if (direction == SpeedDirection.DOWNLOAD)
            DownloadLatencyProbe(cancelled, check, { tcpConnect(network, probeSocket, cancelled) }) else null
        probe?.measureIdle()
        val count = if (direction == SpeedDirection.DOWNLOAD) DOWNLOAD_BYTES else UPLOAD_BYTES
        val url = if (direction == SpeedDirection.DOWNLOAD)
            "https://speed.cloudflare.com/__down?bytes=$count" else "https://speed.cloudflare.com/__up"
        val conn = network.openConnection(URL(url)) as HttpURLConnection
        current.set(conn)
        try {
            check() // Also handles cancellation between openConnection and registration.
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (direction == SpeedDirection.UPLOAD) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(count)
                conn.setRequestProperty("Content-Type", "application/octet-stream")
            }
            val buffer = ByteArray(16 * 1024)
            if (direction == SpeedDirection.UPLOAD) SecureRandom().nextBytes(buffer)
            val start = System.nanoTime()
            var lastSafetyCheck = start
            fun checkDeadline() {
                if (cancelled.get()) throw CancellationException()
                val now = System.nanoTime()
                if (now - lastSafetyCheck >= 250_000_000L) {
                    check()
                    lastSafetyCheck = now
                }
                if (now - start > MAX_TRANSFER_NANOS) throw java.io.IOException("Transfer deadline")
            }
            if (direction == SpeedDirection.DOWNLOAD) {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                if (conn.contentEncoding != null && !conn.contentEncoding.equals("identity", ignoreCase = true)) return null
                val declared = conn.contentLengthLong
                if (declared >= 0 && declared != count.toLong()) return null
                var received = 0
                conn.inputStream.use { input ->
                    while (received < count) {
                        checkDeadline()
                        val n = input.read(buffer, 0, minOf(buffer.size, count - received))
                        if (n < 0) break
                        received += n
                        probe?.onBytes(n)
                    }
                    if (received != count) return null
                    // With no Content-Length, reject excess data rather than reporting a partial response.
                    if (declared < 0 && input.read() != -1) return null
                }
            } else {
                conn.outputStream.use { output ->
                    var sent = 0
                    while (sent < count) {
                        checkDeadline()
                        val n = minOf(buffer.size, count - sent)
                        output.write(buffer, 0, n)
                        sent += n
                    }
                }
                checkDeadline()
                if (conn.responseCode !in 200..299) return null
            }
            checkDeadline()
            return SpeedTestPlan.mbps(count.toLong(), System.nanoTime() - start)
        } finally {
            try {
                probe?.let { latency.set(it.finish()) }
            } finally {
                current.compareAndSet(conn, null)
                conn.disconnect()
            }
        }
    }

    private fun tcpConnect(network: Network, current: AtomicReference<Socket?>, cancelled: AtomicBoolean): Int {
        val socket = Socket()
        current.set(socket)
        return try {
            if (cancelled.get()) throw CancellationException()
            network.bindSocket(socket)
            val target = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(target, 443), 1_500)
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            -1
        } finally {
            current.compareAndSet(socket, null)
            try { socket.close() } catch (_: Exception) { }
        }
    }

    companion object {
        const val DOWNLOAD_BYTES = 10_000_000
        const val UPLOAD_BYTES = 5_000_000
        private const val MAX_TRANSFER_NANOS = 45_000_000_000L
    }
}
