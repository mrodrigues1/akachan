package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class VaccineRecordTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-21T12:00:00Z")

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
    fun `isOverdue only when scheduled and a whole day past`() {
        val base = VaccineRecord(
            name = "BCG",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = now,
            createdAt = now,
        )
        val yesterday = base.copy(scheduledDate = now.minusSeconds(86_400))
        // Earlier the same calendar day: the screenshot's "Overdue by 0 days" case — must NOT be overdue.
        val earlierToday = base.copy(scheduledDate = now.minusSeconds(3_600))
        val tomorrow = base.copy(scheduledDate = now.plusSeconds(86_400))
        val administered = base.copy(
            status = VaccineStatus.ADMINISTERED,
            administeredDate = now,
            scheduledDate = null,
        )
        assertTrue(yesterday.isOverdue(now, zone))
        assertFalse(earlierToday.isOverdue(now, zone))
        assertFalse(tomorrow.isOverdue(now, zone))
        assertFalse(administered.isOverdue(now, zone))
    }
}
