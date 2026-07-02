package com.babytracker.domain.usecase.sleep

import app.cash.turbine.test
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BabyEventRepository
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PredictSleepWindowUseCaseTest {

    private lateinit var sleepRepository: SleepRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var babyRepository: BabyRepository
    private lateinit var babyEventRepository: BabyEventRepository

    private val fixedNow = Instant.parse("2024-06-15T20:00:00Z")
    private val fixedZoneId = ZoneOffset.UTC
    private val fixedClock = Clock.fixed(fixedNow, fixedZoneId)

    private lateinit var useCase: PredictSleepWindowUseCase

    @BeforeEach
    fun setUp() {
        sleepRepository = mockk()
        breastfeedingRepository = mockk()
        babyRepository = mockk()
        babyEventRepository = mockk()
        every { babyEventRepository.getEventsSince(any()) } returns flowOf(emptyList())
        useCase = PredictSleepWindowUseCase(
            sleepRepository, breastfeedingRepository, babyRepository, babyEventRepository, fixedClock, fixedZoneId,
        )
    }

    // birthDate is set relative to fixedNow so the use case's clock-based ageInWeeks computation
    // yields exactly `weeks` weeks when the injected clock reads fixedNow (2024-06-15T20:00:00Z).
    private fun babyOfWeeks(weeks: Int) = Baby(
        name = "TestBaby",
        birthDate = LocalDate.ofInstant(fixedNow, fixedZoneId).minusWeeks(weeks.toLong()),
    )

    /**
     * 9 records over 4 local days (Jun 12–15). Produces 5 valid wake intervals:
     * 4 × 180 min (within-day) + 1 × 120 min (final). IQR = 30 min (< 45 min ceiling).
     *
     * Layout per day (base = fixedNow minus daysAgo days):
     *   morning nap: [base-9h, base-8h]
     *   afternoon nap: [base-5h, base-4h]
     * Final record: [fixedNow-2h, fixedNow-1h]
     */
    private fun sufficientSleepRecords(): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        for (daysAgo in 3 downTo 0) {
            val dayBase = fixedNow.minus(Duration.ofDays(daysAgo.toLong()))
            val morningEnd = dayBase.minus(Duration.ofHours(8))
            val afternoonEnd = dayBase.minus(Duration.ofHours(4))
            records += SleepRecord(id++, morningEnd.minus(Duration.ofHours(1)), morningEnd, SleepType.NAP)
            records += SleepRecord(id++, afternoonEnd.minus(Duration.ofHours(1)), afternoonEnd, SleepType.NAP)
        }
        val lastEnd = fixedNow.minus(Duration.ofHours(1))
        records += SleepRecord(id, lastEnd.minus(Duration.ofHours(1)), lastEnd, SleepType.NAP)
        return records.sortedBy { it.startTime }
    }

    /**
     * 22 records: 3 naps per day × 7 days + 1 final. Produces 15 valid 2h wake intervals.
     * qualityC = min(15/14, 1.0) = 1.0. Used to assert confidence is capped at MEDIUM.
     *
     * Nap ends per day (base = fixedNow - daysAgo): base-9h, base-6h, base-3h.
     * Final: fixedNow-1h end, 2h gap from last day's evening nap.
     */
    private fun fullyPersonalizedRecords(): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        for (daysAgo in 6 downTo 0) {
            val dayBase = fixedNow.minus(Duration.ofDays(daysAgo.toLong()))
            for (hoursBeforeBase in listOf(9L, 6L, 3L)) {
                val napEnd = dayBase.minus(Duration.ofHours(hoursBeforeBase))
                records += SleepRecord(id++, napEnd.minus(Duration.ofHours(1)), napEnd, SleepType.NAP)
            }
        }
        val lastEnd = fixedNow.minus(Duration.ofHours(1))
        records += SleepRecord(id, lastEnd.minus(Duration.ofHours(1)), lastEnd, SleepType.NAP)
        return records.sortedBy { it.startTime }
    }

    /**
     * Dense prior days (3 naps each over Jun 12–14 → 6 valid 180-min wake intervals, 3 local days)
     * plus exactly ONE nap today (Jun 15) ending 1h before fixedNow. With no NIGHT_SLEEP records the
     * learned bedtime is null, so resolveNextSleepType returns NAP. napCountToday = 1 < expectedNaps
     * (2 at 12w) → the real NapBudgetFactor fires with a "Nap deficit" reason. Used to prove the use
     * case wires NapBudgetFactor (a Neutral default would omit the reason).
     */
    private fun napDeficitRecords(): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        for (daysAgo in 3 downTo 1) {
            val dayBase = fixedNow.minus(Duration.ofDays(daysAgo.toLong()))
            for (hour in listOf(8L, 12L, 16L)) {
                val start = dayBase.minus(Duration.ofHours(20 - hour))
                records += SleepRecord(id++, start, start.plus(Duration.ofHours(1)), SleepType.NAP)
            }
        }
        // Single nap today, ending 1h before now → fresh lastWake, napCountToday = 1.
        val todayEnd = fixedNow.minus(Duration.ofHours(1))
        records += SleepRecord(id, todayEnd.minus(Duration.ofHours(1)), todayEnd, SleepType.NAP)
        return records.sortedBy { it.startTime }
    }

    // Open record started 1h ago — within MAX_OPEN_SLEEP_AGE_HOURS (18h) → triggers CurrentlySleeping.
    private fun openSleepRecord() = SleepRecord(
        id = 99,
        startTime = fixedNow.minus(Duration.ofHours(1)),
        endTime = null,
        sleepType = SleepType.NAP,
    )

    // Open record with a future startTime (clock skew / bad import) → predicate rejects it because
    // startTime > now, so CurrentlySleeping is not emitted.
    private fun futureSleepRecord() = SleepRecord(
        id = 97,
        startTime = fixedNow.plus(Duration.ofHours(1)),
        endTime = null,
        sleepType = SleepType.NAP,
    )

    // Open record started 20h ago — older than MAX_OPEN_SLEEP_AGE_HOURS (18h) → staleness filter
    // discards it, so CurrentlySleeping is not emitted.
    private fun staleSleepRecord() = SleepRecord(
        id = 98,
        startTime = fixedNow.minus(Duration.ofHours(20)),
        endTime = null,
        sleepType = SleepType.NAP,
    )

    // Recent open feed — within MAX_OPEN_FEED_AGE_HOURS (4h) → SleepFeatureExtractor includes it.
    private fun openFeedSession() = BreastfeedingSession(
        id = 2,
        startTime = fixedNow.minus(Duration.ofMinutes(30)),
        endTime = null,
        startingSide = BreastSide.LEFT,
    )

    // Stale open feed — older than MAX_OPEN_FEED_AGE_HOURS (4h) → SleepFeatureExtractor filters it out,
    // so features.feedIntervals has no open entry and AfterActiveFeed is not emitted.
    private fun staleFeedSession() = BreastfeedingSession(
        id = 3,
        startTime = fixedNow.minus(Duration.ofHours(6)),
        endTime = null,
        startingSide = BreastSide.LEFT,
    )

    @Nested
    inner class CurrentlySleepingTests {

        @Test
        fun `emits CurrentlySleeping when open sleep record exists`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(listOf(openSleepRecord()))
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.CurrentlySleeping)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `future-dated open sleep record does not emit CurrentlySleeping`() = runTest {
            // startTime 1h in the future — clock skew or bad import. Negative age subtraction
            // must not satisfy the staleness bound; requires explicit startTime <= now check.
            every { sleepRepository.getAllRecords() } returns flowOf(listOf(futureSleepRecord()))
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state !is SleepPredictionState.CurrentlySleeping) {
                    "Future-dated open sleep record must not trigger CurrentlySleeping — got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `stale open sleep record (older than MAX_OPEN_SLEEP_AGE_HOURS) does not emit CurrentlySleeping`() = runTest {
            // A record with endTime=null but started 20h ago must not permanently suppress predictions.
            // The staleness check mirrors SleepFeatureExtractor.isPossibleAt() so both paths agree.
            every { sleepRepository.getAllRecords() } returns flowOf(listOf(staleSleepRecord()))
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state !is SleepPredictionState.CurrentlySleeping) {
                    "Stale open sleep record (>18h) must not trigger CurrentlySleeping — got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class CueLedTests {

        @Test
        fun `emits CueLed when baby is under 6 weeks old`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(3))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.CueLed)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `does not emit CueLed when baby is exactly 6 weeks old`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(6))

            useCase().test {
                val state = awaitItem()
                assertTrue(state !is SleepPredictionState.CueLed)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `injected clock controls cue-led boundary — same baby is 3w relative to fixedClock`() = runTest {
            // birthDate = fixedNow - 3w. Use case computes ageInWeeks from fixedClock, not
            // LocalDate.now(), so the result is deterministic regardless of the real wall clock.
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(3))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.CueLed) {
                    "ageInWeeks must be computed from injected clock, not LocalDate.now() — got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class NeedMoreDataTests {

        @Test
        fun `emits NeedMoreData with correct progress when no sleep history`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.NeedMoreData)
                val progress = (state as SleepPredictionState.NeedMoreData).progress
                assertEquals(0, progress.completedIntervals)
                assertEquals(5, progress.requiredIntervals)
                assertTrue(progress.hint.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `sparse logger — fewer than MIN_COMPLETED_INTERVALS emits NeedMoreData not Window`() = runTest {
            // 3 records → 2 wake intervals → below MIN_COMPLETED_INTERVALS (5)
            val sparseRecords = sufficientSleepRecords().takeLast(3)
            every { sleepRepository.getAllRecords() } returns flowOf(sparseRecords)
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.NeedMoreData) {
                    "Sparse logger must not produce a Window — got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class AfterActiveFeedTests {

        @Test
        fun `emits AfterActiveFeed when recent open feed exists and evidence is sufficient`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(listOf(openFeedSession()))
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.AfterActiveFeed)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `AfterActiveFeed emits no Window — feedEnd is not synthesized`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(listOf(openFeedSession()))
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.AfterActiveFeed) {
                    "Expected AfterActiveFeed (no window emitted, no feedEnd synthesized) but got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `stale open feed (older than MAX_OPEN_FEED_AGE_HOURS) does not suppress sleep window`() = runTest {
            // Feed started 6h ago. SleepFeatureExtractor.isPossibleAt() filters it out
            // (MAX_OPEN_FEED_AGE_HOURS = 4h), so features.feedIntervals has no open entry
            // and the AfterActiveFeed gate does not fire.
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(listOf(staleFeedSession()))
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window) {
                    "Stale open feed (>4h) must not suppress sleep window — got $state"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class OverdueTests {

        @Test
        fun `emits Overdue when now is past windowEnd plus grace period`() = runTest {
            // sufficientSleepRecords() → lastWake = Jun15T19:00. Advancing clock to Jun16T05:00
            // (far past overdue threshold) → Overdue.
            val overdueNow = Instant.parse("2024-06-16T05:00:00Z")
            val overdueUseCase = PredictSleepWindowUseCase(
                sleepRepository,
                breastfeedingRepository,
                babyRepository,
                babyEventRepository,
                Clock.fixed(overdueNow, fixedZoneId),
                fixedZoneId,
            )

            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            overdueUseCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Overdue)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class WindowTests {

        @Test
        fun `emits Window with reasons and valid bounds when data is sufficient`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                val window = (state as SleepPredictionState.Window).window
                assertTrue(window.reasons.isNotEmpty())
                assertTrue(window.windowEnd.isAfter(window.windowStart))
                // bestEstimate is the midpoint of the 30-min window
                assertEquals(
                    window.windowStart.plus(Duration.ofMinutes(15)),
                    window.bestEstimate,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confidence is at most MEDIUM even with fully personalized data (Phase 0 cap)`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(fullyPersonalizedRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(20))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                val window = (state as SleepPredictionState.Window).window
                assertEquals(Confidence.MEDIUM, window.confidence) {
                    "HIGH confidence must not be emitted in Phase 0 — got ${window.confidence}"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `wires NapBudgetFactor — nap deficit reason appears in window reasons`() = runTest {
            // Proves PredictSleepWindowUseCase passes the real NapBudgetFactor through; with the
            // Neutral default the "Nap deficit" reason would be absent.
            every { sleepRepository.getAllRecords() } returns flowOf(napDeficitRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window) {
                    "Expected a Window for the nap-deficit fixture — got $state"
                }
                val reasons = (state as SleepPredictionState.Window).window.reasons
                assertTrue(reasons.any { it is SleepReason.NapDeficit }) {
                    "NapBudgetFactor must be wired — expected a nap-deficit reason, got $reasons"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confidence is MEDIUM at the personalization midpoint (qualityC = 0_5)`() = runTest {
            // sufficientSleepRecords() → 5 nap intervals → qualityC = 5 / FULL_PERSONALIZATION_INTERVALS
            // = 5/10 = 0.5 → MEDIUM. (The faster Phase-6 ramp lifts the minimum-data baby to the
            // midpoint; the LOW band, qualityC < 0.5, is covered by SleepWindowPredictorTest.)
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.MEDIUM, (state as SleepPredictionState.Window).window.confidence)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class DisruptionTests {

        private fun sickEventAt(timestamp: Instant) = BabyEvent(
            timestamp = timestamp,
            type = BabyEventType.SICK,
            createdAt = timestamp,
        )

        // fullyPersonalizedRecords() → qualityC=1.0, but hasQualifiedTimezoneProvenance=false
        // → base confidence = MEDIUM. Disruption lowers MEDIUM→LOW.

        @Test
        fun `disruption event within 48h lowers confidence by one level`() = runTest {
            val recentEvent = sickEventAt(fixedNow.minus(Duration.ofHours(24)))
            every { sleepRepository.getAllRecords() } returns flowOf(fullyPersonalizedRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(20))
            every { babyEventRepository.getEventsSince(any()) } returns flowOf(listOf(recentEvent))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.LOW, (state as SleepPredictionState.Window).window.confidence) {
                    "Recent SICK event must lower base MEDIUM confidence to LOW"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `future-dated disruption event does not lower confidence`() = runTest {
            // getEventsSince returns the event (DAO cutoff = now-48h passes future timestamps too),
            // but the <= now predicate in predictForBaby must block it.
            val futureEvent = sickEventAt(fixedNow.plus(Duration.ofHours(1)))
            every { sleepRepository.getAllRecords() } returns flowOf(fullyPersonalizedRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(20))
            every { babyEventRepository.getEventsSince(any()) } returns flowOf(listOf(futureEvent))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.MEDIUM, (state as SleepPredictionState.Window).window.confidence) {
                    "Future-dated disruption event must not lower base MEDIUM confidence — got ${(state as? SleepPredictionState.Window)?.window?.confidence}"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `disruption event older than 48h returned by a stale query cutoff does not lower confidence`() = runTest {
            // Simulates a long-lived collector: the flow-start query cutoff froze days ago, so the
            // DAO still returns an event now older than the 48h lookback. The fresh per-evaluation
            // lower bound must expire it instead of pinning hasActiveDisruption forever.
            val staleEvent = sickEventAt(fixedNow.minus(Duration.ofHours(49)))
            every { sleepRepository.getAllRecords() } returns flowOf(fullyPersonalizedRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(20))
            every { babyEventRepository.getEventsSince(any()) } returns flowOf(listOf(staleEvent))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.MEDIUM, (state as SleepPredictionState.Window).window.confidence) {
                    "Disruption older than 48h must expire even when the query over-fetches it"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `disruption event older than 48h does not lower confidence (excluded by DAO cutoff)`() = runTest {
            // getEventsSince returns empty list, simulating the DAO correctly excluding events
            // older than the 48h cutoff.
            every { sleepRepository.getAllRecords() } returns flowOf(fullyPersonalizedRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(20))
            every { babyEventRepository.getEventsSince(any()) } returns flowOf(emptyList())

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.MEDIUM, (state as SleepPredictionState.Window).window.confidence) {
                    "With no recent disruption events, confidence must remain at base MEDIUM"
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class UnavailableTests {

        @Test
        fun `emits Unavailable when baby profile is null`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(null)

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Unavailable)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `emits Unavailable when getAllRecords throws synchronously`() = runTest {
            // Verifies that flow { emitAll(combine(repo.getAllRecords(),...)) }.catch catches
            // a synchronous throw from the repository method call itself (not a flow-emission error).
            every { sleepRepository.getAllRecords() } throws RuntimeException("db failure")
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Unavailable)
                assertEquals("db failure", (state as SleepPredictionState.Unavailable).reason)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `emits Unavailable when getAllSessions throws synchronously`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } throws RuntimeException("sessions failure")
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Unavailable)
                assertEquals("sessions failure", (state as SleepPredictionState.Unavailable).reason)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `emits Unavailable when getBabyProfile throws synchronously`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } throws RuntimeException("profile failure")

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Unavailable)
                assertEquals("profile failure", (state as SleepPredictionState.Unavailable).reason)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // The per-minute recompute ticker re-runs the predictor with an unchanged dataset, so the
    // flow must dedupe: downstream collectors (UI, notification coordinator that persists a
    // recommendation per emission) only see real state transitions.
    @Test
    fun `identical recomputed states are deduped - only real transitions reach collectors`() = runTest {
        // replay = 1: the first emit happens before combine has subscribed; without replay a
        // zero-buffer SharedFlow drops it and the flow never produces an item.
        val records = MutableSharedFlow<List<SleepRecord>>(replay = 1)
        every { sleepRepository.getAllRecords() } returns records
        every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
        every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

        useCase().test {
            records.emit(listOf(openSleepRecord()))
            assertTrue(awaitItem() is SleepPredictionState.CurrentlySleeping)

            // Same data again -> same state; must be suppressed. The next item the collector
            // sees is the state produced by the genuinely different emission after it.
            records.emit(listOf(openSleepRecord()))
            records.emit(emptyList())
            val next = awaitItem()
            assertTrue(next !is SleepPredictionState.CurrentlySleeping) {
                "Duplicate state leaked through distinctUntilChanged — got $next"
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
