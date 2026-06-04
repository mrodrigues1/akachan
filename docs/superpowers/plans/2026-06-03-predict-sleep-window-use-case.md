# Baseline PredictSleepWindowUseCase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `PredictSleepWindowUseCase` that emits `Flow<SleepPredictionState>` using the baseline wake-interval blend formula, covering all 7 sealed state branches with full unit test coverage.

**Architecture:** Three new domain models (`Confidence`, `EvidenceProgress`, `SleepWindow`) plus sealed `SleepPredictionState`. The use case wraps `combine()` inside `flow { emitAll(...) }.catch { }` so synchronous throws from repository calls are captured and emitted as `Unavailable`. `Baby.ageInWeeks` uses `LocalDate.now()` (no clock injection), so `ageInWeeks` is computed inside `predict()` from `baby.birthDate` and `clock.instant().atZone(zoneId).toLocalDate()`. The `AfterActiveFeed` gate uses `features.feedIntervals.any { it.endMillis == null }` (validated by `SleepFeatureExtractor`) instead of raw `feedSessions` so stale open feeds (> `MAX_OPEN_FEED_AGE_HOURS`) cannot suppress predictions indefinitely. No new Hilt modules needed — `Clock` and `ZoneId` singletons are already provided by `TimeModule`. All constants from the spec already exist in `SleepPredictionTuning.kt`.

**Tech Stack:** Kotlin 2.3.20, Hilt 2.59, Kotlin Coroutines + Flow, JUnit 5, MockK 1.13.13, Turbine 1.2.0

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `domain/model/Confidence.kt` | `LOW`/`MEDIUM`/`HIGH` enum |
| Create | `domain/model/EvidenceProgress.kt` | Progress payload for `NeedMoreData` state |
| Create | `domain/model/SleepWindow.kt` | Prediction output: window bounds, confidence, prompts |
| Create | `domain/model/SleepPredictionState.kt` | Sealed class with all 7 state branches |
| Create | `domain/usecase/sleep/PredictSleepWindowUseCase.kt` | Flow-emitting prediction engine |
| Create | `src/test/…/PredictSleepWindowUseCaseTest.kt` | Branch + formula + confidence coverage |

No existing files modified.

---

### Task 1: Domain Models

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/Confidence.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/EvidenceProgress.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/SleepWindow.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/SleepPredictionState.kt`

- [ ] **Step 1: Create `Confidence.kt`**

```kotlin
package com.babytracker.domain.model

enum class Confidence { LOW, MEDIUM, HIGH }
```

- [ ] **Step 2: Create `EvidenceProgress.kt`**

```kotlin
package com.babytracker.domain.model

data class EvidenceProgress(
    val completedIntervals: Int,
    val requiredIntervals: Int,
    val localDays: Int,
    val requiredLocalDays: Int,
    val hint: String,
)
```

- [ ] **Step 3: Create `SleepWindow.kt`**

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class SleepWindow(
    val windowStart: Instant,
    val windowEnd: Instant,
    val bestEstimate: Instant,
    val confidence: Confidence,
    val reasons: List<String>,
    val feedPrompt: String?,
    val safetyPrompt: String,
)
```

- [ ] **Step 4: Create `SleepPredictionState.kt`**

```kotlin
package com.babytracker.domain.model

sealed class SleepPredictionState {
    data class Window(val window: SleepWindow) : SleepPredictionState()
    data class NeedMoreData(val progress: EvidenceProgress) : SleepPredictionState()
    data object CueLed : SleepPredictionState()
    data object CurrentlySleeping : SleepPredictionState()
    data object AfterActiveFeed : SleepPredictionState()
    data object Overdue : SleepPredictionState()
    data class Unavailable(val reason: String) : SleepPredictionState()
}
```

- [ ] **Step 5: Compile-check**

```
./gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/Confidence.kt \
        app/src/main/java/com/babytracker/domain/model/EvidenceProgress.kt \
        app/src/main/java/com/babytracker/domain/model/SleepWindow.kt \
        app/src/main/java/com/babytracker/domain/model/SleepPredictionState.kt
git commit -m "feat(usecase): add SleepPredictionState sealed class and supporting models"
```

