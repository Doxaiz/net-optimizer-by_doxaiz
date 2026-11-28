package com.doxaiz.netoptimizer.ui.sessions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.DoxaizApp
import com.doxaiz.netoptimizer.core.data.SessionEntity
import com.doxaiz.netoptimizer.core.session.MetricLevel
import com.doxaiz.netoptimizer.core.session.SessionController
import com.doxaiz.netoptimizer.core.session.SessionCsv
import com.doxaiz.netoptimizer.core.session.SessionInsights
import com.doxaiz.netoptimizer.core.session.SessionPhase
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.ui.home.WorkMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface HistoryResult {
    data object Loading : HistoryResult
    data object Error : HistoryResult
    data class Loaded(val sessions: List<SessionEntity>) : HistoryResult
}

@Composable
fun SessionsScreen(modifier: Modifier = Modifier, controller: SessionController, foregroundSinceMs: Long?, expert: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = expert || expanded
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as DoxaizApp).sessionRepository }
    var historyReload by remember { mutableIntStateOf(0) }
    val historyFlow = remember(repo, historyReload) {
        repo.observeAll().map<List<SessionEntity>, HistoryResult> { HistoryResult.Loaded(it) }
            .catch { emit(HistoryResult.Error) }
    }
    val history by historyFlow.collectAsState(initial = HistoryResult.Loading)
    val sessions = (history as? HistoryResult.Loaded)?.sessions.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val state = controller.state
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var operationMessage by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        pickerPending = false
        if (uri == null) {
            exportMessage = "Сохранение файла отменено."
        } else {
            exporting = true
            exportMessage = ""
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val snapshot = repo.observeAll().first()
                        if (snapshot.isEmpty()) throw IllegalStateException("No saved sessions")
                        val jobContext = currentCoroutineContext()
                        jobContext.ensureActive()
                        val stream = context.applicationContext.contentResolver.openOutputStream(uri, "wt")
                            ?: throw java.io.IOException("Document is unavailable")
                        stream.use { output ->
                            OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
                                SessionCsv.write(writer, snapshot) { jobContext.ensureActive() }
                            }
                        }
                        snapshot.size
                    }
                    exportMessage = "Сохранён CSV: записей $count. Проверьте файл в выбранной папке."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    exportMessage = "Не удалось сохранить CSV. Выбранный файл может быть неполным; попробуйте ещё раз."
                } finally {
                    exporting = false
                }
            }
        }
    }
    val detail = sessions.firstOrNull { it.id == detailId }
    val deleteTarget = sessions.firstOrNull { it.id == deleteId }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("СЕССИИ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("История соединения", style = MaterialTheme.typography.headlineMedium)
            Text("Замеры задержки с паузами; сравнение доступно для двух сохранённых сессий.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!expert) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть дополнительные действия" else "Показать дополнительные действия") }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
                    Text("TCP-задержка до Яндекса · успешных серий: ${state.progress.sampleCount}. Можно переключать вкладки; при сворачивании или заряде ниже 20% сессия ставится на паузу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (details) Text("Черновик записывается на телефон примерно раз в 5 секунд и при паузе. При внезапном сбое последние данные могут не сохраниться; после восстановления замер остаётся на паузе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (state.phase) {
                        SessionPhase.IDLE, SessionPhase.PAUSED -> {
                            Button(onClick = {
                                val mode = OnboardingPrefs.loadMode(context)?.let { runCatching { WorkMode.valueOf(it).label }.getOrNull() } ?: "Обычный"
                                controller.startOrResume(mode)
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (state.phase == SessionPhase.IDLE) "Начать сессию" else "Продолжить") }
                            if (state.phase == SessionPhase.PAUSED) {
                                OutlinedButton(onClick = { controller.stopAndSave(foregroundSinceMs) }, modifier = Modifier.fillMaxWidth()) { Text("Остановить и сохранить") }
                                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) { Text("Удалить черновик") }
                            }
                        }
                        SessionPhase.RUNNING -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { controller.pause() }, modifier = Modifier.weight(1f)) { Text("Пауза") }
                                Button(onClick = { controller.stopAndSave(foregroundSinceMs) }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
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
            if (a != null && b != null) ComparisonCard(a, b)
        }
        if (detail != null) item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ДЕТАЛИ СЕССИИ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("${detail.name} · ${sessionDate(detail.timestamp)}", style = MaterialTheme.typography.titleMedium)
                    SessionMetric("Средняя TCP-задержка", ms(detail.avgPingMs), SessionInsights.latencyLevel(detail.avgPingMs), "${SessionInsights.latencyExplanation(detail.avgPingMs)} Среднее серий, каждая серия имеет одинаковый вес; оценка условная.")
                    SessionMetric("Неудачные TCP-подключения", percent(detail.lossPercent), SessionInsights.failureLevel(detail.lossPercent), "Доля неудачных попыток подключения к сайту, не потери пакетов в игре.")
                    Text("Наибольшая средняя задержка серии: ${ms(detail.maxPingMs)} · Длительность: ${SessionInsights.formatDuration(detail.durationSec)}", style = MaterialTheme.typography.bodySmall)
                    val signal = if (detail.avgRsrp in -160..-30) "${detail.avgRsrp} дБм" else "нет данных"
                    Text("При сохранении: ${detail.operator} · сигнал $signal · диапазон ${detail.band}. Это не средний сигнал сессии; старые записи могут содержать устаревшие показания.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ГРАФИК TCP-ЗАДЕРЖКИ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    PingGraph(SessionInsights.historyValues(detail.pingHistory))
                    Text("Последние до 120 успешных серий: каждая точка — среднее успешных подключений. Неудачные серии и интервалы пауз на графике не показаны.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                try { repo.setPinned(detail.id, !detail.pinned); operationMessage = "" }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { operationMessage = "Не удалось изменить закрепление. Попробуйте ещё раз." }
                            }
                        }, modifier = Modifier.weight(1f)) { Text(if (detail.pinned) "Открепить" else "Закрепить") }
                        OutlinedButton(onClick = { deleteId = detail.id }, modifier = Modifier.weight(1f)) { Text("Удалить") }
                    }
                }
            }
        }
        item {
            Text("ИСТОРИЯ · ВЫБЕРИТЕ ДВЕ ДЛЯ СРАВНЕНИЯ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details) OutlinedButton(onClick = {
                try { pickerPending = true; exportLauncher.launch("doxaiz-sessions.csv") }
                catch (_: Exception) { pickerPending = false; exportMessage = "Не удалось открыть выбор файла в Android." }
            }, enabled = sessions.isNotEmpty() && !exporting && !pickerPending, modifier = Modifier.fillMaxWidth()) {
                Text(if (exporting) "Сохраняем CSV…" else "Экспортировать историю в CSV")
            }
            if (details) Text("Только сохранённые сессии, без черновика. CSV содержит даты, оператора и замеры, не зашифрован и не восстанавливается в приложении; выберите «Загрузки» вне папки приложения. При выборе облака файл передаётся выбранному сервису. Не закрывайте экран до завершения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.phase == SessionPhase.RUNNING) Text("При выборе файла активная сессия может перейти на паузу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (exportMessage.isNotEmpty()) Text(exportMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (operationMessage.isNotEmpty()) Text(operationMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        when (history) {
            HistoryResult.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            HistoryResult.Error -> item {
                Column {
                    Text("Не удалось загрузить историю сессий.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { historyReload++ }) { Text("Повторить") }
                }
            }
            is HistoryResult.Loaded -> if (sessions.isEmpty()) item { Text("Сохранённых сессий пока нет.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(sessions, key = { it.id }) { s ->
            val isSelected = s.id in selected
            Card(Modifier.fillMaxWidth().clickable {
                detailId = s.id
                selected = if (isSelected) selected - s.id else (selected + s.id).takeLast(2)
                scope.launch { listState.animateScrollToItem(2) }
            }, colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.name + if (s.pinned) " · Закреплено" else "", style = MaterialTheme.typography.titleMedium)
                    Text("${sessionDate(s.timestamp)} · ${SessionInsights.formatDuration(s.durationSec)} · ${s.operator}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TCP-задержка: ${ms(s.avgPingMs)} · Неудачные подключения: ${percent(s.lossPercent)}", color = metricColor(SessionInsights.latencyLevel(s.avgPingMs)))
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Удалить черновик?") },
            text = { Text("Несохранённые данные этой сессии будут удалены. История сохранённых сессий не изменится.") },
            confirmButton = { TextButton(onClick = { controller.discardDraft(); confirmDiscard = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Отмена") } })
    }
    if (deleteTarget != null) {
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Удалить сессию?") },
            text = { Text("${deleteTarget.name} от ${sessionDate(deleteTarget.timestamp)} будет удалена без возможности восстановления.") },
            confirmButton = { TextButton(onClick = {
                val id = deleteTarget.id
                deleteId = null
                scope.launch {
                    try { repo.delete(id); selected = selected - id; if (detailId == id) detailId = null; operationMessage = "" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { operationMessage = "Не удалось удалить сессию. Попробуйте ещё раз." }
                }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } })
    }
}

@Composable
private fun ComparisonCard(a: SessionEntity, b: SessionEntity) {
    val delta = SessionInsights.latencyDifference(a.avgPingMs, b.avgPingMs)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("СРАВНЕНИЕ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("1 · ${sessionDate(a.timestamp)} · ${a.mode}: ${ms(a.avgPingMs)} · ${percent(a.lossPercent)} неудачных", style = MaterialTheme.typography.bodyMedium)
            Text("2 · ${sessionDate(b.timestamp)} · ${b.mode}: ${ms(b.avgPingMs)} · ${percent(b.lossPercent)} неудачных", style = MaterialTheme.typography.bodyMedium)
            val difference = when {
                delta == null -> "Недостаточно данных для разницы задержки."
                delta == 0L -> "Средняя задержка одинакова."
                delta < 0 -> "Во второй сессии средняя задержка ниже на ${-delta} мс."
                else -> "Во второй сессии средняя задержка выше на $delta мс."
            }
            Text(difference, style = MaterialTheme.typography.bodyMedium)
            Text("Это среднее серий TCP-подключений до сайта, не пинг игрового сервера. Условия сети и время замеров могут отличаться; разница не доказывает улучшение.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String, level: MetricLevel, explanation: String) {
    Column {
        Text("$label: $value", color = metricColor(level), style = MaterialTheme.typography.bodyMedium)
        Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun metricColor(level: MetricLevel): Color = when (level) {
    MetricLevel.GOOD -> MaterialTheme.colorScheme.primary
    MetricLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
    MetricLevel.POOR -> MaterialTheme.colorScheme.error
    MetricLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ms(value: Int): String = if (value >= 0) "$value мс" else "нет данных"
private fun percent(value: Int): String = if (value in 0..100) "$value%" else "нет данных"
private fun sessionDate(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun PingGraph(history: List<Int>) {
    if (history.size < 2) { Text("Для графика нужно минимум две успешные серии."); return }
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val min = (history.minOrNull() ?: 0).toDouble() - 5.0
        val max = (history.maxOrNull() ?: 0).toDouble() + 5.0
        val path = Path()
        history.forEachIndexed { i, value ->
            val x = size.width * i / (history.size - 1)
            val y = size.height - (((value.toDouble() - min) / (max - min)) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(3f))
    }
}
