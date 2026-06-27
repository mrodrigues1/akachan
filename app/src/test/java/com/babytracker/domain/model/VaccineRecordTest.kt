package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class VaccineRecordTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-21T12:00:00Z")

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

    @Test
    fun `isPastTarget only when to-schedule and a whole day past`() {
        val base = VaccineRecord(
            name = "MMR",
            status = VaccineStatus.TO_SCHEDULE,
            scheduledDate = now,
            createdAt = now,
        )
        val yesterday = base.copy(scheduledDate = now.minusSeconds(86_400))
        val earlierToday = base.copy(scheduledDate = now.minusSeconds(3_600))
        val tomorrow = base.copy(scheduledDate = now.plusSeconds(86_400))
        val noDate = base.copy(scheduledDate = null)
        val scheduledPast = base.copy(
            status = VaccineStatus.SCHEDULED,
            scheduledDate = now.minusSeconds(86_400),
        )
        assertTrue(yesterday.isPastTarget(now, zone))
        assertFalse(earlierToday.isPastTarget(now, zone))
        assertFalse(tomorrow.isPastTarget(now, zone))
        assertFalse(noDate.isPastTarget(now, zone))
        assertFalse(scheduledPast.isPastTarget(now, zone))
    }
}
