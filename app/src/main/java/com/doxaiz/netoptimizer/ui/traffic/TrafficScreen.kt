package com.doxaiz.netoptimizer.ui.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import com.doxaiz.netoptimizer.core.traffic.AppTraffic
import com.doxaiz.netoptimizer.core.traffic.BatchStop
import com.doxaiz.netoptimizer.core.traffic.StopOutcome
import com.doxaiz.netoptimizer.core.traffic.StopPolicy
import com.doxaiz.netoptimizer.core.traffic.StopResult
import com.doxaiz.netoptimizer.core.traffic.StopTarget
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
import com.doxaiz.netoptimizer.core.traffic.DataSaver
import com.doxaiz.netoptimizer.core.traffic.DataSaverStatus
import com.doxaiz.netoptimizer.core.traffic.saturatedAdd
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private class StopHalted(message: String) : Exception(message)

@Composable
fun TrafficScreen(modifier: Modifier = Modifier, expert: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = expert || expanded
    val context = LocalContext.current
    val monitor = remember { TrafficMonitor(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val optimizer = remember(monitor, scope) { LoadOptimizationController(context.applicationContext, scope) }
    val dataSaver = remember(context) { DataSaver(context.applicationContext) }
    var dataSaverStatus by remember(dataSaver) { mutableStateOf(dataSaver.status()) }
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
    var pendingBatch by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf<Map<String, AppTraffic>>(emptyMap()) }
    var stopResults by remember { mutableStateOf<List<StopResult>>(emptyList()) }
    var stopJob by remember { mutableStateOf<Job?>(null) }
    var stopping by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, optimizer, dataSaver) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { foreground = true; dataSaverStatus = dataSaver.status(); optimizer.onStart() }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    optimizer.onStop()
                    pendingStop = null; pendingBatch = false
                    if (stopping) {
                        stopJob?.cancel()
                        notice = "Оставшиеся команды отменены. Уже запущенная команда могла выполниться."
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopJob?.cancel(); optimizer.clear() }
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

    fun stopSelected() {
        if (stopping) return
        val targets = selectedApps.values.mapNotNull { app -> app.packageNames.singleOrNull()?.let { StopTarget(app.uid, it, app.label) } }
        val initial = CellularNetwork.active(context)
        if (targets.isEmpty() || initial == null) {
            notice = "Выберите приложения и подключите мобильный интернет."
            return
        }
        if (!ShizukuManager.isReady()) {
            notice = "Для общей остановки нужен запущенный Shizuku с разрешением. Без него используйте «Стоп» у каждого приложения и системные настройки."
            return
        }
        stopResults = emptyList()
        notice = "Отправляем команды…"
        stopping = true
        stopJob = scope.launch {
            try {
                BatchStop.run(targets, { monitor.canStopNow(it) }, { target ->
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || CellularNetwork.active(context) != initial) throw StopHalted("Мобильная сеть изменилась; оставшиеся команды не отправлены.")
                    if (!ShizukuManager.isReady()) throw StopHalted("Shizuku недоступен; оставшиеся команды не отправлены.")
                    ShizukuManager.forceStopApp(target.packageName)
                }, { result ->
                    stopResults = stopResults + result
                    if (result.outcome != StopOutcome.FAILED) selectedApps = selectedApps - result.target.packageName
                })
                notice = "Проверка команд завершена. Успех команды не доказывает, что приложение использовало сеть или что связь улучшилась."
            } catch (e: CancellationException) {
                notice = "Оставшиеся команды отменены. Уже запущенная команда могла выполниться."
                throw e
            } catch (e: StopHalted) {
                notice = e.message
            } catch (_: Exception) {
                notice = "Не удалось завершить обработку. Уже запущенная команда могла выполниться."
            } finally {
                stopping = false
                stopJob = null
            }
        }
    }

    pendingStop?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingStop = null },
            title = { Text("Остановить ${app.label}?") },
            text = { Text("Принудительная остановка влияет на всё приложение, не только мобильный интернет: могут прерваться загрузки, фоновые задачи и уведомления. Без Shizuku откроется системная карточка, где действие нужно подтвердить вручную.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingStop = null
                    val pkg = app.packageNames.singleOrNull()
                    if (pkg != null && !isProtected(app) && !stopping) {
                        val initial = CellularNetwork.active(context)
                        if (initial == null) {
                            notice = "Подключите мобильный интернет, чтобы выполнить действие."
                        } else {
                            stopping = true
                            stopJob = scope.launch {
                                try {
                                    val target = StopTarget(app.uid, pkg, app.label)
                                    val allowed = monitor.canStopNow(target)
                                    currentCoroutineContext().ensureActive()
                                    when {
                                        !allowed -> notice = "Приложение защищено или изменилось; команда не отправлена."
                                        CellularNetwork.active(context) != initial -> notice = "Мобильная сеть изменилась; команда не отправлена."
                                        ShizukuManager.isReady() && ShizukuManager.forceStopApp(pkg) -> {
                                            notice = "Команда остановки принята: ${app.label}. Эффект на сеть не проверен."
                                            selectedApps = selectedApps - pkg
                                        }
                                        else -> {
                                            currentCoroutineContext().ensureActive()
                                            notice = if (monitor.openAppSettings(pkg)) "Открыта карточка приложения; при необходимости нажмите «Остановить»." else "Не удалось открыть карточку приложения."
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    notice = "Действие отменено. Уже запущенная команда могла выполниться."
                                    throw e
                                } catch (_: Exception) {
                                    notice = "Не удалось выполнить действие."
                                } finally { stopping = false; stopJob = null }
                            }
                        }
                    }
                }) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = { pendingStop = null }) { Text("Отмена") } }
        )
    }

    if (pendingBatch) {
        AlertDialog(
            onDismissRequest = { pendingBatch = false },
            title = { Text("Остановить выбранные приложения?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Остановятся целые приложения, не только их мобильный трафик. Могут прерваться загрузки, фоновые задачи и уведомления. История расхода не доказывает, что они сейчас активны. Для общей остановки необходим Shizuku.")
                    selectedApps.values.forEach { app -> Text("• ${app.label} (${app.packageNames.singleOrNull() ?: "—"})", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { pendingBatch = false; stopSelected() }) { Text("Остановить выбранные") } },
            dismissButton = { TextButton(onClick = { pendingBatch = false }) { Text("Отмена") } }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("ТРАФИК · МОБИЛЬНАЯ СЕТЬ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!expert) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть дополнительные действия" else "Показать дополнительные действия") }
        }
        item { LoadOptimizationCard(optimizer, details, dataSaverStatus, dataSaver::openSettings) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Текущая скорость телефона", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (rate != null) "↓ ${formatRate(rate!!.rxBytesPerSecond)}  ·  ↑ ${formatRate(rate!!.txBytesPerSecond)}" else "Нет данных", style = MaterialTheme.typography.titleMedium, color = if (rate != null) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (details) "Общие счётчики мобильных интерфейсов Android, не скорость отдельных приложений. Первое значение появляется после двух снимков; раздача может учитываться по-разному." else "Общий мобильный трафик телефона, не отдельных приложений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (details && history.size >= 2) {
                        TrafficGraph(history)
                        Text("Приём — голубой · отдача — фиолетовый; последние до 30 снимков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("РАСХОД МОБИЛЬНЫХ ДАННЫХ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrafficPeriod.entries.forEach { choice ->
                            FilterChip(selected = period == choice, onClick = { period = choice }, label = { Text(choice.label) })
                        }
                    }
                    Text(if (details) "7 дней включают сегодня; месяц считается с первого числа. Данные относятся только к мобильным сетям и могут обновляться с задержкой. Это не текущая скорость и не счёт оператора." else "Расход за период; Android может показывать данные с задержкой.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (details) item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Все", "white" to "Белый список").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
            Text("Белый список защищает от остановки в этом приложении. Блокировка сети пока недоступна. Системные компоненты и общие UID защищены автоматически.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Выберите ненужные приложения ниже: ${selectedApps.size}/${StopPolicy.MAX_TARGETS}", style = MaterialTheme.typography.bodyMedium)
            Text("Выбор не означает, что они сейчас нагружают сеть. Команда останавливает приложения целиком и может затронуть уведомления и другие соединения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { pendingBatch = true }, enabled = selectedApps.isNotEmpty() && !stopping && !loading, modifier = Modifier.fillMaxWidth()) { Text("Остановить выбранные через Shizuku") }
            if (stopping) TextButton(onClick = { stopJob?.cancel() }) { Text("Отменить оставшиеся команды") }
            if (selectedApps.isNotEmpty() && !stopping) TextButton(onClick = { selectedApps = emptyMap() }) { Text("Очистить выбор") }
            stopResults.forEach { result ->
                val text = when (result.outcome) {
                    StopOutcome.SENT -> "команда принята"
                    StopOutcome.FAILED -> "не удалось отправить команду"
                    StopOutcome.PROTECTED_OR_CHANGED -> "пропущено: защита или изменение приложения"
                }
                Text("${result.target.label}: $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (!loading && summary?.status == TrafficSummaryStatus.READY && shown.isEmpty() && filter == "white") {
                Text("Защищённых записей с расходом за выбранный период нет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(if (details) shown else summary?.apps.orEmpty(), key = { it.uid }) { app ->
            if (!details) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        Text("Израсходовано: ${formatBytes(app.totalBytes)}", color = AccentCyan)
                        if (app.packageNames.size > 1) Text("Общие данные нескольких приложений, по отдельности недоступны.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
            val userProtected = app.packageNames.singleOrNull()?.let { it in protectedApps } == true
            val pkg = app.packageNames.singleOrNull()
            TrafficRow(app, userProtected, selected = pkg != null && pkg in selectedApps, enabled = !stopping,
                onProtection = { protect ->
                    pkg?.let {
                        monitor.setProtected(it, protect)
                        protectedApps = monitor.protectedByUser()
                        if (protect) selectedApps = selectedApps - it
                    }
                },
                onSelect = { choose ->
                    if (pkg != null && !isProtected(app)) {
                        if (!choose) selectedApps = selectedApps - pkg
                        else if (selectedApps.size < StopPolicy.MAX_TARGETS) selectedApps = selectedApps + (pkg to app)
                        else notice = "Можно выбрать не более ${StopPolicy.MAX_TARGETS} приложений за раз."
                    }
                },
                onStop = { pendingStop = app })
            }
        }
    }
}

@Composable
private fun LoadOptimizationCard(controller: LoadOptimizationController, details: Boolean, dataSaverStatus: DataSaverStatus, openDataSaver: () -> Boolean) {
    val state = controller.state
    var settingsUnavailable by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("СНИЗИТЬ НАГРУЗКУ НА СЕТЬ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when (state.phase) {
                LoadPhase.IDLE -> "Шаг 1: измерьте задержку до изменений."
                LoadPhase.BASELINE -> "Измеряем задержку до изменений…"
                LoadPhase.READY -> "Шаг 2: приостановите ненужные загрузки, затем измерьте снова."
                LoadPhase.CHECKING -> "Измеряем задержку после изменений…"
                LoadPhase.RESULT -> "Сравнение готово"
            }, style = MaterialTheme.typography.titleSmall)
            state.before?.let { Text("До: ${loadSampleText(it)}", style = MaterialTheme.typography.bodyMedium) }
            state.after?.let { after ->
                Text("После: ${loadSampleText(after)}", style = MaterialTheme.typography.bodyMedium)
                state.before?.let { before ->
                    LoadComparison.delta(before, after)?.let { delta ->
                        val text = when {
                            delta < 0 -> "Задержка ниже на ${-delta} мс"
                            delta > 0 -> "Задержка выше на $delta мс"
                            else -> "Задержка не изменилась"
                        }
                        Text(text, style = MaterialTheme.typography.titleMedium, color = when {
                            delta < 0 -> AccentGreen
                            delta > 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    }
                }
                Text("Это разница двух замеров, причина изменения неизвестна.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.message.isNotEmpty() && (details || state.phase == LoadPhase.IDLE || state.phase == LoadPhase.READY))
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state.phase) {
                LoadPhase.IDLE -> Button(onClick = controller::start, modifier = Modifier.fillMaxWidth()) { Text("Начать сравнение") }
                LoadPhase.BASELINE, LoadPhase.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                LoadPhase.READY, LoadPhase.RESULT -> Button(onClick = controller::measureAfter, modifier = Modifier.fillMaxWidth()) { Text("Измерить после изменений") }
            }
            if (state.phase != LoadPhase.IDLE) TextButton(onClick = { controller.clear() }) { Text(if (state.phase == LoadPhase.BASELINE || state.phase == LoadPhase.CHECKING) "Отменить" else "Начать заново") }
            HorizontalDivider()
            Text("Экономия трафика Android: " + when (dataSaverStatus) {
                DataSaverStatus.OFF -> "выключена"
                DataSaverStatus.ON -> "включена"
                DataSaverStatus.ON_APP_EXEMPT -> "включена (это приложение в исключениях)"
                DataSaverStatus.UNKNOWN -> "состояние неизвестно"
            }, style = MaterialTheme.typography.titleSmall)
            Text("Может ограничить фоновые загрузки на лимитных сетях и освободить канал. Уведомления и синхронизация могут задерживаться; исключения настраиваются в Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { settingsUnavailable = !openDataSaver() }, enabled = state.phase != LoadPhase.BASELINE && state.phase != LoadPhase.CHECKING, modifier = Modifier.fillMaxWidth()) { Text("Настроить экономию трафика") }
            if (settingsUnavailable) Text("Не удалось открыть настройки. Найдите «Экономия трафика» в настройках Android вручную.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("Включите или выключите вручную в настройках. Если открылась общая страница, найдите «Экономия трафика». Настройка может действовать и на лимитном Wi‑Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Проверяется задержка до сайта Яндекса, не скорость и не пинг игры. Улучшение не гарантировано.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details) {
                Text("Шесть TCP-подключений к одному IP; требуется минимум четыре ответа. При USB-модеме проверьте загрузки на ПК. Остановка приложений доступна ниже после подтверждения и влияет на всё приложение.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Для сравнения скорости используйте два спидтеста в Инструменты → Замеры (до 15 МБ каждый). При сворачивании замер отменяется; сравнение хранится до 10 минут и сбрасывается при смене сети.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun loadSampleText(sample: LoadSample): String =
    "${sample.averageMs} мс · разброс ${sample.jitterMs} мс · ${sample.successes}/${sample.attempts} успешных"

@Composable
private fun TrafficRow(app: AppTraffic, userProtected: Boolean, selected: Boolean, enabled: Boolean, onProtection: (Boolean) -> Unit, onSelect: (Boolean) -> Unit, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.label + if (app.systemProtected || userProtected) " 🛡" else "", style = MaterialTheme.typography.titleSmall)
            Text("Всего ${formatBytes(app.totalBytes)} · ↓ ${formatBytes(app.rxBytes)} · ↑ ${formatBytes(app.txBytes)}", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
            if (app.packageNames.size > 1) Text("Общий UID: данные нельзя надёжно разделить между приложениями.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.systemProtected) {
                    Text("Системная защита", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = { onProtection(!userProtected) }, enabled = enabled) { Text(if (userProtected) "Убрать защиту" else "Защитить") }
                    TextButton(onClick = onStop, enabled = enabled && !userProtected) { Text("Стоп") }
                }
            }
            if (!app.systemProtected && !userProtected && app.packageNames.size == 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected, onCheckedChange = onSelect, enabled = enabled)
                    Text("Выбрать для остановки", style = MaterialTheme.typography.bodySmall)
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
