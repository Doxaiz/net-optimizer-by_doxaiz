package com.doxaiz.netoptimizer.ui.traffic

import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.doxaiz.netoptimizer.core.dns.DoxaizVpnService
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.traffic.AppTraffic
import com.doxaiz.netoptimizer.core.traffic.TrafficMonitor
import com.doxaiz.netoptimizer.ui.theme.AccentAmber
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentViolet

private data class TrafficSample(val speeds: Map<String, Long>)

private val graphColors = listOf(AccentCyan, AccentViolet, AccentGreen)

@Composable
fun TrafficScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val monitor = remember { TrafficMonitor(context) }
    var blocked by remember { mutableStateOf(DoxaizVpnService.blockedApps) }
    var vpnOn by remember { mutableStateOf(DoxaizVpnService.running) }
    var filter by remember { mutableStateOf("all") }
    val scope = rememberCoroutineScope()
    var shizukuReady by remember { mutableStateOf(ShizukuManager.isReady()) }
    val vpnLauncher = rememberLauncherForVpn(context) { vpnOn = true }

    // Корректный запрос VPN-разрешения (документированный способ Android)
    fun ensureVpn(newBlocked: Set<String>) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnLauncher.launch(prepareIntent)
        } else {
            DoxaizVpnService.start(context, DoxaizVpnService.currentDns, newBlocked)
            vpnOn = true
        }
    }

    val apps by monitor.observe(blocked).collectAsState(initial = emptyList())
    val shown = apps.filter {
        when (filter) {
            "active" -> !it.networkBlocked
            "blocked" -> it.networkBlocked
            "white" -> it.whitelisted
            else -> true
        }
    }

    // История для живого графика (последние 30 замеров по 2 сек)
    var history by remember { mutableStateOf<List<TrafficSample>>(emptyList()) }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            val sample = TrafficSample(apps.associate { it.packageName to (it.rxBytesPerSec + it.txBytesPerSec) })
            history = (history + sample).takeLast(30)
        }
    }

    // Алерт: новое приложение начало активно качать (идея из GlassWire)
    var newActivity by remember { mutableStateOf<String?>(null) }
    val prevSpeeds = remember { mutableMapOf<String, Long>() }
    LaunchedEffect(apps) {
        apps.forEach { app ->
            val prev = prevSpeeds[app.packageName] ?: 0L
            val now = app.rxBytesPerSec + app.txBytesPerSec
            if (prev < 50_000 && now > 300_000 && !app.networkBlocked && !app.whitelisted) {
                newActivity = app.label
            }
            prevSpeeds[app.packageName] = now
        }
    }

    val top3 = apps.sortedByDescending { it.rxBytesPerSec + it.txBytesPerSec }.take(3)

    fun applyBlocked(newBlocked: Set<String>) {
        blocked = newBlocked
        DoxaizVpnService.blockedApps = newBlocked
        if (newBlocked.isNotEmpty() && !vpnOn) {
            ensureVpn(newBlocked)
        } else if (vpnOn) {
            // Сервис перестроится и применит новый список блокировок
            DoxaizVpnService.start(context, DoxaizVpnService.currentDns, newBlocked)
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Кто ест мобильный интернет", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (newActivity != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A121C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ «$newActivity» начал активно качать", modifier = Modifier.weight(1f))
                        TextButton(onClick = { newActivity = null }) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            // Живой график топ-3 приложений
            if (top3.isNotEmpty() && history.size > 2) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Живой трафик (топ-3)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        TrafficGraph(history = history, top = top3)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            top3.forEachIndexed { i, app ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                                        Canvas(Modifier.size(10.dp)) { drawCircle(graphColors[i % graphColors.size]) }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(app.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Все", "active" to "Активные", "blocked" to "Заблокированные", "white" to "Белый список")
                    .forEach { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                    }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (vpnOn) "VPN-фаервол активен: заблокированные приложения не получают сеть, остальные работают как обычно."
                else "Чтобы блокировать приложения, разреши VPN в системном диалоге (локально, без серверов).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!shizukuReady) {
                Text(
                    "💡 Со Shizuku кнопка «Стоп» останавливает приложение в 1 тап (без открытия настроек). Настройте Shizuku в разделе «Настройки».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(shown) { app ->
            AppRow(
                app = app,
                onToggleBlock = {
                    if (app.whitelisted) return@AppRow
                    val newSet = if (app.networkBlocked) blocked - app.packageName else blocked + app.packageName
                    applyBlocked(newSet)
                },
                onStop = {
                    if (shizukuReady) {
                        // Shizuku: принудительная остановка в 1 тап
                        scope.launch {
                            val ok = ShizukuManager.forceStopApp(app.packageName)
                            if (!ok) monitor.openAppSettings(app.packageName)
                        }
                    } else {
                        monitor.openAppSettings(app.packageName)
                    }
                }
            )
        }
    }
}

@Composable
private fun TrafficGraph(history: List<TrafficSample>, top: List<AppTraffic>) {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        if (history.size < 2) return@Canvas
        val maxSpeed = history.flatMap { it.speeds.values }.maxOrNull()?.toFloat() ?: 1f
        val safeMax = if (maxSpeed <= 0f) 1f else maxSpeed
        val stepX = size.width / (history.size - 1)
        top.forEachIndexed { idx, app ->
            val color = graphColors[idx % graphColors.size]
            val path = Path()
            history.forEachIndexed { i, sample ->
                val v = (sample.speeds[app.packageName] ?: 0L).toFloat()
                val x = i * stepX
                val y = size.height - (v / safeMax) * (size.height - 10f) - 5f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.5f))
        }
        // Подпись максимума
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, 5f), Offset(size.width, 5f), 1f)
    }
}

@Composable
private fun AppRow(app: AppTraffic, onToggleBlock: () -> Unit, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(app.label + if (app.whitelisted) " 🛡" else "")
                Text(
                    if (app.networkBlocked) "сеть отключена" else "сейчас: %.1f МБ/с".format((app.rxBytesPerSec + app.txBytesPerSec) / 1_000_000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.networkBlocked) AccentGreen else AccentAmber
                )
            }
            TextButton(onClick = onStop) { Text("Стоп") }
            Switch(
                checked = !app.networkBlocked,
                onCheckedChange = { onToggleBlock() },
                enabled = !app.whitelisted
            )
        }
    }
}

@Composable
private fun rememberLauncherForVpn(
    context: android.content.Context,
    onGranted: () -> Unit
) = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
        DoxaizVpnService.start(context, DoxaizVpnService.currentDns, DoxaizVpnService.blockedApps)
        onGranted()
    }
}
