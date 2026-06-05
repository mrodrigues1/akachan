# Sleep-Debt Scoring Adjustment Implementation Plan

LINEAR_ISSUE: AKA-101

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a sleep-debt scoring factor to `SleepWindowPredictor` that shifts the window earlier when the baby is under-slept vs. their personalized daily target and later when over-slept, then gate it behind the §7.1 evaluation harness.

**Architecture:** Add `avgDailySleepMillis: Long?` to `SleepMetrics` and compute it in `SleepFeatureExtractor` (zone-independent: epoch-ms overlap ÷ `LOOKBACK_DAYS`). Create a pure `SleepDebtFactor` object and `SleepDebtFactorProvider` typealias in the existing `SleepPredictionFactor.kt` file. Wire an injectable `sleepDebtFactorProvider` parameter (default neutral) into `SleepWindowPredictor.predict` and `buildWindow`. The factor is promoted from neutral to `SleepDebtFactor::adjustment` only after the eval comparison test clears all §7.1 acceptance criteria.

**Tech Stack:** Kotlin 2.3.20, JUnit 5, MockK, pure domain tests with fixed `Clock`/`ZoneId`; no Android/Room imports.

**Dependencies:** AKA-94 must be implemented first — it introduces `SleepPredictionFactor`, `CircadianBiasFactor`, `TimeOfDaySimilarityFactor`, and the injectable `circadianFactorProvider`/`timeOfDayFactorProvider` parameters in `SleepWindowPredictor.predict`.

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt` | Modify | Add `avgDailySleepMillis: Long? = null` |
| `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt` | Modify | Compute `avgDailySleepMillis` from lookback total ÷ `LOOKBACK_DAYS` |
| `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt` | Modify | Add `SLEEP_DEBT_MAX_SHIFT_MINUTES`, `SLEEP_DEBT_SCALE_MINUTES_PER_HOUR`, `SLEEP_DEBT_MIN_HOURS`; bump `ALGORITHM_VERSION` to `sleep-pred-phase2-sleep-debt-1` |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt` | Modify | Add `SleepDebtFactorProvider` typealias |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepDebtFactor.kt` | Create | Pure factor: personalized target blend → debt → bounded shift |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt` | Modify | Add `sleepDebtFactorProvider` parameter (default neutral); apply in `buildWindow` |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt` | Modify | Update `ALGORITHM_VERSION` assertion; update `sufficientMetrics` if needed |
| `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt` | Modify | Add `avgDailySleepMillis` tests; update version assertion |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtFactorTest.kt` | Create | Unit tests for all factor branches |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtEvalComparisonTest.kt` | Create | §7.1 comparison: benefit cohort + no-regression cohort |

---

## Task 1: Add `avgDailySleepMillis` to `SleepMetrics` and `SleepFeatureExtractor`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1.1: Write failing tests for `avgDailySleepMillis`**

Add these tests to `SleepFeatureExtractorTest.kt` (in the metrics section, alongside existing metric tests):

```kotlin
@Test
fun `avgDailySleepMillis is null when fewer than MIN_COMPLETED_INTERVALS completed sleeps in lookback`() {
    val clock = Clock.fixed(Instant.parse("2024-06-15T14:00:00Z"), zone)
    val extractor = SleepFeatureExtractor(clock, zone)
    val records = listOf(
        SleepRecord(1, Instant.parse("2024-06-14T20:00:00Z"), Instant.parse("2024-06-15T06:00:00Z"), SleepType.NIGHT_SLEEP)
    )
    val features = extractor.extract(records, emptyList())
    assertEquals(null, features.metrics.avgDailySleepMillis)
}

@Test
fun `avgDailySleepMillis computes total lookback sleep divided by LOOKBACK_DAYS`() {
    val now = Instant.parse("2024-06-15T14:00:00Z")
    val clock = Clock.fixed(now, zone)
    val extractor = SleepFeatureExtractor(clock, zone)
    // 5 nap records of exactly 60 min each, all within lookback
    val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
    val records = (0 until 5).map { i ->
        val start = lookbackStart.plus(Duration.ofDays(i.toLong())).plus(Duration.ofHours(9))
        SleepRecord(i.toLong() + 1, start, start.plus(Duration.ofHours(1)), SleepType.NAP)
    }
    val features = extractor.extract(records, emptyList())
    val expectedMillis = Duration.ofMinutes(5 * 60).toMillis() / SleepPredictionTuning.LOOKBACK_DAYS
    assertEquals(expectedMillis, features.metrics.avgDailySleepMillis)
}

