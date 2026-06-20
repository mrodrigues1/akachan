package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DoctorVisitTest {
    private val now = Instant.ofEpochMilli(10_000)
    private val base = DoctorVisit(date = now, createdAt = now)

    @Test
    fun `isUpcoming true only when date after now`() {
        assertTrue(base.copy(date = Instant.ofEpochMilli(20_000)).isUpcoming(now))
        assertFalse(base.copy(date = Instant.ofEpochMilli(5_000)).isUpcoming(now))
        assertFalse(base.isUpcoming(now)) // equal is not "after"
    }

    @Test
    fun `hasSnapshot reflects snapshotLabel presence`() {
        assertFalse(base.hasSnapshot())
        assertTrue(base.copy(snapshotLabel = "Backup 2026-06-20").hasSnapshot())
    }
}
