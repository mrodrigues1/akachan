package com.babytracker.export.data

import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupConvertersDoctorVisitTest {
    @Test
    fun `visit with snapshot round trips`() {
        val entity = DoctorVisitEntity(
            id = 3, date = 5_000, providerName = "Dr. Tanaka", notes = "bring chart",
            snapshotLabel = "Data snapshot", snapshotCreatedAt = 4_000, createdAt = 1_000,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `minimal visit round trips with nulls`() {
        val entity = DoctorVisitEntity(id = 9, date = 8_000, createdAt = 7_000)
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `inbox question round trips`() {
        val entity = VisitQuestionEntity(id = 1, text = "rash?", answered = false, visitId = null, createdAt = 100)
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `attached answered question round trips`() {
        val entity = VisitQuestionEntity(id = 2, text = "sleep?", answered = true, visitId = 9, createdAt = 200)
        assertEquals(entity, entity.toBackup().toEntity())
    }
}
