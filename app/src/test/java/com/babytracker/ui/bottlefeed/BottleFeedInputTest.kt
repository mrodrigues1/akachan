package com.babytracker.ui.bottlefeed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BottleFeedInputTest {

    @Test
    fun `blank volume returns null`() {
        assertNull(BottleFeedUiState(volumeText = "").parseBottleFeedInput())
    }

    @Test
    fun `non-numeric volume returns null`() {
        assertNull(BottleFeedUiState(volumeText = "abc").parseBottleFeedInput())
    }

    @Test
    fun `zero volume returns null`() {
        assertNull(BottleFeedUiState(volumeText = "0").parseBottleFeedInput())
    }

    @Test
    fun `valid volume parses and trims notes`() {
        val input = BottleFeedUiState(volumeText = "120", notes = "  after nap  ").parseBottleFeedInput()

        assertEquals(BottleFeedInput(volumeMl = 120, notes = "after nap"), input)
    }

    @Test
    fun `blank notes become null`() {
        val input = BottleFeedUiState(volumeText = "90", notes = "   ").parseBottleFeedInput()

        assertEquals(BottleFeedInput(volumeMl = 90, notes = null), input)
    }
}
