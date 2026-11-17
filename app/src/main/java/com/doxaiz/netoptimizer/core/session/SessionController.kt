package com.doxaiz.netoptimizer.core.session

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
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

enum class SessionPhase { IDLE, RUNNING, PAUSED, SAVING }

data class SessionUiState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val progress: SessionProgress = SessionProgress(),
    val seconds: Long = 0,
    val mode: String = "Обычный",
    val message: String = ""
)

/** Lives above tab screens. Work is suspended when its host activity stops. */
class SessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: SessionRepository,
    private val monitor: SignalMonitor
) {
    var state by mutableStateOf(SessionUiState())
        private set
    private var probeJob: Job? = null
    private var tickerJob: Job? = null

    fun startOrResume(mode: String) {
        if (state.phase != SessionPhase.IDLE && state.phase != SessionPhase.PAUSED) return
        if (CellularNetwork.active(context) == null) {
            state = state.copy(message = "Подключите мобильный интернет.")
            return
        }
        if (!CellularNetwork.batteryOk(context)) {
            state = state.copy(message = "Для замера нужен заряд не ниже 20% и доступные данные батареи.")
            return
        }
        val newProgress = (if (state.phase == SessionPhase.IDLE) SessionProgress() else state.progress)
            .resume(SystemClock.elapsedRealtime())
        state = state.copy(phase = SessionPhase.RUNNING, progress = newProgress,
            seconds = newProgress.elapsedAt(SystemClock.elapsedRealtime()) / 1000,
            mode = if (state.phase == SessionPhase.IDLE) mode else state.mode, message = "")
        val tester = PingTester(context)
        probeJob = scope.launch {
            while (true) {
                val result = tester.test(PingTarget("Яндекс", "yandex.ru", 443, ""))
                when (result.status) {
                    PingStatus.NO_CELLULAR, PingStatus.NETWORK_CHANGED -> {
                        pause("Сессия приостановлена: мобильная сеть недоступна или изменилась.")
                        return@launch
                    }
                    PingStatus.RESOLUTION_FAILED -> state = state.copy(message = "Не удалось определить адрес цели; повторяем замер.")
                    PingStatus.COMPLETED -> if (result.attemptsMade > 0) {
                        state = state.copy(progress = state.progress.addCompleted(result.avgMs, result.attemptsMade, result.successfulAttempts), message = "")
                    }
                }
                delay(5000)
            }
        }
        tickerJob = scope.launch {
            var ticks = 0
            while (true) {
                state = state.copy(seconds = state.progress.elapsedAt(SystemClock.elapsedRealtime()) / 1000)
                if (ticks++ % 5 == 0 && !CellularNetwork.batteryOk(context)) {
                    pause("Сессия приостановлена: заряд ниже 20% или данные батареи недоступны.")
                    return@launch
                }
                delay(1000)
            }
        }
    }

    fun pause(reason: String = "Сессия на паузе.") {
        if (state.phase != SessionPhase.RUNNING) return
        probeJob?.cancel(); probeJob = null
        tickerJob?.cancel(); tickerJob = null
        val progress = state.progress.pause(SystemClock.elapsedRealtime())
        state = state.copy(phase = SessionPhase.PAUSED, progress = progress, seconds = progress.elapsedMs / 1000, message = reason)
    }

    fun stopAndSave() {
        if (state.phase == SessionPhase.RUNNING) pause()
        if (state.phase != SessionPhase.PAUSED) return
        val snapshot = state
        if (snapshot.progress.sampleCount == 0) {
            state = SessionUiState(message = "Нет успешных замеров — сессия не сохранена.")
            return
        }
        state = state.copy(phase = SessionPhase.SAVING, message = "Сохраняем…")
        val signal = monitor.state.value
        scope.launch {
            try {
                repository.save(SessionEntity(
                    name = "Сессия · ${snapshot.mode}", timestamp = System.currentTimeMillis(),
                    mode = snapshot.mode, durationSec = snapshot.seconds,
                    avgPingMs = snapshot.progress.averageLatency(), maxPingMs = snapshot.progress.maxLatency,
                    lossPercent = snapshot.progress.failedPercent(), avgSpeedMbps = -1.0,
                    dropsCount = -1, avgRsrp = signal.rsrp, band = signal.band,
                    dnsName = "Не определён", operator = signal.operator,
                    pingHistory = snapshot.progress.history.joinToString(",")
                ))
                state = SessionUiState(message = "Сессия сохранена.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = snapshot.copy(phase = SessionPhase.PAUSED, message = "Не удалось сохранить сессию. Попробуйте ещё раз.")
            }
        }
    }

    fun dispose() {
        probeJob?.cancel(); tickerJob?.cancel()
    }
}
