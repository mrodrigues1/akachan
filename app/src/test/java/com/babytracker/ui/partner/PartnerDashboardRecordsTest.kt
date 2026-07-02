package com.babytracker.ui.partner

import com.babytracker.domain.model.GrowthType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.DoctorVisitSnapshot
import com.babytracker.sharing.domain.model.GrowthSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `recentBottleFeeds returns the three newest feeds newest first`() {
        val feeds = listOf(10L, 40L, 20L, 30L).map { bottle(timestamp = it) }

        assertEquals(listOf(40L, 30L, 20L), recentBottleFeeds(feeds).map { it.timestamp })
    }

    @Test
    fun `upcomingOrLatestVisit prefers the next upcoming visit`() {
        val visits = listOf(100L, 300L, 500L).map { DoctorVisitSnapshot(date = it) }

        assertEquals(300L, upcomingOrLatestVisit(visits, nowMs = 200L)?.date)
    }

    @Test
    fun `upcomingOrLatestVisit falls back to the most recent past visit`() {
        val visits = listOf(100L, 300L).map { DoctorVisitSnapshot(date = it) }

        assertEquals(300L, upcomingOrLatestVisit(visits, nowMs = 400L)?.date)
    }

    @Test
    fun `upcomingOrLatestVisit is null without visits`() {
        assertNull(upcomingOrLatestVisit(emptyList(), nowMs = 400L))
    }

    @Test
    fun `latestGrowthPerType keeps the newest measurement per category and drops empty ones`() {
        val growth = listOf(
            GrowthSnapshot(type = GrowthType.WEIGHT.name, takenAtMs = 1L, valueCanonical = 4000L),
            GrowthSnapshot(type = GrowthType.WEIGHT.name, takenAtMs = 2L, valueCanonical = 4500L),
            GrowthSnapshot(type = GrowthType.LENGTH.name, takenAtMs = 1L, valueCanonical = 550L),
        )

        val latest = latestGrowthPerType(growth)

        assertEquals(
            listOf(GrowthType.WEIGHT to 4500L, GrowthType.LENGTH to 550L),
            latest.map { (type, snapshot) -> type to snapshot.valueCanonical },
        )
    }

    private fun bottle(timestamp: Long) = BottleFeedSnapshot(timestamp = timestamp, volumeMl = 100, type = "FORMULA")
}
