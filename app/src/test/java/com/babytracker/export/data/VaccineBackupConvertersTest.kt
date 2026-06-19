package com.babytracker.export.data

import com.babytracker.data.local.entity.VaccineEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VaccineBackupConvertersTest {
    @Test
    fun `administered entity to backup to entity round trips`() {
        val entity = VaccineEntity(
            id = 3, name = "BCG", doseLabel = "Dose 1", status = "ADMINISTERED",
            scheduledDate = null, administeredDate = 1_000, notes = "left arm", createdAt = 50,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `scheduled entity to backup to entity round trips`() {
        val entity = VaccineEntity(
            id = 7, name = "Hep B", doseLabel = null, status = "SCHEDULED",
            scheduledDate = 9_000, administeredDate = null, notes = null, createdAt = 80,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }
}
