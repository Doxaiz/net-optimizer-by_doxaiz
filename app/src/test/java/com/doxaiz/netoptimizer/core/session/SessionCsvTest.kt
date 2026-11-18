package com.doxaiz.netoptimizer.core.session

import com.doxaiz.netoptimizer.core.data.SessionEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class SessionCsvTest {
    private fun session() = SessionEntity(
        id = -42, name = "Сессия; \"игра\"", timestamp = 0, mode = "Игра",
        durationSec = 61, avgPingMs = 50, maxPingMs = 70, lossPercent = 10,
        avgSpeedMbps = -1.0, dropsCount = -1, avgRsrp = -90, band = "B3",
        dnsName = "Не определён", operator = "Оператор", pingHistory = "40,60", pinned = true
    )

    @Test fun writesUtf8BomHeadersQuotedFieldsUtcAndNoInventedMeasurements() {
        val writer = StringWriter()
        SessionCsv.write(writer, listOf(session()))
        val output = writer.toString()
        assertTrue(output.startsWith("\uFEFF\"id\";\"started_at_utc\""))
        assertTrue(output.contains("\"-42\";\"1970-01-01T00:00:00Z\";\"Сессия; \"\"игра\"\"\""))
        assertTrue(output.contains("\"10\";\"\";\"\";\"-90\";\"B3\""))
        assertTrue(output.endsWith("\"40,60\";\"1\"\r\n"))
    }

    @Test fun protectsDangerousTextAndQuotesCsvDelimitersAndNewlines() {
        val writer = StringWriter()
        SessionCsv.write(writer, listOf(session().copy(name = "  =1+1", mode = "+cmd", band = "@a", operator = "\nformula;\"x\"", pingHistory = "-evil")))
        val output = writer.toString()
        assertTrue(output.contains("\"'  =1+1\""))
        assertTrue(output.contains("\"'+cmd\""))
        assertTrue(output.contains("\"'@a\""))
        assertTrue(output.contains("\"'\nformula;\"\"x\"\"\""))
        assertTrue(output.contains("\"'-evil\""))
    }

    @Test fun emptyHistoryStillWritesOnlyHeaders() {
        val writer = StringWriter()
        SessionCsv.write(writer, emptyList())
        assertTrue(writer.toString().startsWith("\uFEFF\"id\""))
        assertEquals(1, writer.toString().split("\r\n").size - 1)
    }

    @Test fun cancellationCheckRunsBeforeEachRow() {
        val writer = StringWriter()
        var checked = 0
        try {
            SessionCsv.write(writer, listOf(session(), session())) {
                checked++
                if (checked == 2) throw IllegalStateException("cancel")
            }
            fail("Expected cancellation")
        } catch (_: IllegalStateException) { }
        assertEquals(2, checked)
        assertEquals(2, writer.toString().split("\r\n").size - 1)
    }
}
