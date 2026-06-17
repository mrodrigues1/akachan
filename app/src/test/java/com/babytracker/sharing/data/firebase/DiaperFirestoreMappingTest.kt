package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.DiaperSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiaperFirestoreMappingTest {
    @Test
    fun `diaper map round trips`() {
        val original = DiaperSnapshot(timestamp = 5_000, type = "DIRTY", notes = "note")
        val restored = mapToDiaper(diaperToMap(original))
        assertEquals(original, restored)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val restored = mapToDiaper(mapOf<String, Any?>())
        assertEquals(0L, restored.timestamp)
        assertEquals("WET", restored.type)
        assertEquals(null, restored.notes)
    }
}
