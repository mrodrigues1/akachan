package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeatureDomainTest {

    @Test
    fun `every feature belongs to exactly one domain`() {
        val mapped = FeatureDomain.entries.flatMap { it.features }
        assertEquals(AppFeature.entries.toList().sorted(), mapped.sorted())
        assertEquals(mapped.size, mapped.toSet().size) // no feature in two domains
    }

    @Test
    fun `feeding domain groups the four feeding trackers`() {
        assertEquals(
            listOf(AppFeature.BREASTFEEDING, AppFeature.BOTTLE_FEED, AppFeature.PUMPING, AppFeature.INVENTORY),
            FeatureDomain.FEEDING.features,
        )
        assertFalse(FeatureDomain.FEEDING.isSingleFeature)
    }

    @Test
    fun `single-feature domains report isSingleFeature`() {
        assertTrue(FeatureDomain.SLEEP.isSingleFeature)
        assertTrue(FeatureDomain.DIAPERS.isSingleFeature)
    }
}
