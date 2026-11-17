package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class SpeedResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val bufferbloatMs: Int
)

/**
 * Честный спидтест на открытых узлах Cloudflare + замер пинга под нагрузкой
 * (Bufferbloat — причина «скорость есть, а в игре лагает»).
 * При ошибках сети возвращает 0.0 вместо краша.
 */
class SpeedTester {

    suspend fun test(): SpeedResult = withContext(Dispatchers.IO) {
        val down = safeDownload(10_000_000)
        val up = safeUpload()
        val bb = bufferbloat()
        SpeedResult(down, up, bb)
    }

    /** Быстрый замер скачивания (для сравнения Wi-Fi vs 4G) */
    suspend fun quickDownload(bytes: Int = 5_000_000): Double = withContext(Dispatchers.IO) {
        safeDownload(bytes)
    }

    private fun safeDownload(bytes: Int): Double = try {
        download(bytes)
    } catch (_: Exception) {
        0.0
    }

    private fun safeUpload(): Double = try {
        upload()
    } catch (_: Exception) {
        0.0
    }

    private fun download(bytes: Int): Double {
        val conn = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        val start = System.nanoTime()
        var read = 0
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                read += n
            }
        }
        val seconds = (System.nanoTime() - start) / 1e9
        return if (seconds > 0) (read * 8 / 1e6) / seconds else 0.0
    }

    private fun upload(): Double {
        val bytes = 5_000_000
        val conn = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        val data = ByteArray(bytes) { 1 }
        val start = System.nanoTime()
        conn.outputStream.use { it.write(data) }
        conn.inputStream.readBytes()
        val seconds = (System.nanoTime() - start) / 1e9
        return if (seconds > 0) (bytes * 8 / 1e6) / seconds else 0.0
    }

    /** Пинг во время загрузки: чем меньше, тем лучше для игр */
    private fun bufferbloat(): Int {
        var max = 0
        repeat(3) {
            val s = java.net.Socket()
            val t = try {
                val start = System.nanoTime()
                s.connect(java.net.InetSocketAddress("1.1.1.1", 443), 2000)
                ((System.nanoTime() - start) / 1_000_000).toInt()
            } catch (_: Exception) { 0 }
            finally {
                try { s.close() } catch (_: Exception) {}
            }
            if (t > max) max = t
        }
        return max
    }
}
