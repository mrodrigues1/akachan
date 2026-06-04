# Sleep Evaluation Harness (AKA-90) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline evaluation harness in `domain/sleep/eval/` that replays historical sleep logs in strict no-lookahead order, computes MAE / in-window % / missed-window rate per age-band × sleep-type segment, and blocks segments with < 20 wake anchors or 0 scored predictions.

**Architecture:** Extract the pure prediction math from `PredictSleepWindowUseCase` into a new `SleepWindowPredictor` object so both the use case and the harness share one implementation. The harness iterates completed sleep records chronologically, builds a fixed-clock `SleepFeatureExtractor` at each wake event, calls `SleepWindowPredictor`, and groups scores into `SegmentResult`s. The entire eval package is pure Kotlin — zero Android imports.

**Tech Stack:** Kotlin, `java.time` (Clock, Instant, ZoneId, Duration, ChronoUnit), `SleepFeatureExtractor` (existing), `SleepPredictionTuning` constants (existing — EVAL_MIN_ANCHORS=20 already added), JUnit 5 for tests.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| CREATE | `domain/sleep/eval/SegmentKey.kt` | Segment identifier: ageBand (Int) + SleepType |
| CREATE | `domain/sleep/eval/AnchorScore.kt` | Per-anchor scoring result: error, inWindow, missedWindow |
| CREATE | `domain/sleep/eval/SegmentResult.kt` | Aggregated metrics + status (PASS / BLOCK_INSUFFICIENT_DATA) for one segment |
| CREATE | `domain/sleep/eval/EvalReport.kt` | Full harness output: algorithmVersion, evaluatedAt, all segments |
| CREATE | `domain/sleep/eval/SleepWindowPredictor.kt` | Pure prediction: features + ageInWeeks + now → SleepPredictionState |
| CREATE | `domain/sleep/eval/SleepEvalHarness.kt` | Chronological replay engine |
| MODIFY | `domain/usecase/sleep/PredictSleepWindowUseCase.kt` | Delegate to SleepWindowPredictor; remove private helpers |
| CREATE | `test/.../domain/sleep/eval/SleepWindowPredictorTest.kt` | Unit tests for SleepWindowPredictor |
| CREATE | `test/.../domain/sleep/eval/SleepEvalHarnessTest.kt` | Harness integration tests + sparse/noisy fixture tests |

All paths are relative to `app/src/main/java/com/babytracker/` (production) and `app/src/test/java/com/babytracker/` (test).

---

## Task 1: Domain Models

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SegmentKey.kt`
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/AnchorScore.kt`
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SegmentResult.kt`
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/EvalReport.kt`

- [ ] **Step 1: Create SegmentKey**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/SegmentKey.kt
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType

data class SegmentKey(val ageBand: Int, val sleepType: SleepType) {
    fun label(): String = "${ageBand}w+ ${sleepType.name.lowercase().replace('_', ' ')}"
}

fun ageBandFor(ageInWeeks: Int): Int = when {
    ageInWeeks < 6 -> 0
    ageInWeeks < 8 -> 6
    ageInWeeks < 12 -> 8
    ageInWeeks < 16 -> 12
    ageInWeeks < 24 -> 16
    ageInWeeks < 36 -> 24
    else -> 36
}
```

- [ ] **Step 2: Create AnchorScore**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/AnchorScore.kt
package com.babytracker.domain.sleep.eval

data class AnchorScore(
    val errorMillis: Long,
    val inWindow: Boolean,
    val missedWindow: Boolean,
)
```

- [ ] **Step 3: Create SegmentResult**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/SegmentResult.kt
package com.babytracker.domain.sleep.eval

enum class SegmentStatus { PASS, BLOCK_INSUFFICIENT_DATA }

data class SegmentResult(
    val key: SegmentKey,
    val anchorCount: Int,
    val scoredCount: Int,
    val maeMinutes: Double?,
    val inWindowPct: Double?,
    val missedWindowRate: Double?,
    val status: SegmentStatus,
    val blockReason: String?,
)
```

- [ ] **Step 4: Create EvalReport**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/EvalReport.kt
package com.babytracker.domain.sleep.eval

import java.time.Instant

data class EvalReport(
    val algorithmVersion: String,
    val evaluatedAt: Instant,
    val segments: List<SegmentResult>,
    val totalAnchors: Int,
    val totalScored: Int,
)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/eval/SegmentKey.kt \
        app/src/main/java/com/babytracker/domain/sleep/eval/AnchorScore.kt \
        app/src/main/java/com/babytracker/domain/sleep/eval/SegmentResult.kt \
        app/src/main/java/com/babytracker/domain/sleep/eval/EvalReport.kt
git commit -m "feat(sleep): add eval harness domain models (SegmentKey, AnchorScore, SegmentResult, EvalReport)"
```

---

## Task 2: SleepWindowPredictor — extract and test

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

- [ ] **Step 1: Write failing tests for SleepWindowPredictor**

