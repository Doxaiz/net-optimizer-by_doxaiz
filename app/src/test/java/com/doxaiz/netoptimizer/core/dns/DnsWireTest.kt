package com.doxaiz.netoptimizer.core.dns

import org.junit.Assert.*
import org.junit.Test

class DnsWireTest {
    private val questionName = byteArrayOf(0xc0.toByte(), 0x0c)
    private val ip = byteArrayOf(1, 2, 3, 4)

    private fun name(value: String): ByteArray = value.split('.').fold(byteArrayOf()) { result, label ->
        result + byteArrayOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII)
    } + byteArrayOf(0)

    private fun record(owner: ByteArray, type: Int, rdata: ByteArray): ByteArray =
        owner + byteArrayOf(0, type.toByte(), 0, 1, 0, 0, 0, 60, (rdata.size shr 8).toByte(), rdata.size.toByte()) + rdata

    private fun response(query: ByteArray, answers: List<ByteArray>, authority: List<ByteArray> = emptyList(), extra: List<ByteArray> = emptyList()): ByteArray {
        val packet = query.copyOf().also {
            it[2] = 0x81.toByte(); it[3] = 0x80.toByte()
            it[7] = answers.size.toByte(); it[9] = authority.size.toByte(); it[11] = extra.size.toByte()
        }
        return (answers + authority + extra).fold(packet) { result, rr -> result + rr }
    }

    private fun isValid(query: ByteArray, packet: ByteArray) = DnsWire.validResponse(query, packet, packet.size)

    @Test fun acceptsACompleteMatchingARecord() {
        val query = DnsWire.query()
        val packet = response(query, listOf(record(questionName, 1, ip)))
        assertTrue(isValid(query, packet))
        assertTrue(isValid(query, response(query, listOf(record(name("EXAMPLE.COM"), 1, ip)))))
    }

    @Test fun rejectsBadTransactionsTruncationAndLyingHeaders() {
        val query = DnsWire.query()
        val ok = response(query, listOf(record(questionName, 1, ip)))
        assertFalse(DnsWire.validResponse(query, ok, query.size))
        assertFalse(DnsWire.validResponse(query, ok, ok.size - 1))
        assertFalse(DnsWire.validResponse(query, ok, -1))
        assertFalse(DnsWire.validResponse(query, ok, ok.size + 1))
        assertFalse(isValid(query, ok.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertFalse(isValid(query, ok.copyOf().also { it[2] = 0x83.toByte() })) // truncated
        assertFalse(isValid(query, ok.copyOf().also { it[3] = 0x83.toByte() })) // error
        assertFalse(isValid(query, ok.copyOf().also { it[3] = 0xc0.toByte() })) // reserved bit
        assertFalse(isValid(query, ok.copyOf().also { it[12] = 1 }))
        assertFalse(isValid(query, ok.copyOf().also { it[7] = 2 }))
        assertFalse(isValid(query, ok + byteArrayOf(0))) // unexplained trailing data
    }

    @Test fun rejectsInvalidCompressionAndNonAResponses() {
        val query = DnsWire.query()
        val ok = response(query, listOf(record(questionName, 1, ip)))
        assertFalse(isValid(query, ok.copyOf().also { it[query.size + 1] = 0 }))
        assertFalse(isValid(query, response(query, listOf(record(questionName, 28, ip)))))
        assertFalse(isValid(query, response(query, listOf(record(questionName, 1, byteArrayOf(1, 2, 3))))))
    }

    @Test fun rejectsUnrelatedOrAdditionalOnlyAddress() {
        val query = DnsWire.query()
        val wrong = record(name("unrelated.test"), 1, ip)
        assertFalse(isValid(query, response(query, listOf(wrong))))
        val alias = record(questionName, 5, name("target.test"))
        assertFalse(isValid(query, response(query, listOf(alias), extra = listOf(record(name("target.test"), 1, ip)))))
    }

    @Test fun acceptsOnlyAddressReachableThroughAnswerCnameChain() {
        val query = DnsWire.query()
        val first = record(questionName, 5, name("target.test"))
        val second = record(name("TARGET.test"), 5, name("final.test"))
        val address = record(name("FINAL.TEST"), 1, ip)
        assertTrue(isValid(query, response(query, listOf(address, second, first))))
        assertFalse(isValid(query, response(query, listOf(first, record(name("other.test"), 1, ip)))))
    }

    @Test fun rejectsMalformedCnameCyclesAndConflictingAliases() {
        val query = DnsWire.query()
        val first = record(questionName, 5, name("target.test"))
        val loop = record(name("target.test"), 5, questionName)
        assertFalse(isValid(query, response(query, listOf(first, loop))))
        assertFalse(isValid(query, response(query, listOf(first, record(questionName, 5, name("other.test")), record(name("target.test"), 1, ip)))))
        assertFalse(isValid(query, response(query, listOf(first, record(questionName, 1, ip)))))
        assertFalse(isValid(query, response(query, listOf(record(questionName, 5, name("target.test") + byteArrayOf(0)), record(name("target.test"), 1, ip)))))
    }

    @Test fun validatesTheEntirePacketIncludingOtherSections() {
        val query = DnsWire.query()
        val answer = record(questionName, 1, ip)
        val extra = record(name("other.test"), 1, ip)
        val packet = response(query, listOf(answer), extra = listOf(extra))
        assertTrue(isValid(query, packet))
        assertFalse(DnsWire.validResponse(query, packet, packet.size - 1))
        assertFalse(isValid(query, packet.copyOf().also { it[11] = 2 }))
    }
}
