package com.doxaiz.netoptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.doxaiz.netoptimizer.core.diagnostics.CrashLog
import com.doxaiz.netoptimizer.ui.settings.Diagnostics
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.core.util.ThemePrefs
import com.doxaiz.netoptimizer.ui.DoxaizNav
import com.doxaiz.netoptimizer.ui.onboarding.OnboardingScreen
import com.doxaiz.netoptimizer.ui.theme.DoxaizTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showRecovery by remember { mutableStateOf(CrashLog.read(this) != null) }
            var themeKind by remember { mutableStateOf(ThemePrefs.load(this)) }
            var onboarded by remember { mutableStateOf(OnboardingPrefs.isDone(this)) }
            DoxaizTheme(themeKind) {
                if (showRecovery) {
                    Diagnostics(onContinue = { showRecovery = false })
                } else if (!onboarded) {
                    OnboardingScreen(onDone = { onboarded = true })
                } else {
                    DoxaizNav(onThemeChange = { themeKind = it })
                }
            }
        }
    }
}
