# Predictive Feeding — Task 2: `PredictNextFeedUseCase`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the pure prediction algorithm with the full filter / freshness / overdue-grace pipeline. No callers wired up yet — the use case is verified entirely through unit tests.

**Architecture:** Single-responsibility use case in the domain layer. `combine`s a reactive recent-sessions flow (`BreastfeedingRepository.getAllSessions().map { it.take(LOOKBACK_LIMIT) }`) with the two quiet-hours prefs from Task 1, applies the filter pipeline, and emits `FeedPrediction?`. **Predictions must re-emit when the session history changes** so Home and the coordinator stay current after a feed is stopped, edited, or deleted. `Clock` and `ZoneId` are provided through a new Hilt `TimeModule` — no default-arg fallback, since Kotlin defaults are not visible to Dagger and would leave both unbound.

**Tech Stack:** Kotlin · Coroutines/Flow · JUnit 5 · MockK · Turbine.

**Branch:** `feat/predictive-feeding-2-usecase`
**Linear issue:** Predictive feeding — `PredictNextFeedUseCase`
**Depends on:** Task 1
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PredictNextFeedUseCase.kt`
- Create: `app/src/main/java/com/babytracker/di/TimeModule.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/PredictNextFeedUseCaseTest.kt`

## Steps

- [ ] **Step 1: Write the failing test skeleton**

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
        // Explicit Clock/ZoneId; no default-arg fallback (Hilt would not see defaults).
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

    @Test
    fun `returns null when an active session is in progress`() = runTest {
        val sessions = listOf(session(now.minusSeconds(60), end = null))
        every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)
        useCase().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test, verify it fails with unresolved reference**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCaseTest"
```

Expected: FAIL with "unresolved reference: PredictNextFeedUseCase".

- [ ] **Step 3: Create `TimeModule.kt` (Hilt bindings for `Clock` and `ZoneId`)**

```kotlin
package com.babytracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()
}
```

Rationale: Kotlin default constructor values are not visible to Dagger/Hilt. Without these bindings, `@Inject` on `Clock` / `ZoneId` parameters fails component generation. The defaults stay only on test-only constructors / direct instantiation in tests.

- [ ] **Step 4: Implement the use case**

Create `PredictNextFeedUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class PredictNextFeedUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    operator fun invoke(): Flow<FeedPrediction?> {
        // Reactive: getAllSessions() is a Flow that re-emits on session
        // insert/update/delete. Take the most-recent LOOKBACK_LIMIT after the
        // Dao's existing descending-by-startTime ordering.
        val recentSessionsFlow: Flow<List<BreastfeedingSession>> =
            breastfeedingRepository.getAllSessions()
                .map { it.take(PredictionTuning.LOOKBACK_LIMIT) }

        return combine(
            recentSessionsFlow,
            settingsRepository.getQuietHoursStartMinute(),
            settingsRepository.getQuietHoursEndMinute(),
        ) { sessions, qhStart, qhEnd ->
            predict(sessions, qhStart, qhEnd)
        }.catch { e ->
            // Existing `FlowExt.catchAndLog()` swallows errors without emitting,
            // which would leave downstream stuck on a stale value. We need a
            // typed `null` fallback so Home/coordinator can collapse to the
            // "no prediction" state. Inline the catch rather than extending
            // FlowExt to keep the contract local to this use case.
            android.util.Log.e("PredictNextFeed", "Prediction failed", e)
            emit(null)
        }
    }

    private fun predict(
        sessions: List<BreastfeedingSession>,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ): FeedPrediction? {
        if (sessions.any { it.endTime == null }) return null
        val now = Instant.now(clock)
        val sortedDesc = sessions.sortedByDescending { it.startTime }
        val mostRecent = sortedDesc.firstOrNull() ?: return null

        if (Duration.between(mostRecent.startTime, now).toHours()
            >= PredictionTuning.FRESHNESS_HORIZON_HOURS) return null

        val rawIntervals = sortedDesc.zipWithNext { newer, older ->
            IntervalSample(
                endpointA = older.startTime,
                endpointB = newer.startTime,
                minutes = Duration.between(older.startTime, newer.startTime).toMinutes().toInt(),
            )
        }

        val filtered = rawIntervals
            .filter { it.minutes <= PredictionTuning.INTERVAL_MAX_MINUTES }
            .filter { !endpointInQuietHours(it.endpointA, quietStartMinute, quietEndMinute) }
            .filter { !endpointInQuietHours(it.endpointB, quietStartMinute, quietEndMinute) }

        val taken = filtered.take(PredictionTuning.SAMPLE_SIZE_TARGET)
        if (taken.size < PredictionTuning.SAMPLE_SIZE_MIN) return null

        val avg = taken.map { it.minutes }.average().toInt()
        val predictedAt = mostRecent.startTime.plus(Duration.ofMinutes(avg.toLong()))
        val isOverdue = now.isAfter(predictedAt)
        val minutesPast = Duration.between(predictedAt, now).toMinutes()
        if (isOverdue && minutesPast > PredictionTuning.OVERDUE_GRACE_MINUTES) return null

        val minutesUntil = Duration.between(now, predictedAt).toMinutes().toInt()
        return FeedPrediction(
            predictedAt = predictedAt,
            averageIntervalMinutes = avg,
            sampleSize = taken.size,
            isOverdue = isOverdue,
            minutesUntil = minutesUntil,
        )
    }

    private fun endpointInQuietHours(endpoint: Instant, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        val localMinute = endpoint.atZone(zoneId).toLocalTime().toSecondOfDay() / 60
        return if (startMinute < endMinute) {
            localMinute in startMinute until endMinute
        } else {
            localMinute >= startMinute || localMinute < endMinute
        }
    }

    private data class IntervalSample(
        val endpointA: Instant,
        val endpointB: Instant,
        val minutes: Int,
    )
}
```

