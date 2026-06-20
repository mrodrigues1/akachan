package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.DoctorVisit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainToSnapshotDoctorVisitTest {
    @Test
    fun `maps date to epoch-ms and carries provider and notes`() {
        val visit = DoctorVisit(
            id = 5,
            date = Instant.ofEpochMilli(5_000),
            providerName = "Dr. A",
            notes = "follow up",
            // snapshotLabel / snapshotCreatedAt are local-only and must NOT appear in the snapshot.
            snapshotLabel = "local",
            snapshotCreatedAt = Instant.ofEpochMilli(4_000),
            createdAt = Instant.ofEpochMilli(1_000),
        )
        val snapshot = visit.toSnapshot()
        assertEquals(5_000L, snapshot.date)
        assertEquals("Dr. A", snapshot.providerName)
        assertEquals("follow up", snapshot.notes)
    }

    @Test
    fun `preserves null provider and notes`() {
        val visit = DoctorVisit(date = Instant.ofEpochMilli(2_000), createdAt = Instant.ofEpochMilli(1_000))
        val snapshot = visit.toSnapshot()
        assertEquals(2_000L, snapshot.date)
        assertNull(snapshot.providerName)
        assertNull(snapshot.notes)
    }
}