@Test
fun `avgDailySleepMillis clips sleep that started before lookback window`() {
    val now = Instant.parse("2024-06-15T14:00:00Z")
    val clock = Clock.fixed(now, zone)
    val extractor = SleepFeatureExtractor(clock, zone)
    val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
    // One sleep that starts 2h before lookback but ends 1h after lookback start
    val preStart = lookbackStart.minus(Duration.ofHours(2))
    val preEnd = lookbackStart.plus(Duration.ofHours(1))
    // Plus 4 more to meet MIN_COMPLETED_INTERVALS
    val extras = (0 until 4).map { i ->
        val start = lookbackStart.plus(Duration.ofDays(i.toLong() + 1L)).plus(Duration.ofHours(9))
        SleepRecord(i.toLong() + 2, start, start.plus(Duration.ofHours(1)), SleepType.NAP)
    }
    val records = listOf(SleepRecord(1, preStart, preEnd, SleepType.NIGHT_SLEEP)) + extras
    val features = extractor.extract(records, emptyList())
    // Only 1h (the overlap after lookbackStart) + 4h from extras = 5h total / LOOKBACK_DAYS
    val expectedMillis = Duration.ofHours(5).toMillis() / SleepPredictionTuning.LOOKBACK_DAYS
    assertEquals(expectedMillis, features.metrics.avgDailySleepMillis)
}
```

- [ ] **Step 1.2: Run tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: FAIL because `avgDailySleepMillis` does not exist on `SleepMetrics`.

- [ ] **Step 1.3: Add `avgDailySleepMillis` to `SleepMetrics`**

In `SleepMetrics.kt`, add at the end of the constructor (default `null` so all existing callers compile unchanged):

```kotlin
data class SleepMetrics(
    val lastWakeMillis: Long?,
    val lastSleepType: SleepType?,
    val lastSleepDurationMillis: Long?,
    val completedWakeIntervals: List<Long>,
    val medianWakeIntervalMillis: Long?,
    val wakeIntervalIqrMillis: Long?,
    val sleepLast24hMillis: Long,
    val daySleepTodayMillis: Long,
    val napCountToday: Int,
    val medianBedtimeMinuteOfDay: Int?,
    val medianMorningWakeMinuteOfDay: Int?,
    val napWakeIntervalCount: Int = 0,
    val napWakeP25Millis: Long? = null,
    val napWakeP50Millis: Long? = null,
    val napWakeP75Millis: Long? = null,
    val bedtimeWakeIntervalCount: Int = 0,
    val bedtimeWakeP25Millis: Long? = null,
    val bedtimeWakeP50Millis: Long? = null,
    val bedtimeWakeP75Millis: Long? = null,
    val avgDailySleepMillis: Long? = null,
)
```

- [ ] **Step 1.4: Compute `avgDailySleepMillis` in `SleepFeatureExtractor`**

Add this private helper to `SleepFeatureExtractor` (after the existing `iqr` helper):

```kotlin
private fun avgDailySleepMillis(completed: List<SleepInterval>): Long? {
    val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()
    val inLookback = completed.filter { it.endMillis!! >= lookbackStartMillis }
    if (inLookback.size < SleepPredictionTuning.MIN_COMPLETED_INTERVALS) return null
    val totalSleepMillis = inLookback.sumOf { interval ->
        val overlapStart = maxOf(interval.startMillis, lookbackStartMillis)
        (interval.endMillis!! - overlapStart).coerceAtLeast(0L)
    }
    return totalSleepMillis / SleepPredictionTuning.LOOKBACK_DAYS
}
```

Then add the field to the `SleepMetrics` builder at the end of `computeMetrics`:

```kotlin
return SleepMetrics(
    lastWakeMillis = lastSleep?.endMillis,
    lastSleepType = lastSleep?.sleepType,
    lastSleepDurationMillis = lastSleep?.durationMillis,
    completedWakeIntervals = wakeIntervals,
    medianWakeIntervalMillis = median(wakeIntervals),
    wakeIntervalIqrMillis = iqr(wakeIntervals),
    sleepLast24hMillis = sumOverlap(completed, nowMillis - Duration.ofHours(24).toMillis(), nowMillis),
    daySleepTodayMillis = sumTodayDaySleep(completed, today),
    napCountToday = completed.count {
        it.sleepType == SleepType.NAP && LocalDate.ofInstant(Instant.ofEpochMilli(it.startMillis), zoneId) == today
    },
    medianBedtimeMinuteOfDay = medianMinuteOfDay(nightSleeps.map { it.startMillis }),
    medianMorningWakeMinuteOfDay = medianMinuteOfDay(nightSleeps.mapNotNull { it.endMillis }),
    napWakeIntervalCount = napIntervals.size,
    napWakeP25Millis = napQuartiles?.first,
    napWakeP50Millis = napQuartiles?.second ?: median(napIntervals),
    napWakeP75Millis = napQuartiles?.third,
    bedtimeWakeIntervalCount = bedtimeIntervals.size,
    bedtimeWakeP25Millis = bedtimeQuartiles?.first,
    bedtimeWakeP50Millis = bedtimeQuartiles?.second ?: median(bedtimeIntervals),
    bedtimeWakeP75Millis = bedtimeQuartiles?.third,
    avgDailySleepMillis = avgDailySleepMillis(completed),
)
```

- [ ] **Step 1.5: Run the extractor tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 1.6: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add avg daily sleep metric for sleep-debt factor [AKA-98]"
```

