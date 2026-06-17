package com.babytracker.export.data

import com.babytracker.data.local.entity.DiaperEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiaperBackupConvertersTest {
    @Test
    fun `entity to backup to entity round trips`() {
        val entity = DiaperEntity(id = 3, timestamp = 100, type = "BOTH", notes = "n", createdAt = 50)
        assertEquals(entity, entity.toBackup().toEntity())
    }
}
