package com.doxaiz.netoptimizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.doxaiz.netoptimizer.core.util.OnboardingPrefs
import com.doxaiz.netoptimizer.core.util.ThemePrefs
import com.doxaiz.netoptimizer.ui.DoxaizNav
import com.doxaiz.netoptimizer.ui.onboarding.OnboardingScreen
import com.doxaiz.netoptimizer.ui.theme.DoxaizTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* разрешения запрошены — UI сам обновится через SignalMonitor */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        setContent {
            var themeKind by remember { mutableStateOf(ThemePrefs.load(this)) }
            var onboarded by remember { mutableStateOf(OnboardingPrefs.isDone(this)) }
            DoxaizTheme(themeKind) {
                if (!onboarded) {
                    OnboardingScreen(onDone = { onboarded = true })
                } else {
                    DoxaizNav(onThemeChange = { themeKind = it })
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.READ_PHONE_STATE
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
