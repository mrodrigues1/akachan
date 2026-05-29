package com.babytracker.widget

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    private fun noneSettings(): SettingsRepository = mockk {
        every { getAppMode() } returns flowOf(AppMode.NONE)
    }

    @Test
    fun `none mode maps local repositories to WidgetData`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk { coEvery { getLatestRecord() } returns null }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = scheduler,
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals("Akira", result.babyName)
        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(SleepState.NONE, result.sleepState)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `baby repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk {
            every { getBabyProfile() } returns flow { throw IllegalStateException("datastore busted") }
        }
        val feed: BreastfeedingRepository = mockk()
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `feed repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk {
            coEvery { getLastSession() } throws IllegalStateException("room busted")
        }
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `sleep repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk {
            coEvery { getLatestRecord() } throws IllegalStateException("room busted")
        }

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `partner mode returns cached data and does not schedule a refresh`() = runTest {
        val cached = WidgetData(
            babyName = "Akira",
            lastFeedSide = BreastSide.RIGHT,
            lastFeedStart = Instant.parse("2026-05-27T10:00:00Z"),
            feedState = FeedState.RECENT,
            sleepState = SleepState.SLEEPING,
            sleepSince = Instant.parse("2026-05-27T11:00:00Z"),
        )
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf("CODE")
        }
        val cache: PartnerWidgetCache = mockk { coEvery { read("CODE") } returns cached }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(cached, result)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `partner mode cache-miss returns EMPTY and schedules an immediate refresh`() = runTest {
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf("CODE")
        }
        val cache: PartnerWidgetCache = mockk { coEvery { read("CODE") } returns null }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(WidgetData.EMPTY, result)
        verify(exactly = 1) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `partner mode with null share code returns EMPTY without reading cache or scheduling`() = runTest {
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf(null)
        }
        val cache: PartnerWidgetCache = mockk()
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(WidgetData.EMPTY, result)
        coVerifyCacheNeverRead(cache)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    private fun coVerifyCacheNeverRead(cache: PartnerWidgetCache) {
        io.mockk.coVerify(exactly = 0) { cache.read(any()) }
    }
}
