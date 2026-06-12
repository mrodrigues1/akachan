package com.babytracker.export.data

import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupConvertersTest {

    @Test
    fun `breastfeeding entity round-trips through backup dto`() {
        val entity = BreastfeedingEntity(
            id = 7, startTime = 1000, endTime = 2000, startingSide = "LEFT",
            switchTime = 1500, notes = "n", pausedAt = 1200, pausedDurationMs = 50,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `sleep entity round-trips through backup dto`() {
        val entity = SleepEntity(
            id = 3,
            startTime = 100,
            endTime = 900,
            sleepType = "NAP",
            notes = null,
            timezoneId = "America/New_York",
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `pumping entity round-trips through backup dto`() {
        val entity = PumpingEntity(
            id = 4, startTime = 10, endTime = 99, breast = "BOTH",
            volumeMl = 120, notes = "x", pausedAt = null, pausedDurationMs = 0,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `milk bag entity round-trips through backup dto`() {
        val entity = MilkBagEntity(
            id = 5, collectionDate = 111, volumeMl = 90, sourceSessionId = 4,
            usedAt = null, notes = null, createdAt = 222,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `bottle feed entity round-trips through backup dto with fresh clientId`() {
        val entity = BottleFeedEntity(
            id = 8, clientId = "client-8", timestamp = 1_000, volumeMl = 120, type = "BREAST_MILK",
            linkedMilkBagId = 5, notes = "evening", createdAt = 2_000,
        )

        val restored = entity.toBackup().toEntity()

        // The backup format carries no clientId, so import mints a fresh one.
        assertTrue(restored.clientId.isNotEmpty())
        assertNotEquals(entity.clientId, restored.clientId)
        assertEquals(entity, restored.copy(clientId = entity.clientId))
    }
}
