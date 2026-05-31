package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TileStateResolverTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository

    private val fixedNow = Instant.ofEpochSecond(1_000_000)
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        settingsRepository = mockk()
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
    }

    private fun resolver() = TileStateResolver(
        settingsRepository = settingsRepository,
        breastfeedingRepository = breastfeedingRepository,
        sleepRepository = sleepRepository,
        clock = clock,
    )

    private fun setupAvailable(mode: AppMode = AppMode.NONE, onboarded: Boolean = true) {
        every { settingsRepository.isOnboardingComplete() } returns flowOf(onboarded)
        every { settingsRepository.getAppMode() } returns flowOf(mode)
    }

    private fun activeSession(): BreastfeedingSession = BreastfeedingSession(
        startTime = fixedNow.minusSeconds(65 * 60),
        startingSide = BreastSide.LEFT,
    )

    private fun activeRecord(): SleepRecord = SleepRecord(
        startTime = fixedNow.minusSeconds(90 * 60),
        sleepType = SleepType.NAP,
    )

    // Feed tests

    @Test
    fun modeNoneOnboardedNoActiveSessionReturnsInactiveFeed() = runTest {
        setupAvailable(AppMode.NONE)
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.INACTIVE, state.availability)
        assertEquals("Start Feed", state.label)
        assertEquals(null, state.subtitle)
    }

    @Test
    fun modePrimaryOnboardedNoActiveSessionReturnsInactiveFeed() = runTest {
        setupAvailable(AppMode.PRIMARY)
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.INACTIVE, state.availability)
        assertEquals("Start Feed", state.label)
    }

    @Test
    fun modeNoneOnboardedActiveSessionReturnsActiveFeedWithElapsed() = runTest {
        setupAvailable(AppMode.NONE)
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession())

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.ACTIVE, state.availability)
        assertEquals("Stop Feed", state.label)
        assertEquals("1h 5m", state.subtitle)
    }

    @Test
    fun modePartnerReturnsFeedUnavailable() = runTest {
        setupAvailable(AppMode.PARTNER)

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.UNAVAILABLE, state.availability)
        assertEquals("Feed", state.label)
        assertEquals("Open app", state.subtitle)
    }

    @Test
    fun onboardingIncompleteReturnsFeedUnavailable() = runTest {
        setupAvailable(AppMode.NONE, onboarded = false)

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.UNAVAILABLE, state.availability)
        assertEquals("Feed", state.label)
    }

    @Test
    fun feedRepositoryExceptionReturnsUnavailable() = runTest {
        setupAvailable(AppMode.NONE)
        every { breastfeedingRepository.getActiveSession() } throws RuntimeException("db error")

        val state = resolver().resolveFeed()

        assertEquals(TileAvailability.UNAVAILABLE, state.availability)
    }

    // Sleep tests

    @Test
    fun modeNoneOnboardedNoInProgressSleepReturnsInactiveSleep() = runTest {
        setupAvailable(AppMode.NONE)
        coEvery { sleepRepository.getLatestRecord() } returns null

        val state = resolver().resolveSleep()

        assertEquals(TileAvailability.INACTIVE, state.availability)
        assertEquals("Start Sleep", state.label)
        assertEquals(null, state.subtitle)
    }

    @Test
    fun modeNoneOnboardedInProgressSleepReturnsActiveSleepWithElapsed() = runTest {
        setupAvailable(AppMode.NONE)
        coEvery { sleepRepository.getLatestRecord() } returns activeRecord()

        val state = resolver().resolveSleep()

        assertEquals(TileAvailability.ACTIVE, state.availability)
        assertEquals("Stop Sleep", state.label)
        assertEquals("1h 30m", state.subtitle)
    }

    @Test
    fun modeNoneOnboardedCompletedSleepReturnsInactive() = runTest {
        setupAvailable(AppMode.NONE)
        val completed = SleepRecord(
            startTime = fixedNow.minusSeconds(3600),
            endTime = fixedNow.minusSeconds(1800),
            sleepType = SleepType.NAP,
        )
        coEvery { sleepRepository.getLatestRecord() } returns completed

        val state = resolver().resolveSleep()

        assertEquals(TileAvailability.INACTIVE, state.availability)
        assertEquals("Start Sleep", state.label)
    }

    @Test
    fun modePartnerReturnsSleepUnavailable() = runTest {
        setupAvailable(AppMode.PARTNER)

        val state = resolver().resolveSleep()

        assertEquals(TileAvailability.UNAVAILABLE, state.availability)
        assertEquals("Sleep", state.label)
        assertEquals("Open app", state.subtitle)
    }

    @Test
    fun sleepRepositoryExceptionReturnsUnavailable() = runTest {
        setupAvailable(AppMode.NONE)
        coEvery { sleepRepository.getLatestRecord() } throws RuntimeException("db error")

        val state = resolver().resolveSleep()

        assertEquals(TileAvailability.UNAVAILABLE, state.availability)
    }
}
