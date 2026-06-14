package com.babytracker.domain.growth

import com.babytracker.domain.model.GrowthType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhoPercentileCalculatorTest {

    // WHO boys weight-for-age, 0 and 1 month (kg).
    private val boysWeight = listOf(
        LmsPoint(ageMonths = 0, l = 0.3487, m = 3.3464, s = 0.14602),
        LmsPoint(ageMonths = 1, l = 0.2297, m = 4.4709, s = 0.13395),
    )

    // WHO boys length-for-age uses L = 1 (no skew).
    private val boysLength0 = LmsPoint(ageMonths = 0, l = 1.0, m = 49.8842, s = 0.03795)

    @Test
    fun `percentile of z=0 is the median`() {
        assertEquals(50.0, WhoPercentileCalculator.percentileForZ(0.0), 1e-6)
    }

    @Test
    fun `percentile matches standard normal reference points`() {
        assertEquals(84.134, WhoPercentileCalculator.percentileForZ(1.0), 0.05)
        assertEquals(15.866, WhoPercentileCalculator.percentileForZ(-1.0), 0.05)
        assertEquals(97.0, WhoPercentileCalculator.percentileForZ(1.8807936), 0.3)
        assertEquals(3.0, WhoPercentileCalculator.percentileForZ(-1.8807936), 0.3)
    }

    @Test
    fun `zScore is zero at the median for skewed and unskewed LMS`() {
        val w = boysWeight.first()
        assertEquals(0.0, WhoPercentileCalculator.zScore(w.m, w.l, w.m, w.s), 1e-9)
        assertEquals(0.0, WhoPercentileCalculator.zScore(boysLength0.m, boysLength0.l, boysLength0.m, boysLength0.s), 1e-9)
    }

    @Test
    fun `l=0 logarithmic fallback gives median at value equals M`() {
        assertEquals(0.0, WhoPercentileCalculator.zScore(8.0, l = 0.0, m = 8.0, s = 0.1), 1e-9)
        assertTrue(WhoPercentileCalculator.zScore(9.0, l = 0.0, m = 8.0, s = 0.1) > 0.0)
    }

    @Test
    fun `boys weight birth reproduces published WHO percentiles`() {
        // WHO published 0-month boys weight: P3 ~ 2.5 kg, P50 ~ 3.3 kg, P97 ~ 4.3-4.4 kg.
        assertEquals(50.0, WhoPercentileCalculator.percentileFor(3.3464, 0.0, boysWeight)!!, 0.2)
        assertTrue(WhoPercentileCalculator.percentileFor(2.5, 0.0, boysWeight)!! in 1.5..5.0)
        assertTrue(WhoPercentileCalculator.percentileFor(4.35, 0.0, boysWeight)!! in 95.0..98.5)
    }

    @Test
    fun `valueForZ inverts zScore`() {
        val w = boysWeight.first()
        val value = 4.0
        val z = WhoPercentileCalculator.zScore(value, w.l, w.m, w.s)
        assertEquals(value, WhoPercentileCalculator.valueForZ(z, w.l, w.m, w.s), 1e-6)
    }

    @Test
    fun `lmsAt interpolates linearly between months`() {
        val mid = WhoPercentileCalculator.lmsAt(0.5, boysWeight)!!
        assertEquals((3.3464 + 4.4709) / 2.0, mid.m, 1e-6)
    }

    @Test
    fun `lmsAt returns endpoints exactly at the table bounds`() {
        assertEquals(3.3464, WhoPercentileCalculator.lmsAt(0.0, boysWeight)!!.m, 1e-9)
        assertEquals(4.4709, WhoPercentileCalculator.lmsAt(1.0, boysWeight)!!.m, 1e-9)
    }

    @Test
    fun `lmsAt suppresses ages outside the table range instead of clamping`() {
        assertNull(WhoPercentileCalculator.lmsAt(-0.5, boysWeight))
        assertNull(WhoPercentileCalculator.lmsAt(12.0, boysWeight))
        assertNull(WhoPercentileCalculator.percentileFor(9.0, 30.0, boysWeight))
    }

    @Test
    fun `lmsAt returns null for empty table`() {
        assertNull(WhoPercentileCalculator.lmsAt(0.0, emptyList()))
    }

    @Test
    fun `percentileForCanonical converts grams and millimetres to WHO units`() {
        // 3346 g at birth is the boys weight median (~50th); raw grams would be absurd.
        val weightPercentile =
            WhoPercentileCalculator.percentileForCanonical(GrowthType.WEIGHT, 3346L, 0.0, boysWeight)!!
        assertEquals(50.0, weightPercentile, 1.0)

        // 498 mm length ~= the boys 0-month median of 49.88 cm.
        val lengthTable = listOf(boysLength0)
        val lengthPercentile =
            WhoPercentileCalculator.percentileForCanonical(GrowthType.LENGTH, 499L, 0.0, lengthTable)!!
        assertEquals(50.0, lengthPercentile, 2.0)
    }

    @Test
    fun `curves returns ordered standard percentiles with median equal to M`() {
        val curves = WhoPercentileCalculator.curves(boysWeight)
        assertEquals(listOf(3, 15, 50, 85, 97), curves.map { it.percentile })

        // At each age the curve values strictly increase with percentile.
        boysWeight.indices.forEach { ageIndex ->
            val valuesByPercentile = curves.map { it.points[ageIndex].value }
            valuesByPercentile.zipWithNext().forEach { (lower, higher) ->
                assertTrue(higher > lower, "P-curves must be ordered ascending")
            }
        }

        // The P50 curve equals the median M at month 0.
        val p50 = curves.first { it.percentile == 50 }
        assertEquals(3.3464, p50.points.first().value, 1e-6)
    }
}