```kotlin
// app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.feature.SleepInterval
import com.babytracker.domain.sleep.feature.SleepMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SleepWindowPredictorTest {

    private val baseNow = Instant.parse("2024-06-15T14:00:00Z")
    private val ageInWeeks = 20 // 90-180 min wake-window bounds

    private fun sufficientQuality(completedCount: Int = 10) = EvidenceQuality(
        lastWakeRecencyMillis = Duration.ofHours(1).toMillis(),
        isFresh = true,
        completedIntervalCount = completedCount,
        localDayCoverage = 5,
        isLocalDayCoverageSufficient = true,
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        invalidRecordRate = 0f,
        hasSufficientZoneIndependentEvidence = true,
    )

    private fun sufficientMetrics(lastWakeMillis: Long, medianIntervalMillis: Long) = SleepMetrics(
        lastWakeMillis = lastWakeMillis,
        lastSleepType = SleepType.NAP,
        lastSleepDurationMillis = Duration.ofMinutes(60).toMillis(),
        completedWakeIntervals = listOf(medianIntervalMillis),
        medianWakeIntervalMillis = medianIntervalMillis,
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        sleepLast24hMillis = Duration.ofHours(4).toMillis(),
        daySleepTodayMillis = Duration.ofHours(2).toMillis(),
        napCountToday = 2,
        medianBedtimeMinuteOfDay = null,
        medianMorningWakeMinuteOfDay = null,
    )

    private fun features(
        quality: EvidenceQuality = sufficientQuality(),
        metrics: SleepMetrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ),
        feedIntervals: List<BreastfeedInterval> = emptyList(),
    ) = SleepFeatures(
        validIntervals = emptyList(),
        feedIntervals = feedIntervals,
        metrics = metrics,
        quality = quality,
    )

    @Test
    fun `returns Window when quality and metrics are sufficient`() {
        val result = SleepWindowPredictor.predict(features(), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when not fresh`() {
        val staleQuality = sufficientQuality().copy(
            isFresh = false,
            hasSufficientZoneIndependentEvidence = false,
        )
        val result = SleepWindowPredictor.predict(features(quality = staleQuality), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when local day coverage insufficient`() {
        val lowCoverageQuality = sufficientQuality().copy(
            localDayCoverage = 1,
            isLocalDayCoverageSufficient = false,
        )
        val result = SleepWindowPredictor.predict(features(quality = lowCoverageQuality), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when lastWakeMillis is null`() {
        val metricsNoWake = sufficientMetrics(
            lastWakeMillis = 0L,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastWakeMillis = null)
        val result = SleepWindowPredictor.predict(features(metrics = metricsNoWake), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when medianWakeIntervalMillis is null`() {
        val metricsNoMedian = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(medianWakeIntervalMillis = null)
        val result = SleepWindowPredictor.predict(features(metrics = metricsNoMedian), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns AfterActiveFeed when open feed interval exists`() {
        val openFeed = BreastfeedInterval(
            startMillis = baseNow.minusSeconds(1800).toEpochMilli(),
            endMillis = null,
        )
        val result = SleepWindowPredictor.predict(features(feedIntervals = listOf(openFeed)), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.AfterActiveFeed::class.java, result)
    }

    @Test
    fun `returns Overdue when window passed OVERDUE_GRACE_MINUTES ago`() {
        // lastWake = 5 hours ago, median interval = 90 min → bestEstimate = 3.5h ago
        // window = bestEstimate ± 15 min → windowEnd = 3h 15min ago
        // now > windowEnd + 45 min grace → Overdue
        val lastWakeMillis = baseNow.minusSeconds(5 * 3600).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.Overdue::class.java, result)
    }

    @Test
    fun `Window bestEstimate is blend of age prior and personal median`() {
        // At qualityC = 0 (0 intervals / 14 full), weight is 100% age prior
        val zeroQuality = sufficientQuality(completedCount = 0).copy(
            hasSufficientZoneIndependentEvidence = false,
            isLocalDayCoverageSufficient = false,
        )
        // We need sufficient quality to get a Window — use fully personalized quality
        val fullQuality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val personalMedian = Duration.ofMinutes(120).toMillis()
        val metrics = sufficientMetrics(lastWakeMillis, personalMedian)

        val result = SleepWindowPredictor.predict(features(quality = fullQuality, metrics = metrics), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(Confidence.MEDIUM, window.confidence)
        // At full personalization (qualityC=1): wakeTarget = 0.4*agePrior + 0.6*personal
        // agePrior midpoint for 20w = (90+180)/2 = 135 min
        val expectedTargetMillis = (0.4 * Duration.ofMinutes(135).toMillis() + 0.6 * personalMedian).toLong()
        val expectedBestEstimate = Instant.ofEpochMilli(lastWakeMillis + expectedTargetMillis)
        assertEquals(expectedBestEstimate, window.bestEstimate)
    }

    @Test
    fun `Window confidence is LOW when completedIntervalCount below half of FULL_PERSONALIZATION_INTERVALS`() {
        val lowCountQuality = sufficientQuality(completedCount = 3) // 3/14 < 0.5
        val result = SleepWindowPredictor.predict(features(quality = lowCountQuality), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(Confidence.LOW, window.confidence)
    }
}
```

- [ ] **Step 2: Run test to verify it fails (SleepWindowPredictor does not exist yet)**

```
./gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" 2>&1 | tail -20
```
Expected: compilation error — `SleepWindowPredictor` not found.

- [ ] **Step 3: Create SleepWindowPredictor**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import java.time.Instant

object SleepWindowPredictor {

    fun predict(features: SleepFeatures, ageInWeeks: Int, now: Instant): SleepPredictionState {
        val quality = features.quality
        if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
            return SleepPredictionState.NeedMoreData(buildProgress(quality))
        }
        if (features.feedIntervals.any { it.endMillis == null }) {
            return SleepPredictionState.AfterActiveFeed
        }
        val metrics = features.metrics
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

- [ ] **Step 4: Run SleepWindowPredictorTest to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, all 8 tests pass.

- [ ] **Step 5: Refactor PredictSleepWindowUseCase to delegate to SleepWindowPredictor**

Replace the body of `PredictSleepWindowUseCase.kt` with:

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.eval.SleepWindowPredictor
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
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
        val now = Instant.now(clock)
        val maxOpenSleepAgeMillis = Duration.ofHours(SleepPredictionTuning.MAX_OPEN_SLEEP_AGE_HOURS).toMillis()
        val hasActiveSleep = sleepRecords.any { record ->
            val startMillis = record.startTime.toEpochMilli()
            val nowMillis = now.toEpochMilli()
            record.endTime == null && startMillis <= nowMillis && (nowMillis - startMillis) <= maxOpenSleepAgeMillis
        }
        if (hasActiveSleep) return SleepPredictionState.CurrentlySleeping
        baby ?: return SleepPredictionState.Unavailable("no baby profile")
        return predictForBaby(baby, sleepRecords, feedSessions, now)
    }

    private fun predictForBaby(
        baby: Baby,
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        now: Instant,
    ): SleepPredictionState {
        val today = now.atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()
        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) return SleepPredictionState.CueLed

        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val features = SleepFeatureExtractor(clock, zoneId)
            .extract(sleepRecords.filter { it.startTime >= lookbackStart }, feedSessions)

        return SleepWindowPredictor.predict(features, ageInWeeks, now)
    }
}
```

- [ ] **Step 6: Run existing PredictSleepWindowUseCaseTest to verify no regression**

```
./gradlew :app:testDebugUnitTest --tests "*.PredictSleepWindowUseCaseTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, all tests pass (same count as before).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt \
        app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt \
        app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): extract SleepWindowPredictor; PredictSleepWindowUseCase delegates to it"
```

---

## Task 3: SleepEvalHarness — core replay and scoring

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepEvalHarnessTest.kt`

- [ ] **Step 1: Write failing core harness tests**

```kotlin
// app/src/test/java/com/babytracker/domain/sleep/eval/SleepEvalHarnessTest.kt
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepEvalHarnessTest {

    private val zone = ZoneOffset.UTC
    private val baseNow = Instant.parse("2024-06-15T14:00:00Z")
    private val harness = SleepEvalHarness(zone)

    // Baby born 20 weeks before baseNow. Age = 20 weeks → ageBand = 16.
    private val baby = Baby(
        name = "TestBaby",
        birthDate = LocalDate.ofInstant(baseNow, zone).minusWeeks(20),
    )

    /**
     * Creates N completed NAP records with a stable 90-min wake window and 90-min nap duration.
     * Cycle = 180 min. Records run backwards from baseNow - 60 min.
     *
     * Record i (0 = newest, N-1 = oldest):
     *   end   = baseNow - 60min - i*180min
     *   start = end - 90min
     *
     * Wake interval between record[i] and record[i+1]: 90 min (constant, IQR ≈ 0).
     * N=32 records span ~95 h (~4 days) → LOCAL_DAYS ≥ 3 ✓ LOOKBACK_DAYS=14 ✓
     */
    private fun stableNapRecords(count: Int = 32): List<SleepRecord> {
        var id = 1L
        return (0 until count).map { i ->
            val napEnd = baseNow.minus(Duration.ofMinutes(60 + i * 180L))
            val napStart = napEnd.minus(Duration.ofMinutes(90))
            SleepRecord(id++, napStart, napEnd, SleepType.NAP)
        }.sortedBy { it.startTime }
    }

    @Nested
    inner class BlockInsufficientData {

        @Test
        fun `fewer than EVAL_MIN_ANCHORS records results in BLOCK`() {
            // 14 records → 13 wake anchors < 20 → BLOCK
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty())
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        }

        @Test
        fun `BLOCK result has null metrics`() {
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val seg = report.segments.first()
            assertNull(seg.maeMinutes)
            assertNull(seg.inWindowPct)
            assertNull(seg.missedWindowRate)
        }

        @Test
        fun `BLOCK result contains human-readable blockReason`() {
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val seg = report.segments.first()
            assertNotNull(seg.blockReason)
            assertTrue(seg.blockReason!!.isNotBlank())
        }
    }

    @Nested
    inner class PassSegment {

        @Test
        fun `32 stable records produces a PASS segment with metrics`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.firstOrNull { it.status == SegmentStatus.PASS }
            assertNotNull(passSeg, "Expected at least one PASS segment")
            assertNotNull(passSeg!!.maeMinutes)
            assertNotNull(passSeg.inWindowPct)
            assertNotNull(passSeg.missedWindowRate)
        }

        @Test
        fun `PASS segment anchor count is at least EVAL_MIN_ANCHORS`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.anchorCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS)
        }

        @Test
        fun `PASS segment has positive scoredCount`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.scoredCount > 0)
        }

        @Test
        fun `EvalReport carries correct algorithmVersion and evaluatedAt`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertEquals(SleepPredictionTuning.ALGORITHM_VERSION, report.algorithmVersion)
            assertEquals(baseNow, report.evaluatedAt)
        }

        @Test
        fun `inWindowPct is between 0 and 1`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            val pct = passSeg.inWindowPct!!
            assertTrue(pct in 0.0..1.0, "inWindowPct=$pct out of [0,1]")
        }

        @Test
        fun `missedWindowRate is between 0 and 1`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            val rate = passSeg.missedWindowRate!!
            assertTrue(rate in 0.0..1.0, "missedWindowRate=$rate out of [0,1]")
        }
    }

    @Nested
    inner class NoLookahead {

        @Test
        fun `changing ground-truth record changes MAE but not scored count`() {
            val records = stableNapRecords(32)
            // Shift last record 3 hours earlier — changes ground truth for anchor[last-1]
            // but must NOT affect any prediction (predictions use only prior records)
            val shifted = records.dropLast(1) + records.last().copy(
                startTime = records.last().startTime.minus(Duration.ofHours(3)),
                endTime = records.last().endTime!!.minus(Duration.ofHours(3)),
            )
            val reportOriginal = harness.evaluate(records, emptyList(), baby, baseNow)
            val reportShifted = harness.evaluate(shifted, emptyList(), baby, baseNow)

            assertEquals(reportOriginal.totalAnchors, reportShifted.totalAnchors)
            assertEquals(reportOriginal.totalScored, reportShifted.totalScored)
            val origSeg = reportOriginal.segments.firstOrNull { it.status == SegmentStatus.PASS }
            val shiftSeg = reportShifted.segments.firstOrNull { it.status == SegmentStatus.PASS }
            if (origSeg != null && shiftSeg != null) {
                assertNotEquals(origSeg.maeMinutes, shiftSeg.maeMinutes,
                    "MAE should differ when ground truth changes")
            }
        }

        @Test
        fun `anchor 15 bestEstimate equals direct prior-only SleepWindowPredictor call`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 15

            // Direct prior-only computation — same logic the harness must use
            val wakeInstant = sorted[anchorIdx].endTime!!
            val priorRecords = sorted.subList(0, anchorIdx + 1)
            val lookbackStart = wakeInstant.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
            val clockAtWake = java.time.Clock.fixed(wakeInstant, zone)
            val features = com.babytracker.domain.sleep.feature.SleepFeatureExtractor(clockAtWake, zone)
                .extract(priorRecords.filter { it.startTime >= lookbackStart }, emptyList())
            val ageInWeeks = java.time.temporal.ChronoUnit.WEEKS.between(
                baby.birthDate, wakeInstant.atZone(zone).toLocalDate()
            ).toInt()
            val expectedState = SleepWindowPredictor.predict(features, ageInWeeks, wakeInstant)

            // Harness per-anchor result via internal buildAnchors
            val anchors = harness.buildAnchors(sorted, emptyList(), baby)
            val anchor = anchors.first { it.wakeInstant == wakeInstant }

            assertEquals(expectedState, anchor.predictedState,
                "Harness prediction at anchor $anchorIdx must exactly match direct prior-only call")
        }

        @Test
        fun `extreme mutation of future records does not change prior anchor predictions`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val cutoff = 15 // verify anchors 0..cutoff-1 are unaffected

            // Shift all records after cutoff by +100 h — they are only ever ground truth
            // for anchors at cutoff..N, never training data for anchors 0..cutoff-1
            val mutated = sorted.mapIndexed { i, r ->
                if (i > cutoff) r.copy(
                    startTime = r.startTime.plus(Duration.ofHours(100)),
                    endTime = r.endTime!!.plus(Duration.ofHours(100)),
                ) else r
            }

            val anchorsOriginal = harness.buildAnchors(sorted, emptyList(), baby)
            val anchorsMutated = harness.buildAnchors(mutated, emptyList(), baby)

            for (i in 0 until cutoff) {
                val orig = anchorsOriginal.getOrNull(i) ?: continue
                val mut = anchorsMutated.getOrNull(i) ?: continue
                assertEquals(orig.wakeInstant, mut.wakeInstant)
                assertEquals(orig.predictedState, mut.predictedState,
                    "Anchor $i prediction changed after mutating future records — lookahead detected")
            }
        }
    }

    @Nested
    inner class FeedPointInTime {

        @Test
        fun `feed crossing wake anchor is seen as active at that anchor`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 20
            val wakeInstant = sorted[anchorIdx].endTime!!

            // Feed started before wake, ends after wake → at wakeInstant it is active
            val crossingFeed = com.babytracker.domain.model.BreastfeedingSession(
                id = 1L,
                startTime = wakeInstant.minus(Duration.ofMinutes(30)),
                endTime = wakeInstant.plus(Duration.ofMinutes(30)),
                startingSide = com.babytracker.domain.model.BreastSide.LEFT,
            )

            val anchors = harness.buildAnchors(sorted, listOf(crossingFeed), baby)
            val anchor = anchors.first { it.wakeInstant == wakeInstant }

            assertInstanceOf(SleepPredictionState.AfterActiveFeed::class.java, anchor.predictedState,
                "Feed crossing wake anchor must appear active → AfterActiveFeed, not Window")
            assertNull(anchor.score, "AfterActiveFeed anchor must not be scored")
        }

        @Test
        fun `completed feed entirely before wake anchor is included normally`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 20
            val wakeInstant = sorted[anchorIdx].endTime!!

            // Feed entirely before wakeInstant — should not trigger AfterActiveFeed
            val completedFeed = com.babytracker.domain.model.BreastfeedingSession(
                id = 2L,
                startTime = wakeInstant.minus(Duration.ofHours(2)),
                endTime = wakeInstant.minus(Duration.ofHours(1)),
                startingSide = com.babytracker.domain.model.BreastSide.RIGHT,
            )

            val anchorsWithFeed = harness.buildAnchors(sorted, listOf(completedFeed), baby)
            val anchorsNoFeed = harness.buildAnchors(sorted, emptyList(), baby)
            val anchorWith = anchorsWithFeed.first { it.wakeInstant == wakeInstant }
            val anchorNo = anchorsNoFeed.first { it.wakeInstant == wakeInstant }

            // Completed past feed must not change the prediction type
            assertEquals(anchorNo.predictedState::class, anchorWith.predictedState::class,
                "Completed pre-wake feed should not change prediction type")
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (SleepEvalHarness does not exist)**

