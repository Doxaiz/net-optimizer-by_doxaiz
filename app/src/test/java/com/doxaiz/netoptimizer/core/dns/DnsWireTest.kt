package com.doxaiz.netoptimizer.core.dns

import org.junit.Assert.*
import org.junit.Test

class DnsWireTest {
    private fun answer(query: ByteArray): ByteArray {
        val headerQuestion = query.copyOf().also {
            it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = 1
        }
        // compressed example.com, A/IN, TTL 60 seconds, four-byte address
        return headerQuestion + byteArrayOf(0xc0.toByte(), 0x0c, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 1, 2, 3, 4)
    }

    @Test fun acceptsACompleteMatchingARecord() {
        val query = DnsWire.query()
        val packet = answer(query)
        assertTrue(DnsWire.validResponse(query, packet, packet.size))
    }

    @Test fun rejectsBadTransactionsTruncationAndLyingHeaders() {
        val query = DnsWire.query()
        val ok = answer(query)
        assertFalse(DnsWire.validResponse(query, ok, query.size))
        assertFalse(DnsWire.validResponse(query, ok, ok.size - 1))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[2] = 0x83.toByte() }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[3] = 0x83.toByte() }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[12] = 1 }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[7] = 2 }, ok.size))
    }

    @Test fun rejectsInvalidCompressionAndNonAResponses() {
        val query = DnsWire.query()
        val ok = answer(query)
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[query.size + 1] = 0 }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[query.size + 3] = 28 }, ok.size))
        assertFalse(DnsWire.validResponse(query, ok.copyOf().also { it[query.size + 11] = 3 }, ok.size))
    }
}