---

### Task 2: PredictSleepWindowUseCase

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt`

**State precedence (must match spec order):**
1. Open sleep record (endTime == null) → `CurrentlySleeping`
2. Baby null → `Unavailable("no baby profile")`
3. `ageInWeeks` (computed from `clock` + `zoneId`, not `baby.ageInWeeks`) < `CUE_LED_MAX_AGE_WEEKS` → `CueLed`
4. Feature extraction + quality gate fails → `NeedMoreData(progress)`
5. `features.feedIntervals.any { it.endMillis == null }` (clock-bounded, validated by extractor) → `AfterActiveFeed`
6. Window computed; now > windowEnd + OVERDUE_GRACE → `Overdue`
7. Error anywhere in flow → `Unavailable(reason)` via `.catch { }` on outer `flow { emitAll(...) }`
8. Otherwise → `Window(...)`

**Prediction formula:**
```
qualityC      = clamp(completedIntervalCount / FULL_PERSONALIZATION_INTERVALS, 0f..1f)
agePriorMidpt = (minBound.toMillis() + maxBound.toMillis()) / 2   // from SleepAgePriors.getWakeWindowBounds()
wakeTarget    = (1.0 - 0.6 * qualityC) * agePriorMidpt + 0.6 * qualityC * babyWakeP50Millis
bestEstimate  = lastWakeMillis + wakeTarget
window        = [bestEstimate - HALF_WINDOW, bestEstimate + HALF_WINDOW]
```

**Confidence (Phase 0):** `if (qualityC >= 0.5f) MEDIUM else LOW` — never HIGH.

- [ ] **Step 1: Implement `PredictSleepWindowUseCase.kt`**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import com.babytracker.domain.sleep.prior.SleepAgePriors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PredictSleepWindowUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val babyRepository: BabyRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    // flow { emitAll(...) } defers repository calls into the flow body so .catch intercepts
    // synchronous throws from getAllRecords() / getBabyProfile() as Unavailable states.
    operator fun invoke(): Flow<SleepPredictionState> = flow {
        emitAll(
            combine(
                sleepRepository.getAllRecords(),
                breastfeedingRepository.getAllSessions(),
                babyRepository.getBabyProfile(),
            ) { sleepRecords, feedSessions, baby ->
                predict(sleepRecords, feedSessions, baby)
            }
        )
    }.catch { e ->
        emit(SleepPredictionState.Unavailable(e.message ?: "prediction error"))
    }

    private fun predict(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby?,
    ): SleepPredictionState {
        if (sleepRecords.any { it.endTime == null }) return SleepPredictionState.CurrentlySleeping

        baby ?: return SleepPredictionState.Unavailable("no baby profile")

        // Baby.ageInWeeks uses LocalDate.now() (no clock injection) — compute from clock here
        // so tests with a fixed clock produce deterministic age-gate results.
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()

        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) {
            return SleepPredictionState.CueLed
        }

        val now = Instant.now(clock)
        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val lookbackRecords = sleepRecords.filter { it.startTime >= lookbackStart }

        val features = SleepFeatureExtractor(clock, zoneId).extract(lookbackRecords, feedSessions)
        val quality = features.quality
        val metrics = features.metrics

        if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
            return SleepPredictionState.NeedMoreData(buildProgress(quality))
        }

        // Use validated feed intervals (already clock-bounded by SleepFeatureExtractor) so a
        // stale open feed older than MAX_OPEN_FEED_AGE_HOURS cannot suppress predictions forever.
        if (features.feedIntervals.any { it.endMillis == null }) return SleepPredictionState.AfterActiveFeed

        val lastWakeMillis = metrics.lastWakeMillis
            ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))
        val babyWakeP50Millis = metrics.medianWakeIntervalMillis
            ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))

        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        val agePriorMidpointMillis = (minBound.toMillis() + maxBound.toMillis()) / 2

        val qualityC = (quality.completedIntervalCount.toFloat() / SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .coerceIn(0f, 1f)
        val wakeTargetMillis = (
            (1.0 - 0.6 * qualityC) * agePriorMidpointMillis +
                0.6 * qualityC * babyWakeP50Millis
            ).toLong()

        val bestEstimate = Instant.ofEpochMilli(lastWakeMillis + wakeTargetMillis)
        val halfWindow = Duration.ofMinutes(SleepPredictionTuning.HALF_WINDOW_MINUTES)
        val windowStart = bestEstimate.minus(halfWindow)
        val windowEnd = bestEstimate.plus(halfWindow)

        if (now.isAfter(windowEnd.plus(Duration.ofMinutes(SleepPredictionTuning.OVERDUE_GRACE_MINUTES)))) {
            return SleepPredictionState.Overdue
        }

        val confidence = if (qualityC >= 0.5f) Confidence.MEDIUM else Confidence.LOW

        return SleepPredictionState.Window(
            SleepWindow(
                windowStart = windowStart,
                windowEnd = windowEnd,
                bestEstimate = bestEstimate,
                confidence = confidence,
                reasons = buildReasons(qualityC, ageInWeeks),
                feedPrompt = computeFeedPrompt(features.feedIntervals, windowStart, windowEnd, now),
                safetyPrompt = "Always follow your baby's sleep cues — windows are estimates, not schedules.",
            )
        )
    }

    private fun buildProgress(quality: EvidenceQuality) = EvidenceProgress(
        completedIntervals = quality.completedIntervalCount,
        requiredIntervals = SleepPredictionTuning.MIN_COMPLETED_INTERVALS,
        localDays = quality.localDayCoverage,
        requiredLocalDays = SleepPredictionTuning.MIN_LOCAL_DAYS,
        hint = "log a few more naps with both sleep and wake times",
    )

    private fun buildReasons(qualityC: Float, ageInWeeks: Int): List<String> {
        val pct = (qualityC * 100).toInt()
        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        return listOf(
            if (qualityC >= 1f) {
                "Fully personalized from your baby's wake history"
            } else {
                "Blended from age-based expectations ($pct% personalized from your baby's history)"
            },
            "Typical wake window for ${ageInWeeks}w: ${minBound.toMinutes()}–${maxBound.toMinutes()} min",
        )
    }

    private fun computeFeedPrompt(
        feedIntervals: List<BreastfeedInterval>,
        windowStart: Instant,
        windowEnd: Instant,
        now: Instant,
    ): String? {
        val freshnessMillis = Duration.ofHours(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS).toMillis()
        val nowMillis = now.toEpochMilli()
        val recent = feedIntervals
            .filter { it.endMillis != null && (nowMillis - it.startMillis) <= freshnessMillis }
            .sortedByDescending { it.startMillis }
        val lastFeed = recent.firstOrNull() ?: return null
        val intervals = recent.zipWithNext { a, b -> a.startMillis - b.startMillis }.filter { it > 0 }
        if (intervals.isEmpty()) return null
        val avgIntervalMillis = intervals.average().toLong()
        val predictedNextFeed = Instant.ofEpochMilli(lastFeed.startMillis + avgIntervalMillis)
        val toleranceMillis = Duration.ofMinutes(30).toMillis()
        val predictedMillis = predictedNextFeed.toEpochMilli()
        val windowStartMillis = windowStart.toEpochMilli()
        val windowEndMillis = windowEnd.toEpochMilli()
        return if (predictedMillis in (windowStartMillis - toleranceMillis)..(windowEndMillis + toleranceMillis)) {
            "a breastfeed may be due near this window — offer a feed first if hunger cues appear"
        } else {
            null
        }
    }
}
```