```
./gradlew :app:testDebugUnitTest --tests "*.SleepEvalHarnessTest" 2>&1 | tail -20
```
Expected: compilation error — `SleepEvalHarness` not found.

- [ ] **Step 3: Create SleepEvalHarness**

```kotlin
// app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SleepEvalHarness(private val zoneId: ZoneId) {

    fun evaluate(
        records: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby,
        evaluatedAt: Instant,
    ): EvalReport {
        val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
        val anchors = buildAnchors(sorted, feedSessions, baby)

        // Materialize the full grid: (anchor age bands ∪ baby's current age band) × all SleepTypes.
        // Including the baby's current age band ensures that even empty logs, single records, or
        // all-cue-led datasets emit BLOCK_INSUFFICIENT_DATA segments rather than an empty report —
        // an empty report would silently mask a failed coverage evaluation.
        val anchorAgeBands = anchors.map { it.segmentKey.ageBand }.toSet()
        val currentAgeBand = ageBandFor(
            ChronoUnit.WEEKS.between(baby.birthDate, evaluatedAt.atZone(zoneId).toLocalDate()).toInt()
        )
        val allKeys = (anchorAgeBands + currentAgeBand).flatMap { band ->
            SleepType.entries.map { type -> SegmentKey(band, type) }
        }.toSet()
        val grouped = anchors.groupBy { it.segmentKey }
        val segments = allKeys.map { key ->
            scoreSegment(key, grouped[key] ?: emptyList())
        }.sortedWith(compareBy({ it.key.ageBand }, { it.key.sleepType.name }))

        return EvalReport(
            algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
            evaluatedAt = evaluatedAt,
            segments = segments,
            totalAnchors = anchors.size,
            totalScored = anchors.count { it.score != null },
        )
    }

    // internal so tests can inspect per-anchor predictions directly to verify no-lookahead.
    internal fun buildAnchors(
        sorted: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby,
    ): List<EvalAnchor> {
        val anchors = mutableListOf<EvalAnchor>()
        for (i in sorted.indices) {
            val nextRecord = sorted.getOrNull(i + 1) ?: continue

            val wakeInstant = sorted[i].endTime!!
            val ageInWeeks = ChronoUnit.WEEKS.between(
                baby.birthDate,
                wakeInstant.atZone(zoneId).toLocalDate(),
            ).toInt()
            if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) continue

            val priorRecords = sorted.subList(0, i + 1)
            val lookbackStart = wakeInstant.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
            // Point-in-time feed snapshot: a feed that started before wakeInstant but ends after it
            // must appear as active (endTime=null) — that is exactly what production sees at that moment.
            // Filtering only by startTime < wakeInstant would wrongly classify it as a completed feed
            // (or drop it), causing the harness to score a Window where production would return AfterActiveFeed.
            val priorFeeds = feedSessions.mapNotNull { session ->
                when {
                    session.startTime >= wakeInstant -> null
                    session.endTime == null || session.endTime > wakeInstant ->
                        session.copy(endTime = null)
                    else -> session
                }
            }

            val clockAtWake = Clock.fixed(wakeInstant, zoneId)
            val features = SleepFeatureExtractor(clockAtWake, zoneId)
                .extract(priorRecords.filter { it.startTime >= lookbackStart }, priorFeeds)

            val predictedState = SleepWindowPredictor.predict(features, ageInWeeks, wakeInstant)

            val segmentKey = SegmentKey(ageBandFor(ageInWeeks), nextRecord.sleepType)
            val score = (predictedState as? SleepPredictionState.Window)?.let { windowState ->
                val actualMillis = nextRecord.startTime.toEpochMilli()
                val errorMillis = abs(actualMillis - windowState.window.bestEstimate.toEpochMilli())
                val inWindow = actualMillis in
                    windowState.window.windowStart.toEpochMilli()..windowState.window.windowEnd.toEpochMilli()
                val hardMissThresholdMillis =
                    (SleepPredictionTuning.HALF_WINDOW_MINUTES + SleepPredictionTuning.OVERDUE_GRACE_MINUTES) * 60_000L
                AnchorScore(errorMillis, inWindow, errorMillis > hardMissThresholdMillis)
            }
            anchors += EvalAnchor(segmentKey, wakeInstant, predictedState, score)
        }
        return anchors
    }

    private fun scoreSegment(key: SegmentKey, anchors: List<EvalAnchor>): SegmentResult {
        if (anchors.size < SleepPredictionTuning.EVAL_MIN_ANCHORS) {
            return SegmentResult(
                key = key,
                anchorCount = anchors.size,
                scoredCount = 0,
                maeMinutes = null,
                inWindowPct = null,
                missedWindowRate = null,
                status = SegmentStatus.BLOCK_INSUFFICIENT_DATA,
                blockReason = "only ${anchors.size} anchors (min ${SleepPredictionTuning.EVAL_MIN_ANCHORS})",
            )
        }
        val scored = anchors.mapNotNull { it.score }
        if (scored.isEmpty()) {
            return SegmentResult(
                key = key,
                anchorCount = anchors.size,
                scoredCount = 0,
                maeMinutes = null,
                inWindowPct = null,
                missedWindowRate = null,
                status = SegmentStatus.BLOCK_INSUFFICIENT_DATA,
                blockReason = "0 scored anchors — predictor returned non-Window state for all ${anchors.size} wake events",
            )
        }
        return SegmentResult(
            key = key,
            anchorCount = anchors.size,
            scoredCount = scored.size,
            maeMinutes = scored.map { it.errorMillis / 60_000.0 }.average(),
            inWindowPct = scored.count { it.inWindow }.toDouble() / scored.size,
            missedWindowRate = scored.count { it.missedWindow }.toDouble() / scored.size,
            status = SegmentStatus.PASS,
            blockReason = null,
        )
    }
}

// internal so tests can call buildAnchors and inspect predictedState per anchor.
internal data class EvalAnchor(
    val segmentKey: SegmentKey,
    val wakeInstant: Instant,
    val predictedState: SleepPredictionState,
    val score: AnchorScore?,
)
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "*.SleepEvalHarnessTest" 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt \
        app/src/test/java/com/babytracker/domain/sleep/eval/SleepEvalHarnessTest.kt
git commit -m "feat(sleep): implement SleepEvalHarness with rolling-origin replay and §7.1 segment blocking"
```

