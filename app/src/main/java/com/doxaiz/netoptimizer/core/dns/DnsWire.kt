package com.doxaiz.netoptimizer.core.dns

import java.security.SecureRandom

/** Small DNS A query/response verifier; never interprets malformed/truncated answers as successes. */
internal object DnsWire {
    private val random = SecureRandom()

    fun query(): ByteArray {
        val domain = "example.com"
        val labels = domain.split('.')
        val packet = ByteArray(12 + labels.sumOf { it.length + 1 } + 1 + 4)
        val id = random.nextInt(65536)
        packet[0] = (id shr 8).toByte()
        packet[1] = id.toByte()
        packet[2] = 1
        packet[5] = 1
        var pos = 12
        for (label in labels) {
            packet[pos++] = label.length.toByte()
            for (c in label) packet[pos++] = c.code.toByte()
        }
        packet[pos++] = 0
        packet[pos++] = 0; packet[pos++] = 1
        packet[pos++] = 0; packet[pos] = 1
        return packet
    }

    fun validResponse(query: ByteArray, data: ByteArray, size: Int): Boolean {
        if (size > data.size || size < query.size || query.size < 12) return false
        fun u8(i: Int) = data[i].toInt() and 0xff
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        if (data[0] != query[0] || data[1] != query[1] || (u8(2) and 0xfa) != 0x80 ||
            (u8(3) and 0x0f) != 0 || u16(4) != 1 || u16(6) == 0) return false
        // The echoed, uncompressed question must match our exact A/IN query.
        if ((12 until query.size).any { data[it] != query[it] }) return false
        var pos = query.size
        var hasAddress = false
        repeat(u16(6)) {
            pos = nameEnd(data, size, pos) ?: return false
            if (pos + 10 > size) return false
            val type = u16(pos)
            val clazz = u16(pos + 2)
            val length = u16(pos + 8)
            pos += 10
            if (length > size - pos) return false
            if (type == 1 && clazz == 1 && length == 4) hasAddress = true
            pos += length
        }
        return hasAddress
    }

    // Validate the entire name, including compressed pointers and loops; return where its encoded form ends.
    private fun nameEnd(data: ByteArray, size: Int, start: Int): Int? {
        var pos = start
        var end = -1
        val visited = HashSet<Int>()
        var nameBytes = 0
        while (pos < size && visited.add(pos)) {
            val b = data[pos].toInt() and 0xff
            when {
                b == 0 -> return if (end >= 0) end else pos + 1
                b and 0xc0 == 0xc0 -> {
                    if (pos + 1 >= size) return null
                    val target = ((b and 0x3f) shl 8) or (data[pos + 1].toInt() and 0xff)
                    if (target >= pos || target < 12) return null
                    if (end < 0) end = pos + 2
                    pos = target
                }
                b and 0xc0 != 0 -> return null
                else -> {
                    nameBytes += b + 1
                    if (nameBytes > 254 || pos + 1 + b > size) return null
                    pos += 1 + b
                }
            }
        }
        return null
    }
}
