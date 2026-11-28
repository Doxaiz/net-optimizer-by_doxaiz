package com.doxaiz.netoptimizer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.cellular.SignalFreshness
import com.doxaiz.netoptimizer.core.cellular.SignalFreshnessStatus
import com.doxaiz.netoptimizer.core.cellular.withoutCellData
import com.doxaiz.netoptimizer.core.dns.DnsBenchmark
import com.doxaiz.netoptimizer.core.dns.DnsServer
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import android.os.SystemClock
import android.net.Network
import com.doxaiz.netoptimizer.core.net.PingResult
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.util.QualityText
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentRed
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import com.doxaiz.netoptimizer.ui.components.DoxaizCard
import com.doxaiz.netoptimizer.ui.components.HeroPanel
import com.doxaiz.netoptimizer.ui.components.MetricTile
import com.doxaiz.netoptimizer.ui.components.SectionHeader
import com.doxaiz.netoptimizer.ui.components.StatusPill
import com.doxaiz.netoptimizer.ui.components.PillTone
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class WorkMode(val label: String, val color: Color) {
    NORMAL("Обычный", AccentCyan), GAME("Игра", AccentViolet), USB("USB-модем", AccentGreen)
}

private enum class CheckPhase { IDLE, CONNECTING, TCP, DNS, DONE, ERROR, CANCELLED }

