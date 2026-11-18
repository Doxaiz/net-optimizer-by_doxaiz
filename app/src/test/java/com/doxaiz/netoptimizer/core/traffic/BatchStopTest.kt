package com.doxaiz.netoptimizer.core.traffic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BatchStopTest {
    private val target = StopTarget(10123, "org.example.app", "Пример")

    @Test fun policyRejectsChangedIdentitySharedAndProtectedPackages() {
        fun allowed(t: StopTarget = target, myUid: Int = 10001, uid: Int? = 10123,
                    packages: List<String> = listOf("org.example.app"), system: Boolean = false,
                    protected: Set<String> = emptySet()) = StopPolicy.eligible(t, myUid, uid, packages, system, protected)
        assertTrue(allowed())
        assertFalse(allowed(myUid = 10123))
        assertFalse(allowed(uid = null))
        assertFalse(allowed(uid = 10124))
        assertFalse(allowed(packages = listOf("org.example.app", "org.example.other")))
        assertFalse(allowed(packages = emptyList()))
        assertFalse(allowed(system = true))
        assertFalse(allowed(protected = setOf("org.example.app")))
        assertFalse(allowed(t = target.copy(packageName = "--user"), packages = listOf("--user")))
        assertFalse(allowed(t = target.copy(uid = -1), uid = -1))
        assertFalse(allowed(t = target.copy(uid = 1000), uid = 1000))
        assertTrue(allowed(t = target.copy(uid = 110123), uid = 110123))
    }

    @Test fun checksEveryTargetAndDoesNotSendForProtectedTargets() = runBlocking {
        val other = StopTarget(10124, "org.example.other", "Второй")
        val checked = mutableListOf<StopTarget>()
        val sent = mutableListOf<StopTarget>()
        val results = mutableListOf<StopResult>()
        BatchStop.run(listOf(target, other), { checked += it; it == target }, { sent += it; true }, { results += it })
        assertEquals(listOf(target, other), checked)
        assertEquals(listOf(target), sent)
        assertEquals(listOf(StopOutcome.SENT, StopOutcome.PROTECTED_OR_CHANGED), results.map { it.outcome })
    }

    @Test fun unsuccessfulCommandsAreNotPresentedAsSent() = runBlocking {
        val results = mutableListOf<StopResult>()
        BatchStop.run(listOf(target), { true }, { false }, { results += it })
        assertEquals(StopOutcome.FAILED, results.single().outcome)
    }

    @Test fun cancellationPreventsFollowingCommands() = runBlocking {
        val other = StopTarget(10124, "org.example.other", "Второй")
        val attempted = mutableListOf<StopTarget>()
        try {
            BatchStop.run(listOf(target, other), { true }, { attempted += it; throw CancellationException() }, {})
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(target), attempted)
    }

    @Test fun refusesDuplicateAndOversizedBatches() = runBlocking {
        suspend fun run(targets: List<StopTarget>) = BatchStop.run(targets, { true }, { true }, {})
        try { run(emptyList()); fail() } catch (_: IllegalArgumentException) { }
        try { run(listOf(target, target)); fail() } catch (_: IllegalArgumentException) { }
        val tooMany = (1..11).map { StopTarget(10000 + it, "org.example.app$it", "App $it") }
        try { run(tooMany); fail() } catch (_: IllegalArgumentException) { }
    }
}
