package com.doxaiz.netoptimizer.core.session

import org.junit.Assert.*
import org.junit.Test

class SessionDraftCodecTest {
    private fun draft() = SessionDraft(-123, 12345, "Игра", SessionProgress()
        .resume(1_000).addCompleted(45, 6, 5).addCompleted(-1, 6, 0).pause(5_500))

    @Test fun roundTripRestoresOnlyPausedStateAndAllStatistics() {
        val original = draft()
        val restored = SessionDraftCodec.decode(SessionDraftCodec.encode(original))!!
        assertEquals(original, restored)
        assertNull(restored.progress.segmentStartMs)
        assertEquals(4500L, restored.progress.elapsedAt(999_999))
        assertEquals(58, restored.progress.failedPercent())
    }

    @Test fun emptyAndFailedOnlyDraftCanBeRestored() {
        val empty = SessionDraft(-2, 1, "Обычный", SessionProgress())
        assertEquals(empty, SessionDraftCodec.decode(SessionDraftCodec.encode(empty)))
        val failures = empty.copy(progress = empty.progress.addCompleted(-1, 6, 0))
        assertEquals(failures, SessionDraftCodec.decode(SessionDraftCodec.encode(failures)))
    }

    @Test fun corruptOrOversizedDataIsRejected() {
        assertNull(SessionDraftCodec.decode("not base64!"))
        assertNull(SessionDraftCodec.decode("A".repeat(4097)))
        val encoded = SessionDraftCodec.encode(draft())
        assertNull(SessionDraftCodec.decode(encoded.dropLast(8)))
        assertNull(SessionDraftCodec.decode("AAAAAg=="))
    }

    @Test fun invalidDraftsAreRejected() {
        val value = draft()
        assertFalse(SessionDraftCodec.isValid(value.copy(id = 0)))
        assertFalse(SessionDraftCodec.isValid(value.copy(id = -1)))
        assertFalse(SessionDraftCodec.isValid(value.copy(mode = "неизвестен")))
        assertFalse(SessionDraftCodec.isValid(value.copy(progress = value.progress.resume(9_000))))
        assertFalse(SessionDraftCodec.isValid(value.copy(progress = value.progress.copy(failures = 100))))
        assertFalse(SessionDraftCodec.isValid(value.copy(progress = value.progress.copy(attempts = Long.MAX_VALUE))))
        assertFalse(SessionDraftCodec.isValid(value.copy(progress = value.progress.copy(latencySum = Long.MAX_VALUE))))
        assertFalse(SessionDraftCodec.isValid(value.copy(progress = value.progress.copy(history = listOf(-4)))))
    }
}
