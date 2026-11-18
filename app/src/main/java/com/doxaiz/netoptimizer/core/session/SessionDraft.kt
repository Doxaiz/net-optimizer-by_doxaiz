package com.doxaiz.netoptimizer.core.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** A stopped snapshot: monotonic segment start times are never persisted across process restarts. */
data class SessionDraft(val id: Long, val startedAt: Long, val mode: String, val progress: SessionProgress)

object SessionDraftCodec {
    fun encode(draft: SessionDraft): String {
        require(isValid(draft))
        val p = draft.progress
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeLong(draft.id)
            out.writeLong(draft.startedAt)
            out.writeUTF(draft.mode)
            out.writeLong(p.elapsedMs)
            out.writeInt(p.sampleCount)
            out.writeLong(p.latencySum)
            out.writeInt(p.maxLatency)
            out.writeLong(p.attempts)
            out.writeLong(p.failures)
            out.writeInt(p.history.size)
            p.history.forEach(out::writeInt)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String): SessionDraft? {
        if (encoded.length > 4096) return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != 1) return null
                val id = input.readLong()
                val startedAt = input.readLong()
                val mode = input.readUTF()
                val elapsed = input.readLong()
                val count = input.readInt()
                val sum = input.readLong()
                val max = input.readInt()
                val attempts = input.readLong()
                val failures = input.readLong()
                val historySize = input.readInt()
                if (historySize !in 0..120) return null
                val history = List(historySize) { input.readInt() }
                val draft = SessionDraft(id, startedAt, mode, SessionProgress(elapsedMs = elapsed, history = history,
                    sampleCount = count, latencySum = sum, maxLatency = max, attempts = attempts, failures = failures))
                draft.takeIf { input.available() == 0 && isValid(it) }
            }
        } catch (_: Exception) { null }
    }

    fun isValid(draft: SessionDraft): Boolean {
        val p = draft.progress
        if (draft.id >= 0 || draft.id == -1L || draft.startedAt <= 0 || draft.mode !in setOf("Обычный", "Игра", "USB-модем")) return false
        if (p.segmentStartMs != null || p.elapsedMs < 0 || p.sampleCount < 0 || p.latencySum < 0 || p.attempts < 0 || p.attempts > Long.MAX_VALUE / 100 || p.failures !in 0..p.attempts) return false
        if (p.history.size != minOf(p.sampleCount, 120) || p.history.any { it < 0 } || p.attempts - p.failures < p.sampleCount.toLong()) return false
        if (p.sampleCount == 0) return p.latencySum == 0L && p.maxLatency == -1
        if (p.maxLatency < 0 || p.history.any { it > p.maxLatency } || p.latencySum < p.maxLatency || p.latencySum < p.history.sumOf { it.toLong() } || p.latencySum > p.sampleCount.toLong() * p.maxLatency) return false
        return true
    }
}
