package com.doxaiz.netoptimizer.core.overlay

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.doxaiz.netoptimizer.core.dns.DoxaizVpnService
import com.doxaiz.netoptimizer.core.net.KeepAliveEngine
import com.doxaiz.netoptimizer.core.net.KeepAliveProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Быстрая плитка в шторке Android: включение/выключение оптимизации в 1 тап.
 * Долгое нажатие открывает приложение.
 */
class DoxaizTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keepAlive = KeepAliveEngine()
    private var active = false

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.label = "Doxaiz: оптимизация"
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        active = !active
        if (active) {
            keepAlive.start(scope, KeepAliveProfile.MAX)
            if (!DoxaizVpnService.running) {
                DoxaizVpnService.start(this, DoxaizVpnService.currentDns, DoxaizVpnService.blockedApps)
            }
        } else {
            keepAlive.stop()
        }
        qsTile?.let {
            it.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.updateTile()
        }
    }
}
