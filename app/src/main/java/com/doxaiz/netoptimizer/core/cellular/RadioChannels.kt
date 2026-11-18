package com.doxaiz.netoptimizer.core.cellular

/** Downlink frequency raster. Unknown LTE bands are deliberately not guessed. */
object RadioChannels {
    data class RadioDetails(val channel: Int?, val frequencyKhz: Int?, val band: String, val bandSource: BandSource?)
    enum class BandSource { DEVICE, CHANNEL }
    private data class LteRange(val band: Int, val first: Int, val last: Int, val lowKhz: Int)

    // Subset of 3GPP TS 36.101 table 5.7.3-1 (downlink); other LTE channels remain unknown.
    private val lteRanges = listOf(
        LteRange(1, 0, 599, 2_110_000), LteRange(2, 600, 1199, 1_930_000),
        LteRange(3, 1200, 1949, 1_805_000), LteRange(4, 1950, 2399, 2_110_000),
        LteRange(5, 2400, 2649, 869_000), LteRange(7, 2750, 3449, 2_620_000),
        LteRange(8, 3450, 3799, 925_000), LteRange(12, 5010, 5179, 729_000),
        LteRange(13, 5180, 5279, 746_000), LteRange(17, 5730, 5849, 734_000),
        LteRange(18, 5850, 5999, 860_000), LteRange(19, 6000, 6149, 875_000),
        LteRange(20, 6150, 6449, 791_000), LteRange(28, 9210, 9659, 758_000),
        LteRange(38, 37750, 38249, 2_570_000), LteRange(39, 38250, 38649, 1_880_000),
        LteRange(40, 38650, 39649, 2_300_000), LteRange(41, 39650, 41589, 2_496_000)
    )

    fun lte(earfcn: Int, reportedBands: IntArray): RadioDetails {
        val channel = earfcn.takeIf { it in 0..262143 }
        val range = lteRanges.firstOrNull { channel != null && channel in it.first..it.last }
        val reported = reportedBands.filter { it in 1..1024 }.distinct().sorted()
        val band = when {
            reported.isNotEmpty() -> reported.joinToString(" / ") { "B$it" }
            range != null -> "B${range.band}"
            else -> "—"
        }
        val source = when { reported.isNotEmpty() -> BandSource.DEVICE; range != null -> BandSource.CHANNEL; else -> null }
        return RadioDetails(channel, if (channel != null && range != null) range.lowKhz + (channel - range.first) * 100 else null, band, source)
    }

    fun nr(arfcn: Int, reportedBands: IntArray): RadioDetails {
        val channel = arfcn.takeIf { it in 0..3279165 }
        // 3GPP TS 38.104 table 5.4.2.1-1: global raster, in kHz.
        val frequency = channel?.let { n ->
            when (n) {
                in 0..599999 -> n * 5
                in 600000..2016666 -> 3_000_000 + (n - 600000) * 15
                in 2016667..3279165 -> 24_250_080 + (n - 2016667) * 60
                else -> null
            }
        }
        val bands = reportedBands.filter { it in 1..1024 }.distinct().sorted()
        // Multiple NR bands overlap on the global raster: do not infer them from the channel.
        return RadioDetails(channel, frequency, if (bands.isEmpty()) "—" else bands.joinToString(" / ") { "n$it" }, if (bands.isEmpty()) null else BandSource.DEVICE)
    }
}
