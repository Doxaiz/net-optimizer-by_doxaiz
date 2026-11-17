package com.doxaiz.netoptimizer.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.ui.home.WorkMode
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen

/**
 * Быстрый старт при первом запуске (1 экран, без скучных слайдов):
 * разрешения с человеческим объяснением + выбор стартового режима.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(WorkMode.NORMAL) }
    var permCell by remember { mutableStateOf(hasPerm(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var permPhone by remember { mutableStateOf(hasPerm(context, Manifest.permission.READ_PHONE_STATE)) }
    var permUsage by remember { mutableStateOf(hasUsageAccess(context)) }
    var shizukuStatus by remember { mutableStateOf(ShizukuManager.status()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permCell = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permPhone = result[Manifest.permission.READ_PHONE_STATE] == true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("◈", style = MaterialTheme.typography.displayLarge, color = AccentCyan)
            Text("Net Optimizer", style = MaterialTheme.typography.headlineMedium)
            Text("by Doxaiz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Бесплатно · Без регистрации · Всё локально на устройстве\nУлучшение 4G/5G, скорости и пинга — честно и без «волшебных кнопок»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Разрешения (можно позже)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        title = "Состояние сотовой сети",
                        sub = "Сигнал 4G/5G, частота Band, оператор",
                        granted = permCell && permPhone,
                        onRequest = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE)) }
                    )
                    PermissionRow(
                        title = "Статистика трафика",
                        sub = "Какие приложения едят интернет",
                        granted = permUsage,
                        onRequest = { openUsageAccess(context) }
                    )
                    PermissionRow(
                        title = "Работа в фоне без ограничений",
                        sub = "Чтобы система не выключала оптимизацию",
                        granted = false,
                        onRequest = { requestIgnoreBattery(context) }
                    )
                    PermissionRow(
                        title = "Shizuku (супер-фичи, необязательно)",
                        sub = when (shizukuStatus) {
                            ShizukuManager.Status.READY -> "✅ Готов: режим сети и стоп приложений работают автоматически"
                            ShizukuManager.Status.NO_PERMISSION -> "Shizuku запущен — нажмите «Разрешить»"
                            ShizukuManager.Status.NOT_RUNNING -> "Установите и запустите Shizuku"
                            ShizukuManager.Status.NOT_INSTALLED -> "Не установлен. Без него основная оптимизация всё равно работает"
                        },
                        granted = shizukuStatus == ShizukuManager.Status.READY,
                        onRequest = {
                            when (shizukuStatus) {
                                ShizukuManager.Status.NOT_INSTALLED, ShizukuManager.Status.NOT_RUNNING -> ShizukuManager.openShizukuStore(context)
                                ShizukuManager.Status.NO_PERMISSION -> ShizukuManager.requestPermission()
                                ShizukuManager.Status.READY -> { }
                            }
                        }
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Стартовый режим", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
            }
        }
        item {
            Button(
                onClick = {
                    OnboardingPrefs.saveMode(context, mode.name)
                    OnboardingPrefs.setDone(context)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("🚀 Запустить Doxaiz") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Open Source · 0% рекламы · Данные только на телефоне",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, sub: String, granted: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text("✅", color = AccentGreen)
        } else {
            OutlinedButton(onClick = onRequest) { Text("Разрешить") }
        }
    }
}

private fun hasPerm(context: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

private fun hasUsageAccess(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    return try {
        appOps.checkOpNoThrow(
            "android:get_package_usage_stats",
            android.os.Process.myUid(), context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
}

private fun openUsageAccess(context: android.content.Context) {
    try {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    } catch (_: Exception) { }
}

private fun requestIgnoreBattery(context: android.content.Context) {
    try {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // На некоторых прошивках диалог недоступен — просто пропускаем
    }
}