---

## Task 2: Add tuning constants, provider typealias, and bump algorithm version

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 2.1: Write failing version test**

In `SleepWindowPredictorTest.kt`, replace the current `ALGORITHM_VERSION` assertion:

```kotlin
@Test
fun `ALGORITHM_VERSION is phase2 sleep-debt version`() {
    assertEquals(
        "sleep-pred-phase2-sleep-debt-1",
        SleepPredictionTuning.ALGORITHM_VERSION,
        "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
    )
}
```

In `SleepFeatureExtractorTest.kt`, update the version assertion in the tuning-constants test to:

```kotlin
assertEquals("sleep-pred-phase2-sleep-debt-1", SleepPredictionTuning.ALGORITHM_VERSION)
```

- [ ] **Step 2.2: Run the version tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: FAIL because `ALGORITHM_VERSION` is still the previous value.

- [ ] **Step 2.3: Add tuning constants and bump algorithm version**

In `SleepPredictionTuning.kt`, add these constants after `EVAL_MAX_REGRESSION`:

```kotlin
const val SLEEP_DEBT_MAX_SHIFT_MINUTES = 20L
const val SLEEP_DEBT_SCALE_MINUTES_PER_HOUR = 5L
const val SLEEP_DEBT_MIN_HOURS = 1L
```

Then bump:

```kotlin
const val ALGORITHM_VERSION = "sleep-pred-phase2-sleep-debt-1"
```

- [ ] **Step 2.4: Add `SleepDebtFactorProvider` typealias**

In `SleepPredictionFactor.kt`, add after the existing `TimeOfDayFactorProvider` typealias:

```kotlin
typealias SleepDebtFactorProvider = (
    Long,  // sleepLast24hMillis
    Long?, // avgDailySleepMillis (null if insufficient history)
    Int,   // ageInWeeks
) -> SleepPredictionFactor
```

- [ ] **Step 2.5: Run version tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 2.6: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add sleep-debt factor tuning constants and provider typealias [AKA-98]"
```

---

## Task 3: Create `SleepDebtFactor` with unit tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepDebtFactor.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtFactorTest.kt`

**Design note:** `debtMillis = personalizedTarget - sleepLast24hMillis`. Positive debt = under-slept → shift window EARLIER (negative `adjustment`). Negative debt = over-slept → shift LATER (positive `adjustment`). The scaling: `debtHours * SLEEP_DEBT_SCALE_MINUTES_PER_HOUR` with a minimum threshold of `SLEEP_DEBT_MIN_HOURS` to filter noise. Personalized target blends age-prior midpoint with `avgDailySleepMillis` at 50/50 when personal history is available, falling back to age prior alone when history is absent. Both the age-prior range endpoints come from `SleepAgePriors.getTotalSleepRecommendation`, which returns a `ClosedRange<Duration>`.

