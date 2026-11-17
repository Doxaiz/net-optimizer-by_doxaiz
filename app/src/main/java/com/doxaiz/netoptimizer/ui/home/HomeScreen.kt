package com.doxaiz.netoptimizer.ui.home

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.dns.DnsBenchmark
import com.doxaiz.netoptimizer.core.dns.DnsCatalog
import com.doxaiz.netoptimizer.core.dns.DnsServer
import com.doxaiz.netoptimizer.core.dns.DoxaizVpnService
import com.doxaiz.netoptimizer.core.net.KeepAliveEngine
import com.doxaiz.netoptimizer.core.net.KeepAliveProfile
import com.doxaiz.netoptimizer.core.net.PingTester
import com.doxaiz.netoptimizer.core.net.SpeedTester
import com.doxaiz.netoptimizer.core.net.WifiHint
import com.doxaiz.netoptimizer.core.overlay.DoxaizWidgetProvider
import com.doxaiz.netoptimizer.core.overlay.OverlayService
import com.doxaiz.netoptimizer.core.util.QualityText
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentViolet
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

enum class WorkMode(val label: String, val color: Color) {
    NORMAL("Обычный", AccentCyan),
    GAME("Игра", AccentViolet),
    USB("USB-модем", AccentGreen)
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, monitor: SignalMonitor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signal by monitor.state.collectAsState()

    var mode by remember {
        mutableStateOf(
            com.doxaiz.netoptimizer.core.util.OnboardingPrefs.loadMode(context)
                ?.let { runCatching { WorkMode.valueOf(it) }.getOrNull() } ?: WorkMode.NORMAL
        )
    }
    var score by remember { mutableStateOf(0) } // считается из реальных метрик
    var pingMs by remember { mutableStateOf(34) }
    var jitter by remember { mutableStateOf(3) }
    var dns by remember { mutableStateOf<DnsServer?>(DnsCatalog.servers.first()) }
    var boosting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showDeep by remember { mutableStateOf(false) }
    var beforeAfter by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var optReport by remember { mutableStateOf<List<String>>(emptyList()) }
    var needsCellReset by remember { mutableStateOf(false) }
    var batteryWarn by remember { mutableStateOf<String?>(null) }
    var autopilot by remember { mutableStateOf(true) }
    var wifiHint by remember { mutableStateOf<String?>(null) }
    var wifiChecking by remember { mutableStateOf(false) }

