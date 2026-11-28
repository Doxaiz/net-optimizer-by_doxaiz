package com.doxaiz.netoptimizer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
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
import com.doxaiz.netoptimizer.core.cellular.SignalHelp
import com.doxaiz.netoptimizer.core.cellular.SignalHelpPolicy
import com.doxaiz.netoptimizer.core.net.CellularNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class Readiness(
    val mobileSelected: Boolean = false,
    val fineLocation: Boolean = false,
    val locationEnabled: Boolean? = null
)

private fun readReadiness(context: Context): Readiness {
    val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val locationEnabled = try { context.getSystemService(LocationManager::class.java)?.isLocationEnabled } catch (_: Exception) { null }
    return Readiness(CellularNetwork.active(context) != null, fineLocation, locationEnabled)
}

/** Never changes phone settings. These checks do not send traffic or validate connectivity. */
@Composable
fun ConnectionGuide(hasDisplayableSignal: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var readiness by remember { mutableStateOf<Readiness?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> { foreground = false; readiness = null }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        if (foreground) while (isActive) {
            readiness = readReadiness(context)
            delay(3_000)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Что проверить", style = MaterialTheme.typography.titleMedium)
            val current = readiness
            if (current == null) {
                Text("Проверяем настройки телефона…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!expanded) {
                val status = when {
                    !current.mobileSelected -> "Мобильная сеть не найдена для замера."
                    !current.fineLocation -> "Для сведений о соте нужен доступ к местоположению."
                    !hasDisplayableSignal -> "Данные о сигнале пока недоступны."
                    else -> "Мобильная сеть выбрана."
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { expanded = true }) { Text("Настроить и узнать больше") }
            } else {
                TextButton(onClick = { expanded = false }) { Text("Скрыть подробности") }
                Text(if (current.mobileSelected) "Мобильная сеть выбрана Android" else "Мобильная сеть не найдена для замеров", style = MaterialTheme.typography.titleSmall,
                    color = if (current.mobileSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(if (current.mobileSelected) "Это не подтверждает доступ в интернет. Запустите проверку связи." else "Включите мобильные данные и выберите мобильную сеть для интернета. VPN или другая активная сеть могут помешать замерам; настройки сами не изменяются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!current.mobileSelected) OutlinedButton(onClick = {
                    error = if (openSettings(context, Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))) null else "Не удалось открыть настройки. Откройте настройки сети вручную."
                }) { Text("Открыть настройки сети") }
                HorizontalDivider()
                when (SignalHelpPolicy.choose(current.fineLocation, current.locationEnabled, hasDisplayableSignal)) {
                    SignalHelp.PERMISSION_NEEDED -> {
                        Text("Для сведений о соте нужен доступ к точному местоположению", style = MaterialTheme.typography.titleSmall)
                        Text("Откройте разрешения приложения и включите точное местоположение. Сетевые замеры можно выполнять без него.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            error = if (openSettings(context, app, Intent(Settings.ACTION_SETTINGS))) null else "Откройте разрешения приложения в настройках Android вручную."
                        }) { Text("Разрешения приложения") }
                    }
                    SignalHelp.AVAILABLE -> Text("Данные о сигнале получены. Android может обновлять их с задержкой.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SignalHelp.LOCATION_OFF -> {
                        Text("Данные о сигнале пока недоступны", style = MaterialTheme.typography.titleSmall)
                        Text("Местоположение телефона выключено. На некоторых устройствах оно необходимо для получения данных о соте; даже после включения Android может их не предоставить.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            error = if (openSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), Intent(Settings.ACTION_SETTINGS))) null else "Откройте настройки местоположения Android вручную."
                        }) { Text("Настройки местоположения") }
                    }
                    SignalHelp.WAITING -> {
                        Text("Данные о сигнале пока недоступны", style = MaterialTheme.typography.titleSmall)
                        Text("Подождите обновления; проверьте, включено ли местоположение телефона. Данные могут быть недоступны из-за ограничений Android или модема.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            error = if (openSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), Intent(Settings.ACTION_SETTINGS))) null else "Откройте настройки местоположения Android вручную."
                        }) { Text("Настройки местоположения") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun openSettings(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) { }
    }
    return false
}