- [ ] **Step 3.1: Write failing tests**

Create `SleepDebtFactorTest.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SleepDebtFactorTest {

    private val ageInWeeks = 20 // getTotalSleepRecommendation: 12h–16h → midpoint 14h = 50400000 ms

    @Test
    fun `neutral when sleepLast24h equals personalized target`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = targetMillis,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `neutral when debt is below SLEEP_DEBT_MIN_HOURS threshold`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val slightlyUnder = targetMillis - Duration.ofMinutes(30).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = slightlyUnder,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `under-slept produces negative adjustment (shift earlier)`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val underSlept = targetMillis - Duration.ofHours(2).toMillis() // 2h debt
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(factor.adjustment < Duration.ZERO,
            "Under-slept baby should have a negative (earlier) adjustment, got ${factor.adjustment}")
    }

    @Test
    fun `over-slept produces positive adjustment (shift later)`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val overSlept = targetMillis + Duration.ofHours(3).toMillis() // 3h surplus
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = overSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(factor.adjustment > Duration.ZERO,
            "Over-slept baby should have a positive (later) adjustment, got ${factor.adjustment}")
    }

    @Test
    fun `adjustment is capped at SLEEP_DEBT_MAX_SHIFT_MINUTES`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val severelyUnderSlept = targetMillis - Duration.ofHours(10).toMillis() // extreme debt
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = severelyUnderSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.adjustment.abs().toMinutes() <= SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
            "Adjustment magnitude must not exceed SLEEP_DEBT_MAX_SHIFT_MINUTES",
        )
    }

    @Test
    fun `uses age-prior only when avgDailySleepMillis is null`() {
        // Age prior midpoint for 20w: (12h + 16h) / 2 = 14h
        val agePriorMidpointMillis = Duration.ofHours(14).toMillis()
        // Under-slept by 2h vs. age prior
        val underSlept = agePriorMidpointMillis - Duration.ofHours(2).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = null,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(factor.adjustment < Duration.ZERO,
            "Should still shift earlier vs. age prior when avgDailySleepMillis is null")
    }

    @Test
    fun `personalized target blends age prior and personal average at 50-50`() {
        // Age prior midpoint = 14h; personal avg = 12h → blended target = 13h
        val agePriorMidpointMillis = Duration.ofHours(14).toMillis()
        val personalAvgMillis = Duration.ofHours(12).toMillis()
        val blendedTargetMillis = (agePriorMidpointMillis + personalAvgMillis) / 2  // 13h
        // Exact match → neutral
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = blendedTargetMillis,
            avgDailySleepMillis = personalAvgMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `factor reason contains direction word when factor is active`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val underSlept = targetMillis - Duration.ofHours(2).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.reason != null && (factor.reason.contains("earlier") || factor.reason.contains("later")),
            "Active factor must include direction in reason string",
        )
    }
}
```

- [ ] **Step 3.2: Run tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepDebtFactorTest" -PfastTests
```

Expected: FAIL because `SleepDebtFactor` does not exist.

- [ ] **Step 3.3: Create `SleepDebtFactor.kt`**

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration

object SleepDebtFactor {

    fun adjustment(
        sleepLast24hMillis: Long,
        avgDailySleepMillis: Long?,
        ageInWeeks: Int,
    ): SleepPredictionFactor {
        val agePriorMidpointMillis = agePriorMidpointMillis(ageInWeeks)
        val personalTargetMillis = if (avgDailySleepMillis != null) {
            (agePriorMidpointMillis + avgDailySleepMillis) / 2
        } else {
            agePriorMidpointMillis
        }

        val debtMillis = personalTargetMillis - sleepLast24hMillis
        val debtHours = debtMillis.toDouble() / Duration.ofHours(1).toMillis().toDouble()

        if (kotlin.math.abs(debtHours) < SleepPredictionTuning.SLEEP_DEBT_MIN_HOURS) {
            return SleepPredictionFactor.Neutral
        }

        // Positive debt (under-slept) → shift earlier → negative adjustment
        val rawShiftMinutes = (debtHours * SleepPredictionTuning.SLEEP_DEBT_SCALE_MINUTES_PER_HOUR).toLong()
        val clampedShiftMinutes = rawShiftMinutes.coerceIn(
            -SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
            SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
        )
        if (clampedShiftMinutes == 0L) return SleepPredictionFactor.Neutral

        val direction = if (clampedShiftMinutes > 0) "earlier" else "later"
        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(-clampedShiftMinutes),
            reason = "Sleep debt: 24h total vs. daily target suggests $direction window",
        )
    }

    private fun agePriorMidpointMillis(ageInWeeks: Int): Long {
        val range = SleepAgePriors.getTotalSleepRecommendation(ageInWeeks)
        return (range.start.toMillis() + range.endInclusive.toMillis()) / 2L
    }
}
```

