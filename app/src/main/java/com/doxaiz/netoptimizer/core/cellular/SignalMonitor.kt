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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Живое состояние сотовой сети */
data class SignalState(
    val operator: String = "—",
    val networkType: String = "—",
    val rsrp: Int = 0,          // dBm
    val rsrq: Int = 0,          // dB
    val sinr: Int = 0,          // dB
    val band: String = "—",
    val bandHint: String = "",
    val pci: Int = 0,
    val is5g: Boolean = false,
    val isLteA: Boolean = false,
    val neighbors: List<String> = emptyList(), // соседние соты: "PCI 123 · B3"
    val temperatureC: Float = 0f,
    val batteryLevel: Int = 100, // % заряда
    val hasPermission: Boolean = false
)

/**
 * Монитор сотового сигнала: RSRP / RSRQ / SINR / Band / агрегация.
 *
 * Работает через опрос (polling) allCellInfo раз в 3 сек — так надёжнее на
 * Infinix XOS / MediaTek, где колбэки TelephonyCallback приходят нестабильно.
 * Никаких скрытых API: всё из публичного TelephonyManager.
 */
class SignalMonitor(private val context: Context) {

    private val telephony: TelephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    private val _state = MutableStateFlow(SignalState())
    val state: StateFlow<SignalState> = _state

    @Volatile
    private var running = false

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        scope.launch {
            while (running) {
                updateTemperature()
                updateOperator()
                readCells()
                delay(3000)
            }
        }
    }

    fun stop() {
        running = false
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
            val tenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (scale > 0) (level * 100 / scale) else level
            _state.value = _state.value.copy(
                temperatureC = if (tenths > 0) tenths / 10f else _state.value.temperatureC,
                batteryLevel = percent
            )
        } catch (_: Exception) { }
    }

    private fun updateOperator() {
        try {
            val mccMnc = (telephony.networkOperator ?: "").ifEmpty { telephony.simOperator ?: "" }
            val profile = CarrierProfiles.find(mccMnc)
            val operator = profile?.name ?: (telephony.networkOperatorName ?: "—")
            _state.value = _state.value.copy(operator = operator)
        } catch (_: Exception) { }
    }

    private fun readCells() {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(hasPermission = false, rsrp = 0, rsrq = 0, sinr = 0, band = "—", networkType = "—")
            return
        }
        val cells: List<CellInfo> = try {
            telephony.allCellInfo ?: emptyList()
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(hasPermission = false)
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (cells.none { it.isRegistered && (it.cellIdentity is CellIdentityLte || it.cellIdentity is CellIdentityNr) }) {
            _state.value = _state.value.copy(rsrp = 0, rsrq = 0, sinr = 0, band = "—", networkType = "—", neighbors = emptyList())
            return
        }
        updateFromCells(cells)
    }

    private fun updateFromCells(cells: List<CellInfo>) {
        val cur = _state.value

        // Соседние соты (исключая активную): PCI + бэнд — видно, куда телефон может переключиться
        val neighborList = cells.filter { !it.isRegistered }.mapNotNull { info ->
            when (val id = info.cellIdentity) {
                is CellIdentityLte -> "PCI ${id.pci} · ${earfcnToBand(id.earfcn, false)}"
                is CellIdentityNr -> "PCI ${id.pci} · ${earfcnToBand(id.nrarfcn, true)}"
                else -> null
            }
        }.distinct().take(4)

        val nrInfo = cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityNr }
        val lteInfo = cells.firstOrNull { it.isRegistered && it.cellIdentity is CellIdentityLte }

        if (nrInfo != null) {
            val id = nrInfo.cellIdentity as CellIdentityNr
            val s = nrInfo.cellSignalStrength as CellSignalStrengthNr
            val band = earfcnToBand(id.nrarfcn, isNr = true)
            _state.value = cur.copy(
                rsrp = s.dbm.takeIf { it != Int.MAX_VALUE } ?: cur.rsrp,
                band = band,
                bandHint = CarrierProfiles.bandHint(band),
                pci = id.pci,
                is5g = true,
                networkType = "5G",
                neighbors = neighborList,
                hasPermission = true
            )
            return
        }

        if (lteInfo != null) {
            val id = lteInfo.cellIdentity as CellIdentityLte
            val s = lteInfo.cellSignalStrength as CellSignalStrengthLte
            val band = earfcnToBand(id.earfcn, isNr = false)
            // Neighbouring LTE cells do not establish carrier aggregation.
            val lteA = false
            _state.value = cur.copy(
                rsrp = s.rsrp.takeIf { it != Int.MAX_VALUE } ?: cur.rsrp,
                rsrq = s.rsrq.takeIf { it != Int.MAX_VALUE } ?: cur.rsrq,
                sinr = s.rssnr.takeIf { it != Int.MAX_VALUE } ?: cur.sinr,
                band = band,
                bandHint = CarrierProfiles.bandHint(band),
                pci = id.pci,
                isLteA = lteA,
                is5g = false,
                networkType = if (lteA) "4G+" else "4G",
                neighbors = neighborList,
                hasPermission = true
            )
        }
    }

    /** Упрощённая таблица EARFCN -> Band (LTE + NR) */
    private fun earfcnToBand(earfcn: Int, isNr: Boolean): String {
        if (isNr) {
            return when (earfcn) {
                in 620000..627000 -> "n78"   // 3500 МГц
                in 500000..510000 -> "n41"   // 2600 МГц
                in 184000..190000 -> "n1"    // 2100 МГц
                else -> "n$earfcn"
            }
        }
        return when (earfcn) {
            in 0..599 -> "B1"
            in 600..1199 -> "B2"
            in 1200..1949 -> "B3"
            in 1950..2399 -> "B4"
            in 2400..2649 -> "B5"
            in 2750..3449 -> "B7"
            in 3450..3799 -> "B8"
            in 3800..4149 -> "B9"
            in 6150..6449 -> "B12"
            in 6600..7399 -> "B13"
            in 7500..7699 -> "B14"
            in 7700..8039 -> "B17"
            in 8040..8689 -> "B18"
            in 8690..9039 -> "B19"
            in 9040..9399 -> "B20"
            in 9750..9999 -> "B28"
            in 37750..38249 -> "B38"
            in 38250..38649 -> "B39"
            in 38650..39649 -> "B40"
            in 39650..41589 -> "B41"
            else -> "B$earfcn"
        }
    }
}
