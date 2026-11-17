package com.doxaiz.netoptimizer.ui.sessions

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.DoxaizApp
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.data.SessionEntity
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.ui.home.WorkMode
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(modifier: Modifier = Modifier, monitor: SignalMonitor) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as DoxaizApp).sessionRepository }
    val sessions by repo.observeAll().collectAsState(initial = emptyList())
    val signal by monitor.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var active by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableLongStateOf(0L) }
    var history by remember { mutableStateOf<List<Int>>(emptyList()) }
    var losses by remember { mutableStateOf<List<Int>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    val detail = sessions.firstOrNull { it.id == detailId }

    LaunchedEffect(active) {
        if (active) {
            val tester = PingTester(context)
            while (true) {
                seconds = (SystemClock.elapsedRealtime() - start) / 1000
                val result = tester.test(PingTarget("Яндекс", "yandex.ru", 443, ""))
                if (result.avgMs >= 0) history = (history + result.avgMs).takeLast(120)
                if (result.lossPercent >= 0) losses = (losses + result.lossPercent).takeLast(120)
                seconds = (SystemClock.elapsedRealtime() - start) / 1000
                delay(5000)
            }
        }
    }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("СЕССИИ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("История соединения", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (active) "ИДЁТ ЗАМЕР" else "РУЧНАЯ СЕССИЯ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
                    Text("TCP-задержка до Яндекса · замеров: ${history.size}. Сессия работает только на этом экране и прекращается при выходе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (!active) {
                        Button(onClick = {
                            if (CellularNetwork.active(context) == null) message = "Подключите мобильный интернет."
                            else { history = emptyList(); losses = emptyList(); seconds = 0; start = SystemClock.elapsedRealtime(); message = ""; active = true }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Начать сессию") }
                    } else {
                        Button(onClick = {
                            active = false
                            seconds = (SystemClock.elapsedRealtime() - start) / 1000
                            if (history.isEmpty()) message = "Нет успешных замеров — сессия не сохранена."
                            else {
                                val values = history
                                val mode = OnboardingPrefs.loadMode(context)?.let { runCatching { WorkMode.valueOf(it).label }.getOrNull() } ?: "Обычный"
                                val snapshot = signal
                                scope.launch {
                                    repo.save(SessionEntity(name = "Сессия · $mode", timestamp = System.currentTimeMillis(), mode = mode, durationSec = seconds,
                                        avgPingMs = values.average().toInt(), maxPingMs = values.maxOrNull() ?: -1,
                                        lossPercent = if (losses.isEmpty()) -1 else losses.average().toInt(), avgSpeedMbps = -1.0,
                                        dropsCount = -1, avgRsrp = snapshot.rsrp, band = snapshot.band, dnsName = "Не определён",
                                        operator = snapshot.operator, pingHistory = values.joinToString(",")))
                                    message = "Сессия сохранена."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Остановить и сохранить") }
                    }
                }
            }
        }
        if (selected.size == 2) item {
            val a = sessions.firstOrNull { it.id == selected[0] }
            val b = sessions.firstOrNull { it.id == selected[1] }
            if (a != null && b != null) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("СРАВНЕНИЕ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("${a.avgPingMs} мс  ↔  ${b.avgPingMs} мс", style = MaterialTheme.typography.headlineSmall)
                    Text("Разница: ${kotlin.math.abs(a.avgPingMs - b.avgPingMs)} мс. Условия сети могут отличаться.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (detail != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ГРАФИК TCP-ЗАДЕРЖКИ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    PingGraph(detail.pingHistory.split(",").mapNotNull { it.toIntOrNull() })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { repo.setPinned(detail.id, !detail.pinned) } }, modifier = Modifier.weight(1f)) { Text(if (detail.pinned) "Открепить" else "Закрепить") }
                        OutlinedButton(onClick = { scope.launch { repo.delete(detail.id); selected = selected - detail.id; detailId = null } }, modifier = Modifier.weight(1f)) { Text("Удалить") }
                    }
                }
            }
        }
        item { Text("ИСТОРИЯ · ВЫБЕРИТЕ ДВЕ ДЛЯ СРАВНЕНИЯ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (sessions.isEmpty()) item { Text("Сохранённых сессий пока нет.", style = MaterialTheme.typography.bodyMedium) }
        items(sessions, key = { it.id }) { s ->
            val isSelected = s.id in selected
            Card(Modifier.fillMaxWidth().clickable {
                detailId = s.id
                selected = if (isSelected) selected - s.id else (selected + s.id).takeLast(2)
                scope.launch { listState.animateScrollToItem(2) }
            }, colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.name + if (s.pinned) " · Закреплено" else "", style = MaterialTheme.typography.titleMedium)
                    Text("${SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(s.timestamp))} · ${s.durationSec / 60} мин · ${s.operator}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TCP-задержка: ${s.avgPingMs} мс", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PingGraph(history: List<Int>) {
    if (history.size < 2) { Text("Для графика нужно минимум два замера."); return }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val min = (history.minOrNull() ?: 0) - 5
        val max = (history.maxOrNull() ?: 0) + 5
        val path = Path()
        history.forEachIndexed { i, value ->
            val x = size.width * i / (history.size - 1)
            val y = size.height - ((value - min).toFloat() / (max - min)) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AccentViolet, style = Stroke(3f))
    }
}
