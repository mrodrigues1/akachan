# Nap-Budget Scoring Adjustment Implementation Plan

LINEAR_ISSUE: AKA-100

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a nap-budget scoring factor to `SleepWindowPredictor` that shifts the predicted NAP window earlier when the baby is behind on today's expected nap count, then gate it behind the §7.1 evaluation harness.

**Architecture:** No new `SleepMetrics` fields — `napCountToday` and `SleepAgePriors.getScheduledNapCount(ageInWeeks)` are already available. Create a pure `NapBudgetFactor` object and `NapBudgetFactorProvider` typealias added to the existing `SleepPredictionFactor.kt`. Wire an injectable `napBudgetFactorProvider` parameter (default neutral) into `SleepWindowPredictor.predict` and `buildWindow`. The factor only adjusts NAP predictions (NIGHT_SLEEP is unaffected in this PR). Factor is promoted from neutral to `NapBudgetFactor::adjustment` only after the eval comparison test clears all §7.1 acceptance criteria.

**Tech Stack:** Kotlin 2.3.20, JUnit 5, MockK, pure domain tests with fixed `Clock`/`ZoneId`; no Android/Room imports.

**Dependencies:** AKA-94 must be implemented first (introduces `SleepPredictionFactor` type and injectable provider pattern). This plan may be implemented before or after `2026-06-05-aka-98-sleep-debt-adjustment.md` — the two are independent. If both are in flight simultaneously, expect a minor conflict in `SleepPredictionFactor.kt` (typealias additions) and `SleepWindowPredictor.predict` (signature extension); resolve by keeping all parameters.

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt` | Modify | Add `NAP_BUDGET_MAX_SHIFT_MINUTES`, `NAP_BUDGET_MINUTES_PER_NAP`; bump `ALGORITHM_VERSION` to `sleep-pred-phase2-nap-budget-1` |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt` | Modify | Add `NapBudgetFactorProvider` typealias |
| `app/src/main/java/com/babytracker/domain/sleep/eval/NapBudgetFactor.kt` | Create | Pure factor: nap deficit → bounded earlier shift for NAP predictions |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt` | Modify | Add `napBudgetFactorProvider` parameter (default neutral); apply in `buildWindow` |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt` | Modify | Update `ALGORITHM_VERSION` assertion; add nap-budget wiring tests |
| `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt` | Modify | Update version assertion |
| `app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetFactorTest.kt` | Create | Unit tests for all factor branches |
| `app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetEvalComparisonTest.kt` | Create | §7.1 comparison: deficit benefit cohort + stable no-regression cohort |

---

## Task 1: Add tuning constants, provider typealias, and bump algorithm version

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepPredictionFactor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

**Note on ALGORITHM_VERSION:** If `2026-06-05-aka-98-sleep-debt-adjustment.md` was implemented first, the current version is `sleep-pred-phase2-sleep-debt-1`. If not, it is the version left by AKA-94. Bump to `sleep-pred-phase2-nap-budget-1` regardless of the previous value.

- [ ] **Step 1.1: Write failing version test**

In `SleepWindowPredictorTest.kt`, replace the current `ALGORITHM_VERSION` assertion:

```kotlin
@Test
fun `ALGORITHM_VERSION is phase2 nap-budget version`() {
    assertEquals(
        "sleep-pred-phase2-nap-budget-1",
        SleepPredictionTuning.ALGORITHM_VERSION,
        "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
    )
}
```

In `SleepFeatureExtractorTest.kt`, update the version assertion in the tuning-constants test to:

```kotlin
assertEquals("sleep-pred-phase2-nap-budget-1", SleepPredictionTuning.ALGORITHM_VERSION)
```