- [ ] **Step 3.4: Run the factor tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepDebtFactorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 3.5: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepDebtFactor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtFactorTest.kt
git commit -m "feat(sleep): add sleep-debt scoring factor [AKA-98]"
```

---

## Task 4: Wire `sleepDebtFactorProvider` into `SleepWindowPredictor`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

**Design note:** The provider is applied inside `buildWindow` after the circadian and time-of-day factors (added by AKA-94), before the final staleness check on the adjusted window. The raw-window staleness check (before any factor) remains as the first gate. A positive factor cannot revive a raw-stale window; a negative factor can make a raw-fresh window stale, which is caught by the second gate.

- [ ] **Step 4.1: Write wiring tests**

Add to `SleepWindowPredictorTest.kt`:

```kotlin
@Test
fun `sleep-debt factor shifts window earlier when injected with negative adjustment`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val neutralResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        sleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
    )
    val debtResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        sleepDebtFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-10))
        },
    )
    val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
    val debtEstimate = (debtResult as SleepPredictionState.Window).window.bestEstimate
    assertTrue(debtEstimate.isBefore(neutralEstimate),
        "Sleep-debt factor with -10 min adjustment must shift bestEstimate earlier")
}

@Test
fun `sleep-debt factor reason appears in window reasons`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        sleepDebtFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-10), reason = "sleep debt reason")
        },
    )
    val window = (result as SleepPredictionState.Window).window
    assertTrue(window.reasons.contains("sleep debt reason"),
        "Factor reason must appear in window.reasons")
}

@Test
fun `negative sleep-debt adjustment cannot make a raw-fresh window stale`() {
    // lastWake 60 min ago, median 90 min → raw bestEstimate = 30 min in future → not stale raw
    // -20 min adjustment → adjusted estimate = 10 min in future → still not stale (window ends 25 min in future)
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        sleepDebtFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES))
        },
    )
    // Window should still be a Window (not Overdue) because adjusted estimate is still in the future
    assertInstanceOf(SleepPredictionState.Window::class.java, result)
}

