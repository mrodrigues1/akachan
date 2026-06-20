package com.babytracker.ui.growth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the Growth chart's Y-axis range padding. Regression for the crash when the first
 * measurement is added with no baby sex set: the chart then has a single point and no WHO curves, so
 * `minY == maxY`. A span-fraction padding collapses to 0 there, leaving Vico a zero-height range to
 * divide by — which crashes at draw. The padding must stay strictly positive even for a flat range.
 */
class GrowthChartRangeTest {

    @Test
    fun `flat range (single measurement, no curves) still gets positive padding`() {
        // 6.5 kg, the WHO-unit value a first weight entry would plot at.
        assertTrue(growthYPadding(6.5, 6.5) > 0.0, "padding must be > 0 for a flat range")
    }

    @Test
    fun `flat range at zero falls back to the epsilon floor`() {
        assertEquals(MIN_Y_PADDING, growthYPadding(0.0, 0.0), 1e-9)
    }

    @Test
    fun `normal span is padded by the span fraction (unchanged behavior)`() {
        // A multi-point / WHO-curve chart spanning 3..12 kg keeps the original fractional padding.
        assertEquals(9.0 * RANGE_PADDING, growthYPadding(3.0, 12.0), 1e-9)
    }
}
