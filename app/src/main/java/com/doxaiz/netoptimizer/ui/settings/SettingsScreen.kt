package com.doxaiz.netoptimizer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.util.ThemePrefs
import com.doxaiz.netoptimizer.core.util.InterfaceMode
import com.doxaiz.netoptimizer.ui.theme.DoxaizThemeKind

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onThemeChange: (DoxaizThemeKind) -> Unit,
    interfaceMode: InterfaceMode,
    onInterfaceModeChange: (InterfaceMode) -> Unit,
    onOpenSetup: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }
        catch (_: Exception) { "—" }
    }
    var theme by remember { mutableStateOf(ThemePrefs.load(context)) }
    var shizukuStatus by remember { mutableStateOf(ShizukuManager.status()) }
    var permissionError by remember { mutableStateOf(false) }

    // Слушаем изменения статуса Shizuku (запуск, разрешение)
    DisposableEffect(Unit) {
        val listener = object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                shizukuStatus = ShizukuManager.status()
            }
        }
        ShizukuManager.addListener(listener)
        onDispose { ShizukuManager.removeListener(listener) }
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("НАСТРОЙКИ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Настройки Doxaiz", style = MaterialTheme.typography.headlineMedium)
            Text("Вид интерфейса, оформление, доступы и диагностика.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Вид приложения", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = interfaceMode == InterfaceMode.SIMPLE, onClick = { onInterfaceModeChange(InterfaceMode.SIMPLE) }, label = { Text("Обычный") })
                        FilterChip(selected = interfaceMode == InterfaceMode.EXPERT, onClick = { onInterfaceModeChange(InterfaceMode.EXPERT) }, label = { Text("Эксперт") })
                    }
                    Text(if (interfaceMode == InterfaceMode.SIMPLE) "Основные действия видны сразу, подробности можно раскрыть на каждом экране." else "Все доступные показатели и инструменты видны сразу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) { Text("Подготовка и доступы") }
                    Text("Можно повторить настройку; активная сессия перейдёт на паузу. Данные истории сохранятся.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Оформление", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoxaizThemeKind.entries.forEach { kind ->
                            FilterChip(
                                selected = theme == kind,
                                onClick = { theme = kind; ThemePrefs.save(context, kind); onThemeChange(kind) },
                                label = {
                                    Text(
                                        when (kind) {
                                            DoxaizThemeKind.AURORA -> "🌌 Aurora"
                                            DoxaizThemeKind.CYBER -> "🌑 Тёмная"
                                            DoxaizThemeKind.LIGHT -> "☀️ Светлая"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        if (interfaceMode == InterfaceMode.EXPERT) item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Фоновые функции", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Автосценарии и оверлей пока недоступны. Приложение не отправляет push-уведомления.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (interfaceMode == InterfaceMode.EXPERT) item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Доступ к системе · Shizuku", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when (shizukuStatus) {
                            ShizukuManager.Status.READY -> "Shizuku подключён. Доступность действий зависит от устройства и разрешений."
                            ShizukuManager.Status.NO_PERMISSION -> "⚠️ Shizuku запущен — нажмите «Разрешить»."
                            ShizukuManager.Status.NOT_RUNNING -> "Shizuku не запущен. Основные функции доступны без него."
                            ShizukuManager.Status.NOT_INSTALLED -> "Shizuku не подключён; доступны только функции без привилегий."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (shizukuStatus) {
                            ShizukuManager.Status.NOT_INSTALLED -> {
                                Button(onClick = { ShizukuManager.openShizukuStore(context) }) { Text("📥 Установить Shizuku") }
                            }
                            ShizukuManager.Status.NOT_RUNNING -> {
                                OutlinedButton(onClick = { ShizukuManager.openShizukuStore(context) }) { Text("📥 Открыть Shizuku") }
                            }
                            ShizukuManager.Status.NO_PERMISSION -> {
                                Button(onClick = { ShizukuManager.requestPermission() }) { Text("🔓 Разрешить Shizuku") }
                            }
                            ShizukuManager.Status.READY -> {
                                Text("✅ Активен", color = com.doxaiz.netoptimizer.ui.theme.AccentGreen)
                            }
                        }
                    }
                    if (shizukuStatus != ShizukuManager.Status.READY) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Как установить: 1) Скачайте Shizuku из Play Market или GitHub. 2) Запустите его и следуйте инструкции (для Infinix XOS — через беспроводную отладку). 3) Вернитесь сюда и нажмите «Разрешить».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (interfaceMode == InterfaceMode.EXPERT) item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Разрешения", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Сигнал и сота требуют доступа к местоположению. Разрешения необязательны и управляются в Android.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        permissionError = try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            false
                        } catch (_: Exception) { true }
                    }) { Text("Настройки приложения") }
                    if (permissionError) Text("Откройте настройки приложения вручную.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Diagnostics() }
        }
        item {
            Text(
                "Net Optimizer by Doxaiz · v$versionName · Open Source\n100% бесплатно, без регистрации и рекламы. История хранится на телефоне; замеры обращаются к сторонним серверам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