---

## Task 4: Sparse and Noisy Fixture Tests

**Files:**
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepEvalHarnessTest.kt`

All fixtures test that the harness correctly blocks segments that should not produce predictions.

- [ ] **Step 1: Add fixture tests to SleepEvalHarnessTest**

Add a new `@Nested` class inside `SleepEvalHarnessTest`:

```kotlin
@Nested
inner class SparseAndNoisyFixtures {

    /**
     * Sparse logger: one NAP per day for 15 days.
     * 14 potential anchors < EVAL_MIN_ANCHORS (20) → BLOCK.
     */
    @Test
    fun `sparse logger - one sleep per day produces BLOCK`() {
        var id = 1L
        val records = (0 until 15).map { dayAgo ->
            val napEnd = baseNow.minus(Duration.ofDays(dayAgo.toLong())).minus(Duration.ofHours(2))
            SleepRecord(id++, napEnd.minus(Duration.ofHours(1)), napEnd, SleepType.NAP)
        }
        val report = harness.evaluate(records, emptyList(), baby, baseNow)
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
            "Sparse logger (1/day, 15 days) should BLOCK; got: ${report.segments}")
    }

    /**
     * Nights-only: 25 NIGHT_SLEEP records with ~15 h wake windows between them.
     * Wake intervals far exceed MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS (6 h) → filtered out →
     * completedWakeIntervals = [] → NeedMoreData for all anchors → 0 scored → BLOCK.
     */
    @Test
    fun `nights-only - wake intervals exceed plausibility ceiling, all anchors return NeedMoreData`() {
        var id = 1L
        val records = (0 until 25).map { i ->
            // Night sleep: 10 pm to 6 am → 8 h sleep, 16 h wake window → > MAX (6 h)
            val sleepEnd = baseNow.minus(Duration.ofHours((25 - i) * 24L - 6))
            val sleepStart = sleepEnd.minus(Duration.ofHours(8))
            SleepRecord(id++, sleepStart, sleepEnd, SleepType.NIGHT_SLEEP)
        }
        val report = harness.evaluate(records, emptyList(), baby, baseNow)
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
            "Nights-only with 16h wake windows should BLOCK; got: ${report.segments}")
    }

    /**
     * Naps-only: 15 NAP records.
     * NAP segment: 14 anchors < 20 → BLOCK.
     * NIGHT_SLEEP segment: 0 anchors → BLOCK (segment grid materialization ensures it appears).
     */
    @Test
    fun `naps-only - both NAP and NIGHT_SLEEP segments appear as BLOCK`() {
        var id = 1L
        val records = (0 until 15).map { i ->
            val napEnd = baseNow.minus(Duration.ofMinutes(60 + i * 180L))
            SleepRecord(id++, napEnd.minus(Duration.ofMinutes(90)), napEnd, SleepType.NAP)
        }
        val report = harness.evaluate(records, emptyList(), baby, baseNow)
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        // Segment grid materializes NIGHT_SLEEP with 0 anchors — it must be present as BLOCK,
        // not silently absent. Absence would falsely imply "no data needed" for that segment.
        val nightSeg = report.segments.firstOrNull { it.key.sleepType == SleepType.NIGHT_SLEEP }
        assertNotNull(nightSeg, "NIGHT_SLEEP segment must appear as BLOCK even with 0 anchors")
        assertEquals(0, nightSeg!!.anchorCount)
    }

    /**
     * Very erratic intervals: alternating 60 min and 4 h wake windows.
     * IQR = 180 min >> INSTABILITY_CEILING_MINUTES (45 min) AND record span < 3 local days →
     * hasSufficientZoneIndependentEvidence = false → NeedMoreData for all anchors → 0 scored → BLOCK.
     */
    @Test
    fun `erratic intervals - high IQR causes NeedMoreData for all anchors, segment BLOCKS`() {
        var id = 1L
        // Alternating short (60 min) and long (240 min) wake intervals between 90-min naps.
        // Build 25 naps from the end backwards.
        val records = mutableListOf<SleepRecord>()
        var cursor = baseNow.minus(Duration.ofMinutes(60)) // end of most recent nap
        repeat(25) { i ->
            val napEnd = cursor
            val napStart = napEnd.minus(Duration.ofMinutes(90))
            records += SleepRecord(id++, napStart, napEnd, SleepType.NAP)
            // Alternate wake gap: even iterations use 60 min, odd use 240 min
            val wakeGap = if (i % 2 == 0) 60L else 240L
            cursor = napStart.minus(Duration.ofMinutes(wakeGap))
        }
        val sorted = records.sortedBy { it.startTime }
        val report = harness.evaluate(sorted, emptyList(), baby, baseNow)
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
            "Erratic (IQR >> 45 min) fixture should BLOCK; got: ${report.segments}")
    }

    /**
     * Overlapping records: 25 naps all overlapping each other within a 2-hour window.
     * SleepFeatureExtractor.removeOverlapping() drops the entire cluster → 0 completed intervals →
     * NeedMoreData for all anchors → 0 scored → BLOCK.
     */
    @Test
    fun `overlapping records - all dropped by removeOverlapping, segment BLOCKS`() {
        var id = 1L
        // All records start within a 30-min band and end within another 30-min band → one giant cluster
        val clusterStart = baseNow.minus(Duration.ofHours(4))
        val records = (0 until 25).map { i ->
            val start = clusterStart.plus(Duration.ofMinutes(i.toLong()))
            val end = start.plus(Duration.ofHours(1))
            SleepRecord(id++, start, end, SleepType.NAP)
        }
        val report = harness.evaluate(records, emptyList(), baby, baseNow)
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
            "Overlapping cluster fixture should BLOCK; got: ${report.segments}")
    }
}

