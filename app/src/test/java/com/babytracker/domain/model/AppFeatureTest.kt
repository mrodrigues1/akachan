package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppFeatureTest {

    @Test
    fun `ALL contains every feature`() {
        assertEquals(AppFeature.entries.toSet(), AppFeature.ALL)
    }

    @Test
    fun `deserialize null returns ALL`() {
        assertEquals(AppFeature.ALL, AppFeature.deserialize(null))
    }

    @Test
    fun `deserialize blank returns ALL`() {
        assertEquals(AppFeature.ALL, AppFeature.deserialize("   "))
    }

    @Test
    fun `deserialize empty after parsing returns ALL`() {
        // Only unknown names -> nothing valid parsed -> fall back to ALL (never empty).
        assertEquals(AppFeature.ALL, AppFeature.deserialize("UNKNOWN,GONE"))
    }

    @Test
    fun `deserialize drops unknown names but keeps known ones`() {
        assertEquals(
            setOf(AppFeature.SLEEP, AppFeature.GROWTH),
            AppFeature.deserialize("SLEEP, GONE ,GROWTH"),
        )
    }

    @Test
    fun `serialize then deserialize round-trips`() {
        val features = setOf(AppFeature.BREASTFEEDING, AppFeature.DIAPERS)
        assertEquals(features, AppFeature.deserialize(AppFeature.serialize(features)))
    }

    @Test
    fun `serialize of empty set is empty string`() {
        assertTrue(AppFeature.serialize(emptySet()).isEmpty())
    }
}