- [ ] **Step 2: Compile-check**

```
./gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run ktlint and detekt**

```
./gradlew ktlintFormat detekt
```

Expected: both pass. Fix any violations before committing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt
git commit -m "feat(usecase): implement PredictSleepWindowUseCase emitting Flow<SleepPredictionState>"
```

---

### Task 3: Unit Tests

**Files:**
- Create: `app/src/test/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCaseTest.kt`

**Test data design:**

`sufficientSleepRecords()` — 9 records spread over 4 local days producing exactly 5 valid wake intervals (180, 180, 180, 180, 120 min). Freshness: last record ends 1h before `fixedNow`. IQR = 30 min (< 45 min ceiling). Local day coverage = 4 days (≥ MIN_LOCAL_DAYS = 3). This set reliably passes the quality gate.

`fullyPersonalizedRecords()` — 22 records (3 naps/day × 7 days + 1 final) producing 15 valid 2h wake intervals. `qualityC = min(15/14, 1.0) = 1.0`. Used to test the MEDIUM confidence cap.

**Clock:** `fixedNow = 2024-06-15T20:00:00Z`, `ZoneOffset.UTC`. Predictable local-day boundaries.

- [ ] **Step 1: Write the test file**

```kotlin
package com.babytracker.domain.usecase.sleep

import app.cash.turbine.test
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.every
import io.mockk.mockk
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

    private val fixedNow = Instant.parse("2024-06-15T20:00:00Z")
    private val fixedZoneId = ZoneOffset.UTC
    private val fixedClock = Clock.fixed(fixedNow, fixedZoneId)

    private lateinit var useCase: PredictSleepWindowUseCase

    @BeforeEach
    fun setUp() {
        sleepRepository = mockk()
        breastfeedingRepository = mockk()
        babyRepository = mockk()
        useCase = PredictSleepWindowUseCase(
            sleepRepository, breastfeedingRepository, babyRepository, fixedClock, fixedZoneId,
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
     * 4 × 180 min (within-day) + 1 × 120 min (final). IQR = 30 min. Passes quality gate.
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
     * Wake intervals: afternoon_start - morning_end = 2h, evening_start - afternoon_end = 2h.
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

    private fun openSleepRecord() = SleepRecord(
        id = 99,
        startTime = fixedNow.minus(Duration.ofHours(1)),
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
    // so features.feedIntervals.none { it.endMillis == null } and no AfterActiveFeed is emitted.
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
            }
        }
    }

    @Nested
    inner class OverdueTests {

        @Test
        fun `emits Overdue when now is past windowEnd plus grace period`() = runTest {
            // sufficientSleepRecords() → bestEstimate ≈ Jun15T20:49, overdueThreshold ≈ 21:49.
            // Advancing clock to Jun16T05:00 (7h past threshold) → Overdue.
            val overdueNow = Instant.parse("2024-06-16T05:00:00Z")
            val overdueUseCase = PredictSleepWindowUseCase(
                sleepRepository,
                breastfeedingRepository,
                babyRepository,
                Clock.fixed(overdueNow, fixedZoneId),
                fixedZoneId,
            )

            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            overdueUseCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Overdue)
                awaitComplete()
            }
        }
    }

    @Nested
    inner class WindowTests {

        @Test
        fun `emits Window with non-null safetyPrompt and valid bounds when data is sufficient`() = runTest {
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                val window = (state as SleepPredictionState.Window).window
                assertNotNull(window.safetyPrompt)
                assertTrue(window.safetyPrompt.isNotEmpty())
                assertTrue(window.windowEnd.isAfter(window.windowStart))
                // bestEstimate is the midpoint of the 30-min window
                assertEquals(
                    window.windowStart.plus(Duration.ofMinutes(15)),
                    window.bestEstimate,
                )
                awaitComplete()
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
                awaitComplete()
            }
        }

        @Test
        fun `confidence is LOW when qualityC is below 0_5 (fewer than 7 completed intervals)`() = runTest {
            // sufficientSleepRecords() → 5 intervals → qualityC = 5/14 = 0.357 < 0.5 → LOW
            every { sleepRepository.getAllRecords() } returns flowOf(sufficientSleepRecords())
            every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
            every { babyRepository.getBabyProfile() } returns flowOf(babyOfWeeks(12))

            useCase().test {
                val state = awaitItem()
                assertTrue(state is SleepPredictionState.Window)
                assertEquals(Confidence.LOW, (state as SleepPredictionState.Window).window.confidence)
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
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
                awaitComplete()
            }
        }
    }
}
```