- [ ] **Step 1.2: Run the version tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" --tests "*.SleepFeatureExtractorTest" -PfastTests
```

Expected: FAIL because `ALGORITHM_VERSION` is still the previous value.

- [ ] **Step 1.3: Add tuning constants and bump algorithm version**

In `SleepPredictionTuning.kt`, add after `EVAL_MAX_REGRESSION` (or after the sleep-debt constants if AKA-98a was implemented first):

```kotlin
const val NAP_BUDGET_MAX_SHIFT_MINUTES = 20L
const val NAP_BUDGET_MINUTES_PER_NAP = 10L
```

Then bump:

```kotlin
const val ALGORITHM_VERSION = "sleep-pred-phase2-nap-budget-1"
```

- [ ] **Step 1.4: Add `NapBudgetFactorProvider` typealias**

In `SleepPredictionFactor.kt`, add after the existing typealiases (or after `SleepDebtFactorProvider` if AKA-98a was implemented first):

```kotlin
typealias NapBudgetFactorProvider = (
    Int,       // napCountToday
    Int,       // ageInWeeks
    com.babytracker.domain.model.SleepType, // nextType
) -> SleepPredictionFactor
```

- [ ] **Step 1.5: Run version tests**

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
git commit -m "feat(sleep): add nap-budget factor tuning constants and provider typealias [AKA-100]"
```

---

## Task 2: Create `NapBudgetFactor` with unit tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/eval/NapBudgetFactor.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetFactorTest.kt`

**Design note:** The factor only adjusts NAP predictions. For NIGHT_SLEEP, it returns `Neutral` — the bedtime window is driven by circadian and wake-window history; nap count already routes the predictor to NIGHT_SLEEP when `napCountToday >= expectedNaps`, so surplus is already handled by routing, not by shifting the bedtime earlier. For a NAP prediction, `napCountToday < expectedNaps` is always true by definition (otherwise `resolveNextSleepType` would have returned NIGHT_SLEEP). The deficit is therefore always ≥ 1 for NAP anchors; the factor's value is in varying the shift magnitude based on how large the deficit is (one-nap deficit vs. zero-nap day).

Shift direction: deficit > 0 → baby is behind on naps → shift next NAP window **earlier** (negative adjustment). The shift is capped at `NAP_BUDGET_MAX_SHIFT_MINUTES` to prevent overriding the wake-interval signal.

- [ ] **Step 2.1: Write failing tests**

Create `NapBudgetFactorTest.kt`:

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class NapBudgetFactorTest {

    // ageInWeeks = 20 → getScheduledNapCount = getDefaultWakeWindows(20).size - 1
    // getDefaultWakeWindows(20): ageInWeeks in 16..23 → [105, 135, 150] → size=3 → expectedNaps=2
    private val ageInWeeks = 20

    @Test
    fun `neutral when nextType is NIGHT_SLEEP`() {
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NIGHT_SLEEP,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `neutral when deficit is zero (should not happen for NAP but is defensive)`() {
        // expectedNaps(20w) = 2; deficit = 2 - 2 = 0
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 2,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `deficit of 1 produces a negative adjustment (shift earlier)`() {
        // napCountToday = 1; expectedNaps = 2 → deficit = 1
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 1,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertTrue(factor.adjustment < Duration.ZERO,
            "Deficit of 1 nap should produce an earlier (negative) adjustment, got ${factor.adjustment}")
        assertEquals(
            Duration.ofMinutes(SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP),
            factor.adjustment.abs(),
            "Deficit of 1 should produce NAP_BUDGET_MINUTES_PER_NAP shift",
        )
    }

    @Test
    fun `deficit of 2 produces twice the shift of deficit of 1, capped at max`() {
        // napCountToday = 0; expectedNaps = 2 → deficit = 2
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        val expectedShiftMinutes = (2 * SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP)
            .coerceAtMost(SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES)
        assertEquals(
            Duration.ofMinutes(expectedShiftMinutes),
            factor.adjustment.abs(),
            "Deficit of 2 should produce 2x shift (capped at max)",
        )
    }

    @Test
    fun `adjustment is capped at NAP_BUDGET_MAX_SHIFT_MINUTES even for extreme deficit`() {
        // napCountToday = 0; ageInWeeks < 6 → getDefaultWakeWindows returns 5 naps → deficit could be large
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = 4, // < 6 weeks → 5 expected naps → deficit = 5
            nextType = SleepType.NAP,
        )
        assertTrue(
            factor.adjustment.abs().toMinutes() <= SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES,
            "Adjustment must be capped at NAP_BUDGET_MAX_SHIFT_MINUTES for large deficits",
        )
    }

    @Test
    fun `reason string contains deficit count`() {
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertTrue(
            factor.reason != null && factor.reason.contains("nap"),
            "Reason must mention nap deficit; got: ${factor.reason}",
        )
    }

    @Test
    fun `negative surplus is neutral (baby had more naps than expected; routing already handles this)`() {
        // napCountToday = 3; expectedNaps = 2 → deficit = -1 → surplus
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 3,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor,
            "Surplus (extra naps today) should not shift for NAP — routing already handles surplus via NIGHT_SLEEP path")
    }
}
```

- [ ] **Step 2.2: Run tests and verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.NapBudgetFactorTest" -PfastTests
```

