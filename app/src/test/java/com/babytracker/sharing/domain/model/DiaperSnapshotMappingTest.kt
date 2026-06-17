package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperSnapshotMappingTest {
    @Test
    fun `maps domain to snapshot`() {
        val snap = DiaperChange(
            id = 1,
            timestamp = Instant.ofEpochMilli(1_234),
            type = DiaperType.BOTH,
            notes = "leak",
            createdAt = Instant.ofEpochMilli(2_000),
        ).toSnapshot()
        assertEquals(1_234L, snap.timestamp)
        assertEquals("BOTH", snap.type)
        assertEquals("leak", snap.notes)
    }
}
