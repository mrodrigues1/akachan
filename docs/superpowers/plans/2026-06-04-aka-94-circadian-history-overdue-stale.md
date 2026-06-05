# AKA-94 - Circadian Bias, Time-of-Day Similarity, and Overdue/Stale Handling Implementation Plan

LINEAR_ISSUE: AKA-94

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit overdue/stale candidate handling and introduce evaluation-gated Phase 2 hooks for circadian bias and baby-history time-of-day similarity without using unsafe local-day history before timezone provenance exists.

**Architecture:** Keep prediction logic in the pure `domain/sleep/eval` package. Overdue/stale handling is hardened inside `SleepWindowPredictor` because it is safe with the existing single-candidate model. Circadian bias is implemented as a pure, bounded bedtime-only candidate adjustment that uses current local time plus age-prior bedtime windows, but it stays disabled by default until the eval gate proves both benefit and no-regression cohorts. Nap circadian bias stays neutral until a real current-day anchor exists. Baby-history time-of-day similarity gets a fail-closed provenance gate and comparison harness coverage now; the actual local-day historical adjustment remains disabled until Phase 3 adds per-record timezone provenance.

**Tech Stack:** Kotlin 2.3.20, JUnit 5, MockK, pure domain tests with fixed `Clock`/`ZoneId`; no Android/Room imports in the new prediction-factor code.

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt` | Modify | Add circadian/history tuning constants; bump `ALGORITHM_VERSION` to `sleep-pred-phase2-circadian-history-1` |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt` | Create | Small value object for candidate adjustments, with a disabled/no-op state |
| `app/src/main/java/com/babytracker/domain/sleep/eval/CircadianBiasFactor.kt` | Create | Pure age-ramped, bounded wall-clock adjustment for NAP/NIGHT_SLEEP candidates |
| `app/src/main/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactor.kt` | Create | Fail-closed historical time-of-day factor; disabled until timezone provenance is qualified |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt` | Modify | Extract stale-window helper; add disabled-by-default factor-provider hooks; call time-of-day factor only through provenance gate |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt` | Modify | Replace callable-reference default predictor with a 3-argument lambda so default-argument arity compiles |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt` | Modify | Add overdue/stale boundary tests and predictor-wiring tests |
| `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt` | Modify | Update `ALGORITHM_VERSION` assertion |
| `app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasFactorTest.kt` | Create | Unit tests for ramp, no-op under cue-led age, bounded shifts, bedtime window behavior, and nap behavior |
| `app/src/test/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactorTest.kt` | Create | Unit tests proving local-day history is blocked without qualified timezone provenance |
| `app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasEvalComparisonTest.kt` | Create | Section 7.1 comparison fixture for circadian factor vs current best baseline |

---

## Task 1: Add prediction-factor model and tuning constants

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1.1: Write failing version test**

In `SleepWindowPredictorTest.kt`, replace the current algorithm-version test with:

```kotlin
@Test
fun `ALGORITHM_VERSION is phase2 circadian history version`() {
    assertEquals(
        "sleep-pred-phase2-circadian-history-1",
        SleepPredictionTuning.ALGORITHM_VERSION,
        "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
    )
}
```

In `SleepFeatureExtractorTest.kt`, replace the existing version assertion in `SleepPredictionTuning constants are positive` with:

```kotlin
assertEquals("sleep-pred-phase2-circadian-history-1", SleepPredictionTuning.ALGORITHM_VERSION)
```

- [ ] **Step 1.2: Run the test and verify it fails**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: FAIL because `ALGORITHM_VERSION` is still `sleep-pred-phase2-personalized-wake-2`.

- [ ] **Step 1.3: Create the shared factor result model**

Create `SleepPredictionFactor.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import java.time.Duration

data class SleepPredictionFactor(
    val adjustment: Duration,
    val reason: String? = null,
    val enabled: Boolean = true,
) {
    companion object {
        val Disabled = SleepPredictionFactor(Duration.ZERO, enabled = false)
        val Neutral = SleepPredictionFactor(Duration.ZERO)
    }
}

typealias CircadianFactorProvider = (
    Int,
    com.babytracker.domain.model.SleepType,
    Int?,
    Int?,
    Int,
) -> SleepPredictionFactor