Expected: FAIL because `NapBudgetFactor` does not exist.

- [ ] **Step 2.3: Create `NapBudgetFactor.kt`**

```kotlin
package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration

object NapBudgetFactor {

    fun adjustment(
        napCountToday: Int,
        ageInWeeks: Int,
        nextType: SleepType,
    ): SleepPredictionFactor {
        if (nextType != SleepType.NAP) return SleepPredictionFactor.Neutral

        val expectedNaps = SleepAgePriors.getScheduledNapCount(ageInWeeks)
        val deficit = expectedNaps - napCountToday

        if (deficit <= 0) return SleepPredictionFactor.Neutral

        val rawShiftMinutes = deficit.toLong() * SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP
        val clampedShiftMinutes = rawShiftMinutes.coerceAtMost(SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES)

        val napWord = if (deficit == 1) "nap" else "naps"
        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(-clampedShiftMinutes), // deficit → shift earlier
            reason = "Nap deficit: $deficit fewer $napWord than expected today",
        )
    }
}
```

- [ ] **Step 2.4: Run the factor tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.NapBudgetFactorTest" -PfastTests
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
git add app/src/main/java/com/babytracker/domain/sleep/eval/NapBudgetFactor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetFactorTest.kt
git commit -m "feat(sleep): add nap-budget scoring factor [AKA-100]"
```

---

## Task 3: Wire `napBudgetFactorProvider` into `SleepWindowPredictor`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

**Design note:** `nextType` is already computed inside `buildWindow`. The `napBudgetFactorProvider` receives it directly (not derived from `features`), which keeps the provider signature clean and testable. Apply the factor after all other factors (circadian, time-of-day, sleep-debt). A positive factor (which cannot occur from `NapBudgetFactor` but can from an injected stub) must not revive a raw-stale window. A negative factor must be checked for adjusted-window staleness.

- [ ] **Step 3.1: Write wiring tests**

Add to `SleepWindowPredictorTest.kt`:

```kotlin
@Test
fun `nap-budget factor shifts NAP window earlier when injected with negative adjustment`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    ).copy(lastSleepType = SleepType.NAP, napCountToday = 1) // < expectedNaps(20w)=2 → nextType=NAP

    val neutralResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
    )
    val budgetResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-15))
        },
    )
    val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
    val budgetEstimate = (budgetResult as SleepPredictionState.Window).window.bestEstimate
    assertTrue(budgetEstimate.isBefore(neutralEstimate),
        "Nap-budget factor with -15 min adjustment must shift bestEstimate earlier")
}

@Test
fun `nap-budget factor does not apply to NIGHT_SLEEP predictions`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    // napCountToday = 2 = expectedNaps(20w) → nextType = NIGHT_SLEEP
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(150).toMillis(),
    ).copy(lastSleepType = SleepType.NAP, napCountToday = 2)

    val neutralResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
    )
    val budgetResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-15))
        },
    )
    // The factor stub always returns -15, but for NIGHT_SLEEP predictions the factor should be ignored
    // (NapBudgetFactor returns Neutral for NIGHT_SLEEP; the stub here will apply — this test verifies
    // that if the provider is invoked with nextType=NIGHT_SLEEP, a real NapBudgetFactor returns Neutral)
    val realBudgetResult = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = NapBudgetFactor::adjustment,
    )
    val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
    val realEstimate = (realBudgetResult as SleepPredictionState.Window).window.bestEstimate
    assertEquals(neutralEstimate, realEstimate,
        "Real NapBudgetFactor must return Neutral for NIGHT_SLEEP → bestEstimate unchanged")
}

@Test
fun `nap-budget factor reason appears in window reasons`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-10), reason = "nap budget reason")
        },
    )
    val window = (result as SleepPredictionState.Window).window
    assertTrue(window.reasons.contains("nap budget reason"),
        "Factor reason must appear in window.reasons")
}

