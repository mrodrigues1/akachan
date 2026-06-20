package com.babytracker.data.local.entity

import com.babytracker.domain.model.DoctorVisit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DoctorVisitEntityMappingTest {
    @Test
    fun `full visit round trips`() {
        val visit = DoctorVisit(
            id = 7,
            date = Instant.ofEpochMilli(5_000),
            providerName = "Dr. Tanaka",
            notes = "Bring growth chart",
            snapshotLabel = "Backup 2026-06-20",
            snapshotCreatedAt = Instant.ofEpochMilli(4_000),
            createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(visit, visit.toEntity().toDomain())
    }

    @Test
    fun `minimal visit round trips with nulls`() {
        val visit = DoctorVisit(id = 2, date = Instant.ofEpochMilli(9_000), createdAt = Instant.ofEpochMilli(8_000))
        assertEquals(visit, visit.toEntity().toDomain())
    }
}
