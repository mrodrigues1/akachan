package com.babytracker.domain.usecase.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PredictNextFeedUseCaseTest {

    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: PredictNextFeedUseCase

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = Instant.parse("2026-05-19T15:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @BeforeEach
    fun setup() {
        breastfeedingRepository = mockk()
        settingsRepository = mockk()
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)
        useCase = PredictNextFeedUseCase(breastfeedingRepository, settingsRepository, clock, zone)
    }

    private fun session(start: Instant, end: Instant?): BreastfeedingSession =
        BreastfeedingSession(
            id = 0L,
            startTime = start,
            endTime = end,
            startingSide = BreastSide.LEFT,
            switchTime = null,
            notes = null,
            pausedAt = null,
            pausedDurationMs = 0L,
        )

    private fun buildEvenlySpacedSessions(
        count: Int,
        intervalMinutes: Int,
        anchor: Instant = now,
    ): List<BreastfeedingSession> {
        val mostRecentStart = anchor.minusSeconds(30 * 60)
        return (0 until count).map { i ->
            val start = mostRecentStart.minusSeconds(i.toLong() * intervalMinutes * 60)
            session(start = start, end = start.plusSeconds(10 * 60))
        }
    }

    @Test
    fun `returns null when an active session is in progress`() = runTest {
        val sessions = listOf(session(now.minusSeconds(60), end = null))
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)
        useCase().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns null when fewer than 3 valid intervals remain after filtering`() = runTest {
        val sessions = buildEvenlySpacedSessions(count = 3, intervalMinutes = 180)
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)
        useCase().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `computes mean of most-recent 5 intervals when more than 5 are valid`() = runTest {
        val mostRecentStart = now.minusSeconds(30 * 60)
        val starts = mutableListOf(mostRecentStart)
        repeat(5) { starts += starts.last().minusSeconds(120 * 60) }
        starts += starts.last().minusSeconds(240 * 60)
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(5, p!!.sampleSize)
            assertEquals(120, p.averageIntervalMinutes)
            assertEquals(mostRecentStart.plusSeconds(120 * 60), p.predictedAt)
            assertFalse(p.isOverdue)
            assertEquals(90, p.minutesUntil)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops intervals greater than 360 minutes rather than capping them`() = runTest {
        val mostRecent = now.minusSeconds(30 * 60)
        val s1 = mostRecent
        val s2 = s1.minusSeconds(180 * 60)
        val s3 = s2.minusSeconds(180 * 60)
        val s4 = s3.minusSeconds(180 * 60)
        val s5 = s4.minusSeconds(480 * 60)
        val sessions = listOf(s1, s2, s3, s4, s5).map {
            session(start = it, end = it.plusSeconds(10 * 60))
        }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(3, p!!.sampleSize)
            assertEquals(180, p.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters intervals whose endpoint falls inside quiet hours`() = runTest {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(8 * 60)

        val mostRecentLocal = Instant.parse("2026-05-19T14:30:00Z")
        val starts = (0 until 9).map { mostRecentLocal.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertTrue(p!!.sampleSize >= PredictionTuning.SAMPLE_SIZE_MIN)
            assertEquals(120, p.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quiet hours wrapping midnight (22 to 06) filter overnight endpoints`() = runTest {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(22 * 60)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(6 * 60)
        val anchor = Instant.parse("2026-05-19T17:00:00Z")
        val starts = (0 until 6).map { anchor.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(3, p!!.sampleSize)
            assertEquals(120, p.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quiet hours start equals end disables filtering`() = runTest {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)
        val sessions = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120)
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(5, p!!.sampleSize)
            assertEquals(120, p.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns null when most-recent session is older than 12 hours`() = runTest {
        val sessions = buildEvenlySpacedSessions(
            count = 6,
            intervalMinutes = 180,
            anchor = now.minusSeconds(13L * 3600 - 30 * 60),
        )
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)
        useCase().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns null when predicted time is more than 90 minutes overdue`() = runTest {
        val mostRecent = now.minusSeconds(220 * 60)
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns null when surviving daytime intervals exceed max interval minutes`() = runTest {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(8 * 60)

        val recent = Instant.parse("2026-05-19T13:00:00Z")
        val overnight = listOf(
            recent,
            Instant.parse("2026-05-19T07:00:00Z"),
            Instant.parse("2026-05-19T05:00:00Z"),
            Instant.parse("2026-05-19T03:00:00Z"),
        )
        val daytime = (0 until 6).map {
            Instant.parse("2026-05-18T19:00:00Z").minusSeconds(it.toLong() * 24 * 3600)
        }
        val sessions = (overnight + daytime).map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNull(p)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isOverdue is true when predictedAt is in the past within the grace window`() = runTest {
        val mostRecent = now.minusSeconds(150 * 60)
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertTrue(p!!.isOverdue)
            assertEquals(-30, p.minutesUntil)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `minutesUntil is positive for future predictions and negative for past predictions`() = runTest {
        val mostRecent = now.minusSeconds(30 * 60)
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertFalse(p!!.isOverdue)
            assertEquals(90, p.minutesUntil)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles DST spring-forward in America New_York without throwing`() = runTest {
        val nyZone = ZoneId.of("America/New_York")
        val nyNow = Instant.parse("2026-03-08T12:30:00Z")
        val nyClock = Clock.fixed(nyNow, nyZone)
        useCase = PredictNextFeedUseCase(breastfeedingRepository, settingsRepository, nyClock, nyZone)

        val mostRecent = Instant.parse("2026-03-08T12:00:00Z")
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(120, p!!.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles DST fall-back in America New_York without double-counting`() = runTest {
        val nyZone = ZoneId.of("America/New_York")
        val nyNow = Instant.parse("2026-11-01T12:30:00Z")
        val nyClock = Clock.fixed(nyNow, nyZone)
        useCase = PredictNextFeedUseCase(breastfeedingRepository, settingsRepository, nyClock, nyZone)

        val mostRecent = Instant.parse("2026-11-01T12:00:00Z")
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(sessions)

        useCase().test {
            val p = awaitItem()
            assertNotNull(p)
            assertEquals(120, p!!.averageIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits an updated prediction when session history changes`() = runTest {
        val sessionsFlow = MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns sessionsFlow

        val initial = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120)
        val newer = session(start = now.minusSeconds(5 * 60), end = now)
        val updated = listOf(newer) + initial

        useCase().test {
            sessionsFlow.emit(initial)
            val first = awaitItem()
            sessionsFlow.emit(updated)
            val second = awaitItem()
            assertNotNull(first)
            assertNotNull(second)
            assertNotEquals(first!!.predictedAt, second!!.predictedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