@Composable
fun HomeScreen(modifier: Modifier = Modifier, monitor: SignalMonitor, foregroundSinceMs: Long?, nowMs: Long, onOptimize: () -> Unit, expert: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val rawSignal by monitor.state.collectAsState()
    val freshness = SignalFreshness.status(rawSignal.observedAtElapsedMs, rawSignal.modemAtElapsedMs, foregroundSinceMs, maxOf(nowMs, SystemClock.elapsedRealtime()))
    val signal = if (SignalFreshness.canDisplay(freshness)) rawSignal else rawSignal.withoutCellData()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = expert || expanded
    var ping by remember { mutableStateOf<Int?>(null) }
    var jitter by remember { mutableStateOf<Int?>(null) }
    var dns by remember { mutableStateOf<DnsServer?>(null) }
    var checkPhase by remember { mutableStateOf(CheckPhase.IDLE) }
    var attemptsDone by remember { mutableIntStateOf(0) }
    var attemptsTotal by remember { mutableIntStateOf(6) }
    var checkResult by remember { mutableStateOf<PingResult?>(null) }
    var measuredNetwork by remember { mutableStateOf<Network?>(null) }
    var errorText by remember { mutableStateOf("") }
    val busy = checkPhase == CheckPhase.CONNECTING || checkPhase == CheckPhase.TCP || checkPhase == CheckPhase.DNS
    var boostJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                boostJob?.cancel()
                if (checkPhase == CheckPhase.DONE) {
                    ping = null; jitter = null; dns = null; checkResult = null; measuredNetwork = null
                    errorText = "Результат сброшен после сворачивания. Повторите проверку."
                    checkPhase = CheckPhase.ERROR
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            boostJob?.cancel()
        }
    }
    LaunchedEffect(foregroundSinceMs, measuredNetwork, checkPhase) {
        val checkedNetwork = measuredNetwork
        if (foregroundSinceMs != null && checkedNetwork != null && checkPhase == CheckPhase.DONE) {
            while (isActive) {
                if (CellularNetwork.active(context) != checkedNetwork) {
                    ping = null; jitter = null; dns = null; checkResult = null; measuredNetwork = null
                    errorText = "Сеть изменилась. Повторите проверку."
                    checkPhase = CheckPhase.ERROR
                    break
                }
                delay(2_000)
            }
        }
    }
    // Keep progress and the result visible after a user-triggered check on smaller screens.
    LaunchedEffect(checkPhase) {
        if (checkPhase == CheckPhase.CONNECTING || checkPhase == CheckPhase.DONE ||
            checkPhase == CheckPhase.ERROR || checkPhase == CheckPhase.CANCELLED) {
            listState.animateScrollToItem(2)
        }
    }
    val validSignal = signal.rsrp in -160..-30

    fun measure() {
        if (busy) return
        ping = null; jitter = null; dns = null; checkResult = null; measuredNetwork = null; errorText = ""
        attemptsDone = 0; attemptsTotal = 6
        val network = CellularNetwork.active(context)
        if (network == null) {
            errorText = "Включите мобильный интернет и повторите проверку."
            checkPhase = CheckPhase.ERROR
            return
        }
        checkPhase = CheckPhase.CONNECTING
        val includeDns = details
        boostJob = scope.launch {
            try {
                val target = PingTarget("Яндекс", "yandex.ru", 443, "")
                val result = PingTester(context).test(target) { completed, total ->
                    withContext(Dispatchers.Main) {
                        attemptsDone = completed
                        attemptsTotal = total
                        checkPhase = CheckPhase.TCP
                    }
                }
                val best = if (includeDns && result.avgMs >= 0) {
                    checkPhase = CheckPhase.DNS
                    DnsBenchmark(context).benchmarkAll().firstOrNull { it.ms >= 0 }
                } else null
                if (CellularNetwork.active(context) != network) {
                    checkResult = result.copy(status = com.doxaiz.netoptimizer.core.net.PingStatus.NETWORK_CHANGED, avgMs = -1, jitterMs = -1, lossPercent = -1, ok = false, medianMs = null, maximumMs = null)
                } else {
                    measuredNetwork = network
                    checkResult = result
                    ping = result.avgMs.takeIf { it >= 0 }
                    jitter = result.jitterMs.takeIf { ping != null && it >= 0 }
                    dns = best
                }
                checkPhase = CheckPhase.DONE
            } catch (e: CancellationException) {
                ping = null; jitter = null; dns = null; checkResult = null; measuredNetwork = null
                checkPhase = CheckPhase.CANCELLED
                throw e
            } catch (_: Exception) {
                ping = null; jitter = null; dns = null; checkResult = null; measuredNetwork = null
                errorText = "Не удалось выполнить замер. Попробуйте ещё раз."
                checkPhase = CheckPhase.ERROR
            } finally { boostJob = null }
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHeader("ГЛАВНАЯ", "Состояние мобильного подключения и быстрая проверка")
        }
        item {
            HeroPanel(
                title = "МОБИЛЬНАЯ СЕТЬ",
                value = if (validSignal) QualityText.signal(signal.rsrp) else "Сигнал недоступен",
                caption = when {
                    validSignal && freshness == SignalFreshnessStatus.UNKNOWN_MODEM_TIME -> "${signal.rsrp} дБм · возраст данных неизвестен"
                    validSignal -> "${signal.rsrp} дБм · уровень сигнала, не скорость"
                    else -> "Замеры работают и без данных о сигнале"
                },
                valueColor = if (validSignal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                accent = if (validSignal) AccentCyan else MaterialTheme.colorScheme.outline
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        if (validSignal) "Сигнал получен" else "Нет данных о соте",
                        if (validSignal) PillTone.GOOD else PillTone.NEUTRAL
                    )
                    StatusPill(signal.networkType, PillTone.NEUTRAL)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOptimize, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Оптимизировать сеть", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(onClick = { measure() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (busy) "Проверяем…" else if (details) "Проверить связь и DNS" else "Проверить связь")
                }
                if (busy) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(when (checkPhase) {
                                CheckPhase.CONNECTING -> "Подключаемся к сайту…"
                                CheckPhase.TCP -> "Проверка связи: $attemptsDone из $attemptsTotal"
                                CheckPhase.DNS -> "Сравниваем DNS…"
                                else -> "Проверяем…"
                            }, style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { boostJob?.cancel() }) { Text("Остановить проверку") }
                        }
                    }
                }
                if (checkPhase == CheckPhase.DONE) checkResult?.let { result ->
                    val summary = homeCheckSummary(result)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("РЕЗУЛЬТАТ ПРОВЕРКИ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(summary.headline, style = MaterialTheme.typography.headlineSmall,
                                color = when {
                                    !summary.hasMeasurement -> MaterialTheme.colorScheme.error
                                    result.avgMs > 120 -> AccentRed
                                    result.avgMs > 70 || result.successfulAttempts < result.attemptsMade -> AccentCyan
                                    else -> AccentGreen
                                })
                            Text(summary.explanation, style = MaterialTheme.typography.bodySmall)
                            if (details) Text(dns?.let { "DNS: ${it.name} · ${it.ms} мс, не подключён." } ?: "DNS: результата нет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (checkPhase == CheckPhase.ERROR || checkPhase == CheckPhase.CANCELLED) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Text(if (checkPhase == CheckPhase.CANCELLED) "Проверка остановлена." else errorText,
                            modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!expert) TextButton(onClick = { expanded = !expanded }, enabled = !busy) { Text(if (expanded) "Скрыть подробности" else "Показать подробности") }
            }
        }
        if (details) item { ConnectionGuide(hasDisplayableSignal = validSignal) }
        if (details) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("ПИНГ · TCP", ping?.let { "$it мс" } ?: "—", ping?.let { QualityText.ping(it) + (jitter?.let { value -> " · джиттер $value мс" } ?: " · джиттер недоступен") } ?: "Нет замера", Modifier.weight(1f), ping?.let { if (it <= 70) AccentGreen else MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                MetricCard("СИГНАЛ · RSRP", if (validSignal) "${signal.rsrp} dBm" else "—", if (validSignal) QualityText.signal(signal.rsrp) + (if (freshness == SignalFreshnessStatus.UNKNOWN_MODEM_TIME) " · возраст неизвестен" else "") else if (freshness == SignalFreshnessStatus.STALE_MODEM) "Данные модема устарели" else "Нет свежих данных о соте", Modifier.weight(1f), if (validSignal && signal.rsrp >= -100) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (details) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("DNS · КАНДИДАТ", dns?.let { "${it.ms} мс" } ?: "—", dns?.let { "${it.name} · не подключён" } ?: "Нет замера", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricCard("СЕТЬ", signal.networkType, "${signal.operator} · ${signal.band}", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, desc: String, modifier: Modifier, valueColor: Color) =
    MetricTile(label = title, value = value, detail = desc, modifier = modifier, valueColor = valueColor)
