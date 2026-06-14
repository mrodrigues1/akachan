package com.babytracker.data.growth

import androidx.test.core.app.ApplicationProvider
import com.babytracker.domain.growth.WhoPercentileCalculator
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetWhoReferenceDataTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = Json { ignoreUnknownKeys = true }
    private val reference = AssetWhoReferenceData(context, json)

    @Test
    fun `every sex-specific table loads 25 monthly rows in ascending age order`() = runTest {
        val types = GrowthType.entries
        val sexes = listOf(BabySex.MALE, BabySex.FEMALE)
        for (type in types) {
            for (sex in sexes) {
                val table = reference.lmsTable(type, sex)
                assertEquals("$type/$sex point count", 25, table.size)
                assertEquals(0, table.first().ageMonths)
                assertEquals(24, table.last().ageMonths)
                assertEquals(table.map { it.ageMonths }, table.map { it.ageMonths }.sorted())
                assertTrue(table.all { it.m > 0.0 && it.s > 0.0 })
            }
        }
    }

    @Test
    fun `unspecified sex has no reference table`() = runTest {
        assertTrue(reference.lmsTable(GrowthType.WEIGHT, BabySex.UNSPECIFIED).isEmpty())
    }

    @Test
    fun `birth medians match WHO published values`() = runTest {
        assertEquals(3.3464, reference.lmsTable(GrowthType.WEIGHT, BabySex.MALE).first().m, 1e-4)
        assertEquals(3.2322, reference.lmsTable(GrowthType.WEIGHT, BabySex.FEMALE).first().m, 1e-4)
        assertEquals(49.8842, reference.lmsTable(GrowthType.LENGTH, BabySex.MALE).first().m, 1e-4)
        assertEquals(34.4618, reference.lmsTable(GrowthType.HEAD_CIRC, BabySex.MALE).first().m, 1e-4)
    }

    @Test
    fun `measuring exactly the median yields the 50th percentile on real data`() = runTest {
        val table = reference.lmsTable(GrowthType.WEIGHT, BabySex.FEMALE)
        val twelveMonth = table.first { it.ageMonths == 12 }
        val percentile = WhoPercentileCalculator.percentileFor(twelveMonth.m, 12.0, table)!!
        assertEquals(50.0, percentile, 0.2)
    }
}
