package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineRecordTest {
    private val now = Instant.ofEpochMilli(10_000)

    @Test
    fun `effectiveDate prefers administered then scheduled then created`() {
        val base = VaccineRecord(name = "MMR", status = VaccineStatus.ADMINISTERED, createdAt = Instant.ofEpochMilli(1))
        assertEquals(Instant.ofEpochMilli(9), base.copy(administeredDate = Instant.ofEpochMilli(9)).effectiveDate())
        assertEquals(
            Instant.ofEpochMilli(8),
            base.copy(status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(8)).effectiveDate(),
        )
        assertEquals(Instant.ofEpochMilli(1), base.effectiveDate())
    }

    @Test
    fun `isOverdue only when scheduled and past`() {
        val past = VaccineRecord(
            name = "BCG",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5),
            createdAt = now,
        )
        val future = past.copy(scheduledDate = Instant.ofEpochMilli(20_000))
        val administered = past.copy(status = VaccineStatus.ADMINISTERED, administeredDate = now)
        assertTrue(past.isOverdue(now))
        assertFalse(future.isOverdue(now))
        assertFalse(administered.isOverdue(now))
    }
}
