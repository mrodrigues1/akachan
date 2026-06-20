package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.DoctorVisitSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FirestoreSnapshotDoctorVisitTest {

    private fun baseSnapshot(visits: List<DoctorVisitSnapshot>) = ShareSnapshot(
        lastSyncAt = Instant.ofEpochSecond(100),
        baby = BabySnapshot("Aiko", 1000L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
        doctorVisits = visits,
    )

    @Test
    fun `doctor visits round-trip through the map`() {
        val snapshot = baseSnapshot(
            listOf(
                DoctorVisitSnapshot(date = 5_000, providerName = "Dr. A", notes = "n"),
                DoctorVisitSnapshot(date = 6_000, providerName = null, notes = null),
            ),
        )
        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))
        assertEquals(snapshot.doctorVisits, roundTripped.doctorVisits)
    }

    @Test
    fun `missing doctorVisits field deserializes to an empty list`() {
        val withoutVisits = snapshotToMap(baseSnapshot(emptyList())) - "doctorVisits"
        val parsed = mapToSnapshot(withoutVisits)
        assertTrue(parsed.doctorVisits.isEmpty())
    }
}
