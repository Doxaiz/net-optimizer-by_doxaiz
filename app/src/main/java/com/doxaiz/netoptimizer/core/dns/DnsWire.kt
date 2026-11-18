package com.doxaiz.netoptimizer.core.dns

import java.security.SecureRandom

/** A small DNS A verifier for a single fixed query, not a general-purpose resolver. */
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
            (u8(3) and 0x4f) != 0 || u16(4) != 1 || u16(6) == 0) return false
        // Accept only the exact uncompressed question we sent (including A/IN).
        if ((12 until query.size).any { data[it] != query[it] }) return false
        val question = readName(data, size, 12) ?: return false
        if (question.end + 4 != query.size) return false
        val answers = u16(6)
        val total = answers + u16(8) + u16(10)
        // Even a root-name RR needs 11 bytes; avoids loops over implausible counts.
        if (total > (size - query.size) / 11) return false
        val addresses = mutableSetOf<List<List<Int>>>()
        val aliases = mutableMapOf<List<List<Int>>, List<List<Int>>>()
        var pos = query.size
        repeat(total) { index ->
            val owner = readName(data, size, pos) ?: return false
            pos = owner.end
            if (pos > size - 10) return false
            val type = u16(pos)
            val clazz = u16(pos + 2)
            val length = u16(pos + 8)
            pos += 10
            if (length > size - pos) return false
            val end = pos + length
            if (clazz == 1 && type == 1 && length != 4) return false
            if (clazz == 1 && type == 5) {
                val target = readName(data, size, pos) ?: return false
                if (target.end != end) return false
                if (index < answers) {
                    val existing = aliases[owner.labels]
                    if (existing != null && existing != target.labels) return false
                    aliases[owner.labels] = target.labels
                }
            }
            if (index < answers && clazz == 1 && type == 1) addresses.add(owner.labels)
            pos = end
        }
        if (pos != size || addresses.any { it in aliases }) return false
        // A record must answer the question itself or a chain of CNAMEs in the answer section.
        var current = question.labels
        val visited = mutableSetOf<List<List<Int>>>()
        while (visited.add(current)) {
            if (current in addresses) return true
            current = aliases[current] ?: return false
        }
        return false
    }

    private data class Name(val labels: List<List<Int>>, val end: Int)

    /** Decode DNS names case-insensitively, checking labels, backward pointers and loops. */
    private fun readName(data: ByteArray, size: Int, start: Int): Name? {
        var pos = start
        var end = -1
        val visited = HashSet<Int>()
        val labels = mutableListOf<List<Int>>()
        var nameBytes = 1 // terminating root label
        while (pos < size && visited.add(pos)) {
            val b = data[pos].toInt() and 0xff
            when {
                b == 0 -> return Name(labels, if (end >= 0) end else pos + 1)
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
                    if (nameBytes > 255 || pos + 1 + b > size) return null
                    labels.add((pos + 1 until pos + 1 + b).map { i ->
                        val c = data[i].toInt() and 0xff
                        if (c in 65..90) c + 32 else c
                    })
                    pos += 1 + b
                }
            }
        }
        return null
    }
}
