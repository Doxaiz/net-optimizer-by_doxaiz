package com.doxaiz.netoptimizer.core.traffic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import com.doxaiz.netoptimizer.core.net.PingStatus
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.session.SessionNetworkGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LoadPhase { IDLE, BASELINE, READY, CHECKING, RESULT }
internal data class LoadState(
    val phase: LoadPhase = LoadPhase.IDLE,
    val before: LoadSample? = null,
    val after: LoadSample? = null,
    val message: String = ""
)

/** User-driven foreground tests; between tests only a temporary network-change callback remains. */
internal class LoadOptimizationController(private val context: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf(LoadState())
        private set
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var guard: SessionNetworkGuard<Network>? = null
    private var network: Network? = null
    private var target: PingTarget? = null
    private var measurement: Job? = null
    private var expiry: Job? = null
    private var startedAtMs: Long? = null

    fun start() {
        clear()
        val initial = CellularNetwork.active(context)
        if (initial == null || manager == null) {
            state = LoadState(message = "Подключите мобильный интернет; без него замер недоступен.")
            return
        }
        if (!CellularNetwork.batteryOk(context)) {
            state = LoadState(message = "Для замера нужен заряд не ниже 20% и доступные данные батареи.")
            return
        }
        val currentGuard = SessionNetworkGuard(initial)
        fun invalidateIfChanged() {
            scope.launch {
                if (guard === currentGuard && !currentGuard.valid(CellularNetwork.active(context))) {
                    clear("Мобильная сеть изменилась: начните сравнение заново.")
                }
            }
        }
        val watcher = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) { currentGuard.available(available); invalidateIfChanged() }
            override fun onLost(lost: Network) { currentGuard.lost(lost); invalidateIfChanged() }
            override fun onCapabilitiesChanged(current: Network, caps: NetworkCapabilities) {
                currentGuard.capabilities(current, caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                invalidateIfChanged()
            }
        }
        try {
            manager.registerDefaultNetworkCallback(watcher)
        } catch (_: Exception) {
            state = LoadState(message = "Не удалось отследить сеть. Попробуйте ещё раз.")
            return
        }
        network = initial
        guard = currentGuard
        callback = watcher
        if (!currentGuard.valid(CellularNetwork.active(context))) {
            clear("Мобильная сеть изменилась: начните сравнение заново.")
            return
        }
        startedAtMs = SystemClock.elapsedRealtime()
        state = LoadState(phase = LoadPhase.BASELINE, message = "Определяем адрес и измеряем исходную TCP-задержку…")
        expiry = scope.launch {
            delay(10 * 60_000L)
            clear("Время сравнения истекло. Начните заново.")
        }
        measurement = scope.launch {
            try {
                val address = withContext(Dispatchers.IO) {
                    try { initial.getAllByName("yandex.ru").firstOrNull()?.hostAddress } catch (_: Exception) { null }
                }
                if (!valid(currentGuard, initial)) return@launch
                if (address == null) {
                    clear("Не удалось определить адрес сайта. Повторите позже.")
                    return@launch
                }
                val pinnedTarget = PingTarget("Яндекс", address, 443, "")
                val result = PingTester(context).test(pinnedTarget, LoadComparison.ATTEMPTS)
                if (!valid(currentGuard, initial)) return@launch
                val sample = result.asLoadSample()
                if (result.status != PingStatus.COMPLETED || !LoadComparison.usable(sample)) {
                    clear("Исходный замер не удался: нужно не менее 4 успешных TCP-подключений из 6, заряд не ниже 20% и стабильная мобильная сеть.")
                    return@launch
                }
                target = pinnedTarget
                state = LoadState(phase = LoadPhase.READY, before = sample, message = "Теперь вручную приостановите ненужные загрузки или выберите приложение ниже, затем повторите замер.")
                measurement = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (guard === currentGuard) clear("Не удалось выполнить исходный замер. Попробуйте ещё раз.")
            }
        }
    }

    fun measureAfter() {
        val initial = network ?: return
        val currentGuard = guard ?: return
        val pinned = target ?: return
        val before = state.before ?: return
        if (state.phase != LoadPhase.READY && state.phase != LoadPhase.RESULT) return
        if (!valid(currentGuard, initial)) return
        if (!CellularNetwork.batteryOk(context)) {
            state = state.copy(message = "Повторный замер недоступен: заряд ниже 20% или данные батареи неизвестны.")
            return
        }
        state = state.copy(phase = LoadPhase.CHECKING, after = null, message = "Повторяем TCP-замер до того же адреса…")
        measurement = scope.launch {
            try {
                val result = PingTester(context).test(pinned, LoadComparison.ATTEMPTS)
                if (!valid(currentGuard, initial)) return@launch
                val sample = result.asLoadSample()
                state = if (result.status == PingStatus.COMPLETED && LoadComparison.usable(sample)) {
                    LoadState(phase = LoadPhase.RESULT, before = before, after = sample)
                } else {
                    LoadState(phase = LoadPhase.READY, before = before, message = "Повторный замер не удался: нужно не менее 4 успешных подключений из 6. Проверьте заряд и попробуйте ещё раз.")
                }
                measurement = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (guard === currentGuard) {
                    state = LoadState(phase = LoadPhase.READY, before = before, message = "Не удалось выполнить повторный замер. Попробуйте ещё раз.")
                    measurement = null
                }
            }
        }
    }

    fun onStart() {
        val currentGuard = guard ?: return
        val initial = network ?: return
        valid(currentGuard, initial)
    }

    /** Android settings can cover the app: cancel probes but retain the baseline and watcher. */
    fun onStop() {
        when (state.phase) {
            LoadPhase.BASELINE -> clear("Исходный замер отменён при сворачивании.")
            LoadPhase.CHECKING -> {
                measurement?.cancel(); measurement = null
                state = state.copy(phase = LoadPhase.READY, after = null, message = "Повторный замер отменён при сворачивании. Повторите его на экране приложения.")
            }
            else -> Unit
        }
    }

    fun clear(message: String = "") {
        measurement?.cancel(); measurement = null
        expiry?.cancel(); expiry = null
        guard = null
        callback?.let { try { manager?.unregisterNetworkCallback(it) } catch (_: Exception) { } }
        callback = null
        network = null
        target = null
        startedAtMs = null
        state = LoadState(message = message)
    }

    private fun valid(expected: SessionNetworkGuard<Network>, initial: Network): Boolean {
        if (guard !== expected) return false
        val started = startedAtMs
        val now = SystemClock.elapsedRealtime()
        if (started != null && (now < started || now - started >= 10 * 60_000L)) {
            clear("Время сравнения истекло. Начните заново.")
            return false
        }
        if (expected.valid(CellularNetwork.active(context)) && network == initial) return true
        clear("Мобильная сеть изменилась: начните сравнение заново.")
        return false
    }
}

private fun com.doxaiz.netoptimizer.core.net.PingResult.asLoadSample() =
    LoadSample(attemptsMade, successfulAttempts, avgMs, jitterMs, lossPercent)
