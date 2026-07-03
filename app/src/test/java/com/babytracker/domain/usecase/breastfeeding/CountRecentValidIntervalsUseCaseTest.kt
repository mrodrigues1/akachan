package com.babytracker.domain.usecase.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class CountRecentValidIntervalsUseCaseTest {

    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: CountRecentValidIntervalsUseCase

    private val zone = ZoneId.of("America/Sao_Paulo")

    // now = 2026-05-19T15:00:00Z → local = 2026-05-19T12:00:00-03:00 (noon, outside any quiet hours)
    private val now = Instant.parse("2026-05-19T15:00:00Z")

    @BeforeEach
    fun setup() {
        breastfeedingRepository = mockk()
        settingsRepository = mockk()
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)
        useCase = CountRecentValidIntervalsUseCase(breastfeedingRepository, settingsRepository, zone)
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

    private fun completedSession(start: Instant, durationMinutes: Long = 10L): BreastfeedingSession =
        session(start = start, end = start.plusSeconds(durationMinutes * 60))

    /**
     * Builds a list of evenly-spaced completed sessions anchored relative to [anchor].
     * The most recent session starts at anchor - 30 min; each previous one is [intervalMinutes] earlier.
     */
    private fun buildEvenlySpacedSessions(
        count: Int,
        intervalMinutes: Int,
        anchor: Instant = now,
    ): List<BreastfeedingSession> {
        val mostRecentStart = anchor.minusSeconds(30 * 60)
        return (0 until count).map { i ->
            val start = mostRecentStart.minusSeconds(i.toLong() * intervalMinutes * 60)
            completedSession(start)
        }
    }

    // ── Test 1: active session present → count is NOT short-circuited to 0 ──────────────────────

    @Test
    fun `active session in history does not short-circuit count to zero`() = runTest {
        // 5 completed sessions spaced 120 min apart → 4 valid intervals
        // Plus one active (endTime == null) session as the most recent
        val completed = buildEvenlySpacedSessions(count = 5, intervalMinutes = 120)
        val activeSession = session(start = now.minusSeconds(10 * 60), end = null)
        val sessions = listOf(activeSession) + completed

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            // Active session is excluded by the endTime != null filter.
            // 5 completed sessions → 4 intervals (all 120 min ≤ 360 cap, no quiet hours).
            assertEquals(4, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Test 2: stale most-recent (> FRESHNESS_HORIZON_HOURS) → full count returned ──────────────

    @Test
    fun `stale most-recent session older than 12 hours still returns full filtered count`() = runTest {
        // Most recent session started 24 h ago — PredictNextFeedUseCase would return null,
        // but CountRecentValidIntervalsUseCase must NOT apply the freshness gate.
        val staleAnchor = now.minusSeconds(24L * 3600)
        val sessions = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120, anchor = staleAnchor)

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(PredictionTuning.SAMPLE_SIZE_TARGET, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Test 3: overdue past grace → count unaffected ────────────────────────────────────────────

    @Test
    fun `overdue past grace window does not affect interval count`() = runTest {
        // Sessions whose predictedAt would be > 90 min in the past; count must still reflect real intervals.
        // mostRecent started 220 min ago; avg interval = 120 min → predictedAt = 100 min ago (overdue by 100 min > 90).
        val mostRecent = now.minusSeconds(220 * 60)
        val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { completedSession(it) }

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(PredictionTuning.SAMPLE_SIZE_TARGET, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Test 4: fewer than SAMPLE_SIZE_MIN valid intervals → returns actual count (not 0) ────────

    @Test
    fun `returns actual count when fewer than SAMPLE_SIZE_MIN valid intervals remain`() = runTest {
        // Only 2 valid intervals (3 sessions → 2 pairs, both ≤ 360 min).
        // PredictNextFeedUseCase would return null (below min), but count must return 2.
        val sessions = buildEvenlySpacedSessions(count = 3, intervalMinutes = 120)

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(2, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns zero when no sessions are present`() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(emptyList())

        useCase().test {
            val count = awaitItem()
            assertEquals(0, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Test 5: quiet-hours wrap-around filter applied to both endpoints ──────────────────────────

    @Test
    fun `quiet hours wrapping midnight filters intervals with endpoint inside 22-00 to 06-00 window`() = runTest {
        // Quiet hours 22:00–06:00 (UTC−3 zone → UTC 01:00–09:00)
        // start=1320 (22*60), end=360 (6*60) — wraps midnight
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(22 * 60)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(6 * 60)

        // All session start times are at 2026-05-19T15:00Z (local noon) going back 2 h each.
        // Local times cycle: 12:00, 10:00, 08:00, 06:00, 04:00, 02:00, 00:00, 22:00, 20:00, 18:00, ...
        // Sessions 5 and 6 (local 04:00 and 02:00) fall inside quiet hours and will be filtered.
        // Sessions 4 (local 06:00) is boundary — endMinute=360, localMinute=360; the condition is
        // localMinute >= 1320 || localMinute <= 360. 360 <= 360 → true → filtered as well.
        // So the count should be less than all 9 intervals.
        val anchor = Instant.parse("2026-05-19T15:00:00Z") // noon local
        val starts = (0 until 10).map { anchor.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { completedSession(it) }

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            // At minimum some intervals must be filtered by the midnight wrap-around
            assertTrue(count < PredictionTuning.SAMPLE_SIZE_TARGET, "Expected count < SAMPLE_SIZE_TARGET, got $count")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quiet hours start equals end disables all filtering and counts normally`() = runTest {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)

        val sessions = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120)

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(PredictionTuning.SAMPLE_SIZE_TARGET, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Test 6: returned count never exceeds SAMPLE_SIZE_TARGET ──────────────────────────────────

    @Test
    fun `count never exceeds SAMPLE_SIZE_TARGET even when more valid intervals exist`() = runTest {
        // 11 sessions → 10 valid intervals, all 120 min (≤ 360 cap), no quiet hours.
        // Count must be capped at SAMPLE_SIZE_TARGET (5).
        val mostRecentStart = now.minusSeconds(30 * 60)
        val starts = (0 until 11).map { mostRecentStart.minusSeconds(it.toLong() * 120 * 60) }
        val sessions = starts.map { completedSession(it) }

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(PredictionTuning.SAMPLE_SIZE_TARGET, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `intervals exceeding INTERVAL_MAX_MINUTES are dropped and reduce the count`() = runTest {
        // 5 sessions with 4 intervals; one is 480 min (> 360) → only 3 valid intervals.
        val mostRecent = now.minusSeconds(30 * 60)
        val s1 = mostRecent
        val s2 = s1.minusSeconds(180 * 60)
        val s3 = s2.minusSeconds(180 * 60)
        val s4 = s3.minusSeconds(180 * 60)
        val s5 = s4.minusSeconds(480 * 60) // exceeds cap
        val sessions = listOf(s1, s2, s3, s4, s5).map { completedSession(it) }

        every { breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT) } returns flowOf(sessions)

        useCase().test {
            val count = awaitItem()
            assertEquals(3, count)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
