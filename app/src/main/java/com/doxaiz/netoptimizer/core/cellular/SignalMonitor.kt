package com.doxaiz.netoptimizer.core.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.os.SystemClock
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.doxaiz.netoptimizer.core.net.BatteryHealth
import com.doxaiz.netoptimizer.core.net.BatterySnapshot
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
import java.util.concurrent.Executor

/** Живое состояние сотовой сети */
data class SignalState(
    val operator: String = "—",
    val networkType: String = "—",
    val rsrp: Int = 0,          // dBm
    val observedAtElapsedMs: Long? = null, // polling completion, not necessarily modem sample time
    val modemAtElapsedMs: Long? = null, // approximate CellInfo receipt time, since boot
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
    val battery: BatterySnapshot? = null,
    val batteryObservedAtMs: Long? = null,
    val hasPermission: Boolean = false
)

/** Remove only cell-derived data; battery and permission state are independent. */
fun SignalState.withoutCellData(): SignalState = copy(
    rsrp = 0, rsrq = null, sinr = null, networkType = "—", band = "—", bandHint = "",
    bandSource = null, channel = null, frequencyKhz = null, pci = null,
    is5g = false, isLteA = false, neighbors = emptyList()
)

/**
 * Монитор сотового сигнала: RSRP / RSRQ / SINR / Band / агрегация.
 *
 * Кэш allCellInfo читается каждые 3 секунды; обновление запрашивается не чаще
 * раза в 15 секунд при достаточном заряде. Android может ограничить или отклонить
 * запрос и вернуть устаревшие данные. Никаких скрытых API.
 */
class SignalMonitor(private val context: Context) {

    private val telephony: TelephonyManager? =
        try { context.getSystemService(TelephonyManager::class.java) } catch (_: Exception) { null }

    private val _state = MutableStateFlow(SignalState())
    val state: StateFlow<SignalState> = _state

    private var pollingJob: Job? = null
    private val refreshGate = CellRefreshGate<List<CellInfo>>()

    fun start(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return
        refreshGate.start()
        pollingJob = scope.launch(Dispatchers.IO) {
            var lastRefresh: List<CellInfo>? = null
            var refreshReceivedAt = 0L
            while (isActive) {
                updateTemperature()
                currentCoroutineContext().ensureActive()
                updateOperator()
                currentCoroutineContext().ensureActive()
                requestRefreshIfAllowed()
                currentCoroutineContext().ensureActive()
                val now = SystemClock.elapsedRealtime()
                refreshGate.take()?.let { lastRefresh = it; refreshReceivedAt = now }
                if (!hasLocationPermission() || now < refreshReceivedAt || now - refreshReceivedAt > 30_000) lastRefresh = null
                readCells(lastRefresh)
                delay(3000)
            }
        }
    }

    fun stop() {
        refreshGate.stop()
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked immediately before the request; revocation can still race
    private fun requestRefreshIfAllowed() {
        val manager = telephony ?: return
        if (!hasLocationPermission() || (_state.value.battery?.percent ?: -1) < 20) return
        val id = refreshGate.tryStart(SystemClock.elapsedRealtime()) ?: return
        try {
            manager.requestCellInfoUpdate(Executor { it.run() }, CellRefreshCallback(refreshGate, id))
        } catch (_: Exception) {
            refreshGate.complete(id, null)
        }
    }

    /** Battery temperature is reported by Android in tenths of a degree. */
    private suspend fun updateTemperature() {
        val snapshot = try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            BatteryHealth.fromRaw(
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1,
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1,
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            )
        } catch (_: Exception) { BatterySnapshot(null, null) }
        currentCoroutineContext().ensureActive()
        _state.value = _state.value.copy(battery = snapshot, batteryObservedAtMs = android.os.SystemClock.elapsedRealtime())
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
            pci = null, networkType = "—", is5g = false, isLteA = false, neighbors = emptyList(),
            observedAtElapsedMs = android.os.SystemClock.elapsedRealtime(), modemAtElapsedMs = null
        )
    }

    private suspend fun readCells(refreshed: List<CellInfo>?) {
        if (!hasLocationPermission()) {
            clearCell(false)
            return
        }
        val cached: List<CellInfo> = try {
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
            val cells = chooseCells(cached, refreshed, SystemClock.elapsedRealtime())
            if (cells.none { it.isRegistered && (it.cellIdentity is CellIdentityLte || it.cellIdentity is CellIdentityNr) }) {
                clearCell(true)
                return
            }
            updateFromCells(cells)
        } catch (_: Exception) { clearCell(true) }
    }

    private fun preferredCell(cells: List<CellInfo>): CellInfo? =
        cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityNr }
            ?: cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityLte }

    private fun chooseCells(cached: List<CellInfo>, refreshed: List<CellInfo>?, now: Long): List<CellInfo> {
        val cachedCell = preferredCell(cached)
        val refreshedCell = refreshed?.let(::preferredCell)
        return if (CellSourceChoice.useRefresh(cachedCell != null, cachedCell?.timestampMillis,
                refreshedCell != null, refreshedCell?.timestampMillis, now)) refreshed!! else cached
    }

    private fun updateFromCells(cells: List<CellInfo>) {
        val cur = _state.value
        val neighborList = cells.filter { !it.isRegistered }.mapNotNull { info ->
            val parts = when (val id = info.cellIdentity) {
                is CellIdentityLte -> Pair(RadioMeasurements.ltePci(id.pci), RadioChannels.lte(id.earfcn, id.bands).band)
                is CellIdentityNr -> Pair(RadioMeasurements.nrPci(id.pci), RadioChannels.nr(id.nrarfcn, id.bands).band)
                else -> null
            } ?: return@mapNotNull null
            val pci = parts.first
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
                observedAtElapsedMs = android.os.SystemClock.elapsedRealtime(), modemAtElapsedMs = nrInfo.timestampMillis,
                rsrq = RadioMeasurements.nrRsrq(s.ssRsrq),
                sinr = RadioMeasurements.nrSinr(s.ssSinr),
                band = radio.band, bandSource = radio.bandSource, channel = radio.channel, frequencyKhz = radio.frequencyKhz,
                bandHint = if (radio.bandSource != null && !radio.band.contains(" / ")) CarrierProfiles.bandHint(radio.band) else "",
                pci = RadioMeasurements.nrPci(id.pci),
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
                observedAtElapsedMs = android.os.SystemClock.elapsedRealtime(), modemAtElapsedMs = lteInfo.timestampMillis,
                rsrq = RadioMeasurements.lteRsrq(s.rsrq),
                sinr = RadioMeasurements.lteSinr(s.rssnr),
                band = radio.band, bandSource = radio.bandSource, channel = radio.channel, frequencyKhz = radio.frequencyKhz,
                bandHint = if (radio.bandSource != null && !radio.band.contains(" / ")) CarrierProfiles.bandHint(radio.band) else "",
                pci = RadioMeasurements.ltePci(id.pci),
                isLteA = false, is5g = false, networkType = "4G", neighbors = neighborList, hasPermission = true
            )
        }
    }
}

/** Does not retain the monitor or Activity; late callbacks are discarded by the gate. */
private class CellRefreshCallback(
    private val gate: CellRefreshGate<List<CellInfo>>,
    private val requestId: Long
) : TelephonyManager.CellInfoCallback() {
    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
        gate.complete(requestId, cellInfo.toList())
    }

    override fun onError(errorCode: Int, detail: Throwable?) {
        gate.complete(requestId, null)
    }
}
