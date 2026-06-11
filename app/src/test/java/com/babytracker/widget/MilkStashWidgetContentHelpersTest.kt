package com.babytracker.widget

import com.babytracker.domain.model.VolumeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MilkStashWidgetContentHelpersTest {

    @Test
    fun `formatVolume renders raw mL below one liter`() {
        assertEquals("0 mL", formatVolume(0, VolumeUnit.ML))
        assertEquals("500 mL", formatVolume(500, VolumeUnit.ML))
        assertEquals("999 mL", formatVolume(999, VolumeUnit.ML))
    }

    @Test
    fun `formatVolume switches to liters at one liter and above`() {
        assertEquals("1.0 L", formatVolume(1000, VolumeUnit.ML))
        assertEquals("1.5 L", formatVolume(1500, VolumeUnit.ML))
    }

    @Test
    fun `formatVolume renders ounces when unit is OZ`() {
        // 840 ml / 29.5735 = 28.40 -> "28.4 oz"; liters rollup does not apply to oz
        assertEquals("28.4 oz", formatVolume(840, VolumeUnit.OZ))
        assertEquals("0 oz", formatVolume(0, VolumeUnit.OZ))
    }
}
