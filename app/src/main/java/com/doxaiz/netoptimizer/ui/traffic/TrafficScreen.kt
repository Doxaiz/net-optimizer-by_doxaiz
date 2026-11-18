package com.doxaiz.netoptimizer.ui.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.traffic.AppTraffic
import com.doxaiz.netoptimizer.core.traffic.MobileRate
import com.doxaiz.netoptimizer.core.traffic.TrafficMonitor
import com.doxaiz.netoptimizer.core.traffic.TrafficSummary
import com.doxaiz.netoptimizer.core.traffic.TrafficSummaryStatus
import com.doxaiz.netoptimizer.core.traffic.TrafficPeriod
import com.doxaiz.netoptimizer.core.traffic.TrafficWindow
import com.doxaiz.netoptimizer.core.traffic.LoadComparison
import com.doxaiz.netoptimizer.core.traffic.LoadOptimizationController
import com.doxaiz.netoptimizer.core.traffic.LoadPhase
import com.doxaiz.netoptimizer.core.traffic.LoadSample
import com.doxaiz.netoptimizer.core.traffic.saturatedAdd
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrafficScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val monitor = remember { TrafficMonitor(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val optimizer = remember(monitor, scope) { LoadOptimizationController(context.applicationContext, scope) }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var rate by remember { mutableStateOf<MobileRate?>(null) }
    var history by remember { mutableStateOf<List<MobileRate>>(emptyList()) }
    var summary by remember { mutableStateOf<TrafficSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf(TrafficPeriod.TODAY) }
    var protectedApps by remember { mutableStateOf(monitor.protectedByUser()) }
    var filter by remember { mutableStateOf("all") }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingStop by remember { mutableStateOf<AppTraffic?>(null) }

    DisposableEffect(lifecycle, optimizer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { foreground = true; optimizer.onStart() }
                Lifecycle.Event.ON_STOP -> { foreground = false; optimizer.onStop() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); optimizer.clear() }
    }
    LaunchedEffect(monitor, foreground) {
        rate = null
        history = emptyList()
        if (foreground) monitor.observeMobileRate().collect { current ->
            rate = current
            history = if (current == null) emptyList() else (history + current).takeLast(30)
        }
    }
    LaunchedEffect(monitor, foreground, refresh, period) {
        if (foreground) {
            loading = true
            summary = null
            try { summary = monitor.summary(period) } finally { loading = false }
        }
    }

    fun isProtected(app: AppTraffic) = app.systemProtected || app.packageNames.any { it in protectedApps }
    val shown = summary?.apps.orEmpty().filter { filter == "all" || isProtected(it) }

    pendingStop?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingStop = null },
            title = { Text("Остановить ${app.label}?") },
            text = { Text("Принудительная остановка может прервать загрузки и уведомления. Без работающего Shizuku откроется системная карточка, где действие нужно подтвердить вручную.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingStop = null
                    val pkg = app.packageNames.singleOrNull()
                    if (pkg != null && !isProtected(app)) {
                        scope.launch {
                            if (ShizukuManager.isReady() && ShizukuManager.forceStopApp(pkg)) {
                                notice = "Команда остановки отправлена: ${app.label}."
                            } else {
                                notice = if (monitor.openAppSettings(pkg)) "Открыта карточка приложения; при необходимости нажмите «Остановить»." else "Не удалось открыть карточку приложения."
                            }
                        }
                    }
                }) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = { pendingStop = null }) { Text("Отмена") } }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("ТРАФИК · МОБИЛЬНАЯ СЕТЬ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Текущая скорость телефона", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (rate != null) "↓ ${formatRate(rate!!.rxBytesPerSecond)}  ·  ↑ ${formatRate(rate!!.txBytesPerSecond)}" else "Нет данных", style = MaterialTheme.typography.titleMedium, color = if (rate != null) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Общие счётчики мобильных интерфейсов Android, не скорость отдельных приложений. Первое значение появляется после двух снимков; раздача может учитываться по-разному.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history.size >= 2) {
                        TrafficGraph(history)
                        Text("Приём — голубой · отдача — фиолетовый; последние до 30 снимков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { LoadOptimizationCard(optimizer) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("РАСХОД МОБИЛЬНЫХ ДАННЫХ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrafficPeriod.entries.forEach { choice ->
                            FilterChip(selected = period == choice, onClick = { period = choice }, label = { Text(choice.label) })
                        }
                    }
                    Text("7 дней включают сегодня; месяц считается с первого числа. Данные относятся только к мобильным сетям и могут обновляться с задержкой. Это не текущая скорость и не счёт оператора.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!loading && summary?.status == TrafficSummaryStatus.READY) {
                        summary?.window?.let { Text("Период: ${formatWindow(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val apps = summary?.apps.orEmpty()
                        if (apps.isNotEmpty()) {
                            val total = apps.fold(0L) { sum, app -> saturatedAdd(sum, app.totalBytes) }
                            Text("Всего по доступным записям: ${formatBytes(total)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Включает доступные системные UID и раздачу, если Android их предоставил; статистика может быть неполной.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when {
                        loading -> Text("Получаем статистику…", style = MaterialTheme.typography.bodySmall)
                        summary?.status == TrafficSummaryStatus.NO_ACCESS -> {
                            Text("Нужен доступ к истории использования приложений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { if (!monitor.openUsageAccess()) notice = "Откройте доступ к истории использования в настройках Android вручную." }) { Text("Предоставить доступ") }
                        }
                        summary?.status == TrafficSummaryStatus.UNAVAILABLE -> Text("Android не предоставил данные. Попробуйте обновить позже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        summary?.apps?.isEmpty() == true -> Text("За этот период данных пока нет или Android их ещё не обновил.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { refresh++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Обновить статистику") }
                }
            }
        }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Все", "white" to "Белый список").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
            Text("Белый список защищает от остановки в этом приложении. Блокировка сети пока недоступна. Системные компоненты и общие UID защищены автоматически.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (!loading && summary?.status == TrafficSummaryStatus.READY && shown.isEmpty() && filter == "white") {
                Text("Защищённых записей с расходом за выбранный период нет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(shown, key = { it.uid }) { app ->
            val userProtected = app.packageNames.singleOrNull()?.let { it in protectedApps } == true
            TrafficRow(app, userProtected,
                onProtection = { protect ->
                    app.packageNames.singleOrNull()?.let { pkg ->
                        monitor.setProtected(pkg, protect)
                        protectedApps = monitor.protectedByUser()
                    }
                },
                onStop = { pendingStop = app })
        }
    }
}

@Composable
private fun LoadOptimizationCard(controller: LoadOptimizationController) {
    val state = controller.state
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("СНИЗИТЬ НАГРУЗКУ НА СЕТЬ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("1. Измерьте задержку. 2. Поставьте ненужные загрузки на паузу или остановите выбранное приложение ниже. При USB-модеме проверьте загрузки и на ПК. 3. Измерьте снова.", style = MaterialTheme.typography.bodySmall)
            Text("Уменьшение конкурирующего трафика может освободить канал и снизить задержку, но не меняет настройки сети и не гарантирует улучшения. Расход за период ниже не показывает, что приложение использует сеть прямо сейчас.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Для сравнения скорости отдельно запустите два спидтеста в Инструменты → Замеры, до и после паузы загрузок. Каждый тест расходует до 15 МБ плюс служебный трафик.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.before?.let { Text("До: ${loadSampleText(it)}", style = MaterialTheme.typography.bodyMedium) }
            state.after?.let { after ->
                Text("После: ${loadSampleText(after)}", style = MaterialTheme.typography.bodyMedium)
                state.before?.let { before ->
                    LoadComparison.delta(before, after)?.let { delta ->
                        val text = when {
                            delta < 0 -> "Средняя TCP-задержка ниже на ${-delta} мс."
                            delta > 0 -> "Средняя TCP-задержка выше на $delta мс."
                            else -> "Средняя TCP-задержка не изменилась."
                        }
                        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("Это разница двух замеров, а не доказательство причины: нагрузка, маршрут и сота могли измениться.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state.phase) {
                LoadPhase.IDLE -> Button(onClick = controller::start, modifier = Modifier.fillMaxWidth()) { Text("Начать сравнение") }
                LoadPhase.BASELINE, LoadPhase.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                LoadPhase.READY, LoadPhase.RESULT -> Button(onClick = controller::measureAfter, modifier = Modifier.fillMaxWidth()) { Text("Измерить после изменений") }
            }
            if (state.phase != LoadPhase.IDLE) TextButton(onClick = { controller.clear() }) { Text("Начать заново") }
            Text("Каждый замер — 6 TCP-подключений к одному IP сайта Яндекса; нужно минимум 4 успешных. Это не пинг игрового сервера и не проверка скорости. Действия с приложениями выполняются только с вашего подтверждения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("При сворачивании замер отменяется. Сравнение хранится только на этом экране до 10 минут; при смене мобильной сети оно сбрасывается. Между замерами Android отслеживает смену сети без отправки проб.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun loadSampleText(sample: LoadSample): String =
    "${sample.averageMs} мс · разброс ${sample.jitterMs} мс · ${sample.successes}/${sample.attempts} успешных"

@Composable
private fun TrafficRow(app: AppTraffic, userProtected: Boolean, onProtection: (Boolean) -> Unit, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.label + if (app.systemProtected || userProtected) " 🛡" else "", style = MaterialTheme.typography.titleSmall)
            Text("Всего ${formatBytes(app.totalBytes)} · ↓ ${formatBytes(app.rxBytes)} · ↑ ${formatBytes(app.txBytes)}", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
            if (app.packageNames.size > 1) Text("Общий UID: данные нельзя надёжно разделить между приложениями.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.systemProtected) {
                    Text("Системная защита", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = { onProtection(!userProtected) }) { Text(if (userProtected) "Убрать защиту" else "Защитить") }
                    TextButton(onClick = onStop, enabled = !userProtected) { Text("Стоп") }
                }
            }
        }
    }
}

private fun formatWindow(window: TrafficWindow): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault()).withZone(ZoneId.of(window.zoneId))
    return "${formatter.format(Instant.ofEpochMilli(window.startMs))} — ${formatter.format(Instant.ofEpochMilli(window.endMs))}"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f ГБ".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f МБ".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f КБ".format(bytes / 1_000.0)
    else -> "$bytes Б"
}
private fun formatRate(bytesPerSecond: Double): String = when {
    bytesPerSecond >= 1_000_000 -> "%.2f МБ/с".format(bytesPerSecond / 1_000_000)
    bytesPerSecond >= 1_000 -> "%.1f КБ/с".format(bytesPerSecond / 1_000)
    else -> "%.0f Б/с".format(bytesPerSecond)
}

@Composable
private fun TrafficGraph(history: List<MobileRate>) {
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        if (history.size < 2) return@Canvas
        val maxValue = history.maxOf { maxOf(it.rxBytesPerSecond, it.txBytesPerSecond) }.coerceAtLeast(1.0)
        val step = size.width / (history.size - 1)
        listOf(AccentCyan to { r: MobileRate -> r.rxBytesPerSecond }, AccentViolet to { r: MobileRate -> r.txBytesPerSecond }).forEach { (color, value) ->
            val path = Path()
            history.forEachIndexed { i, sample ->
                val x = step * i
                val y = size.height - (value(sample) / maxValue).toFloat() * (size.height - 10f) - 5f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.5f))
        }
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, 5f), Offset(size.width, 5f), 1f)
    }
}
