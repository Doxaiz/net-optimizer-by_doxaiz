package com.doxaiz.netoptimizer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.dns.DnsBenchmark
import com.doxaiz.netoptimizer.core.dns.DnsServer
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import com.doxaiz.netoptimizer.core.net.KeepAliveEngine
import com.doxaiz.netoptimizer.core.net.KeepAliveProfile
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.util.ConnectionScore
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.core.util.QualityText
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch

enum class WorkMode(val label: String, val color: Color) {
    NORMAL("Обычный", AccentCyan), GAME("Игра", AccentViolet), USB("USB-модем", AccentGreen)
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, monitor: SignalMonitor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signal by monitor.state.collectAsState()
    var mode by remember { mutableStateOf(OnboardingPrefs.loadMode(context)?.let { runCatching { WorkMode.valueOf(it) }.getOrNull() } ?: WorkMode.NORMAL) }
    var ping by remember { mutableStateOf<Int?>(null) }
    var jitter by remember { mutableStateOf<Int?>(null) }
    var dns by remember { mutableStateOf<DnsServer?>(null) }
    var comparison by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var keepAliveOn by remember { mutableStateOf(false) }
    val keepAlive = remember { KeepAliveEngine(context) }
    DisposableEffect(keepAlive) { onDispose { keepAlive.stop() } }
    val validSignal = signal.rsrp in -160..-30
    val score = ConnectionScore.calculate(ping, signal.rsrp)

    fun boost() {
        if (busy) return
        comparison = null
        val network = CellularNetwork.active(context)
        if (network == null) {
            report = listOf("Активная мобильная сеть не найдена. Подключите мобильный интернет и повторите попытку.")
            return
        }
        busy = true
        scope.launch {
            try {
                val lines = mutableListOf<String>()
                val target = PingTarget("Яндекс", "yandex.ru", 443, "")
                val tester = PingTester(context)
                val before = tester.test(target)
                val best = DnsBenchmark(context).benchmarkAll().firstOrNull { it.ms >= 0 }
                dns = best
                lines += if (best != null) "DNS-кандидат: ${best.name} · ${best.ms} мс. Подключение DNS пока недоступно." else "DNS: не удалось получить ответ."
                if (CellularNetwork.batteryOk(context) && CellularNetwork.active(context) == network) {
                    val profile = if (mode == WorkMode.NORMAL) KeepAliveProfile.BALANCE else KeepAliveProfile.MAX
                    keepAlive.start(scope, profile)
                    keepAliveOn = true
                    lines += "UDP Keep-Alive запущен на этом экране; эффект зависит от сети."
                } else {
                    keepAlive.stop()
                    keepAliveOn = false
                    lines += "Keep-Alive не запущен: заряд ниже 20%, неизвестен или сеть изменилась."
                }
                val after = tester.test(target)
                val sameNetwork = CellularNetwork.active(context) == network
                ping = after.avgMs.takeIf { it >= 0 && sameNetwork }
                jitter = after.jitterMs.takeIf { ping != null }
                if (before.avgMs >= 0 && after.avgMs >= 0 && sameNetwork) {
                    comparison = before.avgMs to after.avgMs
                    lines += "Измерена TCP-задержка до Яндекса. Разница может быть случайной и не равна пингу игры."
                } else {
                    lines += "Сравнение недоступно: замер не удался или мобильная сеть изменилась."
                }
                if (signal.temperatureC > 42f) lines += "Температура батареи ${signal.temperatureC} °C: дайте устройству остыть."
                report = lines
            } finally { busy = false }
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("РЕЖИМ РАБОТЫ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m; OnboardingPrefs.saveMode(context, m.name) }, label = { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
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
            Button(onClick = { boost() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (busy) "ИЗМЕРЯЕМ…" else "⚡ УЛУЧШИТЬ")
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("ПИНГ · TCP", ping?.let { "$it мс" } ?: "—", ping?.let { QualityText.ping(it) + " · джиттер ${jitter ?: 0} мс" } ?: "Нет замера", Modifier.weight(1f), ping?.let { if (it <= 70) AccentGreen else MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                MetricCard("СИГНАЛ · RSRP", if (validSignal) "${signal.rsrp} dBm" else "—", if (validSignal) QualityText.signal(signal.rsrp) else "Нет данных о соте", Modifier.weight(1f), if (validSignal && signal.rsrp >= -100) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("DNS · КАНДИДАТ", dns?.let { "${it.ms} мс" } ?: "—", dns?.let { "${it.name} · не подключён" } ?: "Нет замера", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricCard("СЕТЬ", signal.networkType, "${signal.operator} · ${signal.band}", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("АВТОПИЛОТ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Фоновый автопилот пока недоступен", style = MaterialTheme.typography.titleSmall)
                    Text("Keep-Alive работает только на этом экране и приостанавливается при заряде ниже 20%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (keepAliveOn) TextButton(onClick = { keepAlive.stop(); keepAliveOn = false }) { Text("Остановить Keep-Alive") }
                }
            }
        }
        if (comparison != null || report.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("РЕЗУЛЬТАТ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    comparison?.let { (a, b) -> Text("До $a мс  →  После $b мс", style = MaterialTheme.typography.titleLarge) }
                    report.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
