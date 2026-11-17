package com.doxaiz.netoptimizer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.doxaiz.netoptimizer.core.cellular.SignalMonitor
import com.doxaiz.netoptimizer.ui.home.HomeScreen
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
    Tab("Сессии", Icons.Filled.DateRange)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoxaizNav(onThemeChange: (DoxaizThemeKind) -> Unit) {
    var selected by remember { mutableStateOf(0) }
    // Единый монитор сигнала на всё приложение: одна регистрация колбэка,
    // без переподписок при переключении вкладок
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val monitor = remember { SignalMonitor(context) }
    LaunchedEffect(Unit) { monitor.start(scope) }
    DisposableEffect(Unit) { onDispose { monitor.stop() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net Optimizer by Doxaiz") }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
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
                0 -> HomeScreen(modifier = Modifier.padding(padding), monitor = monitor)
                1 -> ToolsScreen(modifier = Modifier.padding(padding), monitor = monitor)
                2 -> TrafficScreen(modifier = Modifier.padding(padding))
                3 -> SessionsScreen(
                    modifier = Modifier.padding(padding),
                    monitor = monitor,
                    onOpenSettings = { selected = 4 }
                )
                4 -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onThemeChange = onThemeChange,
                    onBack = { selected = 3 }
                )
            }
        }
    }
}