- [ ] **Step 2: Run the tests**

```
./gradlew :app:testDebugUnitTest --tests "*.PredictSleepWindowUseCaseTest" --rerun-tasks
```

Expected: ALL 17 TESTS PASS. If any fail, fix the implementation in Task 2 and re-run.

- [ ] **Step 3: Run the full suite (no regressions)**

```
./gradlew :app:testDebugUnitTest -PfastTests
```

Expected: all tests pass, no regressions.

- [ ] **Step 4: Run ktlint and detekt**

```
./gradlew ktlintFormat detekt
```

Expected: both pass. Fix any violations before committing.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCaseTest.kt
git commit -m "test(usecase): add PredictSleepWindowUseCaseTest covering all state branches"
```

---

## Self-Review

### 1. Spec Coverage

| Spec requirement | Task / step |
|-----------------|-------------|
| `SleepPredictionState` sealed class with 7 branches | Task 1 Step 4 |
| `SleepWindow` with all fields | Task 1 Step 3 |
| `EvidenceProgress` with hint | Task 1 Step 2 |
| `Confidence` enum LOW/MEDIUM/HIGH | Task 1 Step 1 |
| `SleepPredictionTuning` constants | Already exists — no changes |
| Prediction formula (qualityC blend) | Task 2 `predict()` |
| State precedence order 1→7 | Task 2 `predict()` |
| Confidence capped at MEDIUM in Phase 0 | Task 2 `predict()` + WindowTests |
| `AfterActiveFeed` — no feedEnd synthesized | State is `data object` (no window field) — AfterActiveFeedTests |
| `Unavailable` via `.catch {}` | Task 2 `invoke()` |
| `safetyPrompt` always non-null on `Window` | Task 2 hardcoded string + WindowTests |
| `feedPrompt` nullable | Task 2 `computeFeedPrompt()` |
| Injected `Clock` + `ZoneId` | Task 2 constructor |
| Every state branch covered by tests | Task 3 nested test classes |
| Sparse-logger → `NeedMoreData` not `Window` | Task 3 `NeedMoreDataTests` |
| Confidence never exceeds MEDIUM | Task 3 `WindowTests.confidence is at most MEDIUM` |
| `ageInWeeks` computed from `clock` not `LocalDate.now()` | Task 2 `predict()` + Task 3 `CueLedTests.injected clock controls` |
| Stale open feed cannot suppress prediction forever | Task 2 `features.feedIntervals` gate + Task 3 `AfterActiveFeedTests.stale open feed` |
| Synchronous throw from any repository method → `Unavailable` | Task 2 `flow { emitAll(...) }` + Task 3 three `throws`-based `UnavailableTests` |

### 2. Placeholder Scan

No "TBD", "TODO", "Similar to Task N", steps without code, or undefined type references.

### 3. Type Consistency

- `Confidence` enum (Task 1 Step 1) used in Task 2 `predict()` and asserted in Task 3 `WindowTests`.
- `EvidenceProgress` fields (`completedIntervals`, `requiredIntervals`, `localDays`, `requiredLocalDays`, `hint`) defined in Task 1 Step 2, constructed in Task 2 `buildProgress()`, asserted in Task 3 `NeedMoreDataTests`.
- `SleepWindow` fields defined in Task 1 Step 3 match the `SleepWindow(...)` constructor call in Task 2.
- `SleepPredictionState.Window(window: SleepWindow)` matches `(state as SleepPredictionState.Window).window` in tests.
- `SleepFeatureExtractor(clock, zoneId).extract(lookbackRecords, feedSessions)` matches the public API in `SleepFeatureExtractor.kt:20`.
- `SleepAgePriors.getWakeWindowBounds(ageInWeeks): Pair<Duration, Duration>` matches usage in `buildReasons()` and `predict()`.