typealias TimeOfDayFactorProvider = (
    com.babytracker.domain.sleep.feature.SleepMetrics,
    com.babytracker.domain.model.SleepType,
    Int?,
    Boolean,
) -> SleepPredictionFactor
```

- [ ] **Step 1.4: Add tuning constants and bump algorithm version**

In `SleepPredictionTuning.kt`, add these constants near the existing Phase 2 constants:

```kotlin
const val CIRCADIAN_MIN_AGE_WEEKS = 6
const val CIRCADIAN_FULL_WEIGHT_AGE_WEEKS = 12
const val CIRCADIAN_MAX_SHIFT_MINUTES = 20L
const val CIRCADIAN_TARGET_NEUTRALITY_MINUTES = 10L
const val TIME_OF_DAY_MAX_SHIFT_MINUTES = 15L
const val TIME_OF_DAY_MIN_HISTORY_COUNT = 7
```

Then change:

```kotlin
const val ALGORITHM_VERSION = "sleep-pred-phase2-circadian-history-1"
```

Remove the stale issue-reference wording from `EVAL_MIN_MAE_GAIN_MIN` and `EVAL_MAX_REGRESSION` comments so detekt does not flag documentation debt in code:

```kotlin
const val EVAL_MIN_MAE_GAIN_MIN = 5
const val EVAL_MAX_REGRESSION = 0
```

- [ ] **Step 1.5: Run the focused predictor test**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.SleepFeatureExtractorTest" -PfastTests
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
git add app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add sleep prediction factor tuning [AKA-94]"
```

---

## Task 2: Harden overdue/stale candidate handling

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

**Design note:** The circadian factor provider is injectable and defaults to neutral until Task 6 proves the gate. This keeps production behavior unchanged while giving the evaluation harness a production-equivalent way to compare neutral vs. circadian behavior. Candidate local minute is derived from `features.currentMinuteOfDay + Duration.between(now, bestEstimate)`, so every time-of-day comparison stays in the same local wall-clock frame.

**Design note:** Current Phase 2 still has one candidate (`bestEstimate` plus dynamic window). This task makes stale handling explicit and testable: the predictor returns `Overdue` only after `windowEnd + OVERDUE_GRACE_MINUTES`; it returns `Window` at the exact grace boundary; and it never creates a new "start now" candidate from stale anchors.

- [ ] **Step 2.1: Add stale-boundary tests**

Add these tests to `SleepWindowPredictorTest.kt`:

```kotlin
@Test
fun `returns Window at exact overdue grace boundary`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(150)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )

    val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)

    assertInstanceOf(
        SleepPredictionState.Window::class.java,
        result,
        "Exact windowEnd + grace boundary must still be a Window; stale starts only after grace",
    )
}

@Test
fun `stale anchor returns Overdue instead of shifting best estimate to now`() {
    val lastWakeMillis = baseNow.minus(Duration.ofHours(8)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )

    val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)

    assertInstanceOf(SleepPredictionState.Overdue::class.java, result)
}
```

- [ ] **Step 2.2: Run tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: the exact-boundary test may fail depending on the current `isAfter` boundary and fixture math; the stale-anchor test should pass if current overdue behavior is already correct.

- [ ] **Step 2.3: Extract an explicit stale helper**

In `SleepWindowPredictor.kt`, replace the inline overdue check with:

```kotlin
if (isStaleWindow(now, windowEnd)) {
    return SleepPredictionState.Overdue
}
```

Add this helper near `dynamicHalfWindowMillis`:

```kotlin
private fun isStaleWindow(now: Instant, windowEnd: Instant): Boolean {
    val staleAfter = windowEnd.plus(Duration.ofMinutes(SleepPredictionTuning.OVERDUE_GRACE_MINUTES))
    return now.isAfter(staleAfter)
}
```

- [ ] **Step 2.4: Run the focused test**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 2.5: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): harden stale sleep window handling [AKA-94]"
```

---

## Task 3: Implement pure circadian bias factor

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/CircadianBiasFactor.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasFactorTest.kt`

**Design note:** The factor is intentionally bounded. It never runs under `CUE_LED_MAX_AGE_WEEKS`; it ramps from zero at 6 weeks to full weight at 12 weeks; and its shift is capped by `CIRCADIAN_MAX_SHIFT_MINUTES`. For bedtime, it nudges toward the midpoint of `SleepAgePriors.getBedtimeWindow(ageInWeeks)`. For naps, it returns neutral in AKA-94 because the app does not yet have a provenance-qualified current-day wake anchor; using a synthetic 7 AM start would move windows for the wrong reason on early- or late-wake days. It does not read historical local-day features.

- [ ] **Step 3.1: Write failing factor tests**

