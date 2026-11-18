package com.doxaiz.netoptimizer.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.location.LocationManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doxaiz.netoptimizer.core.shizuku.ShizukuManager
import com.doxaiz.netoptimizer.core.traffic.TrafficMonitor
import com.doxaiz.netoptimizer.core.util.InterfaceMode
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.ui.home.WorkMode
import rikka.shizuku.Shizuku

/** One scrollable setup screen; every access is optional and can be changed later. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val traffic = remember { TrafficMonitor(context.applicationContext) }
    var interfaceMode by rememberSaveable { mutableStateOf(OnboardingPrefs.loadInterfaceMode(context) ?: InterfaceMode.SIMPLE) }
    var cellAccess by remember { mutableStateOf(hasLocation(context)) }
    var locationEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var usageAccess by remember { mutableStateOf(traffic.hasUsageAccess()) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    var shizukuStatus by remember { mutableStateOf(ShizukuManager.status()) }
    var message by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun refresh() {
        cellAccess = hasLocation(context)
        locationEnabled = isLocationEnabled(context)
        usageAccess = traffic.hasUsageAccess()
        batteryExempt = isBatteryExempt(context)
        shizukuStatus = ShizukuManager.status()
    }
    DisposableEffect(lifecycle, traffic) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        val received = Shizuku.OnBinderReceivedListener { shizukuStatus = ShizukuManager.status() }
        val dead = Shizuku.OnBinderDeadListener { shizukuStatus = ShizukuManager.status() }
        val permission = Shizuku.OnRequestPermissionResultListener { code, _ ->
            if (code == 1001) shizukuStatus = ShizukuManager.status()
        }
        lifecycle.addObserver(observer)
        try { Shizuku.addBinderReceivedListener(received) } catch (_: Exception) { }
        try { Shizuku.addBinderDeadListener(dead) } catch (_: Exception) { }
        try { Shizuku.addRequestPermissionResultListener(permission) } catch (_: Exception) { }
        onDispose {
            lifecycle.removeObserver(observer)
            try { Shizuku.removeBinderReceivedListener(received) } catch (_: Exception) { }
            try { Shizuku.removeBinderDeadListener(dead) } catch (_: Exception) { }
            try { Shizuku.removeRequestPermissionResultListener(permission) } catch (_: Exception) { }
        }
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cellAccess = hasLocation(context)
        if (!cellAccess) message = "Точный доступ не предоставлен. Его можно включить позже в настройках приложения."
    }

    Scaffold(bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Button(onClick = {
                    OnboardingPrefs.saveInterfaceMode(context, interfaceMode)
                    if (OnboardingPrefs.loadMode(context) == null) OnboardingPrefs.saveMode(context, WorkMode.NORMAL.name)
                    OnboardingPrefs.setDone(context)
                    onDone()
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Продолжить") }
                Text("Можно пропустить и настроить позже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
            item {
                Text("NET OPTIMIZER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Подготовим приложение", style = MaterialTheme.typography.headlineSmall)
                Text("Выберите удобный вид и при желании включите нужные доступы. Без них приложение тоже откроется, но часть функций будет недоступна.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Как вам удобнее?", style = MaterialTheme.typography.titleMedium)
                        ModeChoice("Обычный", "Только основные действия; подробности можно раскрыть.", interfaceMode == InterfaceMode.SIMPLE) { interfaceMode = InterfaceMode.SIMPLE }
                        ModeChoice("Эксперт", "Все показатели и инструменты сразу.", interfaceMode == InterfaceMode.EXPERT) { interfaceMode = InterfaceMode.EXPERT }
                        Text("Режим можно изменить в настройках.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Для основных возможностей", style = MaterialTheme.typography.titleMedium)
                        SetupItem("Сигнал и сота", if (cellAccess) "Доступ к точному местоположению включён." else "Android требует точное местоположение для сведений о соте. Координаты не сохраняются приложением.", cellAccess) {
                            locationRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                        if (!cellAccess) TextButton(onClick = { if (!openAppDetails(context)) message = "Откройте настройки приложения в Android вручную." }) { Text("Открыть разрешения приложения") }
                        if (locationEnabled == false) {
                            Text("Местоположение телефона выключено. На некоторых устройствах его тоже нужно включить для данных о соте; результат зависит от Android и модема.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { if (!open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) message = "Откройте настройки местоположения Android вручную." }) { Text("Настройки местоположения") }
                        }
                        HorizontalDivider()
                        SetupItem("Трафик приложений", if (usageAccess) "Доступ к статистике включён." else "Нужен доступ к истории использования, чтобы увидеть расход мобильного интернета по приложениям.", usageAccess) {
                            if (!traffic.openUsageAccess()) message = "Откройте доступ к истории использования в настройках Android вручную."
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Дополнительно", style = MaterialTheme.typography.titleMedium)
                        val ready = shizukuStatus == ShizukuManager.Status.READY
                        SetupItem("Shizuku · не обязательно", when (shizukuStatus) {
                            ShizukuManager.Status.READY -> "Подключён. Остановка выбранных приложений выполняется только с подтверждением; работа зависит от устройства."
                            ShizukuManager.Status.NO_PERMISSION -> "Запущен. Можно разрешить дополнительный доступ."
                            else -> "Позволяет пробовать остановку выбранных приложений. Установите и запустите Shizuku по его инструкции (например, через беспроводную отладку), вернитесь сюда и разрешите доступ. Без него используйте системные настройки."
                        }, ready, if (shizukuStatus == ShizukuManager.Status.NO_PERMISSION) "Разрешить" else "Установить или открыть") {
                            if (shizukuStatus == ShizukuManager.Status.NO_PERMISSION) ShizukuManager.requestPermission(1001)
                            else if (!ShizukuManager.openAppOrStore(context)) message = "Не удалось открыть Shizuku. Найдите его в магазине приложений или на GitHub."
                        }
                        HorizontalDivider()
                        Text("Работа в фоне: пока недоступна", style = MaterialTheme.typography.titleSmall)
                        Text("Сейчас замеры работают только на экране и останавливаются при сворачивании. Исключение из оптимизации батареи это не изменит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (batteryExempt) "Android: ограничение батареи снято." else "Android: ограничение батареи не снято (не обязательно).", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { if (!requestIgnoreBattery(context)) message = "Откройте настройки батареи приложения вручную." }) { Text("Настройки батареи") }
                    }
                }
            }
            item {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("История хранится на телефоне. Сетевые замеры обращаются к сторонним серверам и расходуют мобильный трафик.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeChoice(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = androidx.compose.ui.semantics.Role.RadioButton), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupItem(title: String, description: String, ready: Boolean, action: String = "Настроить", onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (ready) "$title · готово" else "$title · по желанию", style = MaterialTheme.typography.titleSmall,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ready) OutlinedButton(onClick = onClick) { Text(action) }
    }
}

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isLocationEnabled(context: Context): Boolean? = try {
    context.getSystemService(LocationManager::class.java)?.isLocationEnabled
} catch (_: Exception) { null }

private fun openAppDetails(context: Context): Boolean = open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:${context.packageName}")
})

private fun requestIgnoreBattery(context: Context): Boolean = open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
    data = Uri.parse("package:${context.packageName}")
})

private fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: Exception) { false }

private fun isBatteryExempt(context: Context): Boolean = try {
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
} catch (_: Exception) { false }