@Nested
inner class ZeroAnchorEdgeCases {

    /**
     * Empty records: no sleep history at all.
     * Segment grid derives from baby's current age band → NAP + NIGHT_SLEEP both BLOCK.
     * Without this, the report would silently return an empty segment list.
     */
    @Test
    fun `empty records - report emits BLOCK segments for baby current age band`() {
        val report = harness.evaluate(emptyList(), emptyList(), baby, baseNow)
        assertTrue(report.segments.isNotEmpty(),
            "Empty records must still produce segments (derived from baby age), not empty report")
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        assertEquals(0, report.totalAnchors)
        assertEquals(SleepType.entries.size, report.segments.size,
            "Expected one BLOCK segment per SleepType for baby's current age band")
    }

    /**
     * One completed record: valid but no nextRecord → 0 anchors.
     * Must still emit BLOCK segments, not an empty report.
     */
    @Test
    fun `single completed record - zero anchors emits BLOCK segments`() {
        val singleRecord = listOf(
            SleepRecord(
                id = 1L,
                startTime = baseNow.minus(Duration.ofHours(2)),
                endTime = baseNow.minus(Duration.ofHours(1)),
                sleepType = SleepType.NAP,
            )
        )
        val report = harness.evaluate(singleRecord, emptyList(), baby, baseNow)
        assertTrue(report.segments.isNotEmpty())
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        assertEquals(0, report.totalAnchors)
    }

    /**
     * All records belong to a baby under CUE_LED_MAX_AGE_WEEKS — all anchors skipped.
     * Segment grid still derives from baby's current age band → BLOCK segments emitted.
     */
    @Test
    fun `all-cue-led records - skipped anchors still produce BLOCK segments`() {
        // Baby born 4 weeks ago → ageInWeeks = 4 < CUE_LED_MAX_AGE_WEEKS (6)
        val youngBaby = Baby(
            name = "YoungBaby",
            birthDate = LocalDate.ofInstant(baseNow, zone).minusWeeks(4),
        )
        val records = stableNapRecords(32)
        val report = harness.evaluate(records, emptyList(), youngBaby, baseNow)
        assertTrue(report.segments.isNotEmpty(),
            "Cue-led dataset must still emit segments, not empty report")
        assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        assertEquals(0, report.totalAnchors)
    }
}
```

- [ ] **Step 2: Run fixture tests**

```
./gradlew :app:testDebugUnitTest --tests "*.SleepEvalHarnessTest.SparseAndNoisyFixtures" --tests "*.SleepEvalHarnessTest.ZeroAnchorEdgeCases" 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL, all 8 fixture/edge-case tests pass (5 sparse + 3 zero-anchor).

