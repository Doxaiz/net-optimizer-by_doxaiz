package com.doxaiz.netoptimizer.core.net

import android.content.Context
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
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
        continuation.invokeOnCancellation {
            cancelled.set(true)
            // Disconnect off the main thread: a vendor's socket implementation can block.
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { connection.get()?.disconnect() })
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
                            transfer(requireNotNull(network), direction, connection, cancelled, check)
                        }
                    )
                } finally {
                    if (registered) manager?.unregisterNetworkCallback(callback)
                }
                if (continuation.isActive) continuation.resume(result)
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
        cancelled: AtomicBoolean,
        check: () -> Unit
    ): Double? {
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
            current.compareAndSet(conn, null)
            conn.disconnect()
        }
    }

    companion object {
        const val DOWNLOAD_BYTES = 10_000_000
        const val UPLOAD_BYTES = 5_000_000
        private const val MAX_TRANSFER_NANOS = 45_000_000_000L
    }
}
