package com.babytracker.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MilkStashWidgetContentHelpersTest {

    @Test
    fun `formatVolume renders raw mL below one liter`() {
        assertEquals("0 mL", formatVolume(0))
        assertEquals("500 mL", formatVolume(500))
        assertEquals("999 mL", formatVolume(999))
    }

    @Test
    fun `formatVolume switches to liters at one liter and above`() {
        assertEquals("1.0 L", formatVolume(1000))
        assertEquals("1.5 L", formatVolume(1500))
    }
}