Create `CircadianBiasFactorTest.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class CircadianBiasFactorTest {

    @Test
    fun `disabled below cue-led age`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS - 1,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 17 * 60,
            napCountToday = 0,
        )

        assertEquals(SleepPredictionFactor.Disabled, factor)
    }

    @Test
    fun `night sleep shifts toward bedtime midpoint and is capped`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )

        assertTrue(factor.adjustment > Duration.ZERO)
        assertTrue(factor.adjustment <= Duration.ofMinutes(SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
        assertTrue(factor.reason!!.contains("circadian", ignoreCase = true))
    }

    @Test
    fun `night sleep shift is negative when candidate is later than bedtime target`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 22 * 60,
            candidateMinuteOfDay = 23 * 60,
            napCountToday = 2,
        )

        assertTrue(factor.adjustment < Duration.ZERO)
    }

    @Test
    fun `near target returns neutral`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 19 * 60,
            candidateMinuteOfDay = 19 * 60 + 5,
            napCountToday = 2,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `ramp weight is smaller at eight weeks than twelve weeks`() {
        val eightWeeks = CircadianBiasFactor.adjustment(
            ageInWeeks = 8,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )
        val twelveWeeks = CircadianBiasFactor.adjustment(
            ageInWeeks = 12,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )

        assertTrue(eightWeeks.adjustment < twelveWeeks.adjustment)
    }

    @Test
    fun `nap circadian bias is neutral until a real current-day anchor exists`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NAP,
            currentMinuteOfDay = 10 * 60,
            candidateMinuteOfDay = 12 * 60,
            napCountToday = 0,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }
}
```

- [ ] **Step 3.2: Run tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.CircadianBiasFactorTest" -PfastTests
```

Expected: FAIL because `CircadianBiasFactor` does not exist.

- [ ] **Step 3.3: Create `CircadianBiasFactor.kt`**

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToLong

object CircadianBiasFactor {

    fun adjustment(
        ageInWeeks: Int,
        nextType: SleepType,
        currentMinuteOfDay: Int?,
        candidateMinuteOfDay: Int?,
        napCountToday: Int,
    ): SleepPredictionFactor {
        if (ageInWeeks < SleepPredictionTuning.CIRCADIAN_MIN_AGE_WEEKS) {
            return SleepPredictionFactor.Disabled
        }
        if (currentMinuteOfDay == null || candidateMinuteOfDay == null) return SleepPredictionFactor.Neutral
        if (nextType == SleepType.NAP) {
            val loggedNaps = napCountToday.coerceAtLeast(0)
            return SleepPredictionFactor(
                adjustment = Duration.ZERO,
                reason = null,
                enabled = loggedNaps >= 0,
            )
        }

        val targetMinute = targetMinuteOfDay(ageInWeeks, nextType) ?: return SleepPredictionFactor.Neutral
        val diffMinutes = shortestSignedMinuteDiff(from = candidateMinuteOfDay, to = targetMinute)
        if (abs(diffMinutes) <= SleepPredictionTuning.CIRCADIAN_TARGET_NEUTRALITY_MINUTES) {
            return SleepPredictionFactor.Neutral
        }

        val ramp = rampWeight(ageInWeeks)
        val capped = diffMinutes
            .coerceIn(
                -SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES.toInt(),
                SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES.toInt(),
            )
        val adjustmentMinutes = (capped * ramp).roundToLong()
        if (adjustmentMinutes == 0L) return SleepPredictionFactor.Neutral

        val typeLabel = if (nextType == SleepType.NIGHT_SLEEP) "bedtime" else "nap"
        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(adjustmentMinutes),
            reason = "Adjusted toward the expected $typeLabel circadian slot",
        )
    }

    private fun rampWeight(ageInWeeks: Int): Double {
        val minAge = SleepPredictionTuning.CIRCADIAN_MIN_AGE_WEEKS
        val fullAge = SleepPredictionTuning.CIRCADIAN_FULL_WEIGHT_AGE_WEEKS
        return ((ageInWeeks - minAge).toDouble() / (fullAge - minAge).toDouble()).coerceIn(0.0, 1.0)
    }

    private fun targetMinuteOfDay(ageInWeeks: Int, nextType: SleepType): Int? =
        when (nextType) {
            SleepType.NIGHT_SLEEP -> bedtimeMidpointMinute(ageInWeeks)
            SleepType.NAP -> null
        }

    private fun bedtimeMidpointMinute(ageInWeeks: Int): Int {
        val window = SleepAgePriors.getBedtimeWindow(ageInWeeks)
        return circularAverage(minuteOfDay(window.start), minuteOfDay(window.endInclusive))
    }

    private fun minuteOfDay(time: java.time.LocalTime): Int = time.hour * MINUTES_PER_HOUR + time.minute

    private fun shortestSignedMinuteDiff(from: Int, to: Int): Int {
        val raw = (to - from + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (raw > MINUTES_PER_DAY / 2) raw - MINUTES_PER_DAY else raw
    }

    private fun circularAverage(a: Int, b: Int): Int {
        val diff = shortestSignedMinuteDiff(from = a, to = b)
        return (a + diff / 2 + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1_440
}
```

