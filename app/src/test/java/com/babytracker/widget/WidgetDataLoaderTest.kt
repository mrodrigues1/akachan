package com.babytracker.widget

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WidgetDataLoaderTest {

    private val happyBaby = Baby(name = "Akira", birthDate = LocalDate.of(2025, 12, 1))
    private val happyFeed = BreastfeedingSession(
        id = 1,
        startTime = Instant.parse("2026-05-27T10:00:00Z"),
        endTime = Instant.parse("2026-05-27T10:15:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @Test
    fun `happy path maps repositories to WidgetData`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk { coEvery { getLatestRecord() } returns null }

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals("Akira", result.babyName)
        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(SleepState.NONE, result.sleepState)
    }

    @Test
    fun `baby repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk {
            every { getBabyProfile() } returns flow { throw IllegalStateException("datastore busted") }
        }
        val feed: BreastfeedingRepository = mockk()
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `feed repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk {
            coEvery { getLastSession() } throws IllegalStateException("room busted")
        }
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `sleep repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk {
            coEvery { getLatestRecord() } throws IllegalStateException("room busted")
        }

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }
}
