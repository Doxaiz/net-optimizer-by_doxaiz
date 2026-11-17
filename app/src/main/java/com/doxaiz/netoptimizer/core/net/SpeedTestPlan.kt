package com.doxaiz.netoptimizer.core.net

/** Result of two independent transfers on the same mobile network. Null is not a zero speed. */
data class SpeedResult(val downloadMbps: Double?, val uploadMbps: Double?, val status: SpeedStatus)
enum class SpeedStatus { COMPLETED, NO_CELLULAR, NETWORK_CHANGED, LOW_BATTERY }
enum class SpeedDirection { DOWNLOAD, UPLOAD }

/** Pure orchestration, independently testable without a modem. */
internal object SpeedTestPlan {
    private class Stopped(val status: SpeedStatus) : RuntimeException()

    fun run(
        hasInitialNetwork: Boolean,
        sameNetwork: () -> Boolean,
        batteryOk: () -> Boolean,
        cancelled: () -> Boolean,
        transfer: (SpeedDirection, () -> Unit) -> Double?
    ): SpeedResult {
        if (!hasInitialNetwork) return SpeedResult(null, null, SpeedStatus.NO_CELLULAR)
        fun check() {
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (!sameNetwork()) throw Stopped(SpeedStatus.NETWORK_CHANGED)
            if (!batteryOk()) throw Stopped(SpeedStatus.LOW_BATTERY)
        }
        fun measure(direction: SpeedDirection): Double? {
            check()
            val result = try {
                transfer(direction, ::check)
            } catch (e: Stopped) { throw e
            } catch (e: java.util.concurrent.CancellationException) { throw e
            } catch (_: Exception) { null }
            check()
            return result?.takeIf { it.isFinite() && it > 0.0 }
        }
        return try {
            val down = measure(SpeedDirection.DOWNLOAD)
            val up = measure(SpeedDirection.UPLOAD)
            SpeedResult(down, up, SpeedStatus.COMPLETED)
        } catch (e: Stopped) {
            // Never publish results from an interrupted or mixed-network test.
            SpeedResult(null, null, e.status)
        }
    }

    fun mbps(bytes: Long, elapsedNanos: Long): Double? =
        if (bytes <= 0 || elapsedNanos <= 0) null else bytes.toDouble() * 8_000.0 / elapsedNanos
}
