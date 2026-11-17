package com.doxaiz.netoptimizer.ui.sessions

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
import com.doxaiz.netoptimizer.core.session.SessionController
import com.doxaiz.netoptimizer.core.session.SessionPhase
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.ui.home.WorkMode
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(modifier: Modifier = Modifier, controller: SessionController) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as DoxaizApp).sessionRepository }
    val sessions by repo.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val state = controller.state
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val detail = sessions.firstOrNull { it.id == detailId }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("СЕССИИ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("История соединения", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (state.phase) {
                        SessionPhase.RESTORING -> "ВОССТАНОВЛЕНИЕ"
                        SessionPhase.RECOVERY_ERROR -> "ЧЕРНОВИК НЕДОСТУПЕН"
                        SessionPhase.IDLE -> "РУЧНАЯ СЕССИЯ"
                        SessionPhase.RUNNING -> "ИДЁТ ЗАМЕР"
                        SessionPhase.PAUSED -> "НА ПАУЗЕ"
                        SessionPhase.SAVING -> "СОХРАНЕНИЕ"
                    }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("%02d:%02d".format(state.seconds / 60, state.seconds % 60), style = MaterialTheme.typography.headlineMedium)
                    Text("TCP-задержка до Яндекса · замеров: ${state.progress.sampleCount}. Можно переключать вкладки; при сворачивании или заряде ниже 20% сессия ставится на паузу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Черновик записывается на телефон примерно раз в 5 секунд и при паузе. При внезапном сбое последние данные могут не сохраниться; после восстановления замер остаётся на паузе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (state.phase) {
                        SessionPhase.IDLE, SessionPhase.PAUSED -> {
                            Button(onClick = {
                                val mode = OnboardingPrefs.loadMode(context)?.let { runCatching { WorkMode.valueOf(it).label }.getOrNull() } ?: "Обычный"
                                controller.startOrResume(mode)
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (state.phase == SessionPhase.IDLE) "Начать сессию" else "Продолжить") }
                            if (state.phase == SessionPhase.PAUSED) {
                                OutlinedButton(onClick = controller::stopAndSave, modifier = Modifier.fillMaxWidth()) { Text("Остановить и сохранить") }
                                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) { Text("Удалить черновик") }
                            }
                        }
                        SessionPhase.RUNNING -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { controller.pause() }, modifier = Modifier.weight(1f)) { Text("Пауза") }
                                Button(onClick = controller::stopAndSave, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                            }
                        }
                        SessionPhase.RESTORING, SessionPhase.SAVING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        SessionPhase.RECOVERY_ERROR -> {
                            OutlinedButton(onClick = controller::restoreDraft, modifier = Modifier.fillMaxWidth()) { Text("Повторить восстановление") }
                            TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) { Text("Удалить черновик") }
                        }
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
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Удалить черновик?") },
            text = { Text("Несохранённые данные этой сессии будут удалены. История сохранённых сессий не изменится.") },
            confirmButton = { TextButton(onClick = { controller.discardDraft(); confirmDiscard = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Отмена") } }
        )
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
