package com.doxaiz.netoptimizer.core.net

/** Narrow JNI bridge. Kotlin remains the fallback on JVM tests or unsupported builds. */
internal object NativeMeasurements {
    private const val MAX_SAMPLES = 4096

    internal val available: Boolean = try {
        System.loadLibrary("netoptimizer_core")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    private external fun tcpNative(samples: IntArray): IntArray?
    private external fun medianNative(samples: IntArray, minimum: Int): Int

    fun tcp(samples: List<Int>): TcpProbeSummary? {
        if (!available || samples.size > MAX_SAMPLES) return null
        val result = try { tcpNative(samples.toIntArray()) } catch (_: LinkageError) { return null }
        if (result == null || result.size != 5 || result[0] != samples.size || result[1] !in 0..samples.size) return null
        val successes = result[1]
        val expectedFailures = if (samples.isEmpty()) -1 else (samples.size - successes) * 100 / samples.size
        if (result[4] != expectedFailures ||
            (if (successes == 0) result[2] != -1 else result[2] < 0) ||
            (if (successes < 2) result[3] != -1 else result[3] < 0)) return null
        return TcpProbeSummary(result[0], successes, result[2], result[3], result[4])
    }

    /** -1 means no median; null means use Kotlin instead. */
    fun median(samples: List<Int>, minimum: Int): Int? {
        if (!available || minimum <= 0 || samples.size > MAX_SAMPLES) return null
        val result = try { medianNative(samples.toIntArray(), minimum) } catch (_: LinkageError) { return null }
        return result.takeIf { it >= -1 }
    }
}