- [ ] **Step 3: Run full test suite to verify no regressions**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Run ktlintFormat then detekt**

```
./gradlew ktlintFormat detekt 2>&1 | tail -30
```
Fix any violations before committing.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepEvalHarnessTest.kt
git commit -m "test(sleep): add sparse/noisy fixture tests for SleepEvalHarness (AKA-90)"
```

---

## Self-Review

### Spec Coverage Check

| Spec requirement | Covered by |
|-----------------|------------|
| Replay baby's historical logs chronologically, no-lookahead | `SleepEvalHarness.buildAnchors()` uses `sorted.subList(0, i+1)` |
| MAE between `bestEstimate` and actual next sleep start | `AnchorScore.errorMillis`, `SegmentResult.maeMinutes` |
| In-window % | `AnchorScore.inWindow`, `SegmentResult.inWindowPct` |
| Missed-window rate | `AnchorScore.missedWindow` uses HALF_WINDOW + OVERDUE_GRACE threshold |
| §7.1.1 Min 20 anchors per segment → BLOCK | `scoreSegment()` first guard |
| §7.1.2 Out-of-sample replay | Rolling-origin via `subList(0, i+1)` |
| §7.1.6 Insufficient data → reject not pass | `scoreSegment()` second guard (0 scored → BLOCK) |
| Point-in-time feed state at each anchor | `buildAnchors()` mapNotNull reconstructs active/completed state |
| Zero-anchor segments must appear as BLOCK | Grid = `(anchorAgeBands ∪ currentAgeBand) × SleepType.entries`; `ZeroAnchorEdgeCases` tests empty/single/cue-led |
| No-lookahead correctness verified by test | `NoLookahead.anchor 15 bestEstimate equals direct...` + mutation test |
| Feed crossing wake anchor returns AfterActiveFeed | `FeedPointInTime.feed crossing wake anchor is seen as active` |
| `EVAL_MIN_ANCHORS = 20` in SleepPredictionTuning | Already present — no change needed |
| `ALGORITHM_VERSION = "sleep-pred-baseline-1"` | Already present — propagated to `EvalReport.algorithmVersion` |
| Sparse logger fixture | `SparseAndNoisyFixtures.sparse logger` test |
| Nights-only fixture | `SparseAndNoisyFixtures.nights-only` test |
| Naps-only fixture | `SparseAndNoisyFixtures.naps-only` test |
| Erratic intervals (high IQR) fixture | `SparseAndNoisyFixtures.erratic intervals` test |
| Overlapping/edited records fixture | `SparseAndNoisyFixtures.overlapping records` test |
| No-lookahead correctness verified by test | `NoLookahead` nested class |
| Harness pure (no Android imports) | `SleepEvalHarness` uses only `java.time` and domain classes |
| Testable with injected Clock/ZoneId | `SleepEvalHarness(zone)` constructor |
| Entry point debug-only, no production UI | Not implemented here — debug hookup belongs to AKA-92 |

**Gaps from spec not in this plan:**
- §7.1.3 Effect size ≥ 5 min MAE gain — Phase 2 (comparing factors against baseline, not baseline itself)
- §7.1.4 Bootstrap CI lower bound — Phase 2
- §7.1.5 Missed-window rate must not worsen vs baseline — Phase 2
- "Stale last wake" fixture — not directly testable via harness (the harness always evaluates at wake events, so `lastWakeMillis` is always fresh). Covered by `SleepWindowPredictorTest.returns NeedMoreData when not fresh`.
- Phase 0 GA gate (flip debug flag) — AKA-92
- Production/debug UI entry point — AKA-92

### Placeholder Scan
None found — all steps contain complete code.

### Type Consistency Check
- `SegmentKey` referenced in `SleepEvalHarness` and test — ✓ same fields
- `AnchorScore` fields `errorMillis`, `inWindow`, `missedWindow` — consistent across harness and test assertions
- `SegmentResult.status: SegmentStatus` — consistent; tests reference `SegmentStatus.BLOCK_INSUFFICIENT_DATA` and `SegmentStatus.PASS`
- `SleepWindowPredictor.predict()` signature `(features, ageInWeeks, now)` — called identically in harness and test
- `ageBandFor()` top-level function in `SegmentKey.kt` — called in `SleepEvalHarness.buildAnchors()`; matches function signature

---

Plan complete and saved to `docs/superpowers/plans/2026-06-03-sleep-eval-harness.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task, adversarial review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
