package com.doxaiz.netoptimizer.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionRepositoryTest {
    private class FakeDao : SessionDao {
        val sessions = mutableMapOf<Long, SessionEntity>()
        override fun observeAll(): Flow<List<SessionEntity>> = flowOf(sessions.values.toList())
        override suspend fun insert(session: SessionEntity): Long {
            if (sessions.containsKey(session.id)) return -1
            sessions[session.id] = session
            return session.id
        }
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun delete(id: Long) { sessions.remove(id) }
        override suspend fun get(id: Long): SessionEntity? = sessions[id]
    }

    private fun session(id: Long = -123, timestamp: Long = 1000) = SessionEntity(
        id = id, name = "Сессия · Игра", timestamp = timestamp, mode = "Игра", durationSec = 20,
        avgPingMs = 50, maxPingMs = 60, lossPercent = 0, avgSpeedMbps = -1.0, dropsCount = -1,
        avgRsrp = -90, band = "B3", dnsName = "Не определён", operator = "—", pingHistory = "50,60"
    )

    @Test fun retryAfterSuccessfulInsertDoesNotCreateAnotherSession() = runBlocking {
        val dao = FakeDao()
        val repo = SessionRepository(dao)
        assertEquals(-123L, repo.save(session()))
        assertEquals(-123L, repo.save(session()))
        assertEquals(1, dao.sessions.size)
        assertEquals(session(), repo.find(-123))
    }

    @Test fun unrelatedIdConflictDoesNotOverwriteSavedHistory() = runBlocking {
        val dao = FakeDao()
        val repo = SessionRepository(dao)
        repo.save(session())
        try {
            repo.save(session(timestamp = 9999))
            fail("Expected collision to be rejected")
        } catch (_: IllegalStateException) { }
        assertEquals(1000L, repo.find(-123)!!.timestamp)
    }
}
