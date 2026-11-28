package com.doxaiz.netoptimizer.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.cellular.CarrierProfiles
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.cellular.SignalFreshness
import com.doxaiz.netoptimizer.core.cellular.SignalFreshnessStatus
import com.doxaiz.netoptimizer.core.cellular.withoutCellData
import com.doxaiz.netoptimizer.core.cellular.SignalHistory
import com.doxaiz.netoptimizer.core.cellular.SignalObservation
import com.doxaiz.netoptimizer.core.cellular.SignalState
import com.doxaiz.netoptimizer.core.cellular.RadioChannels
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.doxaiz.netoptimizer.core.dns.DnsBenchmark
import com.doxaiz.netoptimizer.core.dns.PrivateDns
import com.doxaiz.netoptimizer.core.net.KeepAliveEngine
import com.doxaiz.netoptimizer.core.net.KeepAliveProfile
import com.doxaiz.netoptimizer.core.net.KeepAliveStatus
import com.doxaiz.netoptimizer.core.net.LoadedLatency
import com.doxaiz.netoptimizer.core.net.description
import com.doxaiz.netoptimizer.core.util.Beeper
import com.doxaiz.netoptimizer.core.dns.DnsCatalog
import com.doxaiz.netoptimizer.core.dns.DnsServer
import com.doxaiz.netoptimizer.core.net.CustomTarget
import com.doxaiz.netoptimizer.core.net.PingStatus
import com.doxaiz.netoptimizer.core.net.PingCatalog
import com.doxaiz.netoptimizer.core.net.PingResult
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.net.SpeedResult
import com.doxaiz.netoptimizer.core.net.SpeedTester
import com.doxaiz.netoptimizer.core.net.SpeedStatus
import com.doxaiz.netoptimizer.core.net.BatteryHealth
import com.doxaiz.netoptimizer.core.net.SpeedDiagnostics
import com.doxaiz.netoptimizer.core.net.SpeedComparisonStatus
import com.doxaiz.netoptimizer.core.net.SpeedHint
import com.doxaiz.netoptimizer.core.net.NetworkConditionLevel
import com.doxaiz.netoptimizer.core.net.NetworkConditionPolicy
import com.doxaiz.netoptimizer.core.net.NetworkConditionReader
import com.doxaiz.netoptimizer.core.net.NetworkConditions
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentRed
import com.doxaiz.netoptimizer.ui.components.DoxaizCard
import com.doxaiz.netoptimizer.ui.components.StatusPill
import com.doxaiz.netoptimizer.ui.components.PillTone
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

private enum class ToolsSection(val label: String) {
    SIGNAL("Сигнал"), DNS("DNS"), TESTS("Замеры"), USB("USB-модем")
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, monitor: SignalMonitor, foregroundSinceMs: Long?, nowMs: Long, expert: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rawSignal by monitor.state.collectAsState()

    var section by rememberSaveable { mutableStateOf(ToolsSection.SIGNAL) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = expert || expanded
    LaunchedEffect(details) {
        if (!details && section != ToolsSection.SIGNAL && section != ToolsSection.TESTS) section = ToolsSection.SIGNAL
    }
    var soundOn by remember { mutableStateOf(false) }
    var vibOn by remember { mutableStateOf(false) }
    var radioError by remember { mutableStateOf(false) }
    var dialError by remember { mutableStateOf(false) }
    var showTestingGuide by remember { mutableStateOf(false) }
    var dnsList by remember { mutableStateOf(DnsCatalog.servers) }
    var selectedDns by remember { mutableStateOf<DnsServer?>(null) }
    var benching by remember { mutableStateOf(false) }
    var benchJob by remember { mutableStateOf<Job?>(null) }
    var dnsError by remember { mutableStateOf(false) }
    var dnsTried by remember { mutableStateOf(false) }
    var pingResults by remember { mutableStateOf<Map<String, PingResult>>(emptyMap()) }
    var pingTesting by remember { mutableStateOf(false) }
    var pingError by remember { mutableStateOf(false) }
    var pingJob by remember { mutableStateOf<Job?>(null) }
    var customHost by rememberSaveable { mutableStateOf("") }
    var customPort by rememberSaveable { mutableStateOf("443") }
    var customError by remember { mutableStateOf<String?>(null) }
    var customResult by remember { mutableStateOf<PingResult?>(null) }
    var customTesting by remember { mutableStateOf(false) }
    var customJob by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableStateOf<SpeedResult?>(null) }
    var previousSpeed by remember { mutableStateOf<SpeedResult?>(null) }
    var speedTesting by remember { mutableStateOf(false) }
    var speedError by remember { mutableStateOf(false) }
    var speedCancelled by remember { mutableStateOf(false) }
    var speedJob by remember { mutableStateOf<Job?>(null) }
    var speedComparisonClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var networkConditions by remember { mutableStateOf<NetworkConditions?>(null) }
    LaunchedEffect(section, details) { networkConditions = NetworkConditionReader.read(context) }

