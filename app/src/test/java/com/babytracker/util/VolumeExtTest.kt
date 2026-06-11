package com.babytracker.util

import com.babytracker.domain.model.VolumeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VolumeExtTest {

    @Test
    fun `formatVolume in ML shows whole millilitres`() {
        assertEquals("120 ml", formatVolume(120, VolumeUnit.ML))
    }

    @Test
    fun `formatVolume in OZ converts and rounds to one decimal`() {
        // 120 ml / 29.5735 = 4.057 -> 4.1 oz
        assertEquals("4.1 oz", formatVolume(120, VolumeUnit.OZ))
    }

    @Test
    fun `formatVolume in OZ drops trailing zero decimal`() {
        // 296 ml / 29.5735 = 10.009 -> 10.0 -> "10 oz"
        assertEquals("10 oz", formatVolume(296, VolumeUnit.OZ))
    }

    @Test
    fun `mlToOz converts using US fluid ounce`() {
        // 30 ml / 29.5735 = 1.0144 oz
        assertEquals(1.0144, mlToOz(30), 0.001)
    }
}