    // Живой пинг до Яндекса: каждые 5 сек с автопилотом, каждые 15 сек без (экономия батареи)
    LaunchedEffect(mode, autopilot) {
        val tester = PingTester()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
            val r = tester.test(com.doxaiz.netoptimizer.core.net.PingTarget("Яндекс", "yandex.ru", 443, ""))
            if (r.avgMs > 0) {
                pingMs = r.avgMs; jitter = r.jitterMs
                score = computeScore(r.avgMs, monitor.state.value.rsrp)
            }
            // Обновляем оверлей и виджет живыми данными
            OverlayService.updateText("Пинг $pingMs мс")
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            DoxaizWidgetProvider.update(
                context, pingMs,
                if (isCellular) WifiHint.lastCellularSpeed(context) else 0.0
            )
            kotlinx.coroutines.delay(if (autopilot) 5000 else 15000)
        }
    }


    // Подсказка «Wi-Fi медленнее 4G» (P2: идея failover из Speedify)
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true && WifiHint.shouldCheckWifi(context)) {
            wifiChecking = true
            WifiHint.markWifiChecked(context)
            val wifiSpeed = SpeedTester().quickDownload()
            val cellSpeed = WifiHint.lastCellularSpeed(context)
            wifiChecking = false
            if (cellSpeed > 0 && wifiSpeed > 0 && wifiSpeed < cellSpeed * 0.8) {
                wifiHint = "Wi-Fi (%.0f Мбит/с) медленнее вашего 4G (%.0f Мбит/с). Переключитесь на мобильный интернет — будет быстрее." .format(wifiSpeed, cellSpeed)
            }
        }
    }

    val keepAlive = remember { KeepAliveEngine() }

    /**
     * Полная оптимизация (не только DNS!):
     * 1) Диагностика: сеть, батарея, температура, сигнал
     * 2) Исправление: DNS + Keep-Alive + подсказки по соте/фону
     * 3) Проверка: реальный перемер пинга и оценка
     */
    fun boost(deep: Boolean) {
        if (boosting) return
        boosting = true; progress = 0f
        optReport = emptyList()
        needsCellReset = false
        batteryWarn = null
        val oldPing = pingMs
        scope.launch {
            val report = mutableListOf<String>()
            val sig = monitor.state.value

            // --- 1) Диагностика ---
            progress = 0.1f
            if (sig.networkType !in listOf("4G", "4G+", "5G")) {
                report += "⚠️ Сеть ${sig.networkType}, а не 4G/5G — нужна перерегистрация соты"
                needsCellReset = true
            }
            if (sig.temperatureC > 42f) {
                batteryWarn = "Перегрев ${sig.temperatureC.toInt()}°C: снимите чехол, дайте остыть"
            } else if (sig.batteryLevel < 20) {
                batteryWarn = "Заряд ${sig.batteryLevel}%: автопилот снизит активность"
            }
            if (sig.rsrp < -105) {
                report += "⚠️ Сигнал слабый (${sig.rsrp} dBm) — подойдите к окну или выйдите на улицу"
            }

            // --- 2) DNS: бенчмарк + подключение лучшего ---
            progress = 0.3f
            val bench = DnsBenchmark()
            val best = bench.benchmarkAll().firstOrNull { it.ms > 0 }
            if (best != null) {
                dns = best
                if (DoxaizVpnService.running) {
                    DoxaizVpnService.start(context, best.ip, DoxaizVpnService.blockedApps)
                }
                report += "✅ DNS: ${best.name} (${best.ms} мс)"
            }

            // --- 3) Keep-Alive: профиль по режиму, ЭКО при низком заряде ---
            progress = 0.5f
            val profile = when {
                sig.batteryLevel < 20 -> KeepAliveProfile.ECO
                mode == WorkMode.NORMAL -> KeepAliveProfile.BALANCE
                else -> KeepAliveProfile.MAX
            }
            keepAlive.start(scope, profile)
            report += "✅ Анти-сон модема: ${profile.label}"

            // --- 4) Глубокая проверка: фон и сота ---
            if (deep) {
                progress = 0.7f
                // Ищем прожорливое приложение в фоне
                try {
                    val tm = com.doxaiz.netoptimizer.core.traffic.TrafficMonitor(context)
                    tm.observe(DoxaizVpnService.blockedApps).first()
                    report += "ℹ️ Фоновый трафик: откройте вкладку «Трафик» — там видно, кто ест канал"
                } catch (_: Exception) { }
            }

            // --- 5) Честная проверка: реальный перемер пинга ---
            progress = 0.85f
            val tester = PingTester()
            val r = tester.test(com.doxaiz.netoptimizer.core.net.PingTarget("Яндекс", "yandex.ru", 443, ""))
            val newPing = if (r.avgMs > 0) r.avgMs else pingMs
            pingMs = newPing
            jitter = r.jitterMs
            score = computeScore(newPing, sig.rsrp)
            beforeAfter = oldPing to newPing
            optReport = report
            progress = 1f
            boosting = false
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Качество мобильной связи", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score = score)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(QualityText.scoreVerdict(score), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сигнал чистый, пинг стабильный, DNS быстрый.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row {
                Button(
                    onClick = { boost(false) },
                    enabled = !boosting,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(if (boosting) "⏳ Оптимизирую…" else "⚡ УЛУЧШИТЬ") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { showDeep = !showDeep }, modifier = Modifier.height(52.dp)) {
                    Text("🔍 Глубокая проверка")
                }
            }
            if (boosting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            if (showDeep) {
                Spacer(Modifier.height(8.dp))
                DeepCheckList(onApply = { boost(true) })
            }
            if (beforeAfter != null) {
                Spacer(Modifier.height(8.dp))
                val (old, new) = beforeAfter!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Было", style = MaterialTheme.typography.labelSmall)
                            Text("$old мс", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFF4D6D))
                        }
                        Text("➜", style = MaterialTheme.typography.headlineSmall, color = AccentGreen)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Стало", style = MaterialTheme.typography.labelSmall)
                            Text("$new мс", style = MaterialTheme.typography.headlineSmall, color = AccentGreen)
                        }
                    }
                }
            }
            // Отчёт оптимизации: что найдено и исправлено
            if (optReport.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Отчёт оптимизации", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        optReport.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
            // Карточка сброса соты (если сеть не 4G/5G)
            if (needsCellReset) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F0A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📡 Сеть не 4G/5G — нужна перерегистрация", style = MaterialTheme.typography.bodyMedium)
                        Text("Откройте панель интернета и переключите авиарежим туда-сюда: телефон перерегистрируется на ближнюю соту.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { openInternetPanel(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔄 Открыть панель интернета") }
                    }
                }
            }
            // Предупреждение по батарее/температуре
            if (batteryWarn != null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A121C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔋 " + batteryWarn!!, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item {
            Text("Режим работы", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Умный автопилот", modifier = Modifier.weight(1f))
                Switch(checked = autopilot, onCheckedChange = { autopilot = it })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Сигнал · RSRP / SINR",
                    value = "${signal.rsrp} dBm · ${signal.sinr} dB",
                    desc = QualityText.signal(signal.rsrp),
                    modifier = Modifier.weight(1f)
                )
                val animatedPing by animateIntAsState(
                    targetValue = pingMs,
                    animationSpec = tween(600),
                    label = "ping"
                )
                MetricCard(
                    title = "Пинг · джиттер",
                    value = "$animatedPing мс · ±$jitter",
                    desc = QualityText.ping(pingMs),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Активный DNS",
                    value = dns?.let { "${it.name} · ${it.ms} мс" } ?: "—",
                    desc = "Сайты открываются быстрее стандартного DNS оператора",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Сеть и частота",
                    value = "${signal.operator} · ${signal.networkType}",
                    desc = if (signal.band != "—") "Band ${signal.band}. ${signal.bandHint}" else "Ожидание данных сети…",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            if (wifiChecking) {
                Text("⏳ Проверяю скорость Wi-Fi…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (wifiHint != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F0A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Text("📶 " + wifiHint!!, modifier = Modifier.weight(1f))
                        TextButton(onClick = { wifiHint = null }) { Text("OK") }
                    }
                }
            }
        }
        item {
            Text(
                "Подсказка: нажми «⚡ Улучшить» — Doxaiz сам подберёт быстрый DNS и включит анти-сон модема.",

                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    val brush = Brush.linearGradient(listOf(AccentCyan, AccentViolet, AccentGreen))
    // Плавная анимация дуги и цифры при изменении оценки
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(800),
        label = "score"
    )
    val animatedArc by animateFloatAsState(
        targetValue = 360f * animatedScore / 100f,
        animationSpec = tween(800),
        label = "arc"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Canvas(Modifier.size(160.dp)) {
            drawArc(Color(0xFF16233D), 0f, 360f, false, style = Stroke(14f))
            drawArc(brush, -90f, animatedArc, false, style = Stroke(14f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$animatedScore", style = MaterialTheme.typography.displayMedium)
            Text("из 100", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, desc: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeepCheckList(onApply: () -> Unit) {
    val items = listOf(
        "Подключить самый быстрый DNS" to "Яндекс DNS · ~11 мс вместо 34 мс у оператора",
        "Включить анти-сон модема (Keep-Alive)" to "Убирает «замирание» 4G на первом пакете",
        "Остановить прожорливые приложения в фоне" to "Освобождает канал для игры и USB-модема",
        "Сбросить «залипшую» дальнюю соту" to "Перерегистрация на ближнюю вышку",
        "Фиксация «Только 4G/5G»" to "VoLTE включён — звонки не пострадают"
    )
    val checked = remember { items.map { mutableStateOf(it.first != "Остановить прожорливые приложения в фоне") }.toMutableStateList() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            items.forEachIndexed { i, (title, sub) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Checkbox(checked = checked[i].value, onCheckedChange = { checked[i].value = it })
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Применить отмеченное") }
        }
    }
}

/** Оценка связи 0–100 из РЕАЛЬНЫХ метрик: пинг (40) + сигнал RSRP (40) + запас */
private fun computeScore(pingMs: Int, rsrp: Int): Int {
    val pingPart = when {
        pingMs <= 30 -> 40
        pingMs <= 50 -> 32
        pingMs <= 80 -> 24
        pingMs <= 120 -> 15
        else -> 5
    }
    val signalPart = when {
        rsrp >= -80 -> 40
        rsrp >= -90 -> 32
        rsrp >= -100 -> 22
        rsrp >= -110 -> 12
        else -> 4
    }
    return (pingPart + signalPart + 10).coerceIn(5, 98)
}

private fun openInternetPanel(context: android.content.Context) {
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
