package com.babytracker.util

import com.babytracker.domain.model.MeasurementSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeasurementFormatTest {

    @Test
    fun `weight metric round-trips through kilograms`() {
        listOf(0L, 3346L, 5200L, 9648L, 12151L).forEach { grams ->
            assertEquals(grams, kilogramsToGrams(gramsToKilograms(grams)))
        }
    }

    @Test
    fun `weight metric formats with two decimals`() {
        assertEquals("5.20 kg", formatWeight(5200, MeasurementSystem.METRIC))
        assertEquals("3.35 kg", formatWeight(3346, MeasurementSystem.METRIC))
    }

    @Test
    fun `weight imperial round-trips through pounds and ounces`() {
        for (pounds in 0..30) {
            for (ounces in 0..15) {
                val grams = poundsOuncesToGrams(pounds, ounces)
                assertEquals(pounds to ounces, gramsToPoundsOunces(grams))
            }
        }
    }

    @Test
    fun `pounds and ounces never reports a full sixteen ounces`() {
        // Sweep every gram value across the 0-12 kg infant range.
        for (grams in 0L..12_000L step 1L) {
            val (_, ounces) = gramsToPoundsOunces(grams)
            assertTrue(ounces in 0..15, "ounces carried incorrectly at $grams g -> $ounces oz")
        }
    }

    @Test
    fun `ounce rounding carries into the next pound at the boundary`() {
        // 453 g ~= 15.98 oz, which must round up to 1 lb 0 oz, not 0 lb 16 oz.
        assertEquals(1 to 0, gramsToPoundsOunces(453))
    }

    @Test
    fun `weight imperial formats as pounds and ounces`() {
        assertEquals("11 lb 7 oz", formatWeight(5188, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun `length metric round-trips through centimetres`() {
        listOf(0L, 498L, 600L, 758L).forEach { mm ->
            assertEquals(mm, centimetresToMillimetres(millimetresToCentimetres(mm)))
        }
        assertEquals("60.0 cm", formatLength(600, MeasurementSystem.METRIC))
    }

    @Test
    fun `length imperial round-trips through inches within rounding`() {
        listOf(498L, 600L, 758L).forEach { mm ->
            val roundTripped = inchesToMillimetres(millimetresToInches(mm))
            assertTrue(kotlin.math.abs(roundTripped - mm) <= 1, "round-trip drifted for $mm mm")
        }
        assertEquals("23.6 in", formatLength(600, MeasurementSystem.IMPERIAL))
    }
}
