package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetDayRhythmTrendUseCaseTest {
    // Fixed "now" = 2026-06-14T12:00:00Z, UTC so day fractions map cleanly to hour/24.
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var sleep: SleepRepository
    private lateinit var breastfeeding: BreastfeedingRepository
    private lateinit var bottle: BottleFeedRepository
    private lateinit var useCase: GetDayRhythmTrendUseCase

    private fun sleepRecord(start: String, end: String?, type: SleepType) = SleepRecord(
        id = 0,
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        sleepType = type,
    )

    private fun session(start: String) = BreastfeedingSession(
        id = 0,
        startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(600),
        startingSide = BreastSide.LEFT,
    )

    private fun bottle(ts: String) = BottleFeed(
        id = 0,
        clientId = ts,
        timestamp = Instant.parse(ts),
        volumeMl = 100,
        type = FeedType.FORMULA,
        createdAt = Instant.parse(ts),
    )

    @BeforeEach
    fun setup() {
        sleep = mockk()
        breastfeeding = mockk()
        bottle = mockk()
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns emptyList()
        every { bottle.getSince(any()) } returns flowOf(emptyList())
        coEvery { sleep.getCompletedRecordsSince(any()) } returns emptyList()
        useCase = GetDayRhythmTrendUseCase(sleep, breastfeeding, bottle, clock)
    }

    @Test
    fun `overnight sleep splits across both day rows`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            sleepRecord("2026-06-13T22:00:00Z", "2026-06-14T06:00:00Z", SleepType.NIGHT_SLEEP),
        )

        val result = useCase(TrendRange.SEVEN_DAYS)
        val day13 = result.first { it.date.toString() == "2026-06-13" }
        val day14 = result.first { it.date.toString() == "2026-06-14" }

        assertEquals(1, day13.sleepBlocks.size)
        assertEquals(22f / 24f, day13.sleepBlocks[0].startFraction, 0.001f)
        assertEquals(1.0f, day13.sleepBlocks[0].endFraction, 0.001f)
        assertTrue(day13.sleepBlocks[0].isNight)

        assertEquals(1, day14.sleepBlocks.size)
        assertEquals(0.0f, day14.sleepBlocks[0].startFraction, 0.001f)
        assertEquals(6f / 24f, day14.sleepBlocks[0].endFraction, 0.001f)
        assertTrue(day14.sleepBlocks[0].isNight)
    }

    @Test
    fun `nap is flagged not-night`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            sleepRecord("2026-06-14T09:00:00Z", "2026-06-14T10:30:00Z", SleepType.NAP),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertEquals(1, day.sleepBlocks.size)
        assertTrue(!day.sleepBlocks[0].isNight)
        assertEquals(9f / 24f, day.sleepBlocks[0].startFraction, 0.001f)
        assertEquals(10.5f / 24f, day.sleepBlocks[0].endFraction, 0.001f)
    }

    @Test
    fun `in-progress sleep is excluded`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            sleepRecord("2026-06-14T08:00:00Z", null, SleepType.NAP),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertTrue(day.sleepBlocks.isEmpty())
    }

    @Test
    fun `future-dated sleep is excluded`() = runTest {
        // now = 12:00Z; this nap starts at 14:00 today.
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            sleepRecord("2026-06-14T14:00:00Z", "2026-06-14T15:00:00Z", SleepType.NAP),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertTrue(day.sleepBlocks.isEmpty())
    }

    @Test
    fun `feeds become sorted day fraction marks`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T06:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(
            listOf(bottle("2026-06-14T09:00:00Z")),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertEquals(listOf(6f / 24f, 9f / 24f), day.feedMarks.map { it })
    }

    @Test
    fun `empty data yields one empty row per day`() = runTest {
        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(7, result.size)
        assertTrue(result.all { it.sleepBlocks.isEmpty() && it.feedMarks.isEmpty() })
    }
}
