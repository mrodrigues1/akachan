package com.babytracker.ui.partner

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PartnerDashboardRecordsTest {
    private val emptySnapshot = ShareSnapshot(
        lastSyncAt = Instant.EPOCH,
        baby = BabySnapshot(name = "Baby", birthDateMs = 0L, allergies = emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun sleep(endTime: Long?) = SleepSnapshot(
        id = 0, startTime = 1_000L, endTime = endTime,
        sleepType = "NAP", notes = null, clientId = "c", startedBy = "PARTNER",
    )

    @Test
    fun `empty snapshot with no optimistic sleep has no shared records`() {
        assertFalse(hasSharedDashboardRecords(emptySnapshot, activeSleep = null, mostRecentSleep = null))
    }

    @Test
    fun `optimistic completed sleep counts as shared records even when the snapshot is empty`() {
        // Regression: partner ended a session before the primary republished, so snapshot.sleepRecords
        // is empty but mostRecentSleep is set — the empty state must not show alongside that sleep.
        assertTrue(hasSharedDashboardRecords(emptySnapshot, activeSleep = null, mostRecentSleep = sleep(endTime = 2_000L)))
    }

    @Test
    fun `optimistic active sleep counts as shared records even when the snapshot is empty`() {
        assertTrue(hasSharedDashboardRecords(emptySnapshot, activeSleep = sleep(endTime = null), mostRecentSleep = null))
    }
}
