package com.doxaiz.netoptimizer.core.cellular

/** Reject invalid modem values rather than presenting them as real measurements.
 * Ranges match Android's LTE/NR telephony definitions. A missing value stays missing.
 */
internal object RadioMeasurements {
    fun lteRsrq(value: Int): Int? = value.takeIf { it in -34..3 }
    fun lteSinr(value: Int): Int? = value.takeIf { it in -20..30 }
    fun nrRsrq(value: Int): Int? = value.takeIf { it in -43..20 }
    fun nrSinr(value: Int): Int? = value.takeIf { it in -23..40 }
    fun ltePci(value: Int): Int? = value.takeIf { it in 0..503 }
    fun nrPci(value: Int): Int? = value.takeIf { it in 0..1007 }
}