    val bench = remember { DnsBenchmark(context) }
    val keepAliveEngine = remember { KeepAliveEngine(context.applicationContext) }
    val keepAliveState by keepAliveEngine.state.collectAsState()
    LaunchedEffect(details) {
        if (!details) {
            soundOn = false
            vibOn = false
            keepAliveEngine.stop()
            benchJob?.cancel()
            pingJob?.cancel()
            customJob?.cancel()
        }
    }
    val pingTester = remember { PingTester(context) }
    val speedTester = remember { SpeedTester(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    LaunchedEffect(previousSpeed, speed, foreground) {
        speedComparisonClock = SystemClock.elapsedRealtime()
        val firstTime = previousSpeed?.completedAtElapsedMs
        if (foreground && firstTime != null && firstTime >= 0 && speedComparisonClock >= firstTime) {
            val age = speedComparisonClock - firstTime
            if (age < SpeedDiagnostics.COMPARISON_WINDOW_MS) {
                delay(SpeedDiagnostics.COMPARISON_WINDOW_MS - age)
                speedComparisonClock = SystemClock.elapsedRealtime()
            }
        }
    }
    var signalHistory by remember { mutableStateOf<List<SignalObservation>>(emptyList()) }
    var signalClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val freshness = SignalFreshness.status(rawSignal.observedAtElapsedMs, rawSignal.modemAtElapsedMs, foregroundSinceMs, maxOf(nowMs, signalClock, SystemClock.elapsedRealtime()))
    val signal = if (SignalFreshness.canDisplay(freshness)) rawSignal else rawSignal.withoutCellData()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foreground = true
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                signalHistory = emptyList()
                speedJob?.cancel()
                benchJob?.cancel()
                pingJob?.cancel()
                customJob?.cancel()
                keepAliveEngine.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            speedJob?.cancel()
            benchJob?.cancel()
            pingJob?.cancel()
            customJob?.cancel()
            keepAliveEngine.stop()
        }
    }
    LaunchedEffect(monitor, section, foreground, foregroundSinceMs) {
        signalHistory = emptyList()
        if (foreground && foregroundSinceMs != null && section == ToolsSection.SIGNAL) {
            val openedAt = SystemClock.elapsedRealtime()
            monitor.state.collect { reading ->
                val at = reading.observedAtElapsedMs
                if (at != null && at >= openedAt) {
                    val status = SignalFreshness.status(at, reading.modemAtElapsedMs, foregroundSinceMs, SystemClock.elapsedRealtime())
                    val value = if (SignalFreshness.canDisplay(status)) reading.rsrp else 0
                    signalHistory = SignalHistory.append(signalHistory, at, value, reading.networkType)
                }
            }
        }
    }
    LaunchedEffect(section, foreground) {
        if (foreground && section == ToolsSection.SIGNAL) {
            while (isActive) {
                signalClock = SystemClock.elapsedRealtime()
                signalHistory = SignalHistory.trim(signalHistory, signalClock)
                delay(1_000)
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(section) {
        if (section != ToolsSection.TESTS) {
            speedJob?.cancel()
            pingJob?.cancel()
            customJob?.cancel()
        }
        if (section != ToolsSection.DNS) benchJob?.cancel()
        if (section != ToolsSection.SIGNAL) keepAliveEngine.stop()
        listState.scrollToItem(0)
    }

    if (showTestingGuide) {
        AlertDialog(
            onDismissRequest = { showTestingGuide = false },
            title = { Text("Системное меню сведений") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("На некоторых Android меню «Информация о телефоне» открывается через телефонный набор кода *#*#4636#*#*. На XOS или у оператора оно может отсутствовать. Приложение код не набирает и настройки не меняет.")
                    Text("Перед изменениями запишите исходный режим. Только LTE и NR/LTE могут нарушить звонки, SMS и экстренные вызовы, если VoLTE не работает; NR only может не подключиться в сетях 5G NSA. Мы не можем подтвердить VoLTE. Не меняйте переключатель питания радио и инженерные параметры.", style = MaterialTheme.typography.bodySmall)
                    Text("Меню может показывать IMEI и другие личные данные — не пересылайте их.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showTestingGuide = false; dialError = !openDialer(context) }) { Text("Открыть телефон") } },
            dismissButton = { TextButton(onClick = { showTestingGuide = false }) { Text("Закрыть") } }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("ИНСТРУМЕНТЫ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Диагностика сети", style = MaterialTheme.typography.headlineMedium)
        Text("Замеры выполняются только через мобильное подключение.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (if (details) ToolsSection.entries else listOf(ToolsSection.SIGNAL, ToolsSection.TESTS)).forEach { item ->
                FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item.label) })
            }
        }
        if (!expert) TextButton(onClick = {
            expanded = !expanded
            if (!expanded && section != ToolsSection.SIGNAL && section != ToolsSection.TESTS) section = ToolsSection.SIGNAL
        }) { Text(if (expanded) "Скрыть дополнительные инструменты" else "Показать дополнительные инструменты") }
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Радар
            if (section == ToolsSection.SIGNAL) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Живой радар сигнала", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        RadarCanvas(rsrp = signal.rsrp, soundOn = soundOn, vibOn = vibOn)
                        Text(if (signal.rsrp in -160..-30) "${signal.rsrp} dBm" else "Нет данных о сигнале", style = MaterialTheme.typography.headlineSmall)
                        val freshnessText = when (freshness) {
                            SignalFreshnessStatus.STALE_MODEM -> "Сведения от модема старше 30 секунд. Показания скрыты, звук и вибрация отключены до обновления."
                            SignalFreshnessStatus.STALE_POLL -> "Нет свежего опроса Android. Показания скрыты, звук и вибрация отключены до обновления."
                            SignalFreshnessStatus.UNKNOWN_MODEM_TIME -> if (signal.networkType != "—") "Время получения от модема неизвестно: показания могут быть устаревшими." else null
                            SignalFreshnessStatus.FRESH -> "Время данных модема приблизительное; актуальность не гарантирована."
                        }
                        freshnessText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(8.dp))
                        if (details) CurrentCellDetails(signal)
                        if (details && signal.neighbors.isNotEmpty()) {
                            Text(
                                "Соседние соты: " + signal.neighbors.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (details) Text("Кэш Android читается примерно раз в 3 с; обновление у модема запрашивается не чаще 15 с при заряде от 20%. Android может ограничить запрос или вернуть старые данные. Расположение сот не определяется.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        if (details) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = soundOn, onClick = { soundOn = !soundOn }, label = { Text("🔊 Звук") })
                            FilterChip(selected = vibOn, onClick = { vibOn = !vibOn }, label = { Text("📳 Вибрация") })
                        }
                    }
                }
            }
            if (section == ToolsSection.SIGNAL && details) item {
                SignalHistoryCard(signalHistory, signalClock)
            }
            if (section == ToolsSection.SIGNAL) item {
                BatteryStatusCard(signal, maxOf(signalClock, SystemClock.elapsedRealtime()))
            }
            // Стабилизация 4G/5G
            if (section == ToolsSection.SIGNAL && details) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Стабилизация 4G / 5G", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Тип сети выбирается вручную в настройках телефона: обычно доступны предпочтение 4G или автоматический 5G/4G с резервными сетями. Доступные варианты зависят от устройства и оператора; лучший режим нужно проверить замерами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Приложение не переключает режим и не умеет подтвердить работу VoLTE. Режимы только LTE или NR/LTE могут нарушить звонки и SMS; NR only также может отключить интернет. Не меняйте их без проверки и возможности вернуть исходный режим.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Анти-сон модема (Keep-Alive)")
                                Text("DNS-запрос example.com к 1.1.1.1 по UDP каждые 3 с. Только на этом экране; при сворачивании останавливается. При низком заряде или потере мобильной сети — пауза и повторные попытки, пока экран открыт. Эффект не гарантирован; расходует батарею и трафик.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(keepAliveState.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Switch(checked = keepAliveState != KeepAliveStatus.STOPPED, onCheckedChange = {
                                if (it) keepAliveEngine.start(scope, KeepAliveProfile.MAX) else keepAliveEngine.stop()
                            })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { radioError = !openRadioInfo(context) }, modifier = Modifier.weight(1f)) { Text("📡 Открыть настройки сети") }
                        }
                        Text("Найдите раздел «Мобильная сеть» → «Предпочтительный тип сети». Названия на XOS могут отличаться; если откроются настройки роуминга, ничего там не переключайте.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showTestingGuide = true }, modifier = Modifier.fillMaxWidth()) { Text("О сервисном меню телефона") }
                        if (radioError) Text("Не удалось открыть настройки; откройте настройки мобильной сети вручную.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (dialError) Text("Не удалось открыть приложение «Телефон»; откройте его вручную.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // DNS
            if (section == ToolsSection.DNS) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Бенчмарк DNS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Пять запросов example.com по обычному UDP/53 без шифрования; для медианы нужно минимум 3 корректных ответа. Повторные ответы могут идти из кэша, а сеть может перехватывать DNS: результат не подтверждает личность сервера. Это не проверка или включение Private DNS (DoT).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        dnsList.forEach { d ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Text(if (selectedDns?.name == d.name) "★  " else "    ", color = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(d.name)
                                    Text(d.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (d.ms >= 0) "${d.ms} мс" else "—", color = if (d.ms in 0..14) AccentGreen else if (d.ms >= 0) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                                    d.answered?.let { Text("$it/5 ответов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        if (selectedDns != null) Text("★ Наименьшая медиана в этом замере; DNS не подключён автоматически.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (dnsError) Text("Не удалось выполнить замер DNS.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (dnsTried && !benching && dnsList.all { it.ms < 0 } && selectedDns == null) {
                            Text("Нет результатов или замер отменён. Возможные причины: нет мобильной сети, заряд ниже 20%, UDP/53 заблокирован, сервер недоступен или ответ некорректен.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                benching = true
                                dnsError = false
                                dnsTried = true
                                dnsList = DnsCatalog.servers
                                selectedDns = null
                                benchJob = scope.launch {
                                    try {
                                        dnsList = bench.benchmarkAll()
                                        selectedDns = dnsList.firstOrNull { it.ms >= 0 }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        dnsError = true
                                    } finally {
                                        benching = false
                                    }
                                }
                            },
                            enabled = !benching,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (benching) "⏳ Замеряю…" else "🔎 Сравнить DNS по UDP") }
                    }
                }
            }
            if (section == ToolsSection.TESTS) item { NetworkConditionsCard(networkConditions) { networkConditions = NetworkConditionReader.read(context) } }
            if (section == ToolsSection.DNS) item { PrivateDnsCard() }
            // Спидтест
            if (section == ToolsSection.TESTS) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Спидтест", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            speed?.downloadMbps?.let { "%.1f Мбит/с".format(it) } ?: "Нет данных",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (speed?.downloadMbps != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Скачивание · средняя скорость одного соединения", style = MaterialTheme.typography.bodySmall)
                        speed?.let { result ->
                            Text("Отдача: ${result.uploadMbps?.let { "%.1f Мбит/с".format(it) } ?: "нет данных"}", style = MaterialTheme.typography.bodyMedium, color = if (result.uploadMbps != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            val message = when (result.status) {
                                SpeedStatus.NO_CELLULAR -> "Нет активного мобильного интернета."
                                SpeedStatus.NETWORK_CHANGED -> "Мобильная сеть изменилась или пропала; результат сброшен."
                                SpeedStatus.LOW_BATTERY -> "Тест остановлен: заряд ниже 20% или недоступен."
                                SpeedStatus.COMPLETED -> if (result.downloadMbps == null || result.uploadMbps == null) "Не все замеры удались: сервер недоступен, ответ неполный или истекло время." else "Скорость зависит от сервера, сигнала и загрузки сети."
                            }
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (speedError) Text("Не удалось выполнить тест. Попробуйте позже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(if (details) "Сервер Cloudflare: до 10 МБ скачивания и 5 МБ отдачи плюс служебный трафик. Тест расходует мобильный интернет; соединение и ответ сервера входят во время замера. При сворачивании тест отменяется." else "До 15 МБ данных плюс служебный трафик. Тест останавливается при сворачивании.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                speedTesting = true
                                speed?.takeIf { result ->
                                    result.status == SpeedStatus.COMPLETED &&
                                        listOf(result.downloadMbps, result.uploadMbps).any { it != null && it.isFinite() && it > 0.0 }
                                }?.let { previousSpeed = it }
                                speed = null
                                speedError = false
                                speedCancelled = false
                                speedJob = scope.launch {
                                    try {
                                        speed = speedTester.test()
                                    } catch (e: CancellationException) {
                                        speedCancelled = true
                                        throw e
                                    } catch (_: Exception) {
                                        speedError = true
                                    } finally {
                                        speedTesting = false
                                    }
                                }
                            },
                            enabled = !speedTesting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (speedTesting) "⏳ Тестирую…" else "▶ Запустить спидтест") }
                        if (speedTesting) {
                            Text("Выполняем замер…", style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { speedJob?.cancel() }) { Text("Остановить спидтест") }
                        }
                        if (speedCancelled && !speedTesting) Text("Спидтест остановлен.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (details) speed?.downloadLatency?.let { latency ->
                            SpeedLoadedLatency("СКАЧИВАНИИ", "скачивании", latency)
                        }
                        if (details) speed?.uploadLatency?.let { latency ->
                            SpeedLoadedLatency("ОТДАЧЕ", "отдаче", latency)
                        }
                        if (details) Text("TCP-соединения к 1.1.1.1:443 до и во время каждого обмена с Cloudflare. При отдаче учитывается запись в поток, не подтверждение доставки. Это не ICMP-пинг; короткий тест может не насытить канал, а другие приложения и маршруты влияют на результат. Это не диагноз буферблата.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (details) speed?.let { result -> SpeedHints(result) }
                        val before = previousSpeed
                        val after = speed
                        if (details && before != null && after != null) {
                            SpeedComparison(before, after, maxOf(speedComparisonClock, SystemClock.elapsedRealtime()), onClear = { previousSpeed = null })
                        }
                    }
                }
            }
            if (section == ToolsSection.TESTS && details) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Своя цель", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Укажите IP-адрес или домен и открытый TCP-порт. Адрес без https:// и пути.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = customHost, onValueChange = { customHost = it.take(253); customResult = null; customError = null },
                            label = { Text("IP или домен") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            isError = customError != null, enabled = !customTesting, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customPort, onValueChange = { customPort = it.take(5); customResult = null; customError = null },
                            label = { Text("TCP-порт") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = customError != null, enabled = !customTesting, modifier = Modifier.fillMaxWidth()
                        )
                        if (customError != null) Text(customError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            val target = CustomTarget.parse(customHost, customPort)
                            if (target == null) {
                                customResult = null
                                customError = "Проверьте IP или домен и порт от 1 до 65535."
                            } else {
                                customError = null
                                customResult = null
                                customTesting = true
                                customJob = scope.launch {
                                    try { customResult = pingTester.test(target) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { customError = "Не удалось выполнить проверку. Попробуйте ещё раз." }
                                    finally { customTesting = false }
                                }
                            }
                        }, enabled = !customTesting && !pingTesting, modifier = Modifier.fillMaxWidth()) {
                            Text(if (customTesting) "⏳ Проверяю…" else "▶ Проверить свою цель")
                        }
                        customResult?.let { PingDetails(it) }
                    }
                }
            }
            // Пинг игр
            if (section == ToolsSection.TESTS && details) item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("TCP-задержка сайтов игр и сервисов (не игровых серверов)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        PingCatalog.targets.forEach { t ->
                            val r = pingResults[t.name]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name)
                                    Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        r?.takeIf { it.avgMs >= 0 }?.let { "${it.avgMs} мс" } ?: "—",
                                        color = when {
                                            r == null || r.avgMs < 0 -> Color.Gray
                                            r.avgMs <= 50 -> AccentGreen
                                            r.avgMs <= 100 -> AccentCyan
                                            else -> AccentRed
                                        }
                                    )
                                    if (r != null) Text(probeShortDescription(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (pingError) Text("Не удалось выполнить проверку. Попробуйте ещё раз.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                pingTesting = true
                                pingError = false
                                pingResults = emptyMap()
                                pingJob = scope.launch {
                                    try { pingResults = pingTester.testAll(PingCatalog.targets) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { pingError = true }
                                    finally { pingTesting = false }
                                }
                            },
                            enabled = !pingTesting && !customTesting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (pingTesting) "⏳ Тестирую…" else "▶ Проверить сайты") }
                        Text("Неудачные TCP-подключения не равны потере пакетов. Данные относятся к сайтам, а не к игровым серверам.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // USB-модем
            if (section == ToolsSection.USB) item {
                val profile = CarrierProfiles.find("25002")
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("USB-модем для ПК (Windows 10/11)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Состояние USB-модема не определяется", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Включите USB-модем в настройках Android. Проверьте соединение телефона и ПК.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Пример DNS для ПК: 77.88.8.8 (резерв 1.1.1.1); лучший адрес зависит от сети.")
                        Text("Пример MTU: ${profile?.recommendedMtu ?: 1440}; подходящее значение зависит от сети.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Настройка Windows: Параметры → Сеть → Ethernet → Remote NDIS → DNS вручную 77.88.8.8 / 1.1.1.1. MTU: netsh interface ipv4 set subinterface \"Remote NDIS\" mtu=${profile?.recommendedMtu ?: 1440} store=persistent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Если ПК не получает интернет, проверьте разрешение раздачи в тарифе, USB-подключение и настройки модема телефона. Приложение не изменяет TTL или параметры сети.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedLoadedLatency(title: String, phase: String, latency: LoadedLatency) {
    Spacer(Modifier.height(8.dp))
    Text("ЗАДЕРЖКА ПРИ $title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val delta = latency.differenceMs
    if (delta != null) {
        val deltaText = if (delta > 0) "+$delta" else delta.toString()
        val color = when {
            delta > 100 -> MaterialTheme.colorScheme.error
            delta > 30 -> MaterialTheme.colorScheme.primary
            else -> AccentGreen
        }
        Text("${latency.idleMedianMs} → ${latency.loadedMedianMs} мс · $deltaText мс", style = MaterialTheme.typography.titleMedium, color = color)
        Text(if (delta > 100) "Сильный рост задержки в этом замере." else if (delta > 30) "Задержка заметно выросла в этом замере." else "Значительного роста задержки в этом замере не видно.", style = MaterialTheme.typography.bodySmall)
        Text("Успешных замеров: до ${latency.idleSuccesses}, при $phase ${latency.loadedSuccesses}. Пороги условные.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Недостаточно успешных замеров: до ${latency.idleSuccesses}, при $phase ${latency.loadedSuccesses}; нужно не менее 3 в каждом режиме.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpeedHints(result: SpeedResult) {
    val hints = SpeedDiagnostics.hints(result)
    if (hints.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text("ПОДСКАЗКИ ПО ЗАМЕРУ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    hints.forEach { hint ->
        val text = when (hint) {
            SpeedHint.INCOMPLETE -> "Не все направления измерены; неизвестное не означает нулевую скорость."
            SpeedHint.SLOW_DOWNLOAD -> "Скачивание ниже условных 2 Мбит/с: большие файлы могут загружаться долго."
            SpeedHint.SLOW_UPLOAD -> "Отдача ниже условного 1 Мбит/с: отправка больших файлов может занять больше времени."
            SpeedHint.HIGH_IDLE_TCP -> "TCP-подключение к 1.1.1.1 до скачивания заняло более 150 мс. Это не пинг игрового сервера."
            SpeedHint.LOADED_TCP_INCREASE -> "При скачивании TCP вырос более чем на 100 мс. Попробуйте приостановить крупные скачивания на телефоне и подключённых устройствах и повторить замер. В разделе «Трафик» можно посмотреть историю по скачиванию."
            SpeedHint.UPLOAD_LOADED_TCP_INCREASE -> "При отдаче TCP вырос более чем на 100 мс. Попробуйте приостановить отправку файлов, резервное копирование и обновления на телефоне и подключённых устройствах, затем повторите замер. В разделе «Трафик» можно посмотреть историю по отдаче."
            SpeedHint.NO_STRONG_INDICATION -> "Доступные цифры не вышли за условные пороги этих подсказок; качество связи это не гарантирует."
        }
        Text("• $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Пороги ориентировочные. История трафика не показывает активность сейчас; тест не определяет причину неполадок или работу конкретной игры. Повторный замер снова расходует трафик.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SpeedComparison(first: SpeedResult, second: SpeedResult, nowElapsedMs: Long, onClear: () -> Unit) {
    val status = SpeedDiagnostics.comparisonStatus(first, second, nowElapsedMs)
    val delta = SpeedDiagnostics.difference(first, second, nowElapsedMs)
    Spacer(Modifier.height(8.dp))
    Text("ДВА ЗАМЕРА", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    fun formatted(value: Double?) = value?.takeIf { it.isFinite() && it > 0.0 }?.let { "%.1f".format(it) } ?: "—"
    fun change(value: Double?) = when {
        value == null -> "разница недоступна"
        kotlin.math.abs(value) < 0.05 -> "разница ≈0,0 Мбит/с"
        value > 0 -> "разница +%.1f Мбит/с".format(value)
        else -> "разница %.1f Мбит/с".format(value)
    }
    Text("Скачивание: ${formatted(first.downloadMbps)} → ${formatted(second.downloadMbps)} Мбит/с · ${change(delta?.downloadMbps)}", style = MaterialTheme.typography.bodySmall)
    Text("Отдача: ${formatted(first.uploadMbps)} → ${formatted(second.uploadMbps)} Мбит/с · ${change(delta?.uploadMbps)}", style = MaterialTheme.typography.bodySmall)
    val reason = when (status) {
        SpeedComparisonStatus.READY -> if (delta == null) "Нет общего успешно измеренного направления." else null
        SpeedComparisonStatus.INCOMPLETE -> "Один из тестов прерван; разницу не показываем."
        SpeedComparisonStatus.MISSING_METADATA -> "Нет данных о сети или времени; разницу не показываем."
        SpeedComparisonStatus.DIFFERENT_NETWORK -> "Замеры выполнены в разных мобильных сетях; разницу не показываем."
        SpeedComparisonStatus.EXPIRED -> "Первому замеру не менее 10 минут; разницу не показываем."
        SpeedComparisonStatus.INVALID_TIME -> "Порядок времени замеров недостоверен; разницу не показываем."
    }
    if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Сравнение доступно менее 10 минут после первого замера при совпадении сети. Это не гарантирует непрерывность связи или одинаковые условия между запусками и не доказывает причину изменения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onClear) { Text("Скрыть сравнение") }
}

@Composable
private fun SignalHistoryCard(points: List<SignalObservation>, nowMs: Long) {
    val valid = points.mapNotNull { it.rsrp }
    val newest = points.lastOrNull()
    val ageSeconds = newest?.let { ((nowMs - it.atMs).coerceAtLeast(0) / 1000) }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ДИНАМИКА СИГНАЛА", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (valid.isEmpty()) {
                Text("Ожидаем данные LTE или 5G от телефона.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("За видимый период: ${valid.minOrNull()}…${valid.maxOrNull()} дБм", style = MaterialTheme.typography.titleSmall, color = lineColor)
                Text("Последний опрос: ${ageSeconds ?: 0} с назад${if (newest?.rsrp == null) " · нет данных" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val earliest = points.firstOrNull()?.atMs ?: nowMs
                    val span = (nowMs - earliest).coerceIn(30_000L, SignalHistory.WINDOW_MS).toFloat()
                    fun x(point: SignalObservation) = (size.width * (1f - (nowMs - point.atMs).toFloat() / span)).coerceIn(0f, size.width)
                    fun y(value: Int) = size.height * ((-30f - value) / 130f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                    drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                    points.zipWithNext().forEach { (first, second) ->
                        if (SignalHistory.canConnect(first, second)) {
                            drawLine(lineColor, Offset(x(first), y(first.rsrp!!)), Offset(x(second), y(second.rsrp!!)), 3f)
                        }
                    }
                    points.forEach { point ->
                        point.rsrp?.let { drawCircle(lineColor, radius = 3f, center = Offset(x(point), y(it))) }
                    }
                }
                Text("Прошлое ← → сейчас · сверху −30, снизу −160 дБм. Разрывы — нет данных, большой перерыв или смена 4G/5G.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Снимки опроса примерно раз в 3 с, до 2 минут и только пока открыт этот раздел. Одна и та же запись модема может повторяться; устаревшие данные не рисуются, а если время модема неизвестно — свежесть не гарантирована. При выходе история очищается.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Test-only entry point so instrumentation can render fixed capability sets. */
@Composable
internal fun NetworkConditionsCardForTest(conditions: NetworkConditions, onRefresh: () -> Unit) =
    NetworkConditionsCard(conditions, onRefresh)

@Composable
private fun NetworkConditionsCard(conditions: NetworkConditions?, onRefresh: () -> Unit) {
    val verdict = conditions?.let { NetworkConditionPolicy.verdict(it) }
    DoxaizCard(accent = when (verdict?.level) {
        NetworkConditionLevel.GOOD -> AccentGreen
        NetworkConditionLevel.CAUTION -> MaterialTheme.colorScheme.primary
        NetworkConditionLevel.UNUSABLE -> MaterialTheme.colorScheme.error
        null -> null
    }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("УСЛОВИЯ СЕТИ ПО ДАННЫМ ANDROID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(
                    when (verdict?.level) {
                        NetworkConditionLevel.GOOD -> "Норма"
                        NetworkConditionLevel.CAUTION -> "Осторожно"
                        NetworkConditionLevel.UNUSABLE -> "Непригодна"
                        null -> "Нет сети"
                    },
                    when (verdict?.level) {
                        NetworkConditionLevel.GOOD -> PillTone.GOOD
                        NetworkConditionLevel.CAUTION -> PillTone.WARNING
                        NetworkConditionLevel.UNUSABLE -> PillTone.BAD
                        null -> PillTone.NEUTRAL
                    }
                )
            }
            if (verdict == null) {
                Text("Нет активной мобильной сети: Android не сообщает условия.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(verdict.headline, style = MaterialTheme.typography.bodyMedium,
                    color = if (verdict.level == NetworkConditionLevel.GOOD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                verdict.details.forEach { Text("• " + it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("Это флаги системы на момент последнего обновления, а не замер скорости, задержки или нагрузки от других приложений. Условия могут измениться в любой момент; приложение не переключает сеть и не меняет настройки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Обновить условия сети") }
    }
}

@Composable
private fun BatteryStatusCard(signal: SignalState, nowMs: Long) {
    val fresh = BatteryHealth.isFresh(signal.batteryObservedAtMs, 0L, nowMs)
    val battery = signal.battery.takeIf { fresh }
    val percent = battery?.percent
    val temperature = battery?.temperatureTenths
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("БАТАРЕЯ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Заряд: ${percent?.let { "$it%" } ?: "нет свежих данных"}",
                color = when { percent == null -> MaterialTheme.colorScheme.onSurfaceVariant; percent < 20 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary },
                style = MaterialTheme.typography.bodyMedium)
            Text("При заряде ниже 20% или недоступном уровне сетевые проверки приостанавливаются или не запускаются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Температура: ${temperature?.let { "%.1f °C".format(it / 10.0) } ?: "нет свежих данных"}",
                color = when { temperature == null -> MaterialTheme.colorScheme.onSurfaceVariant; temperature > 420 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary },
                style = MaterialTheme.typography.bodyMedium)
            Text("Предупреждение появляется при температуре батареи выше 42 °C. Это показание Android, а не температура модема.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CurrentCellDetails(signal: SignalState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Текущая сота", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!signal.hasPermission) {
            Text("Разрешите доступ к местоположению, чтобы увидеть данные соты.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (signal.networkType == "—") {
            Text("Нет доступных свежих данных 4G или 5G.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("${signal.operator} · ${signal.networkType}", style = MaterialTheme.typography.bodyMedium)
            val source = when (signal.bandSource) {
                RadioChannels.BandSource.DEVICE -> "данные телефона"
                RadioChannels.BandSource.CHANNEL -> "расчёт по LTE-каналу"
                null -> "нет данных"
            }
            Text("Диапазон: ${signal.band} · $source", style = MaterialTheme.typography.bodySmall)
            val frequency = signal.frequencyKhz?.let { "%.3f МГц".format(java.util.Locale.getDefault(), it / 1000.0) } ?: "нет данных"
            Text("Опорная частота: $frequency${if (signal.frequencyKhz != null) " · расчёт по каналу" else ""}", style = MaterialTheme.typography.bodySmall)
            val channelName = if (signal.is5g) "NR-ARFCN" else "EARFCN"
            Text("$channelName: ${signal.channel?.toString() ?: "—"} · PCI: ${signal.pci?.toString() ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("RSRQ: ${signal.rsrq?.let { "$it dB" } ?: "—"} · качество опорного сигнала", style = MaterialTheme.typography.bodySmall)
            Text("SINR: ${signal.sinr?.let { "$it dB" } ?: "—"} · отношение сигнала к помехам", style = MaterialTheme.typography.bodySmall)
            if (signal.rsrq == null || signal.sinr == null || signal.pci == null) {
                Text("Прочерк: телефон не сообщил данные или вернул значение вне диапазона Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (signal.bandHint.isNotEmpty()) Text(signal.bandHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivateDnsCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var host by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    val selected = PrivateDns.providers.firstOrNull { it.hostname == PrivateDns.hostname(host) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ПЕРСОНАЛЬНЫЙ DNS ANDROID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Без VPN: выберите провайдера или введите своё имя сервера DNS-over-TLS. Изменение выполняется вами в настройках Android, приложение DNS не подключает и не проверяет.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrivateDns.providers.forEach { provider ->
                    FilterChip(
                        selected = selected == provider,
                        onClick = { host = provider.hostname; error = ""; copied = false },
                        label = { Text(provider.name) }
                    )
                }
            }
            if (selected != null) Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.take(253); error = ""; copied = false },
                label = { Text("Имя сервера DoT") },
                placeholder = { Text("Например, dns.google") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, isError = error.isNotEmpty(), modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val normalized = PrivateDns.hostname(host)
                if (normalized == null) {
                    error = "Нужно имя хоста DoT без IP-адреса, порта, tls:// или https://."
                    copied = false
                } else {
                    clipboard.setText(AnnotatedString(normalized))
                    host = normalized
                    error = ""
                    copied = true
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Скопировать имя") }
            if (copied) Text("Имя скопировано. Вставьте его в системных настройках и сохраните.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { settingsError = !openNetworkSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть настройки сети")
            }
            if (settingsError) Text("Не удалось открыть настройки. Откройте их вручную.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("В настройках найдите «Персональный/Частный DNS» → имя хоста провайдера → вставьте имя → Сохранить. Пункты в XOS могут называться иначе; используйте поиск настроек.", style = MaterialTheme.typography.bodySmall)
            Text("Важно: системная настройка действует на всех сетях, не только мобильной. Запомните прежний режим и имя. Если интернет перестал работать, верните прежнюю настройку или выберите «Автоматически». Выбранный провайдер видит DNS-запросы. Некоторые приложения и VPN могут использовать собственный DNS; фильтрация не блокирует всю рекламу или трекеры.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openNetworkSettings(context: android.content.Context): Boolean {
    for (action in listOf(android.provider.Settings.ACTION_WIRELESS_SETTINGS, android.provider.Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
        } catch (_: SecurityException) { }
    }
    return false
}

private fun probeShortDescription(result: PingResult): String = when (result.status) {
    PingStatus.LOW_BATTERY -> "Пауза: заряд ниже 20% или неизвестен"
    PingStatus.NO_CELLULAR -> "Нет мобильной сети"
    PingStatus.NETWORK_CHANGED -> "Сеть изменилась"
    PingStatus.NETWORK_CHECK_UNAVAILABLE -> "Проверка сети недоступна"
    PingStatus.RESOLUTION_FAILED -> "Адрес не найден"
    PingStatus.COMPLETED -> {
        val failed = result.attemptsMade - result.successfulAttempts
        val jitter = if (result.jitterMs >= 0) "разброс ${result.jitterMs} мс · " else ""
        "$jitter$failed/${result.attemptsMade} неудач"
    }
}

@Composable
private fun PingDetails(result: PingResult) {
    when (result.status) {
        PingStatus.LOW_BATTERY -> Text("Заряд ниже 20% или данные батареи недоступны. Замер остановлен.", color = MaterialTheme.colorScheme.error)
        PingStatus.NO_CELLULAR -> Text("Мобильное подключение не найдено. Проверьте сеть и повторите замер.", color = MaterialTheme.colorScheme.error)
        PingStatus.NETWORK_CHANGED -> Text("Сеть изменилась во время замера. Повторите проверку.", color = MaterialTheme.colorScheme.error)
        PingStatus.NETWORK_CHECK_UNAVAILABLE -> Text("Не удалось отследить мобильную сеть. Повторите проверку.", color = MaterialTheme.colorScheme.error)
        PingStatus.RESOLUTION_FAILED -> Text("Не удалось определить IP-адрес цели через мобильную сеть. Проверьте адрес и повторите попытку.", color = MaterialTheme.colorScheme.error)
        PingStatus.COMPLETED -> {
            val failed = result.attemptsMade - result.successfulAttempts
            val latencyColor = when {
                result.avgMs < 0 -> MaterialTheme.colorScheme.error
                result.avgMs <= 50 -> AccentGreen
                result.avgMs <= 100 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Text(if (result.avgMs >= 0) "Средняя TCP-задержка: ${result.avgMs} мс" else "Подключиться не удалось", style = MaterialTheme.typography.titleMedium, color = latencyColor)
            if (result.medianMs != null && result.maximumMs != null) {
                Text("Медиана: ${result.medianMs} мс · максимум успешных: ${result.maximumMs} мс", style = MaterialTheme.typography.bodySmall)
                Text("Только успешные TCP-подключения в короткой серии; максимум не учитывает неудачи и не является пингом игры.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Медиана и максимум: нужно не менее 3 успешных подключений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (result.jitterMs >= 0) "Средняя разница соседних успешных подключений: ${result.jitterMs} мс" else "Разница подключений: недостаточно успешных попыток", style = MaterialTheme.typography.bodySmall)
            Text("Неудачные подключения: $failed из ${result.attemptsMade} (${result.lossPercent}%). Это не потери пакетов.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failed > 0) Text("Проверьте адрес и открытый TCP-порт: отказ может быть связан с сервером, а не качеством сети.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RadarCanvas(rsrp: Int, soundOn: Boolean = true, vibOn: Boolean = false) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Плавная развёртка луча через infiniteTransition (60 fps, без ручных delay-циклов)
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    // Расходящиеся пульс-кольца (3 волны со сдвигом фазы)
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    // Звуковой писк: чем лучше сигнал — тем выше тон и чаще бип
    LaunchedEffect(rsrp, soundOn, vibOn) {
        while (true) {
            val strength = ((rsrp + 120f) / 50f).coerceIn(0f, 1f)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (soundOn && rsrp in -160..-30) Beeper.beep(strength)
                if (vibOn && rsrp in -75..-30) Beeper.vibrate(context) // вибрация на максимуме
            }
            kotlinx.coroutines.delay(500)
        }
    }
    // Цвет центрального маркера зависит от силы сигнала (RSRP)
    val centerColor = when {
        rsrp !in -160..-30 -> Color.Gray
        rsrp >= -80 -> AccentGreen
        rsrp >= -95 -> AccentCyan
        rsrp >= -105 -> Color(0xFFFFB020)
        else -> Color(0xFFFF4D6D)
    }
    Canvas(Modifier.size(200.dp)) {
        val c = center
        val maxR = size.minDimension / 2
        for (i in 1..4) {
            drawCircle(Color(0xFF00E5FF).copy(alpha = 0.25f), radius = maxR * i / 4, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        for (a in 0 until 360 step 45) {
            val rad = Math.toRadians(a.toDouble())
            drawLine(Color(0xFF00E5FF).copy(alpha = 0.12f), c, Offset(c.x + maxR * cos(rad).toFloat(), c.y + maxR * sin(rad).toFloat()))
        }
        // Пульс-кольца: расширяются от центра и затухают
        for (k in 0..2) {
            val phase = ((pulse + k / 3f) % 1f)
            drawCircle(
                centerColor.copy(alpha = (1f - phase) * 0.35f),
                radius = maxR * phase,
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
            )
        }
        drawArc(
            brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.35f), Color.Transparent)
            ),
            startAngle = sweep - 60f, sweepAngle = 60f, useCenter = true,
            topLeft = Offset(c.x - maxR, c.y - maxR), size = Size(maxR * 2, maxR * 2)
        )
        // Свечение центрального маркера
        drawCircle(centerColor.copy(alpha = 0.25f), radius = 14f, center = c)
        drawCircle(centerColor, radius = 5f, center = c)
    }
}

private fun openRadioInfo(context: android.content.Context): Boolean {
    val actions = listOf(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS, android.provider.Settings.ACTION_WIRELESS_SETTINGS, android.provider.Settings.ACTION_SETTINGS)
    for (action in actions) {
        try {
            context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
        } catch (_: SecurityException) { }
    }
    return false
}

/** Opens the dialer without pre-filling or executing a service code. */
private fun openDialer(context: android.content.Context): Boolean = try {
    context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: android.content.ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