- [ ] **Step 3.4: Run factor tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.CircadianBiasFactorTest" -PfastTests
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
git add app/src/main/java/com/babytracker/domain/sleep/eval/CircadianBiasFactor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasFactorTest.kt
git commit -m "feat(sleep): add circadian bias factor [AKA-94]"
```

---

## Task 4: Wire circadian bias into `SleepWindowPredictor`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

- [ ] **Step 4.1: Add predictor wiring tests**

Add to `SleepWindowPredictorTest.kt`:

```kotlin
@Test
fun `circadian factor shifts bedtime estimate toward bedtime slot`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(30)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(120).toMillis(),
        bedtimeWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
        bedtimeWakeP50Millis = Duration.ofMinutes(120).toMillis(),
    ).copy(lastSleepType = SleepType.NAP, napCountToday = 2)

    val result = SleepWindowPredictor.predict(
        features(
            quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS),
            metrics = metrics,
            currentMinuteOfDay = 18 * 60,
        ),
        ageInWeeks,
        baseNow,
        circadianFactorProvider = CircadianBiasFactor::adjustment,
    )

    val window = (result as SleepPredictionState.Window).window
    assertTrue(
        window.reasons.any { it.contains("circadian", ignoreCase = true) },
        "Circadian wiring must expose a reason when the factor changes the candidate",
    )
}
```

The cue-led age case is covered directly in `CircadianBiasFactorTest`; do not duplicate it in the predictor test because `PredictSleepWindowUseCase`, not the pure predictor, owns the `CueLed` precedence branch.

Add a stale-regression test using an injected positive factor:

```kotlin
@Test
fun `positive circadian adjustment cannot revive stale raw window`() {
    val lastWakeMillis = baseNow.minus(Duration.ofHours(8)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )

    val result = SleepWindowPredictor.predict(
        features(metrics = metrics, currentMinuteOfDay = 18 * 60),
        ageInWeeks,
        baseNow,
        circadianFactorProvider = { _, _, _, _, _ ->
            SleepPredictionFactor(Duration.ofMinutes(SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
        },
    )

    assertInstanceOf(
        SleepPredictionState.Overdue::class.java,
        result,
        "Raw stale windows must stay Overdue even when a factor would shift the adjusted estimate forward",
    )
}

@Test
fun `negative circadian adjustment cannot return a stale final window`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(150)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )

    val result = SleepWindowPredictor.predict(
        features(metrics = metrics, currentMinuteOfDay = 18 * 60),
        ageInWeeks,
        baseNow,
        circadianFactorProvider = { _, _, _, _, _ ->
            SleepPredictionFactor(Duration.ofMinutes(-SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
        },
    )

    assertInstanceOf(
        SleepPredictionState.Overdue::class.java,
        result,
        "Adjusted windows must be rechecked for staleness before returning Window",
    )
}
```

- [ ] **Step 4.2: Run tests and verify the circadian reason test fails**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: FAIL because predictor reasons do not yet include circadian output.

- [ ] **Step 4.3: Add injectable factor providers to `predict`**

Change the public function signature in `SleepWindowPredictor.kt` to keep defaults for existing call sites:

```kotlin
fun predict(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider = { _, _, _, _, _ -> SleepPredictionFactor.Neutral },
): SleepPredictionState {
    val quality = features.quality
    if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
        return SleepPredictionState.NeedMoreData(buildProgress(quality))
    }
    if (features.feedIntervals.any { it.endMillis == null }) {
        return SleepPredictionState.AfterActiveFeed
    }
    return buildWindow(features, ageInWeeks, now, circadianFactorProvider)
}
```

Update `buildWindow` to accept the circadian provider:

```kotlin
private fun buildWindow(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider,
): SleepPredictionState {
```

Update `SleepEvalHarness.kt` so the default predictor remains a 3-argument function after `SleepWindowPredictor.predict` gains defaulted provider parameters:

```kotlin
class SleepEvalHarness(
    private val zoneId: ZoneId,
    private val predictor: (SleepFeatures, Int, Instant) -> SleepPredictionState = { features, ageInWeeks, now ->
        SleepWindowPredictor.predict(features, ageInWeeks, now)
    },
) {
```

- [ ] **Step 4.4: Apply factor adjustment in `buildWindow`**

In `SleepWindowPredictor.kt`, after computing the initial `bestEstimate`, compute the candidate minute in the same local frame as `features.currentMinuteOfDay`:

```kotlin
val halfWindowDuration = Duration.ofMillis(dynamicHalfWindowMillis(metrics, nextType))
val rawWindowEnd = bestEstimate.plus(halfWindowDuration)
if (isStaleWindow(now, rawWindowEnd)) {
    return SleepPredictionState.Overdue
}

val candidateMinuteOfDay = candidateMinuteOfDay(
    currentMinuteOfDay = features.currentMinuteOfDay,
    offsetFromNow = Duration.between(now, bestEstimate),
)
val circadianFactor = circadianFactorProvider(
    ageInWeeks,
    nextType,
    features.currentMinuteOfDay,
    candidateMinuteOfDay,
    metrics.napCountToday,
)
val adjustedBestEstimate = bestEstimate
    .plus(circadianFactor.adjustment)
```

Then use `adjustedBestEstimate` for `windowStart`, `windowEnd`, and `SleepWindow.bestEstimate`:

```kotlin
val windowStart = adjustedBestEstimate.minus(halfWindowDuration)
val windowEnd = adjustedBestEstimate.plus(halfWindowDuration)
if (isStaleWindow(now, windowEnd)) {
    return SleepPredictionState.Overdue
}
```

Add factor reason text into the existing reason list:

```kotlin
val reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
    listOfNotNull(circadianFactor.reason)
```

Use `reasons = reasons` in the `SleepWindow`.

Add the helper:

```kotlin
private fun candidateMinuteOfDay(currentMinuteOfDay: Int?, offsetFromNow: Duration): Int? {
    if (currentMinuteOfDay == null) return null
    return Math.floorMod(currentMinuteOfDay + offsetFromNow.toMinutes().toInt(), MINUTES_PER_DAY)
}
```

- [ ] **Step 4.5: Run focused tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.CircadianBiasFactorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 4.6: Run full fast suite**

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
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepEvalHarness.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): apply circadian bias to sleep windows [AKA-94]"
```

---

## Task 5: Add fail-closed time-of-day similarity factor

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactor.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactorTest.kt`

**Design note:** This task deliberately does not shift predictions with `medianBedtimeMinuteOfDay` or nap cluster history yet. The approved spec says Phase 0 local-day-derived features are unqualified evidence and must not raise confidence or move windows until Phase 3 stores per-record timezone provenance. AKA-94's history factor therefore gets a concrete API plus tests proving it returns disabled when provenance is not qualified. Phase 3 will flip the gate and add the real historical adjustment in its own PR.

- [ ] **Step 5.1: Write failing tests**

Create `TimeOfDaySimilarityFactorTest.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class TimeOfDaySimilarityFactorTest {

    @Test
    fun `disabled when timezone provenance is unqualified`() {
        val factor = TimeOfDaySimilarityFactor.adjustment(
            metrics = metricsWithBedtimeHistory(),
            nextType = SleepType.NIGHT_SLEEP,
            candidateMinuteOfDay = 18 * 60,
            hasQualifiedTimezoneProvenance = false,
        )

        assertEquals(SleepPredictionFactor.Disabled, factor)
    }

    @Test
    fun `neutral when provenance is qualified but history is missing`() {
        val factor = TimeOfDaySimilarityFactor.adjustment(
            metrics = metricsWithBedtimeHistory().copy(medianBedtimeMinuteOfDay = null),
            nextType = SleepType.NIGHT_SLEEP,
            candidateMinuteOfDay = 18 * 60,
            hasQualifiedTimezoneProvenance = true,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    private fun metricsWithBedtimeHistory() = SleepMetrics(
        lastWakeMillis = 0,
        lastSleepType = SleepType.NAP,
        lastSleepDurationMillis = Duration.ofMinutes(60).toMillis(),
        completedWakeIntervals = List(10) { Duration.ofMinutes(90).toMillis() },
        medianWakeIntervalMillis = Duration.ofMinutes(90).toMillis(),
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        sleepLast24hMillis = Duration.ofHours(4).toMillis(),
        daySleepTodayMillis = Duration.ofHours(2).toMillis(),
        napCountToday = 2,
        medianBedtimeMinuteOfDay = 19 * 60,
        medianMorningWakeMinuteOfDay = 7 * 60,
    )
}
```

- [ ] **Step 5.2: Run tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.TimeOfDaySimilarityFactorTest" -PfastTests
```

Expected: FAIL because `TimeOfDaySimilarityFactor` does not exist.

- [ ] **Step 5.3: Create fail-closed factor**

Create `TimeOfDaySimilarityFactor.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepMetrics

object TimeOfDaySimilarityFactor {

    fun adjustment(
        metrics: SleepMetrics,
        nextType: SleepType,
        candidateMinuteOfDay: Int?,
        hasQualifiedTimezoneProvenance: Boolean,
    ): SleepPredictionFactor {
        if (!hasQualifiedTimezoneProvenance) return SleepPredictionFactor.Disabled

        val targetMinute = when (nextType) {
            SleepType.NIGHT_SLEEP -> metrics.medianBedtimeMinuteOfDay
            SleepType.NAP -> null
        } ?: return SleepPredictionFactor.Neutral

        // Phase 3 will replace this neutral return with a bounded local-time adjustment after
        // per-record timezone provenance exists. Keeping the parameters used here prevents a later
        // API churn while making the Phase 2 gate explicit and testable.
        return if (candidateMinuteOfDay == null || candidateMinuteOfDay == targetMinute) {
            SleepPredictionFactor.Neutral
        } else {
            SleepPredictionFactor.Neutral
        }
    }
}
```

- [ ] **Step 5.4: Run tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.TimeOfDaySimilarityFactorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 5.5: Verify the factor is wired as disabled in `SleepWindowPredictor`**

Extend the `SleepWindowPredictor.predict` signature from Task 4 with a time-of-day provider default:

```kotlin
fun predict(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider = CircadianBiasFactor::adjustment,
    timeOfDayFactorProvider: TimeOfDayFactorProvider = TimeOfDaySimilarityFactor::adjustment,
): SleepPredictionState {
```

Pass the provider to `buildWindow`:

```kotlin
return buildWindow(features, ageInWeeks, now, circadianFactorProvider, timeOfDayFactorProvider)
```

Update the private `buildWindow` signature:

```kotlin
private fun buildWindow(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider,
    timeOfDayFactorProvider: TimeOfDayFactorProvider,
): SleepPredictionState {
```

After the `circadianFactor` block from Task 4, add the provider-backed call:

```kotlin
val timeOfDayFactor = timeOfDayFactorProvider(metrics, nextType, candidateMinuteOfDay, false)
```

Update `adjustedBestEstimate` and reason assembly so the disabled factor has no behavioral effect now but becomes active without API churn in Phase 3:

```kotlin
val adjustedBestEstimate = bestEstimate
    .plus(circadianFactor.adjustment)
    .plus(timeOfDayFactor.adjustment)

val reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
    listOfNotNull(circadianFactor.reason, timeOfDayFactor.reason)
```

- [ ] **Step 5.6: Run predictor and factor tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.TimeOfDaySimilarityFactorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 5.7: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactor.kt
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/TimeOfDaySimilarityFactorTest.kt
git commit -m "feat(sleep): gate time-of-day history factor on provenance [AKA-94]"
```

---

## Task 6: Add evaluation-gate comparison for circadian bias

**Files:**
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasEvalComparisonTest.kt`

**Design note:** The comparison must fail closed across two cohorts. The benefit fixture proves a bedtime-drift case can improve. The adverse fixture represents a baby whose personalized bedtime history is already correct but later than the age-prior midpoint; circadian must not worsen MAE, in-window percentage, or missed-window rate there. If either fixture lacks at least `EVAL_MIN_ANCHORS` per segment, the test fails with a fixture-size message rather than passing. The history factor is not evaluated here because it is disabled until Phase 3 provenance exists.

- [ ] **Step 6.1: Write comparison test**

Create `CircadianBiasEvalComparisonTest.kt`:

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

class CircadianBiasEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 2, 15))

    @Test
    fun `circadian bias clears section 7-1 acceptance criteria on bedtime-skew fixture`() {
        val records = bedtimeDriftRecords(historyDays = 35, evaluatedDays = 35)
        val baselineHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = { _, _, _, _, _ -> SleepPredictionFactor.Neutral },
            )
        }
        val newHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = CircadianBiasFactor::adjustment,
            )
        }
        val baselineAnchors = baselineHarness.buildAnchors(records, emptyList(), baby).associateBy { it.wakeInstant }
        val newAnchors = newHarness.buildAnchors(records, emptyList(), baby)
        val bedtimeAnchors = newAnchors.filter { it.segmentKey.sleepType == SleepType.NIGHT_SLEEP }

        assertTrue(
            bedtimeAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} bedtime anchors; got ${bedtimeAnchors.size}",
        )

        val missingBaseline = bedtimeAnchors.filter { baselineAnchors[it.wakeInstant] == null }
        assertTrue(
            missingBaseline.isEmpty(),
            "Baseline harness must produce every new-harness wake anchor; missing ${missingBaseline.size}",
        )

        val unscoredNew = bedtimeAnchors.filter { it.score == null }
        val unscoredBaseline = bedtimeAnchors.filter { baselineAnchors.getValue(it.wakeInstant).score == null }
        assertTrue(
            unscoredNew.isEmpty(),
            "New predictor returned non-Window for ${unscoredNew.size} bedtime anchors; do not drop failed anchors from the gate",
        )
        assertTrue(
            unscoredBaseline.isEmpty(),
            "Baseline predictor returned non-Window for ${unscoredBaseline.size} bedtime anchors; fixture is not valid for factor comparison",
        )

        val paired = bedtimeAnchors.map { newAnchor ->
            newAnchor.score!! to baselineAnchors.getValue(newAnchor.wakeInstant).score!!
        }

        assertTrue(
            paired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Need at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} paired bedtime scores; got ${paired.size}",
        )

        val improvements = paired.map { (newScore, baselineScore) ->
            baselineScore.errorMillis / 60_000.0 - newScore.errorMillis / 60_000.0
        }
        val maeGain = improvements.average()
        val newInWindowPct = paired.count { (newScore, _) -> newScore.inWindow }.toDouble() / paired.size
        val baselineInWindowPct = paired.count { (_, baselineScore) -> baselineScore.inWindow }.toDouble() / paired.size
        val newMissedRate = paired.count { (newScore, _) -> newScore.missedWindow }.toDouble() / paired.size
        val baselineMissedRate = paired.count { (_, baselineScore) -> baselineScore.missedWindow }.toDouble() / paired.size

        assertTrue(
            maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN}",
        )
        assertTrue(
            newInWindowPct > baselineInWindowPct,
            "In-window percent must improve; baseline=${"%.2f".format(baselineInWindowPct)}, new=${"%.2f".format(newInWindowPct)}",
        )
        assertTrue(
            bootstrapCiLowerBound(improvements) > 0.0,
            "Bootstrap CI lower bound for MAE improvement must be positive",
        )
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissedRate)}, new=${"%.2f".format(newMissedRate)}",
        )
    }

    @Test
    fun `circadian bias does not regress stable personalized late-bedtime fixture`() {
        val records = stableLateBedtimeRecords(days = 35)
        val paired = pairedBedtimeScores(records)

        val newMaeMin = paired.map { (newScore, _) -> newScore.errorMillis / 60_000.0 }.average()
        val baselineMaeMin = paired.map { (_, baselineScore) -> baselineScore.errorMillis / 60_000.0 }.average()
        val newInWindowPct = paired.count { (newScore, _) -> newScore.inWindow }.toDouble() / paired.size
        val baselineInWindowPct = paired.count { (_, baselineScore) -> baselineScore.inWindow }.toDouble() / paired.size
        val newMissedRate = paired.count { (newScore, _) -> newScore.missedWindow }.toDouble() / paired.size
        val baselineMissedRate = paired.count { (_, baselineScore) -> baselineScore.missedWindow }.toDouble() / paired.size

        assertTrue(
            newMaeMin <= baselineMaeMin + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Adverse cohort MAE must not worsen; baseline=${"%.1f".format(baselineMaeMin)}, new=${"%.1f".format(newMaeMin)}",
        )
        assertTrue(
            newInWindowPct >= baselineInWindowPct,
            "Adverse cohort in-window percent must not worsen; baseline=${"%.2f".format(baselineInWindowPct)}, new=${"%.2f".format(newInWindowPct)}",
        )
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Adverse cohort missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissedRate)}, new=${"%.2f".format(newMissedRate)}",
        )
    }

    private fun bedtimeDriftRecords(historyDays: Int, evaluatedDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = historyDays + evaluatedDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong())).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        repeat(totalDays) { dayIndex ->
            val isEvaluatedDriftDay = dayIndex >= historyDays
            val nightStart = dayStart.plus(Duration.ofHours(19)).plus(Duration.ofMinutes(30))
            val nightEnd = nightStart.plus(Duration.ofHours(8))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(10))
            val nap1End = nap1Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(15)).plus(Duration.ofMinutes(45))
            val nap2End = nap2Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            // History days have a 130-minute pre-bedtime wake interval (nap ends 17:20, bed 19:30).
            // Evaluated drift days have a 150-minute interval (nap ends 17:00, bed 19:30).
            // The no-circadian baseline initially predicts 19:10 from the stale 130-minute median,
            // just outside a 15-minute half-window. The +20 minute bedtime circadian cap moves it
            // to 19:30. Once the rolling lookback learns 150 minutes, the circadian factor becomes
            // neutral because the candidate already lands on the bedtime midpoint.
            if (isEvaluatedDriftDay) {
                records[records.lastIndex] = records.last().copy(
                    startTime = dayStart.plus(Duration.ofHours(15)).plus(Duration.ofMinutes(45)),
                    endTime = dayStart.plus(Duration.ofHours(17)),
                )
            } else {
                records[records.lastIndex] = records.last().copy(
                    startTime = dayStart.plus(Duration.ofHours(16)).plus(Duration.ofMinutes(5)),
                    endTime = dayStart.plus(Duration.ofHours(17)).plus(Duration.ofMinutes(20)),
                )
            }

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    private fun stableLateBedtimeRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong())).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(21))
            val nightEnd = nightStart.plus(Duration.ofHours(8))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(11))
            val nap1End = nap1Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(17)).plus(Duration.ofMinutes(15))
            val nap2End = nap2Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    private fun pairedBedtimeScores(records: List<SleepRecord>): List<Pair<AnchorScore, AnchorScore>> {
        val baselineHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = { _, _, _, _, _ -> SleepPredictionFactor.Neutral },
            )
        }
        val circadianHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = CircadianBiasFactor::adjustment,
            )
        }
        val baselineAnchors = baselineHarness.buildAnchors(records, emptyList(), baby).associateBy { it.wakeInstant }
        val circadianAnchors = circadianHarness.buildAnchors(records, emptyList(), baby)
            .filter { it.segmentKey.sleepType == SleepType.NIGHT_SLEEP }

        assertTrue(
            circadianAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} bedtime anchors; got ${circadianAnchors.size}",
        )
        assertTrue(
            circadianAnchors.all { baselineAnchors[it.wakeInstant] != null },
            "Baseline harness must produce every circadian-harness wake anchor",
        )
        assertTrue(
            circadianAnchors.none { it.score == null },
            "Circadian predictor must not return non-Window states for bedtime anchors",
        )
        assertTrue(
            circadianAnchors.none { baselineAnchors.getValue(it.wakeInstant).score == null },
            "Baseline predictor must not return non-Window states for bedtime anchors",
        )

        return circadianAnchors.map { anchor ->
            anchor.score!! to baselineAnchors.getValue(anchor.wakeInstant).score!!
        }
    }

    private fun bootstrapCiLowerBound(improvements: List<Double>, samples: Int = 1000): Double {
        val rng = Random(94)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
```

- [ ] **Step 6.2: Run comparison test**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.CircadianBiasEvalComparisonTest" -PfastTests
```

Expected: PASS only if the factor clears both the benefit cohort and the adverse cohort. If the benefit cohort fails, or if the adverse cohort shows any MAE, in-window, or missed-window regression, keep `circadianFactorProvider` neutral by default and do not promote the factor in this PR. Attach the failing gate output to the Linear issue as the reason the factor remains disabled.

- [ ] **Step 6.3: Run full fast suite**

Run:

```powershell
.\gradlew :app:testDebugUnitTest -PfastTests
```

Expected: PASS.

- [ ] **Step 6.4: Run full unit suite without `-PfastTests` before pushing**

Run:

```powershell
.\gradlew :app:testDebugUnitTest
```

Expected: PASS, including architecture tests.

- [ ] **Step 6.5: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/test/java/com/babytracker/domain/sleep/eval/CircadianBiasEvalComparisonTest.kt
git commit -m "test(sleep): add circadian bias eval gate [AKA-94]"
```

---

## Self-Review: Spec Coverage Check

| AKA-94 / design-spec requirement | Task that implements it |
|----------------------------------|--------------------------|
| Age-ramped circadian bias, near zero under 6 weeks and full by around 3 months | Task 1 constants, Task 3 `rampWeight`, Task 3 tests |
| Circadian factor must not fire for cue-led babies | Task 3 disabled-under-age test |
| Adjust window toward expected circadian bedtime slot | Task 3 factor, Task 4 predictor wiring |
| Nap circadian slot avoids synthetic fixed-day anchors | Task 3 neutral NAP test; nap adjustment deferred until a real current-day anchor exists |
| Candidate handling: stale/overdue emits `Overdue`, not repeated "start now" windows | Task 2 helper and boundary tests |
| Suppress spam when overdue | Existing notification coordinator already schedules only `Window`; Task 2 keeps stale anchors as `Overdue` |
| Time-of-day similarity uses local-day history only when safe | Task 5 provenance gate disables it in Phase 2 |
| Phase 0 timezone caveat is respected | Task 5 fail-closed tests; no local-day-derived adjustment ships before Phase 3 provenance |
| Each factor is evaluated independently against current baseline | Task 6 evaluates circadian only; history factor is blocked until provenance exists |
| At least `EVAL_MIN_ANCHORS = 20` anchors per segment | Task 6 fixture-size assertion |
| MAE improvement >= 5 minutes and positive in-window gain | Task 6 assertions |
| Bootstrap CI lower bound positive | Task 6 `bootstrapCiLowerBound` assertion |
| Missed-window rate does not worsen | Task 6 assertion |
| Insufficient data blocks rather than passes | Existing harness behavior; Task 6 fails fixture if anchor count is insufficient |
| Attach harness comparison output before merging | Task 6 command output is the artifact to include in the PR comment |
| Conventional commit scope is `feat(sleep)` | All implementation commits use `feat(sleep)` or `test(sleep)` with `[AKA-94]` |

## Execution Notes

- This plan intentionally treats baby-history time-of-day similarity as a gated API, not a live adjustment, because AKA-94 itself says it requires Phase 3 timezone provenance. Shipping a local-day historical shift before that would violate the approved design spec.
- After Kotlin changes, always run `.\gradlew ktlintFormat` and `.\gradlew detekt` before committing.
- Full validation before PR: `.\gradlew :app:testDebugUnitTest`, `.\gradlew ktlintFormat`, and `.\gradlew detekt`.
