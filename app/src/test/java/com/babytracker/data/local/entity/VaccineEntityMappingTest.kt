package com.babytracker.data.local.entity

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineEntityMappingTest {
    @Test
    fun `scheduled record round trips`() {
        val record = VaccineRecord(
            id = 7,
            name = "DTaP",
            doseLabel = "1st dose",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000),
            administeredDate = null,
            notes = "left thigh",
            createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun `administered record round trips`() {
        val record = VaccineRecord(
            id = 3,
            name = "MMR",
            status = VaccineStatus.ADMINISTERED,
            administeredDate = Instant.ofEpochMilli(9_000),
            createdAt = Instant.ofEpochMilli(8_000),
        )
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun `unknown stored status maps to ADMINISTERED`() {
        val entity = VaccineEntity(id = 1, name = "x", status = "???", createdAt = 0)
        assertEquals(VaccineStatus.ADMINISTERED, entity.toDomain().status)
    }
}