- [ ] **Step 5: Run the first test, verify PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCaseTest.returns null when an active session is in progress"
```

Expected: PASS.

- [ ] **Step 6: Add the remaining matrix tests**

Append to the test class. Each test must build a concrete session list and assert the prediction's full shape (`predictedAt`, `sampleSize`, `averageIntervalMinutes`, `isOverdue`, `minutesUntil`) where applicable — empty bodies are not acceptable. First add this fixture helper:

```kotlin
/**
 * Build N sessions ending at `anchor - 30m` (most-recent), each spaced
 * `intervalMinutes` apart going backward. Returned list is newest-first
 * (DAO order). Each session lasts 10 minutes.
 */
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
```

```kotlin
@Test
fun `returns null when fewer than 3 valid intervals remain after filtering`() = runTest {
    // 3 sessions -> 2 raw intervals; SAMPLE_SIZE_MIN=3.
    val sessions = buildEvenlySpacedSessions(count = 3, intervalMinutes = 180)
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)
    useCase().test {
        assertNull(awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `computes mean of most-recent 5 intervals when more than 5 are valid`() = runTest {
    // 7 sessions -> 6 valid intervals. Most-recent 5 are all 120m; the 6th is 240m
    // and must be excluded. Expected avg = 120.
    val mostRecentStart = now.minusSeconds(30 * 60)
    val starts = mutableListOf(mostRecentStart)
    repeat(5) { starts += starts.last().minusSeconds(120 * 60) } // 5 intervals of 120m
    starts += starts.last().minusSeconds(240 * 60)               // 6th interval = 240m (excluded)
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        assertEquals(5, p!!.sampleSize)
        assertEquals(120, p.averageIntervalMinutes)
        assertEquals(mostRecentStart.plusSeconds(120 * 60), p.predictedAt)
        assertFalse(p.isOverdue)
        assertEquals(90, p.minutesUntil) // mostRecent at now-30m, +120m -> now+90m
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `drops intervals greater than 360 minutes rather than capping them`() = runTest {
    // 5 sessions producing intervals desc: 180, 180, 180, 480.
    // 480 must be dropped (not clamped to 360). Remaining 3 valid -> avg 180.
    val mostRecent = now.minusSeconds(30 * 60)
    val s1 = mostRecent
    val s2 = s1.minusSeconds(180 * 60)
    val s3 = s2.minusSeconds(180 * 60)
    val s4 = s3.minusSeconds(180 * 60)
    val s5 = s4.minusSeconds(480 * 60) // > INTERVAL_MAX_MINUTES (360)
    val sessions = listOf(s1, s2, s3, s4, s5).map {
        session(start = it, end = it.plusSeconds(10 * 60))
    }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

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
    // Quiet hours 00:00-08:00 Sao_Paulo local. now=15:00Z=12:00 local.
    // Build 9 sessions so 3 intervals have at least one endpoint in [00:00,08:00),
    // and 4 daytime intervals survive (>= SAMPLE_SIZE_MIN=3).
    every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
    every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(8 * 60)

    // Local-time anchors (Sao_Paulo, UTC-3). 9 sessions spaced 120m, starting at 11:30 local.
    // Local times desc: 11:30, 09:30, 07:30*, 05:30*, 03:30*, 01:30*, 23:30, 21:30, 19:30
    // (* = inside quiet hours).
    val mostRecentLocal = Instant.parse("2026-05-19T14:30:00Z") // 11:30 Sao_Paulo
    val starts = (0 until 9).map { mostRecentLocal.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        // Raw 8 intervals; intervals (09:30,07:30), (07:30,05:30), (05:30,03:30),
        // (03:30,01:30), (01:30,23:30) all touch quiet hours -> dropped.
        // Surviving intervals: (11:30,09:30), (23:30,21:30), (21:30,19:30). All 120m.
        assertTrue(p!!.sampleSize >= com.babytracker.domain.model.PredictionTuning.SAMPLE_SIZE_MIN)
        assertEquals(120, p.averageIntervalMinutes)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `quiet hours wrapping midnight (22 to 06) filter overnight endpoints`() = runTest {
    // Wrap-around range: minute-of-day >= 22*60 OR < 6*60 is quiet.
    every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(22 * 60)
    every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(6 * 60)
    // 6 sessions, all daytime starts (08:00-21:00 local) spaced 120m.
    // Anchor most-recent at 14:00 local = 17:00Z. Going back 120m each:
    // 14:00, 12:00, 10:00, 08:00, 06:00*, 04:00*.
    val anchor = Instant.parse("2026-05-19T17:00:00Z")
    val starts = (0 until 6).map { anchor.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        // Intervals desc: (14,12), (12,10), (10,08), (08,06)*, (06,04)*.
        // Drop the last two (06:00 and 04:00 inside wrap). Surviving = 3 intervals @ 120m.
        assertEquals(3, p!!.sampleSize)
        assertEquals(120, p.averageIntervalMinutes)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `quiet hours start equals end disables filtering`() = runTest {
    every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
    every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)
    // 6 sessions spanning what would otherwise be quiet hours; all kept.
    val sessions = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120)
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        assertEquals(5, p!!.sampleSize) // capped at SAMPLE_SIZE_TARGET
        assertEquals(120, p.averageIntervalMinutes)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `returns null when most-recent session is older than 12 hours`() = runTest {
    // Most-recent start = now - 13h. FRESHNESS_HORIZON_HOURS=12.
    val sessions = buildEvenlySpacedSessions(
        count = 6,
        intervalMinutes = 180,
        anchor = now.minusSeconds(13L * 3600 - 30 * 60), // shift so mostRecent = now - 13h
    )
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)
    useCase().test {
        assertNull(awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `returns null when predicted time is more than 90 minutes overdue`() = runTest {
    // Most-recent start = now - 220m; avg interval = 120m -> predictedAt = now - 100m.
    // OVERDUE_GRACE_MINUTES=90. 100 > 90 -> null.
    val mostRecent = now.minusSeconds(220 * 60)
    val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        assertNull(awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `prefers older daytime intervals when recent intervals are overnight`() = runTest {
    // Quiet 00:00-08:00 local. Most-recent at 10:00 local (daytime).
    // Construct 10 sessions where intervals 1-4 (closest in time) each touch quiet hours,
    // but older sessions form 5 surviving daytime intervals.
    every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
    every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(8 * 60)

    // Most-recent at 10:00 local (13:00Z). Use 4 overnight sessions (in quiet) then
    // 6 daytime sessions further back at 1-day offsets.
    val recent = Instant.parse("2026-05-19T13:00:00Z") // 10:00 local
    val overnight = listOf(
        recent,
        Instant.parse("2026-05-19T07:00:00Z"), // 04:00 local *
        Instant.parse("2026-05-19T05:00:00Z"), // 02:00 local *
        Instant.parse("2026-05-19T03:00:00Z"), // 00:00 local *
    )
    val daytime = (0 until 6).map {
        // 16:00 local on consecutive prior days, spaced 24h, all daytime
        Instant.parse("2026-05-18T19:00:00Z").minusSeconds(it.toLong() * 24 * 3600)
    }
    val sessions = (overnight + daytime).map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        // Algorithm filters all-quiet intervals; remaining daytime intervals (24h each)
        // exceed INTERVAL_MAX_MINUTES (360) and are also dropped -> null.
        // This documents the spec choice: we do NOT silently fall back to multi-day
        // intervals. Change to assertNotNull only if the spec is later relaxed.
        assertNull(p)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `isOverdue is true when predictedAt is in the past within the grace window`() = runTest {
    // predictedAt = now - 30m (overdue but within OVERDUE_GRACE_MINUTES=90).
    // Most-recent start = now - 150m; avg 120m -> predictedAt = now - 30m.
    val mostRecent = now.minusSeconds(150 * 60)
    val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

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
    // Future: most-recent start = now - 30m, avg 120m -> predictedAt = now + 90m.
    val mostRecent = now.minusSeconds(30 * 60)
    val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

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
    // Spring-forward: 2026-03-08 02:00 local jumps to 03:00. Verify no exception
    // when an interval endpoint sits on either side of the skipped hour, and that
    // a prediction is produced.
    val nyZone = ZoneId.of("America/New_York")
    val nyNow = Instant.parse("2026-03-08T12:30:00Z") // 07:30 EST/EDT boundary day
    val nyClock = Clock.fixed(nyNow, nyZone)
    useCase = PredictNextFeedUseCase(breastfeedingRepository, settingsRepository, nyClock, nyZone)

    // 6 sessions spaced 120m back from 07:00 local (12:00Z) so the chain crosses 02:00.
    val mostRecent = Instant.parse("2026-03-08T12:00:00Z")
    val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        assertEquals(120, p!!.averageIntervalMinutes)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `handles DST fall-back in America New_York without double-counting`() = runTest {
    // Fall-back: 2026-11-01 02:00 local repeats. Verify intervals computed from
    // Instants (not LocalDateTime) so the repeated hour is not double-counted.
    val nyZone = ZoneId.of("America/New_York")
    val nyNow = Instant.parse("2026-11-01T12:30:00Z") // 07:30/08:30 boundary day
    val nyClock = Clock.fixed(nyNow, nyZone)
    useCase = PredictNextFeedUseCase(breastfeedingRepository, settingsRepository, nyClock, nyZone)

    val mostRecent = Instant.parse("2026-11-01T12:00:00Z")
    val starts = (0 until 6).map { mostRecent.minusSeconds(it.toLong() * 120 * 60) }
    val sessions = starts.map { session(start = it, end = it.plusSeconds(10 * 60)) }
    every { breastfeedingRepository.getAllSessions() } returns flowOf(sessions)

    useCase().test {
        val p = awaitItem()
        assertNotNull(p)
        // Intervals computed on Instants -> always 120m wall-clock minutes,
        // regardless of repeated local hour. If algorithm regresses to LocalDateTime
        // arithmetic this would read 180m for the crossing interval.
        assertEquals(120, p!!.averageIntervalMinutes)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `re-emits an updated prediction when session history changes`() = runTest {
    // Reactive contract: getAllSessions() re-emission triggers re-prediction.
    val sessionsFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
    every { breastfeedingRepository.getAllSessions() } returns sessionsFlow

    // initial: most-recent at now-30m, 120m spacing -> predictedAt = now+90m
    val initial = buildEvenlySpacedSessions(count = 6, intervalMinutes = 120)
    // updated: insert a brand-new most-recent at now-5m -> predictedAt shifts later
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
```


- [ ] **Step 7: Run full test class**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCaseTest"
```

Expected: all PASS. Fix algorithm gaps inline until green.

- [ ] **Step 8: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PredictNextFeedUseCase.kt \
        app/src/main/java/com/babytracker/di/TimeModule.kt \
        app/src/test/java/com/babytracker/domain/usecase/breastfeeding/PredictNextFeedUseCaseTest.kt
git commit -m "feat(breastfeeding): add PredictNextFeedUseCase with quiet-hours filtering"
```

- [ ] **Step 10: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-2-usecase
gh pr create --title "feat(breastfeeding): PredictNextFeedUseCase" \
  --body "Adds PredictNextFeedUseCase: rolling mean of recent intervals with freshness, INTERVAL_MAX_MINUTES drop, quiet-hours filter (wraparound + start==end disable), and overdue grace bound. Task 2 of 6."
```
