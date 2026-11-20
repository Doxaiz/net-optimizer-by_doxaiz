package com.doxaiz.netoptimizer.core.session

import android.content.Context
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.cellular.SignalFreshness
import com.doxaiz.netoptimizer.core.cellular.SignalFreshnessStatus
import com.doxaiz.netoptimizer.core.data.SessionEntity
import com.doxaiz.netoptimizer.core.data.SessionRepository
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import com.doxaiz.netoptimizer.core.net.PingStatus
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class SessionPhase { RESTORING, RECOVERY_ERROR, IDLE, RUNNING, PAUSED, SAVING }

data class SessionUiState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val progress: SessionProgress = SessionProgress(),
    val seconds: Long = 0,
    val mode: String = "Обычный",
    val sessionId: Long = 0,
    val startedAt: Long = 0,
    val message: String = ""
)

/** Lives above tab screens. Work is suspended when its host activity stops. */
class SessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: SessionRepository,
    private val monitor: SignalMonitor
) {
    private val drafts = SessionDraftStore(context)
    var state by mutableStateOf(SessionUiState(phase = SessionPhase.RESTORING))
        private set
    private var probeJob: Job? = null
    private var tickerJob: Job? = null
    private var recoveryJob: Job? = null
    private var networkGuard: SessionNetworkGuard<Network>? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init { restoreDraft() }

    fun restoreDraft() {
        if (state.phase != SessionPhase.RESTORING && state.phase != SessionPhase.RECOVERY_ERROR) return
        recoveryJob?.cancel()
        state = state.copy(phase = SessionPhase.RESTORING, message = "Проверяем черновик…")
        val loaded = drafts.load()
        if (loaded.isFailure) {
            state = state.copy(phase = SessionPhase.RECOVERY_ERROR, message = "Не удалось прочитать черновик. Попробуйте ещё раз или удалите его.")
            return
        }
        val draft = loaded.getOrNull()
        if (draft == null) {
            state = SessionUiState()
            return
        }
        recoveryJob = scope.launch {
            try {
                val saved = repository.find(draft.id)
                if (saved == null) {
                    state = SessionUiState(phase = SessionPhase.PAUSED, progress = draft.progress,
                        seconds = draft.progress.elapsedMs / 1000, mode = draft.mode,
                        sessionId = draft.id, startedAt = draft.startedAt,
                        message = "Черновик восстановлен. Продолжите замер или сохраните сессию.")
                } else if (saved.timestamp == draft.startedAt && saved.mode == draft.mode) {
                    drafts.clear()
                    state = SessionUiState(message = "Предыдущая сессия уже сохранена в истории.")
                } else {
                    state = state.copy(phase = SessionPhase.RECOVERY_ERROR, message = "Черновик конфликтует с записью в истории. Он не удалён.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = state.copy(phase = SessionPhase.RECOVERY_ERROR, message = "Не удалось проверить черновик. Попробуйте ещё раз.")
            }
        }
    }

    fun startOrResume(mode: String) {
        if (state.phase != SessionPhase.IDLE && state.phase != SessionPhase.PAUSED) return
        val network = CellularNetwork.active(context)
        if (network == null) {
            state = state.copy(message = "Подключите мобильный интернет.")
            return
        }
        if (!CellularNetwork.batteryOk(context)) {
            state = state.copy(message = "Для замера нужен заряд не ниже 20% и доступные данные батареи.")
            return
        }
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            state = state.copy(message = "Не удалось отслеживать мобильную сеть.")
            return
        }
        val guard = SessionNetworkGuard(network)
        fun changed() {
            scope.launch {
                if (networkGuard === guard && state.phase == SessionPhase.RUNNING && !guard.valid(CellularNetwork.active(context))) {
                    pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                }
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) { guard.available(available); changed() }
            override fun onLost(lost: Network) { guard.lost(lost); changed() }
            override fun onCapabilitiesChanged(current: Network, caps: NetworkCapabilities) {
                guard.capabilities(current, caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                changed()
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            state = state.copy(message = "Не удалось отслеживать мобильную сеть. Попробуйте ещё раз.")
            return
        }
        if (!guard.valid(CellularNetwork.active(context))) {
            try { manager.unregisterNetworkCallback(callback) } catch (_: Exception) { }
            state = state.copy(message = "Мобильная сеть изменилась. Попробуйте ещё раз.")
            return
        }
        networkGuard = guard
        networkCallback = callback
        val isNew = state.phase == SessionPhase.IDLE
        val newProgress = (if (isNew) SessionProgress() else state.progress).resume(SystemClock.elapsedRealtime())
        val randomId = UUID.randomUUID().mostSignificantBits or Long.MIN_VALUE
        state = state.copy(phase = SessionPhase.RUNNING, progress = newProgress,
            seconds = newProgress.elapsedAt(SystemClock.elapsedRealtime()) / 1000,
            mode = if (isNew) mode else state.mode,
            sessionId = if (isNew) (if (randomId == -1L) -2L else randomId) else state.sessionId,
            startedAt = if (isNew) System.currentTimeMillis() else state.startedAt, message = "")
        checkpoint()
        val tester = PingTester(context)
        probeJob = scope.launch {
            while (true) {
                if (!guard.valid(CellularNetwork.active(context))) {
                    pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                    return@launch
                }
                val result = tester.test(PingTarget("Яндекс", "yandex.ru", 443, ""))
                if (!guard.valid(CellularNetwork.active(context))) {
                    pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                    return@launch
                }
                when (result.status) {
                    PingStatus.LOW_BATTERY -> {
                        pause("Сессия приостановлена: заряд ниже 20% или данные батареи недоступны.")
                        return@launch
                    }
                    PingStatus.NO_CELLULAR, PingStatus.NETWORK_CHANGED -> {
                        pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                        return@launch
                    }
                    PingStatus.NETWORK_CHECK_UNAVAILABLE -> {
                        pause("Сессия приостановлена: не удалось отследить мобильную сеть.")
                        return@launch
                    }
                    PingStatus.RESOLUTION_FAILED -> state = state.copy(message = "Не удалось определить адрес цели; повторяем замер.")
                    PingStatus.COMPLETED -> if (result.attemptsMade > 0) {
                        state = state.copy(progress = state.progress.addCompleted(result.avgMs, result.attemptsMade, result.successfulAttempts), message = "")
                        checkpoint()
                    }
                }
                delay(5000)
            }
        }
        tickerJob = scope.launch {
            var ticks = 0
            while (true) {
                if (!guard.valid(CellularNetwork.active(context))) {
                    pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                    return@launch
                }
                state = state.copy(seconds = state.progress.elapsedAt(SystemClock.elapsedRealtime()) / 1000)
                if (ticks++ % 5 == 0) {
                    if (!CellularNetwork.batteryOk(context)) {
                        pause("Сессия приостановлена: заряд ниже 20% или данные батареи недоступны.")
                        return@launch
                    }
                    checkpoint()
                }
                delay(1000)
            }
        }
    }

    private fun checkpoint() {
        if (state.phase != SessionPhase.RUNNING && state.phase != SessionPhase.PAUSED) return
        try {
            val progress = state.progress.pause(SystemClock.elapsedRealtime())
            drafts.save(SessionDraft(state.sessionId, state.startedAt, state.mode, progress))
        } catch (_: Exception) {
            state = state.copy(message = "Не удалось записать черновик. Сохранение после выгрузки не гарантируется.")
        }
    }

    fun pause(reason: String = "Сессия на паузе.") {
        if (state.phase != SessionPhase.RUNNING) return
        probeJob?.cancel(); probeJob = null
        tickerJob?.cancel(); tickerJob = null
        networkGuard = null
        networkCallback?.let { callback ->
            try { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) } catch (_: Exception) { }
        }
        networkCallback = null
        val progress = state.progress.pause(SystemClock.elapsedRealtime())
        state = state.copy(phase = SessionPhase.PAUSED, progress = progress, seconds = progress.elapsedMs / 1000, message = reason)
        checkpoint()
    }

    fun discardDraft() {
        if (state.phase != SessionPhase.PAUSED && state.phase != SessionPhase.RECOVERY_ERROR) return
        try {
            drafts.clear()
            state = SessionUiState(message = "Черновик удалён.")
        } catch (_: Exception) {
            state = state.copy(message = "Не удалось удалить черновик.")
        }
    }

    fun stopAndSave(foregroundSinceMs: Long?) {
        if (state.phase == SessionPhase.RUNNING) pause()
        if (state.phase != SessionPhase.PAUSED) return
        val snapshot = state
        if (snapshot.progress.sampleCount == 0) {
            state = snapshot.copy(message = "Нет успешных замеров. Продолжите сессию или удалите черновик.")
            return
        }
        state = state.copy(phase = SessionPhase.SAVING, message = "Сохраняем…")
        val signal = monitor.state.value
        // Stored rows have no timestamp/quality flag: do not turn uncertain cell data into facts.
        val cellFresh = SignalFreshness.status(signal.observedAtElapsedMs, signal.modemAtElapsedMs, foregroundSinceMs, SystemClock.elapsedRealtime()) == SignalFreshnessStatus.FRESH
        scope.launch {
            var saved = false
            try {
                repository.save(SessionEntity(
                    id = snapshot.sessionId,
                    name = "Сессия · ${snapshot.mode}", timestamp = snapshot.startedAt,
                    mode = snapshot.mode, durationSec = snapshot.seconds,
                    avgPingMs = snapshot.progress.averageLatency(), maxPingMs = snapshot.progress.maxLatency,
                    lossPercent = snapshot.progress.failedPercent(), avgSpeedMbps = -1.0,
                    dropsCount = -1, avgRsrp = if (cellFresh) signal.rsrp else 0, band = if (cellFresh) signal.band else "—",
                    dnsName = "Не определён", operator = signal.operator,
                    pingHistory = snapshot.progress.history.joinToString(",")
                ))
                saved = true
                drafts.clear()
                state = SessionUiState(message = "Сессия сохранена.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = if (saved) snapshot.copy(phase = SessionPhase.RECOVERY_ERROR, message = "Сессия сохранена, но черновик не удалён. Повторите восстановление.")
                    else snapshot.copy(phase = SessionPhase.PAUSED, message = "Не удалось завершить сохранение. Попробуйте ещё раз.")
            }
        }
    }

    fun dispose() {
        pause("Сессия на паузе: экран закрыт.")
        probeJob?.cancel(); tickerJob?.cancel(); recoveryJob?.cancel()
    }
}
