package com.doxaiz.netoptimizer.ui.tools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doxaiz.netoptimizer.core.net.NetworkConditions
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic states only: no emulator can be forced into a captive portal reliably, so the
 * rendered wording is checked with fixed capability sets instead of live network state.
 */
@RunWith(AndroidJUnit4::class)
class NetworkConditionsCardTest {
    @get:Rule val compose = createComposeRule()

    private val usable = NetworkConditions(
        validated = true, captivePortal = false, notCongested = true, notSuspended = true,
        notRoaming = true, temporarilyUnmetered = false, metered = true,
        downstreamKbps = 12_000, upstreamKbps = 3_000
    )

    @Test fun usableNetworkShowsNoRestrictionsClaim() {
        var refreshed = false
        compose.setContent { NetworkConditionsCardForTest(usable, onRefresh = { refreshed = true }) }
        compose.onNodeWithText("Android не сообщает об ограничениях сети.").assertIsDisplayed()
        compose.onNodeWithText("Обновить условия сети").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue("refresh button must trigger a re-read", refreshed) }
    }

    @Test fun captivePortalIsRenderedAsUnusableWithReason() {
        compose.setContent { NetworkConditionsCardForTest(usable.copy(validated = false, captivePortal = true), onRefresh = {}) }
        compose.onNodeWithText("Сеть сейчас непригодна для достоверного замера.").assertIsDisplayed()
        compose.onNodeWithText("• Система обнаружила captive portal: до входа в сеть часть запросов может не работать.").assertIsDisplayed()
        compose.onNodeWithText("• Android не подтвердил доступ в интернет (нет VALIDATED): возможен вход на странице провайдера или сбой проверки.").assertIsDisplayed()
    }

    @Test fun missingBandwidthIsShownAsUnknown() {
        compose.setContent { NetworkConditionsCardForTest(usable.copy(downstreamKbps = 0, upstreamKbps = 0), onRefresh = {}) }
        compose.onNodeWithText("• Оценка полосы от Android недоступна: это не означает нулевую скорость.").assertIsDisplayed()
    }
}
