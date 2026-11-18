package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.*
import org.junit.Test

class RadioChannelsTest {
    @Test fun lteReferenceAndBoundaryChannels() {
        val b3 = RadioChannels.lte(1200, intArrayOf())
        assertEquals("B3", b3.band)
        assertEquals(1_805_000, b3.frequencyKhz)
        assertEquals(RadioChannels.BandSource.CHANNEL, b3.bandSource)
        assertEquals("B20", RadioChannels.lte(6150, intArrayOf()).band)
        assertEquals(791_000, RadioChannels.lte(6150, intArrayOf()).frequencyKhz)
        assertEquals(820_900, RadioChannels.lte(6449, intArrayOf()).frequencyKhz)
        assertEquals("B28", RadioChannels.lte(9210, intArrayOf()).band)
        assertEquals(758_000, RadioChannels.lte(9210, intArrayOf()).frequencyKhz)
        assertEquals("B12", RadioChannels.lte(5010, intArrayOf()).band)
        assertEquals("B13", RadioChannels.lte(5180, intArrayOf()).band)
    }

    @Test fun unknownLteChannelsAreNotMistakenForBandNumbers() {
        val unknown = RadioChannels.lte(7000, intArrayOf())
        assertEquals(7000, unknown.channel)
        assertEquals("—", unknown.band)
        assertNull(unknown.frequencyKhz)
        assertNull(unknown.bandSource)
        assertNull(RadioChannels.lte(Int.MAX_VALUE, intArrayOf()).channel)
        assertNull(RadioChannels.lte(-1, intArrayOf()).frequencyKhz)
    }

    @Test fun modemBandsTakePrecedenceAndAreDistinct() {
        val info = RadioChannels.lte(6150, intArrayOf(28, 20, 28, Int.MAX_VALUE, 0))
        assertEquals("B20 / B28", info.band)
        assertEquals(RadioChannels.BandSource.DEVICE, info.bandSource)
        assertEquals(791_000, info.frequencyKhz)
    }

    @Test fun nrGlobalRasterBoundariesAndNoInferredBand() {
        assertEquals(2_999_995, RadioChannels.nr(599999, intArrayOf()).frequencyKhz)
        assertEquals(3_000_000, RadioChannels.nr(600000, intArrayOf()).frequencyKhz)
        assertEquals(24_249_990, RadioChannels.nr(2016666, intArrayOf()).frequencyKhz)
        assertEquals(24_250_080, RadioChannels.nr(2016667, intArrayOf()).frequencyKhz)
        assertEquals(99_999_960, RadioChannels.nr(3279165, intArrayOf()).frequencyKhz)
        assertEquals("—", RadioChannels.nr(620000, intArrayOf()).band)
        assertEquals("n78", RadioChannels.nr(620000, intArrayOf(78)).band)
        assertNull(RadioChannels.nr(3279166, intArrayOf()).frequencyKhz)
        assertNull(RadioChannels.nr(Int.MAX_VALUE, intArrayOf()).channel)
    }
}
