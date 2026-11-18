package com.doxaiz.netoptimizer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.util.ConnectionScore
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.core.util.QualityText
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentRed
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

enum class WorkMode(val label: String, val color: Color) {
    NORMAL("Обычный", AccentCyan), GAME("Игра", AccentViolet), USB("USB-модем", AccentGreen)
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, monitor: SignalMonitor, foregroundSinceMs: Long?, nowMs: Long, onOptimize: () -> Unit, expert: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rawSignal by monitor.state.collectAsState()
    val freshness = SignalFreshness.status(rawSignal.observedAtElapsedMs, rawSignal.modemAtElapsedMs, foregroundSinceMs, maxOf(nowMs, SystemClock.elapsedRealtime()))
    val signal = if (SignalFreshness.canDisplay(freshness)) rawSignal else rawSignal.withoutCellData()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = expert || expanded
    var mode by remember { mutableStateOf(OnboardingPrefs.loadMode(context)?.let { runCatching { WorkMode.valueOf(it) }.getOrNull() } ?: WorkMode.NORMAL) }
    var ping by remember { mutableStateOf<Int?>(null) }
    var jitter by remember { mutableStateOf<Int?>(null) }
    var dns by remember { mutableStateOf<DnsServer?>(null) }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var boostJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                boostJob?.cancel()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            boostJob?.cancel()
        }
    }
    val validSignal = signal.rsrp in -160..-30
    val score = ConnectionScore.calculate(ping, signal.rsrp)

    fun measure() {
        if (busy) return
        report = emptyList()
        ping = null; jitter = null; dns = null
        val network = CellularNetwork.active(context)
        if (network == null) {
            report = listOf("Активная мобильная сеть не найдена. Подключите мобильный интернет и повторите попытку.")
            return
        }
        busy = true
        boostJob = scope.launch {
            try {
                val target = PingTarget("Яндекс", "yandex.ru", 443, "")
                val result = PingTester(context).test(target)
                val best = if (details) DnsBenchmark(context).benchmarkAll().firstOrNull { it.ms >= 0 } else null
                val sameNetwork = CellularNetwork.active(context) == network
                ping = result.avgMs.takeIf { it >= 0 && sameNetwork }
                jitter = result.jitterMs.takeIf { ping != null && it >= 0 }
                dns = best.takeIf { sameNetwork }
                report = buildList {
                    add(if (ping != null) "Задержка до сайта Яндекса: $ping мс. В играх результат может отличаться." else "Проверка не удалась. Убедитесь, что мобильный интернет включён и заряд не ниже 20%.")
                    if (details) add(if (dns != null) "DNS-кандидат по UDP: ${dns!!.name} · ${dns!!.ms} мс. DoT не проверялся; подключение вручную в Инструменты → DNS." else "DNS: результат недоступен или сеть изменилась.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ping = null; jitter = null; dns = null
                report = listOf("Не удалось выполнить замер. Попробуйте ещё раз.")
            } finally { busy = false }
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!expert) item {
            Text("ГЛАВНАЯ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { expanded = !expanded }, enabled = !busy) { Text(if (expanded) "Скрыть подробности" else "Показать подробности") }
        }
        if (!details) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Мобильная сеть", style = MaterialTheme.typography.titleMedium)
                    Text(if (validSignal) "${QualityText.signal(signal.rsrp)} · ${signal.rsrp} дБм" else "Нет свежих данных о сигнале", style = MaterialTheme.typography.bodyLarge, color = when {
                        !validSignal -> MaterialTheme.colorScheme.onSurfaceVariant
                        signal.rsrp >= -90 -> AccentGreen
                        signal.rsrp >= -100 -> AccentCyan
                        else -> AccentRed
                    })
                    Text(if (validSignal && freshness == SignalFreshnessStatus.UNKNOWN_MODEM_TIME) "Возраст показаний неизвестен. Это уровень сигнала, не скорость интернета." else "Уровень сигнала не гарантирует скорость. Для сведений о соте нужен доступ к точному местоположению.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ping != null) Text("Задержка до сайта Яндекса: $ping мс; в играх может отличаться.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (details) item {
            Text("РЕЖИМ РАБОТЫ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m; OnboardingPrefs.saveMode(context, m.name) }, label = { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (details) item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("КАЧЕСТВО СОЕДИНЕНИЯ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ScoreRing(score)
                Spacer(Modifier.height(8.dp))
                Text(score?.let { QualityText.scoreVerdict(it) } ?: "Ожидание данных", style = MaterialTheme.typography.titleMedium)
                Text("Оценка по TCP-задержке и сигналу, не гарантия скорости", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Button(onClick = onOptimize, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("⚡ Снизить нагрузку на сеть")
            }
            Text("Откроется снижение нагрузки на сеть: вы сами выбираете действие, затем можно сравнить задержку до и после.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { measure() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "ИЗМЕРЯЕМ…" else if (details) "Измерить связь и DNS" else "Проверить связь")
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (report.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("РЕЗУЛЬТАТ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    report.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
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
        if (details) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("АВТОПИЛОТ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Фоновый автопилот пока недоступен", style = MaterialTheme.typography.titleSmall)
                    Text("Экспериментальный Keep-Alive включается только вручную в Инструменты → Сигнал. Он расходует батарею и трафик и не гарантирует снижения задержки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int?) {
    val brush = Brush.linearGradient(listOf(AccentCyan, AccentViolet, AccentGreen))
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Color.Gray.copy(alpha = .2f), 0f, 360f, false, style = Stroke(14f))
            if (score != null) drawArc(brush, -90f, 360f * score / 100f, false, style = Stroke(14f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "—", style = MaterialTheme.typography.displayMedium)
            Text("из 100", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, desc: String, modifier: Modifier, valueColor: Color) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 124.dp).padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
