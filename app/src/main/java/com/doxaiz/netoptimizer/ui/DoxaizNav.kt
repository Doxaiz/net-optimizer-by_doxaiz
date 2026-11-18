package com.doxaiz.netoptimizer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doxaiz.netoptimizer.DoxaizApp
import com.doxaiz.netoptimizer.core.session.SessionController
import com.doxaiz.netoptimizer.core.util.InterfaceMode
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.core.session.SessionPhase
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.core.net.BatteryHealth
import com.doxaiz.netoptimizer.core.net.BatterySnapshot
import com.doxaiz.netoptimizer.core.net.BatteryWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.doxaiz.netoptimizer.ui.home.HomeScreen
import com.doxaiz.netoptimizer.ui.onboarding.OnboardingScreen
import com.doxaiz.netoptimizer.ui.sessions.SessionsScreen
import com.doxaiz.netoptimizer.ui.settings.SettingsScreen
import com.doxaiz.netoptimizer.ui.theme.DoxaizThemeKind
import com.doxaiz.netoptimizer.ui.tools.ToolsScreen
import com.doxaiz.netoptimizer.ui.traffic.TrafficScreen

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Главная", Icons.Filled.Home),
    Tab("Инструменты", Icons.Filled.Build),
    Tab("Трафик", Icons.Filled.List),
    Tab("Сессии", Icons.Filled.DateRange),
    Tab("Настройки", Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoxaizNav(onThemeChange: (DoxaizThemeKind) -> Unit, interfaceMode: InterfaceMode, onInterfaceModeChange: (InterfaceMode) -> Unit) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val monitor = remember { SignalMonitor(context) }
    val repository = remember { (context.applicationContext as DoxaizApp).sessionRepository }
    val session = remember { SessionController(context.applicationContext, scope, repository, monitor) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val signal by monitor.state.collectAsState()
    var foregroundSince by remember(lifecycle) { mutableStateOf<Long?>(null) }
    var batteryClock by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(foregroundSince) {
        if (foregroundSince != null) while (isActive) {
            batteryClock = SystemClock.elapsedRealtime()
            delay(5_000)
        }
    }
    DisposableEffect(lifecycle, monitor, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foregroundSince = SystemClock.elapsedRealtime()
                    monitor.start(scope)
                }
                Lifecycle.Event.ON_STOP -> {
                    foregroundSince = null
                    session.pause("Сессия на паузе: приложение не на экране. Сохраните её или продолжите вручную.")
                    monitor.stop()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            monitor.stop()
            session.dispose()
        }
    }
    if (setupOpen) {
        OnboardingScreen(onDone = {
            onInterfaceModeChange(OnboardingPrefs.loadInterfaceMode(context) ?: InterfaceMode.SIMPLE)
            setupOpen = false
        })
        return
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Net Optimizer by Doxaiz") })
                val now = maxOf(batteryClock, SystemClock.elapsedRealtime())
                val start = foregroundSince
                if (BatteryHealth.isFresh(signal.batteryObservedAtMs, start, now)) {
                    signal.battery?.let { BatteryWarningBanner(it) }
                } else if (start != null && now >= start && now - start > BatteryHealth.MAX_READING_AGE_MS) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text("Нет свежих данных батареи; некоторые сетевые проверки могут быть недоступны.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            val measuring = index == 3 && session.state.phase == SessionPhase.RUNNING
                            BadgedBox(badge = { if (measuring) Badge() }) {
                                Icon(tab.icon, contentDescription = if (measuring) "Сессии: идёт замер" else tab.title)
                            }
                        },
                        label = { Text(tab.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        // Плавный переход между вкладками: сдвиг + затухание
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(260),
                    initialOffsetX = { dir * it / 4 }
                ) + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(260),
                        targetOffsetX = { -dir * it / 4 }
                    ) + fadeOut(tween(260)))
            },
            label = "tabTransition"
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(modifier = Modifier.padding(padding), monitor = monitor, foregroundSinceMs = foregroundSince, nowMs = batteryClock, onOptimize = { selected = 2 }, expert = interfaceMode == InterfaceMode.EXPERT)
                1 -> ToolsScreen(modifier = Modifier.padding(padding), monitor = monitor, foregroundSinceMs = foregroundSince, nowMs = batteryClock, expert = interfaceMode == InterfaceMode.EXPERT)
                2 -> TrafficScreen(modifier = Modifier.padding(padding), expert = interfaceMode == InterfaceMode.EXPERT)
                3 -> SessionsScreen(
                    modifier = Modifier.padding(padding),
                    controller = session,
                    foregroundSinceMs = foregroundSince,
                    expert = interfaceMode == InterfaceMode.EXPERT
                )
                4 -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onThemeChange = onThemeChange,
                    interfaceMode = interfaceMode,
                    onInterfaceModeChange = onInterfaceModeChange,
                    onOpenSetup = {
                        session.pause("Сессия на паузе: открыта подготовка. Продолжите или сохраните её вручную.")
                        setupOpen = true
                    }
                )
            }
        }
    }
}

@Composable
private fun BatteryWarningBanner(snapshot: BatterySnapshot) {
    val warnings = BatteryHealth.warnings(snapshot)
    if (warnings.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            warnings.forEach { warning ->
                val text = when (warning) {
                    BatteryWarning.UNKNOWN_CHARGE -> "Заряд батареи недоступен: сетевые проверки могут не запускаться."
                    BatteryWarning.LOW_CHARGE -> "Заряд ${snapshot.percent}%: ниже 20%, сетевые проверки приостанавливаются."
                    BatteryWarning.HIGH_TEMPERATURE -> "Температура батареи %.1f °C: дайте устройству остыть.".format(snapshot.temperatureTenths!! / 10.0)
                }
                Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
