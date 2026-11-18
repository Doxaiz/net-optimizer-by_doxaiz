package com.doxaiz.netoptimizer.core.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Живое состояние сотовой сети */
data class SignalState(
    val operator: String = "—",
    val networkType: String = "—",
    val rsrp: Int = 0,          // dBm
    val rsrq: Int? = null,      // dB; null means unavailable
    val sinr: Int? = null,      // dB; null means unavailable
    val band: String = "—",
    val bandHint: String = "",
    val bandSource: RadioChannels.BandSource? = null,
    val channel: Int? = null,
    val frequencyKhz: Int? = null,
    val pci: Int? = null,
    val is5g: Boolean = false,
    val isLteA: Boolean = false,
    val neighbors: List<String> = emptyList(), // соседние соты: "PCI 123 · B3"
    val temperatureC: Float = 0f,
    val batteryLevel: Int = -1, // % заряда
    val hasPermission: Boolean = false
)

/**
 * Монитор сотового сигнала: RSRP / RSRQ / SINR / Band / агрегация.
 *
 * Опрос allCellInfo каждые 3 секунды, вне главного потока. Android или модем
 * могут вернуть устаревшие данные. Никаких скрытых API.
 */
class SignalMonitor(private val context: Context) {

    private val telephony: TelephonyManager? =
        try { context.getSystemService(TelephonyManager::class.java) } catch (_: Exception) { null }

    private val _state = MutableStateFlow(SignalState())
    val state: StateFlow<SignalState> = _state

    private var pollingJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                updateTemperature()
                currentCoroutineContext().ensureActive()
                updateOperator()
                currentCoroutineContext().ensureActive()
                readCells()
                delay(3000)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Температура и заряд батареи (порог тревоги для USB-модема — 42°C) */
    private fun updateTemperature() {
        try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val tenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).coerceIn(0, 100).toInt() else -1
            _state.value = _state.value.copy(
                temperatureC = if (tenths in -400..1000) tenths / 10f else 0f,
                batteryLevel = percent
            )
        } catch (_: Exception) { _state.value = _state.value.copy(temperatureC = 0f, batteryLevel = -1) }
    }

    private fun updateOperator() {
        try {
            val manager = telephony ?: run { _state.value = _state.value.copy(operator = "—"); return }
            val mccMnc = (manager.networkOperator ?: "").ifEmpty { manager.simOperator ?: "" }
            val profile = CarrierProfiles.find(mccMnc)
            val operator = profile?.name ?: manager.networkOperatorName?.takeIf { it.isNotEmpty() } ?: "—"
            _state.value = _state.value.copy(operator = operator)
        } catch (_: Exception) { _state.value = _state.value.copy(operator = "—") }
    }

    private fun clearCell(hasPermission: Boolean) {
        _state.value = _state.value.copy(
            hasPermission = hasPermission, rsrp = 0, rsrq = null, sinr = null,
            band = "—", bandHint = "", bandSource = null, channel = null, frequencyKhz = null,
            pci = null, networkType = "—", is5g = false, isLteA = false, neighbors = emptyList()
        )
    }

    private suspend fun readCells() {
        if (!hasLocationPermission()) {
            clearCell(false)
            return
        }
        val cells: List<CellInfo> = try {
            telephony?.allCellInfo ?: emptyList()
        } catch (_: SecurityException) {
            clearCell(false)
            return
        } catch (_: Exception) {
            clearCell(true)
            return
        }
        currentCoroutineContext().ensureActive()
        try {
            if (cells.none { it.isRegistered && (it.cellIdentity is CellIdentityLte || it.cellIdentity is CellIdentityNr) }) {
                clearCell(true)
                return
            }
            updateFromCells(cells)
        } catch (_: Exception) { clearCell(true) }
    }

    private fun updateFromCells(cells: List<CellInfo>) {
        val cur = _state.value
        val neighborList = cells.filter { !it.isRegistered }.mapNotNull { info ->
            val parts = when (val id = info.cellIdentity) {
                is CellIdentityLte -> Pair(id.pci, RadioChannels.lte(id.earfcn, id.bands).band)
                is CellIdentityNr -> Pair(id.pci, RadioChannels.nr(id.nrarfcn, id.bands).band)
                else -> null
            } ?: return@mapNotNull null
            val pci = parts.first.takeIf { it >= 0 && it != Int.MAX_VALUE }
            listOfNotNull(pci?.let { "PCI $it" }, parts.second.takeIf { it != "—" }).joinToString(" · ").ifEmpty { null }
        }.distinct().take(4)

        val nrInfo = cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityNr }
        val lteInfo = cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityLte }

        if (nrInfo != null) {
            val id = nrInfo.cellIdentity as CellIdentityNr
            val s = nrInfo.cellSignalStrength as CellSignalStrengthNr
            val radio = RadioChannels.nr(id.nrarfcn, id.bands)
            _state.value = cur.copy(
                rsrp = s.dbm.takeIf { it in -160..-30 } ?: 0,
                rsrq = s.ssRsrq.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
                sinr = s.ssSinr.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
                band = radio.band, bandSource = radio.bandSource, channel = radio.channel, frequencyKhz = radio.frequencyKhz,
                bandHint = if (radio.bandSource != null && !radio.band.contains(" / ")) CarrierProfiles.bandHint(radio.band) else "",
                pci = id.pci.takeIf { it >= 0 && it != Int.MAX_VALUE },
                is5g = true, isLteA = false, networkType = "5G", neighbors = neighborList, hasPermission = true
            )
            return
        }

        if (lteInfo != null) {
            val id = lteInfo.cellIdentity as CellIdentityLte
            val s = lteInfo.cellSignalStrength as CellSignalStrengthLte
            val radio = RadioChannels.lte(id.earfcn, id.bands)
            _state.value = cur.copy(
                rsrp = s.rsrp.takeIf { it in -160..-30 } ?: 0,
                rsrq = s.rsrq.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
                sinr = s.rssnr.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
                band = radio.band, bandSource = radio.bandSource, channel = radio.channel, frequencyKhz = radio.frequencyKhz,
                bandHint = if (radio.bandSource != null && !radio.band.contains(" / ")) CarrierProfiles.bandHint(radio.band) else "",
                pci = id.pci.takeIf { it >= 0 && it != Int.MAX_VALUE },
                isLteA = false, is5g = false, networkType = "4G", neighbors = neighborList, hasPermission = true
            )
        }
    }
}