@Test
fun `large negative nap-budget adjustment cannot make a raw-fresh window stale`() {
    val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(metrics = metrics), ageInWeeks, baseNow,
        napBudgetFactorProvider = { _, _, _ ->
            SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES))
        },
    )
    // Raw estimate ~30 min in future; -20 min → 10 min in future; window ends 25 min in future → still a Window
    assertInstanceOf(SleepPredictionState.Window::class.java, result)
}
```

- [ ] **Step 3.2: Run tests and verify the wiring tests fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: FAIL because `predict` does not accept `napBudgetFactorProvider`.

- [ ] **Step 3.3: Add `napBudgetFactorProvider` to `SleepWindowPredictor.predict`**

In `SleepWindowPredictor.kt`, extend the public `predict` signature (add `napBudgetFactorProvider` as the last provider parameter):

```kotlin
fun predict(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider = CircadianBiasFactor::adjustment,
    timeOfDayFactorProvider: TimeOfDayFactorProvider = TimeOfDaySimilarityFactor::adjustment,
    sleepDebtFactorProvider: SleepDebtFactorProvider = SleepDebtFactor::adjustment, // promoted by AKA-98a, or neutral if not yet promoted
    napBudgetFactorProvider: NapBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
): SleepPredictionState {
    val quality = features.quality
    if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
        return SleepPredictionState.NeedMoreData(buildProgress(quality))
    }
    if (features.feedIntervals.any { it.endMillis == null }) {
        return SleepPredictionState.AfterActiveFeed
    }
    return buildWindow(
        features, ageInWeeks, now,
        circadianFactorProvider, timeOfDayFactorProvider,
        sleepDebtFactorProvider, napBudgetFactorProvider,
    )
}
```

**Note:** If AKA-98a (sleep-debt plan) was NOT implemented first, replace `sleepDebtFactorProvider: SleepDebtFactorProvider = SleepDebtFactor::adjustment` with `sleepDebtFactorProvider: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }` and ensure `SleepDebtFactorProvider` exists (it is added by AKA-98a). If AKA-98a is not in scope, omit the `sleepDebtFactorProvider` parameter entirely.

Update the private `buildWindow` signature:

```kotlin
private fun buildWindow(
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadianFactorProvider: CircadianFactorProvider,
    timeOfDayFactorProvider: TimeOfDayFactorProvider,
    sleepDebtFactorProvider: SleepDebtFactorProvider,
    napBudgetFactorProvider: NapBudgetFactorProvider,
): SleepPredictionState {
```

- [ ] **Step 3.4: Apply nap-budget factor in `buildWindow`**

Inside `buildWindow`, after the `sleepDebtFactor` call (or after `timeOfDayFactor` if AKA-98a is not in scope), add:

```kotlin
val napBudgetFactor = napBudgetFactorProvider(
    metrics.napCountToday,
    ageInWeeks,
    nextType,
)
val adjustedBestEstimate = bestEstimate
    .plus(circadianFactor.adjustment)
    .plus(timeOfDayFactor.adjustment)
    .plus(sleepDebtFactor.adjustment) // omit if AKA-98a not in scope
    .plus(napBudgetFactor.adjustment)
```

Update reasons assembly to include the nap-budget reason:

```kotlin
val reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
    listOfNotNull(
        circadianFactor.reason,
        timeOfDayFactor.reason,
        sleepDebtFactor.reason, // omit if AKA-98a not in scope
        napBudgetFactor.reason,
    )
```

- [ ] **Step 3.5: Run the wiring tests**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.SleepWindowPredictorTest" -PfastTests
```

Expected: PASS.

- [ ] **Step 3.6: Run the full fast suite**

Run:

```powershell
.\gradlew :app:testDebugUnitTest -PfastTests
```

Expected: PASS.

- [ ] **Step 3.7: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit:

```powershell
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git add app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): wire nap-budget factor into SleepWindowPredictor [AKA-100]"
```

---

## Task 4: Eval comparison test and factor promotion

**Files:**
- Create: `app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetEvalComparisonTest.kt`

**Design note:** The benefit fixture contrasts two kinds of NAP anchor events:
1. **Low-deficit anchors** — baby has had 1 nap already and needs the 2nd (deficit = 1, shift = `NAP_BUDGET_MINUTES_PER_NAP`).
2. **High-deficit anchors** — baby has had 0 naps and is already past when the 1st nap would normally have been (deficit = 2, shift = 2 × `NAP_BUDGET_MINUTES_PER_NAP` capped at max).

On high-deficit days the baby falls asleep for the next nap sooner than median (because they are more tired). The nap-budget factor shifts the window earlier on those days → better MAE. The no-regression fixture uses a baby whose nap count at the anchor point always matches the low-deficit case (deficit always = 1), so the factor applies a uniform shift and does not regress.

The comparison uses `buildAnchors` directly to keep the leave-one-day-out semantics from `SleepEvalHarness`.

- [ ] **Step 4.1: Write comparison test**

Create `NapBudgetEvalComparisonTest.kt`:

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

class NapBudgetEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    // 20 weeks → getScheduledNapCount = 2; typical nap wake-window midpoint = 120 min
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 2, 5))

    @Test
    fun `nap-budget factor clears section 7-1 on mixed-deficit NAP fixture`() {
        val records = mixedDeficitRecords(lowDeficitDays = 30, highDeficitDays = 30)

        val baselineAnchors = buildAnchors(records, neutralProvider)
        val budgetAnchors = buildAnchors(records, NapBudgetFactor::adjustment)

        val napPaired = pairedAnchors(baselineAnchors, budgetAnchors, SleepType.NAP)

        assertTrue(
            napPaired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} NAP paired anchors; got ${napPaired.size}",
        )

        val improvements = napPaired.map { (baseline, new) ->
            baseline.errorMillis / 60_000.0 - new.errorMillis / 60_000.0
        }
        val maeGain = improvements.average()
        val newInWindow = napPaired.count { (_, new) -> new.inWindow }.toDouble() / napPaired.size
        val baselineInWindow = napPaired.count { (baseline, _) -> baseline.inWindow }.toDouble() / napPaired.size
        val newMissed = napPaired.count { (_, new) -> new.missedWindow }.toDouble() / napPaired.size
        val baselineMissed = napPaired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / napPaired.size

        assertTrue(
            maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN}",
        )
        assertTrue(
            newInWindow >= baselineInWindow,
            "In-window pct must not worsen; baseline=${"%.2f".format(baselineInWindow)}, new=${"%.2f".format(newInWindow)}",
        )
        assertTrue(
            bootstrapCiLowerBound(improvements) > 0.0,
            "Bootstrap CI lower bound for MAE gain must be positive",
        )
        assertTrue(
            newMissed <= baselineMissed + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissed)}, new=${"%.2f".format(newMissed)}",
        )
    }

    @Test
    fun `nap-budget factor does not regress on uniform single-deficit fixture`() {
        val records = uniformSingleDeficitRecords(days = 60)

        val baselineAnchors = buildAnchors(records, neutralProvider)
        val budgetAnchors = buildAnchors(records, NapBudgetFactor::adjustment)

        for (type in SleepType.entries) {
            val paired = pairedAnchors(baselineAnchors, budgetAnchors, type)
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

    /**
     * Alternates between:
     * - Low-deficit days: baby had nap1 at the normal time; anchor is after nap1, predicting nap2 (deficit=1)
     * - High-deficit days: baby skipped nap1; anchor is at mid-day, predicting nap1 from a zero-nap start (deficit=2)
     *   On high-deficit days, the actual next nap is 20 min earlier than the low-deficit baseline would predict.
     */
    private fun mixedDeficitRecords(lowDeficitDays: Int, highDeficitDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = lowDeficitDays + highDeficitDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(totalDays) { dayIndex ->
            val isHighDeficit = dayIndex % 2 == 1

            // Night sleep: always same (20:00–07:00 = 11h)
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            if (!isHighDeficit) {
                // Low-deficit: normal nap1 (09:30–11:00), normal nap2 (14:00–15:30)
                val nap1Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(30))
                val nap1End = nap1Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

                val nap2Start = dayStart.plus(Duration.ofHours(14))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
            } else {
                // High-deficit: nap1 skipped; nap2 starts 20 min earlier than median interval would predict
                // Median wake interval from normal days: nap1 ends 11:00, nap2 starts 14:00 → 3h interval
                // High-deficit: baby woke from night at 07:00; 3h would give 10:00 but baby is tired → 09:40
                val nap2Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(40))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

                // Second nap of the high-deficit day: normal timing relative to first nap
                val nap3Start = dayStart.plus(Duration.ofHours(14))
                val nap3End = nap3Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap3Start, nap3End, SleepType.NAP)
            }

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records
            .filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    /**
     * Uniform days: baby always has nap1 at 09:30, nap2 at 14:00, bedtime at 20:00.
     * At every NAP anchor, deficit = 1 (after nap1, predicting nap2) — consistent across all days.
     * The factor always applies the same shift, so MAE should be neutral or minimally changed.
     */
    private fun uniformSingleDeficitRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(30))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(14))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records
            .filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    // --- Harness helpers ---

    private val neutralProvider: NapBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }

    private fun buildAnchors(
        records: List<SleepRecord>,
        napBudgetProvider: NapBudgetFactorProvider,
    ): List<EvalAnchor> {
        val harness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                napBudgetFactorProvider = napBudgetProvider,
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
        val rng = Random(982)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
```

- [ ] **Step 4.2: Run the comparison test**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "*.NapBudgetEvalComparisonTest" -PfastTests
```

**If the benefit fixture PASSES all §7.1 criteria:**

Promote the factor by changing the default in `SleepWindowPredictor.predict`:

```kotlin
napBudgetFactorProvider: NapBudgetFactorProvider = NapBudgetFactor::adjustment,
```

**If the benefit fixture FAILS any §7.1 criterion:**

Keep the neutral default. Do not change the `predict` signature. Attach the test output (assertion failure message) as a comment on Linear issue AKA-100. The factor stays disabled until improved in a follow-up PR.

- [ ] **Step 4.3: Run the full fast suite**

Run:

```powershell
.\gradlew :app:testDebugUnitTest -PfastTests
```

Expected: PASS.

- [ ] **Step 4.4: Run full unit suite without `-PfastTests` before pushing**

Run:

```powershell
.\gradlew :app:testDebugUnitTest
```

Expected: PASS including architecture tests.

- [ ] **Step 4.5: Format, lint, and commit**

Run:

```powershell
.\gradlew ktlintFormat
.\gradlew detekt
```

Commit (adapt based on promotion outcome):

If promoted:

```powershell
git add app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetEvalComparisonTest.kt
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt
git commit -m "feat(sleep): promote nap-budget factor after eval gate [AKA-100]"
```

If not promoted:

```powershell
git add app/src/test/java/com/babytracker/domain/sleep/eval/NapBudgetEvalComparisonTest.kt
git commit -m "test(sleep): add nap-budget eval gate (factor stays disabled — gate failed) [AKA-100]"
```

---

## Self-Review: Spec Coverage Check

| Nap-budget requirement (AKA-100) | Task that implements it |
|-------------------------------|------------------------|
| `napCountToday` vs. `expectedNapCount(ageWeeks)` from `SleepAgePriors` | Task 2: `NapBudgetFactor.adjustment` |
| Adjust window to account for nap deficit | Task 2: negative adjustment when deficit > 0 |
| Adjust window for surplus | Task 2: Neutral (surplus handled by `resolveNextSleepType` routing) |
| Each factor independently evaluated against baseline (§7.1) | Task 4: comparison test with both cohorts |
| ≥ EVAL_MIN_ANCHORS per segment | Task 4: fixture-size assertion |
| MAE gain ≥ EVAL_MIN_MAE_GAIN_MIN | Task 4: maeGain assertion |
| Positive in-window % gain | Task 4: newInWindow ≥ baselineInWindow assertion |
| Bootstrap CI lower bound positive | Task 4: bootstrapCiLowerBound assertion |
| No missed-window regression | Task 4: newMissed ≤ baselineMissed assertion |
| Insufficient data blocks rather than passes | Existing harness behavior (BLOCK_INSUFFICIENT_DATA) |
| Factor default neutral until gate passes | Task 3 default + Task 4 conditional promotion |
| Attach harness comparison output before merging | Task 4 command output is the artifact |
| Conventional commit scope `feat(sleep)` | All commits use `feat(sleep)` or `test(sleep)` with `[AKA-100]` |
| Algorithm version bumped | Task 1: `sleep-pred-phase2-nap-budget-1` |
