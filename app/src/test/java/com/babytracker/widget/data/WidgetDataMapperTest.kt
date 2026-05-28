package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
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
}
