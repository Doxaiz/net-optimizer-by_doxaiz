package com.doxaiz.netoptimizer.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.cellular.CarrierProfiles
import com.doxaiz.netoptimizer.core.cellular.NetworkMode
import com.doxaiz.netoptimizer.core.cellular.NetworkModeApplier
import com.doxaiz.netoptimizer.core.cellular.NetworkModePrefs
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.doxaiz.netoptimizer.core.dns.DnsBenchmark
import com.doxaiz.netoptimizer.core.dns.DoxaizVpnService
import com.doxaiz.netoptimizer.core.net.KeepAliveEngine
import com.doxaiz.netoptimizer.core.net.KeepAliveProfile
import com.doxaiz.netoptimizer.core.util.Beeper
import com.doxaiz.netoptimizer.core.dns.DnsLog
import com.doxaiz.netoptimizer.core.dns.DnsCatalog
import com.doxaiz.netoptimizer.core.dns.DnsServer
import com.doxaiz.netoptimizer.core.net.PingCatalog
import com.doxaiz.netoptimizer.core.net.PingResult
import com.doxaiz.netoptimizer.core.net.PingTarget
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.net.ScenarioTester
import com.doxaiz.netoptimizer.core.net.ScenarioVerdict
import com.doxaiz.netoptimizer.core.net.SpeedResult
import com.doxaiz.netoptimizer.core.net.SpeedTester
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentRed
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, monitor: SignalMonitor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signal by monitor.state.collectAsState()

    var soundOn by remember { mutableStateOf(false) }
    var vibOn by remember { mutableStateOf(false) }
    var netMode by remember { mutableStateOf(NetworkModePrefs.load(context)) }
    var netModeStatus by remember { mutableStateOf("") }
    var cellResetting by remember { mutableStateOf(false) }
    var keepAliveOn by remember { mutableStateOf(false) }
    var dnsList by remember { mutableStateOf(DnsCatalog.servers) }
    var selectedDns by remember { mutableStateOf<DnsServer?>(null) }
    var benching by remember { mutableStateOf(false) }
    var pingResults by remember { mutableStateOf<Map<String, PingResult>>(emptyMap()) }
    var pingTesting by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf<SpeedResult?>(null) }
    var speedTesting by remember { mutableStateOf(false) }
    var scenarios by remember { mutableStateOf<List<ScenarioVerdict>?>(null) }
    val dnsEntries by DnsLog.entries.collectAsState()
    val dnsBlocked by DnsLog.blockedCount.collectAsState()
    var scenarioTesting by remember { mutableStateOf(false) }

    val bench = remember { DnsBenchmark(context) }
    val keepAliveEngine = remember { KeepAliveEngine(context) }
    DisposableEffect(keepAliveEngine) { onDispose { keepAliveEngine.stop() } }
    val pingTester = remember { PingTester(context) }
    val speedTester = remember { SpeedTester(context) }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("ИНСТРУМЕНТЫ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Диагностика сети", style = MaterialTheme.typography.headlineSmall)
            Text("Замеры выполняются только через мобильное подключение.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Радар
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Живой радар сигнала", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    RadarCanvas(rsrp = signal.rsrp, soundOn = soundOn, vibOn = vibOn)
                    Text(if (signal.rsrp in -160..-30) "${signal.rsrp} dBm" else "Нет данных о сигнале", style = MaterialTheme.typography.headlineSmall)
                    if (signal.neighbors.isNotEmpty()) {
                        Text(
                            "Соседние соты: " + signal.neighbors.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Оценка только по уровню сигнала; расположение сот не определяется.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = soundOn, onClick = { soundOn = !soundOn }, label = { Text("🔊 Звук") })
                        FilterChip(selected = vibOn, onClick = { vibOn = !vibOn }, label = { Text("📳 Вибрация") })
                    }
                }
            }
        }
        // Стабилизация 4G/5G
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Стабилизация 4G / 5G", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Принудительный режим сети пока недоступен: проверка VoLTE не реализована. Без VoLTE переключение может помешать звонкам.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Анти-сон модема (Keep-Alive)")
                            Text("Только на этом экране; пауза при заряде ниже 20%. Эффект не гарантирован.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = keepAliveOn, onCheckedChange = {
                            keepAliveOn = it
                            if (it) keepAliveEngine.start(scope, KeepAliveProfile.MAX) else keepAliveEngine.stop()
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openRadioInfo(context) }, modifier = Modifier.weight(1f)) { Text("📡 Открыть меню 4G/5G") }
                    }
                }
            }
        }
        // DNS
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Бенчмарк DNS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Подключение DNS и блокировка трекеров временно отключены: VPN требует доработки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(if (d.ms > 0) "${d.ms} мс" else "—", color = if (d.ms in 1..14) AccentGreen else AccentCyan)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            benching = true
                            scope.launch {
                                dnsList = bench.benchmarkAll()
                                selectedDns = dnsList.firstOrNull { it.ms > 0 }
                                benching = false
                            }
                        },
                        enabled = !benching,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (benching) "⏳ Замеряю…" else "🔎 Найти самый быстрый DNS") }
                }
            }
        }
        // Спидтест
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Спидтест", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        speed?.takeIf { it.downloadMbps > 0 }?.let { "%.1f Мбит/с".format(it.downloadMbps) } ?: "Нет данных",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (speed != null && speed!!.downloadMbps > 0) {
                        Text("Отдача: ${if (speed!!.uploadMbps > 0) "%.1f Мбит/с".format(speed!!.uploadMbps) else "нет данных"} · TCP после теста: ${if (speed!!.bufferbloatMs > 0) "${speed!!.bufferbloatMs} мс" else "нет данных"} (не bufferbloat)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            speedTesting = true
                            scope.launch {
                                val r = speedTester.test()
                                speed = r
                                speedTesting = false
                            }
                        },
                        enabled = !speedTesting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (speedTesting) "⏳ Тестирую…" else "▶ Запустить спидтест") }
                }
            }
        }
        // Пинг игр
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
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
                            Text(
                                r?.takeIf { it.avgMs >= 0 }?.let { "${it.avgMs} мс · ±${it.jitterMs}" } ?: "—",
                                color = when {
                                    r == null || r.avgMs < 0 -> Color.Gray
                                    r.avgMs <= 50 -> AccentGreen
                                    r.avgMs <= 100 -> AccentCyan
                                    else -> AccentRed
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            pingTesting = true
                            scope.launch {
                                pingResults = pingTester.testAll(PingCatalog.targets)
                                pingTesting = false
                            }
                        },
                        enabled = !pingTesting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (pingTesting) "⏳ Тестирую…" else "▶ Запустить тест пинга") }
                }
            }
        }
        // USB-модем
        item {
            val profile = CarrierProfiles.find("25002")
            Card(modifier = Modifier.fillMaxWidth()) {
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
                        "Если ПК не получает интернет: некоторые операторы режут раздачу по TTL. Попробуйте TTL 64/128 (нужен Shizuku/Root) или проверьте, что на тарифе разрешена раздача.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarCanvas(rsrp: Int, soundOn: Boolean = true, vibOn: Boolean = false) {
    val context = LocalContext.current
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
            if (soundOn && rsrp in -160..-30) Beeper.beep(strength)
            if (vibOn && rsrp in -75..-30) Beeper.vibrate(context) // вибрация на максимуме
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

private fun openInternetPanel(context: android.content.Context) {
    // Системная панель интернета: авиарежим в 1 тап = перерегистрация на ближнюю соту
    try {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val panelCls = Class.forName("android.provider.Settings\$Panel")
            val m = panelCls.getMethod("createInternetPanelIntent", android.content.Context::class.java)
            val panel = m.invoke(null, context) as android.content.Intent
            panel.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(panel)
            return
        }
    } catch (_: Exception) { }
    try {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    } catch (_: Exception) { }
}

private fun openRadioInfo(context: android.content.Context) {
    val intents = listOf(
        "com.android.phone.settings.RadioInfo",
        "com.android.settings.RadioInfo"
    )
    for (cls in intents) {
        try {
            val intent = android.content.Intent().apply {
                setClassName("com.android.phone", cls)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
    try {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: Exception) { }
}
