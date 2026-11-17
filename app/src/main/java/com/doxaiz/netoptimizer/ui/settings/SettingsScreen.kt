package com.doxaiz.netoptimizer.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.overlay.OverlayService
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.util.ThemePrefs
import com.doxaiz.netoptimizer.ui.theme.DoxaizThemeKind

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onThemeChange: (DoxaizThemeKind) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(ThemePrefs.load(context)) }
    var hudOn by remember { mutableStateOf(false) }
    var notifOn by remember { mutableStateOf(true) }
    var tileOn by remember { mutableStateOf(true) }
    var widgetOn by remember { mutableStateOf(false) }
    var usbAuto by remember { mutableStateOf(true) }
    var gameAuto by remember { mutableStateOf(true) }
    var shizukuStatus by remember { mutableStateOf(ShizukuManager.status()) }
    val scope = rememberCoroutineScope()

    // Слушаем изменения статуса Shizuku (запуск, разрешение)
    DisposableEffect(Unit) {
        val listener = object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                shizukuStatus = ShizukuManager.status()
                // При получении разрешения — автоматически применяем режим сети
                scope.launch {
                    ShizukuManager.applySavedNetworkMode(context)
                    shizukuStatus = ShizukuManager.status()
                }
            }
        }
        ShizukuManager.addListener(listener)
        onDispose { ShizukuManager.removeListener(listener) }
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                TextButton(onClick = onBack) { Text("← Назад") }
                Text("Настройки Doxaiz", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Оформление", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Поверх системы", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingRow("Тонкая строка пинга", "У верхнего края экрана, не мешает нажатиям", hudOn) {
                        if (it && !android.provider.Settings.canDrawOverlays(context)) {
                            // Нет разрешения — ведём в системные настройки
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            } catch (_: Exception) { }
                        } else {
                            hudOn = it
                            if (it) OverlayService.start(context) else OverlayService.stop(context)
                        }
                    }
                    SettingRow("Тихая строка в шторке", "Без звука и всплывающих баннеров", notifOn) { notifOn = it }
                    SettingRow("Быстрая плитка Android", "Вкл/выкл оптимизации и смена режима в 1 тап", tileOn) { tileOn = it }
                    SettingRow("Виджет на рабочий стол", "Скорость, пинг и кнопка «Улучшить»", widgetOn) { widgetOn = it }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Автосценарии", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingRow("Автозапуск при USB-модеме", "Режим «USB-модем» при подключении к ПК", usbAuto) { usbAuto = it }
                    SettingRow("Автозапуск при игре", "Режим «Игра» при открытии игры из списка", gameAuto) { gameAuto = it }
                    // Карточка статуса Shizuku
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("Shizuku / Root", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (shizukuStatus) {
                                ShizukuManager.Status.READY -> "✅ Готов: режим сети, стоп приложений и перерегистрация соты работают автоматически"
                                ShizukuManager.Status.NO_PERMISSION -> "⚠️ Shizuku запущен — нажмите «Разрешить», и Doxaiz сам применит режим сети"
                                ShizukuManager.Status.NOT_RUNNING -> "⚠️ Установите и запустите Shizuku (инструкция ниже)"
                                ShizukuManager.Status.NOT_INSTALLED -> "Shizuku не установлен. Без него супер-фичи недоступны, но основная оптимизация работает"
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
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val json = """{"app":"Net Optimizer by Doxaiz","version":"1.1.1","theme":"$theme"}"""
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "doxaiz_settings.json")
                        }
                        context.startActivity(Intent.createChooser(intent, "Сохранить настройки"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("💾 Бэкап в файл") }
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/doxaiz/net-optimizer/releases"))
                            )
                        } catch (_: Exception) { }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("⬆ Обновления") }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) { Diagnostics() }
        }
        item {
            Text(
                "Net Optimizer by Doxaiz · v1.1.1 · Open Source\n100% бесплатно, без регистрации и рекламы. Данные — только на телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
