package com.doxaiz.netoptimizer.core.session

import com.doxaiz.netoptimizer.core.data.SessionEntity
import java.io.Writer
import java.time.Instant

/** CSV is a human-readable export, not an importable backup. The caller owns the writer. */
object SessionCsv {
    private val header = listOf(
        "id", "started_at_utc", "name", "mode", "duration_seconds", "avg_batch_tcp_ms",
        "max_batch_tcp_ms", "failed_tcp_percent", "speed_mbps", "drops", "signal_dbm_at_save",
        "band_at_save", "dns_name", "operator_at_save", "recent_batch_averages_ms", "pinned"
    )

    fun write(writer: Writer, sessions: List<SessionEntity>, checkCancelled: () -> Unit = {}) {
        writer.write('\uFEFF'.code) // UTF-8 BOM for spreadsheet programs.
        writeRow(writer, header)
        for (session in sessions) {
            checkCancelled()
            val cells = listOf(
                session.id.toString(),
                Instant.ofEpochMilli(session.timestamp).toString(),
                safeText(session.name),
                safeText(session.mode),
                session.durationSec.takeIf { it >= 0 }?.toString().orEmpty(),
                session.avgPingMs.takeIf { it >= 0 }?.toString().orEmpty(),
                session.maxPingMs.takeIf { it >= 0 }?.toString().orEmpty(),
                session.lossPercent.takeIf { it in 0..100 }?.toString().orEmpty(),
                session.avgSpeedMbps.takeIf { it.isFinite() && it >= 0 }?.toString().orEmpty(),
                session.dropsCount.takeIf { it >= 0 }?.toString().orEmpty(),
                session.avgRsrp.takeIf { it in -160..-30 }?.toString().orEmpty(),
                safeText(session.band),
                safeText(session.dnsName),
                safeText(session.operator),
                safeText(session.pingHistory),
                if (session.pinned) "1" else "0"
            )
            writeRow(writer, cells)
        }
    }

    private fun writeRow(writer: Writer, cells: List<String>) {
        cells.forEachIndexed { index, value ->
            if (index > 0) writer.write(";")
            writer.write("\"")
            writer.write(value.replace("\"", "\"\""))
            writer.write("\"")
        }
        writer.write("\r\n")
    }

    /** Mitigate spreadsheet formula execution, including after leading whitespace/control characters. */
    private fun safeText(value: String): String {
        val first = value.firstOrNull { !it.isWhitespace() && !it.isISOControl() }
        return if (first == '=' || first == '+' || first == '-' || first == '@' ||
            value.firstOrNull()?.let { it == '\t' || it == '\r' || it == '\n' } == true) "'$value" else value
    }
}