@Test
fun `large negative sleep-debt adjustment on near-stale window returns Overdue`() {
    // lastWake 5h ago, median 90 min → raw bestEstimate ≈ 3.5h ago → raw stale already
    val lastWakeMillis = baseNow.minus(Duration.ofHours(5)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        sleepDebtFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES))
        },
    )
    assertInstanceOf(SleepPredictionState.Overdue::class.java, result,
        "Raw-stale window must return Overdue even when debt factor is applied")
}
```

- [ ] **Step 4.2: Run tests and verify the wiring tests fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: FAIL because `predict` does not accept `sleepDebtFactorProvider`.

- [ ] **Step 4.3: Add `sleepDebtFactorProvider` to `SleepWindowPredictor.predict`**

In `SleepWindowPredictor.kt`, extend the public `predict` signature (add after `timeOfDayFactorProvider` from AKA-94):

```kotlin
fun predict(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider = CircadianBiasFactor::adjustment,
    timeOfDayFactorProvider: TimeOfDayFactorProvider = TimeOfDaySimilarityFactor::adjustment,
    sleepDebtFactorProvider: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
): SleepPredictionState {
    val quality = features.quality
    if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
        return SleepPredictionState.NeedMoreData(buildProgress(quality))
    }
    if (features.feedIntervals.any { it.endMillis == null }) {
        return SleepPredictionState.AfterActiveFeed
    }
    return buildWindow(features, ageInWeeks, now, circadianFactorProvider, timeOfDayFactorProvider, sleepDebtFactorProvider)
}
```

Update the private `buildWindow` signature:

```kotlin
private fun buildWindow(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider,
    timeOfDayFactorProvider: TimeOfDayFactorProvider,
    sleepDebtFactorProvider: SleepDebtFactorProvider,
): SleepPredictionState {
```

- [ ] **Step 4.4: Apply sleep-debt factor in `buildWindow`**

Inside `buildWindow`, after the existing `timeOfDayFactor` call (from AKA-94) and before the final `adjustedBestEstimate` assignment, add:

```kotlin
val sleepDebtFactor = sleepDebtFactorProvider(
    metrics.sleepLast24hMillis,
    metrics.avgDailySleepMillis,
    ageInWeeks,
)
val adjustedBestEstimate = bestEstimate
    .plus(circadianFactor.adjustment)
    .plus(timeOfDayFactor.adjustment)
    .plus(sleepDebtFactor.adjustment)
```

Update the reasons assembly to include the debt factor reason:

```kotlin
val reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
    listOfNotNull(circadianFactor.reason, timeOfDayFactor.reason, sleepDebtFactor.reason)
```

- [ ] **Step 4.5: Run the wiring tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 4.6: Run the full fast suite**

Run:

```powershell
.\gradlew :app:testDebugUnitTest -PfastTests
```

Expected: PASS.

- [ ] **Step 4.7: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): wire sleep-debt factor into SleepWindowPredictor [AKA-98]"
```

---

## Task 5: Eval comparison test and factor promotion

**Files:**
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtEvalComparisonTest.kt`

**Design note:** The benefit fixture interleaves "normal-sleep" days (baby gets 14h, falls asleep at the standard median interval) with "sleep-deprived" days (baby got only 11.5h in the rolling 24h before the anchor wake event, falls asleep 15 min earlier than median). The leave-one-day-out replay means the debt harness sees `sleepLast24hMillis` below the personalized target on deprived days and shifts the window earlier. The no-regression fixture uses a baby whose `sleepLast24hMillis` is always exactly on target (±0.5h); the debt factor must be neutral and not alter MAE or missed-window rate.

- [ ] **Step 5.1: Write comparison test**

Create `SleepDebtEvalComparisonTest.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class SleepDebtEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 1, 1))

    // ageInWeeks ≈ 25w → getTotalSleepRecommendation returns 12–16h (midpoint 14h)

    @Test
    fun `sleep-debt factor clears section 7-1 on interleaved sleep-deprived fixture`() {
        val records = interleavedDeprivedRecords(normalDays = 30, deprivedDays = 30)

        val baselineAnchors = buildAnchors(records, neutralDebtProvider)
        val debtAnchors = buildAnchors(records, SleepDebtFactor::adjustment)

        val bedtimeAnchors = pairedAnchors(baselineAnchors, debtAnchors, SleepType.NIGHT_SLEEP)
        val napAnchors = pairedAnchors(baselineAnchors, debtAnchors, SleepType.NAP)

        for ((label, paired) in listOf("bedtime" to bedtimeAnchors, "nap" to napAnchors)) {
            assertTrue(
                paired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
                "Fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} $label anchors; got ${paired.size}",
            )
            val improvements = paired.map { (baseline, new) ->
                baseline.errorMillis / 60_000.0 - new.errorMillis / 60_000.0
            }
            val maeGain = improvements.average()
            val newInWindow = paired.count { (_, new) -> new.inWindow }.toDouble() / paired.size
            val baselineInWindow = paired.count { (baseline, _) -> baseline.inWindow }.toDouble() / paired.size
            val newMissed = paired.count { (_, new) -> new.missedWindow }.toDouble() / paired.size
            val baselineMissed = paired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / paired.size

            assertTrue(
                maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
                "[$label] MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN}",
            )
            assertTrue(
                newInWindow >= baselineInWindow,
                "[$label] In-window pct must not worsen; baseline=${"%.2f".format(baselineInWindow)}, new=${"%.2f".format(newInWindow)}",
            )
            assertTrue(
                bootstrapCiLowerBound(improvements) > 0.0,
                "[$label] Bootstrap CI lower bound for MAE gain must be positive",
            )
            assertTrue(
                newMissed <= baselineMissed + SleepPredictionTuning.EVAL_MAX_REGRESSION,
                "[$label] Missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissed)}, new=${"%.2f".format(newMissed)}",
            )
        }
    }

    @Test
    fun `sleep-debt factor does not regress on well-rested stable fixture`() {
        val records = stableWellRestedRecords(days = 60)

        val baselineAnchors = buildAnchors(records, neutralDebtProvider)
        val debtAnchors = buildAnchors(records, SleepDebtFactor::adjustment)

        for (type in SleepType.entries) {
            val paired = pairedAnchors(baselineAnchors, debtAnchors, type)
            if (paired.size < SleepPredictionTuning.EVAL_MIN_ANCHORS) continue

            val newMae = paired.map { (_, new) -> new.errorMillis / 60_000.0 }.average()
            val baselineMae = paired.map { (baseline, _) -> baseline.errorMillis / 60_000.0 }.average()
            val newMissed = paired.count { (_, new) -> new.missedWindow }.toDouble() / paired.size
            val baselineMissed = paired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / paired.size

            assertTrue(
                newMae <= baselineMae + SleepPredictionTuning.EVAL_MAX_REGRESSION,
                "[${type.name}] MAE must not worsen; baseline=${"%.1f".format(baselineMae)}, new=${"%.1f".format(newMae)}",
            )
            assertTrue(
                newMissed <= baselineMissed + SleepPredictionTuning.EVAL_MAX_REGRESSION,
                "[${type.name}] Missed-window rate must not worsen",
            )
        }
    }

    // --- Fixtures ---

    private fun interleavedDeprivedRecords(normalDays: Int, deprivedDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = normalDays + deprivedDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        // Interleave normal and deprived days
        repeat(totalDays) { dayIndex ->
            val isDeprived = dayIndex % 2 == 1

            if (!isDeprived) {
                // Normal day: night 20:00–06:00 (10h), nap1 09:30–11:00 (1.5h), nap2 14:00–15:30 (1.5h) = 13h total
                val nightStart = dayStart.plus(Duration.ofHours(20))
                val nightEnd = nightStart.plus(Duration.ofHours(10))
                records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

                val nap1Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(30))
                val nap1End = nap1Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

                val nap2Start = dayStart.plus(Duration.ofHours(14))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
            } else {
                // Deprived day: short night 22:00–04:00 (6h), nap1 earlier 08:30–10:00 (1.5h), nap2 13:00–14:30 (1.5h)
                // Rolling 24h from previous nap2 end (15:30 day N-1) to nap2 end today = 6+1.5+1.5 = 9h
                // Debt vs 13h target = 4h → shift -20 min (capped at SLEEP_DEBT_MAX_SHIFT_MINUTES)
                // Actual nap1 is 60 min EARLIER than normal (08:30 vs 09:30) because baby is overtired
                val nightStart = dayStart.plus(Duration.ofHours(22))
                val nightEnd = nightStart.plus(Duration.ofHours(6))
                records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

                val nap1Start = dayStart.plus(Duration.ofHours(8)).plus(Duration.ofMinutes(30))
                val nap1End = nap1Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

                val nap2Start = dayStart.plus(Duration.ofHours(13))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
            }
            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    private fun stableWellRestedRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(days) {
            // Consistent 14h sleep: night 20:00–07:00 (11h), nap1 10:00–11:30 (1.5h), nap2 14:30–16:00 (1.5h)
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(10))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(14)).plus(Duration.ofMinutes(30))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    // --- Harness helpers ---

    private val neutralDebtProvider: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }

    private fun buildAnchors(
        records: List<SleepRecord>,
        debtProvider: SleepDebtFactorProvider,
    ): List<EvalAnchor> {
        val harness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                sleepDebtFactorProvider = debtProvider,
            )
        }
        return harness.buildAnchors(records, emptyList(), baby)
    }

    private fun pairedAnchors(
        baselineAnchors: List<EvalAnchor>,
        newAnchors: List<EvalAnchor>,
        type: SleepType,
    ): List<Pair<AnchorScore, AnchorScore>> {
        val baselineByInstant = baselineAnchors.associateBy { it.wakeInstant }
        return newAnchors
            .filter { it.segmentKey.sleepType == type && it.score != null }
            .mapNotNull { new ->
                val baseline = baselineByInstant[new.wakeInstant] ?: return@mapNotNull null
                val baselineScore = baseline.score ?: return@mapNotNull null
                baselineScore to new.score!!
            }
    }

    private fun bootstrapCiLowerBound(improvements: List<Double>, samples: Int = 1000): Double {
        val rng = Random(98)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
```

- [ ] **Step 5.2: Run the comparison test**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepDebtEvalComparisonTest" -PfastTests
```

**If the benefit fixture PASSES all §7.1 criteria:**

Promote the factor by changing the default in `SleepWindowPredictor.predict`:

```kotlin
sleepDebtFactorProvider: SleepDebtFactorProvider = SleepDebtFactor::adjustment,
```

**If the benefit fixture FAILS any §7.1 criterion:**

Keep the neutral default. Do not change the `predict` signature. Attach the test output (from the assertion failure message) as a comment on Linear issue AKA-98. The factor stays disabled until the fixture or the factor implementation is improved in a follow-up PR.

- [ ] **Step 5.3: Run the full fast suite**

Run:

```powershell
.\gradlew :app:testDebugUnitTest -PfastTests
```

Expected: PASS.

- [ ] **Step 5.4: Run full unit suite without `-PfastTests` before pushing**

Run:

```powershell
.\gradlew :app:testDebugUnitTest
```

Expected: PASS including architecture tests.

- [ ] **Step 5.5: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit (adapt message based on promotion outcome):

If promoted:

```powershell
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtEvalComparisonTest.kt
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git commit -m "feat(sleep): promote sleep-debt factor after eval gate [AKA-98]"
```

If not promoted (gate failed):

```powershell
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepDebtEvalComparisonTest.kt
git commit -m "test(sleep): add sleep-debt eval gate (factor stays disabled — gate failed) [AKA-98]"
```

---

## Self-Review: Spec Coverage Check

| AKA-98 sleep-debt requirement | Task that implements it |
|-------------------------------|------------------------|
| `sleepDebtMillis = personalizedDailyTarget - sleepLast24hMillis` | Task 3: `SleepDebtFactor.adjustment` |
| Under-slept → shift window earlier | Task 3: negative adjustment when debt > 0 |
| Over-slept → shift later | Task 3: positive adjustment when debt < 0 |
| Personalized target = blend(age prior, personal history) | Task 3: 50/50 blend when `avgDailySleepMillis` non-null |
| Personal history computed from logs | Task 1: `avgDailySleepMillis` in extractor |
| `avgDailySleepMillis` is zone-independent | Task 1: epoch-ms overlap ÷ LOOKBACK_DAYS |
| Factor independently evaluated against baseline (§7.1) | Task 5: comparison test with both cohorts |
| ≥ EVAL_MIN_ANCHORS per segment | Task 5: fixture-size assertion per type |
| MAE gain ≥ EVAL_MIN_MAE_GAIN_MIN | Task 5: maeGain assertion |
| Positive in-window % gain | Task 5: newInWindow ≥ baselineInWindow assertion |
| Bootstrap CI lower bound positive | Task 5: bootstrapCiLowerBound assertion |
| No missed-window regression | Task 5: newMissed ≤ baselineMissed assertion |
| Insufficient data blocks rather than passes | Existing harness behavior (BLOCK_INSUFFICIENT_DATA) |
| Factor default neutral until gate passes | Task 4 default + Task 5 conditional promotion |
| Attach harness comparison output before merging | Task 5 command output is the artifact |
| Conventional commit scope `feat(sleep)` | All commits use `feat(sleep)` or `test(sleep)` with `[AKA-98]` |
| Algorithm version bumped | Task 2: `sleep-pred-phase2-sleep-debt-1` |
