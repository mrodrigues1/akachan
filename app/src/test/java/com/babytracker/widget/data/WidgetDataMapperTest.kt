package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WidgetDataMapperTest {

    private val babyName = "Akira"

    private fun feed(
        start: Instant = Instant.parse("2026-05-24T10:00:00Z"),
        end: Instant? = Instant.parse("2026-05-24T10:15:00Z"),
        side: BreastSide = BreastSide.LEFT,
    ) = BreastfeedingSession(
        id = 1,
        startTime = start,
        endTime = end,
        startingSide = side,
    )

    private fun sleep(
        start: Instant = Instant.parse("2026-05-24T11:00:00Z"),
        end: Instant? = null,
        type: SleepType = SleepType.NAP,
    ) = SleepRecord(
        id = 1,
        startTime = start,
        endTime = end,
        sleepType = type,
    )

    @Test
    fun `null name falls back to "Baby"`() {
        val result = toWidgetData(
            babyName = null,
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals("Baby", result.babyName)
    }

    @Test
    fun `blank name falls back to "Baby"`() {
        val result = toWidgetData(
            babyName = "   ",
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals("Baby", result.babyName)
    }

    @Test
    fun `name is used when non-blank`() {
        val result = toWidgetData(
            babyName = babyName,
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals(babyName, result.babyName)
    }

    @Test
    fun `no feed produces null side and start`() {
        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = null)

        assertNull(result.lastFeedSide)
        assertNull(result.lastFeedStart)
        assertEquals(FeedState.NONE, result.feedState)
    }

    @Test
    fun `completed feed copies side and startTime`() {
        val session = feed(
            start = Instant.parse("2026-05-24T08:00:00Z"),
            end = Instant.parse("2026-05-24T08:20:00Z"),
            side = BreastSide.RIGHT,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T08:00:00Z"), result.lastFeedStart)
        assertEquals(FeedState.RECENT, result.feedState)
    }

    @Test
    fun `in-progress feed still uses startTime as last feed time`() {
        val session = feed(
            start = Instant.parse("2026-05-24T09:00:00Z"),
            end = null,
            side = BreastSide.LEFT,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T09:00:00Z"), result.lastFeedStart)
        assertEquals(FeedState.ACTIVE, result.feedState)
    }

    @Test
    fun `paused feed maps to PAUSED state`() {
        val session = feed(
            start = Instant.parse("2026-05-24T09:00:00Z"),
            end = null,
            side = BreastSide.LEFT,
        ).copy(pausedAt = Instant.parse("2026-05-24T09:08:00Z"))

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(FeedState.PAUSED, result.feedState)
    }

    @Test
    fun `null sleep maps to NONE state`() {
        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = null)

        assertEquals(SleepState.NONE, result.sleepState)
        assertNull(result.sleepSince)
    }

    @Test
    fun `in-progress sleep maps to SLEEPING with sleepSince = startTime`() {
        val record = sleep(
            start = Instant.parse("2026-05-24T11:00:00Z"),
            end = null,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = record)

        assertEquals(SleepState.SLEEPING, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T11:00:00Z"), result.sleepSince)
    }

    @Test
    fun `completed sleep maps to AWAKE with sleepSince = endTime`() {
        val record = sleep(
            start = Instant.parse("2026-05-24T11:00:00Z"),
            end = Instant.parse("2026-05-24T12:00:00Z"),
        )

        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = record)

        assertEquals(SleepState.AWAKE, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T12:00:00Z"), result.sleepSince)
    }

    @Test
    fun `switched feed reports opposite side as effective side`() {
        val session = BreastfeedingSession(
            id = 1,
            startTime = Instant.parse("2026-05-24T08:00:00Z"),
            endTime = Instant.parse("2026-05-24T08:20:00Z"),
            startingSide = BreastSide.LEFT,
            switchTime = Instant.parse("2026-05-24T08:10:00Z"),
        )

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
    }

    @Test
    fun `combined feed plus in-progress sleep populates both rows`() {
        val session = feed(side = BreastSide.RIGHT)
        val record = sleep(end = null)

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = record)

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(SleepState.SLEEPING, result.sleepState)
    }

    private fun snapshot(
        babyName: String = "Akira",
        sessions: List<SessionSnapshot> = emptyList(),
        sleeps: List<SleepSnapshot> = emptyList(),
    ) = ShareSnapshot(
        lastSyncAt = Instant.parse("2026-05-24T12:00:00Z"),
        baby = BabySnapshot(name = babyName, birthDateMs = 0L, allergies = emptyList()),
        sessions = sessions,
        sleepRecords = sleeps,
    )

    private fun sessionSnapshot(
        id: Long = 1,
        startMs: Long = Instant.parse("2026-05-24T10:00:00Z").toEpochMilli(),
        endMs: Long? = Instant.parse("2026-05-24T10:15:00Z").toEpochMilli(),
        side: String = "LEFT",
        switchMs: Long? = null,
    ) = SessionSnapshot(
        id = id,
        startTime = startMs,
        endTime = endMs,
        startingSide = side,
        switchTime = switchMs,
        pausedDurationMs = 0L,
        notes = null,
    )

    private fun sleepSnapshot(
        id: Long = 1,
        startMs: Long = Instant.parse("2026-05-24T11:00:00Z").toEpochMilli(),
        endMs: Long? = null,
        type: String = "NAP",
    ) = SleepSnapshot(
        id = id,
        startTime = startMs,
        endTime = endMs,
        sleepType = type,
        notes = null,
    )

    @Test
    fun `snapshot picks the latest session by startTime`() {
        val older = sessionSnapshot(id = 1, startMs = Instant.parse("2026-05-24T08:00:00Z").toEpochMilli(), side = "LEFT")
        val newer = sessionSnapshot(id = 2, startMs = Instant.parse("2026-05-24T10:00:00Z").toEpochMilli(), side = "RIGHT")

        val result = toWidgetData(snapshot(sessions = listOf(older, newer)))

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T10:00:00Z"), result.lastFeedStart)
    }

    @Test
    fun `snapshot open session maps to ACTIVE`() {
        val result = toWidgetData(snapshot(sessions = listOf(sessionSnapshot(endMs = null))))

        assertEquals(FeedState.ACTIVE, result.feedState)
    }

    @Test
    fun `snapshot closed session maps to RECENT`() {
        val result = toWidgetData(snapshot(sessions = listOf(sessionSnapshot())))

        assertEquals(FeedState.RECENT, result.feedState)
    }

    @Test
    fun `snapshot with no sessions maps to feed NONE`() {
        val result = toWidgetData(snapshot(sessions = emptyList()))

        assertEquals(FeedState.NONE, result.feedState)
        assertNull(result.lastFeedSide)
        assertNull(result.lastFeedStart)
    }

    @Test
    fun `snapshot switched session reports opposite side`() {
        val switched = sessionSnapshot(
            side = "LEFT",
            switchMs = Instant.parse("2026-05-24T10:10:00Z").toEpochMilli(),
        )

        val result = toWidgetData(snapshot(sessions = listOf(switched)))

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
    }

    @Test
    fun `snapshot picks the latest sleep and maps SLEEPING`() {
        val older = sleepSnapshot(id = 1, startMs = Instant.parse("2026-05-24T09:00:00Z").toEpochMilli(), endMs = Instant.parse("2026-05-24T09:30:00Z").toEpochMilli())
        val newer = sleepSnapshot(id = 2, startMs = Instant.parse("2026-05-24T11:00:00Z").toEpochMilli(), endMs = null)

        val result = toWidgetData(snapshot(sleeps = listOf(older, newer)))

        assertEquals(SleepState.SLEEPING, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T11:00:00Z"), result.sleepSince)
    }

    @Test
    fun `snapshot with no sleeps maps to sleep NONE`() {
        val result = toWidgetData(snapshot(sleeps = emptyList()))

        assertEquals(SleepState.NONE, result.sleepState)
        assertNull(result.sleepSince)
    }

    @Test
    fun `snapshot blank baby name falls back to "Baby"`() {
        val result = toWidgetData(snapshot(babyName = "   "))

        assertEquals("Baby", result.babyName)
    }
}
