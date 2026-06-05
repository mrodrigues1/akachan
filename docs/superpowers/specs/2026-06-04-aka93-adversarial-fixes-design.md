# AKA-93 Adversarial Review Fixes

**Date:** 2026-06-04  
**Branch:** feat/sleep-prediction-phase-0  
**Findings source:** Codex adversarial review of AKA-93 commits

---

## Finding 1 [HIGH] — Combined IQR gate blocks type-separated predictor

### Problem

`SleepFeatureExtractor.computeQuality()` lines 115–118 gate `hasSufficientZoneIndependentEvidence` on the
**combined** wake-interval IQR. With stable nap gaps (~90 min) and stable bedtime gaps (~150 min), the combined
IQR is P75−P25 = 150−90 = 60 min, which exceeds the 45-min `INSTABILITY_CEILING_MINUTES`. The predictor
returns `NeedMoreData` before it can use the type-specific P50/P25/P75 values added by AKA-93.

The existing eval fixture was deliberately capped at combined IQR = 40 min to avoid this, so the test suite
does not exercise the realistic case.

### Fix

Add an OR-branch to the instability check: pass if both type-specific IQRs are ≤ ceiling.

```
pass when:
  (combinedIQR == null || combinedIQR ≤ ceiling)
  OR
  (napP75 − napP25 ≤ ceiling AND bedtimeP75 − bedtimeP25 ≤ ceiling)
```

Non-null P25/P75 implies ≥ 4 intervals of that type (the `quartiles()` function requires size ≥ 4),
which already exceeds `MIN_TYPE_INTERVALS` (3). No extra count guard needed.

**Files changed:**
- `SleepFeatureExtractor.kt`: modify `computeQuality()`, add private `isTypeAwareStable()` helper.

**New tests:**
- `SleepFeatureExtractorTest`: wide combined IQR (60 min) + both type IQRs stable → quality passes.
- `SleepFeatureExtractorTest`: wide combined IQR + only one type stable → quality still fails.
- `PersonalizedWakeEvalComparisonTest`: wide-IQR fixture (nap=90 min, bedtime=150 min) produces Window
  anchors in scored range (would be all-NeedMoreData under old gate).

---

## Finding 2 [MEDIUM] — Skipped-nap days route into nap model near bedtime

### Problem

`SleepWindowPredictor.resolveNextSleepType()` lines 108–111 decide next type purely by
`napCountToday < expectedNapCount`. If a baby skips a nap, `napCountToday` is below expected, so the
function returns NAP even when the time of day is already near the baby's learned bedtime. This causes
nap-specific priors and nap P50 to be used for a bedtime prediction.

### Fix

Add late-day override: if within one nap-wake-window-midpoint of the learned `medianBedtimeMinuteOfDay`,
return NIGHT_SLEEP.

Activates only when `medianBedtimeMinuteOfDay` is non-null (baby has learned bedtime history). No
age-based fallback — avoids false routing for infants without night-sleep data.

Circular-safe: `minutesUntilBedtime = (medianBedtimeMinuteOfDay − currentMinuteOfDay + 1440) % 1440`.

**Files changed:**
- `SleepFeatures.kt`: add `currentMinuteOfDay: Int? = null` (default preserves all existing call sites).
- `SleepFeatureExtractor.kt`: set `currentMinuteOfDay` in `extract()` from `clock` + `zoneId`.
- `SleepWindowPredictor.kt`: add `currentMinuteOfDay: Int? = null` param to `resolveNextSleepType()`;
  apply cutoff in NAP branch; pass `features.currentMinuteOfDay` from `buildWindow()`.

**New tests:**
- `SleepWindowPredictorTest`: skipped nap + 60 min before learned 20:00 bedtime (threshold 120 min
  for 20w) → NIGHT_SLEEP.
- `SleepWindowPredictorTest`: skipped nap + 3 h before bedtime → NAP (not yet cutoff).
- `SleepWindowPredictorTest`: skipped nap + no learned bedtime → NAP (override inactive).

---

## Invariants preserved

- Nap-count gate still takes priority: `napCountToday >= expected` → NIGHT_SLEEP, unchanged.
- Late-day override never fires when `medianBedtimeMinuteOfDay` is null.
- `resolveNextSleepType` is tested directly via `internal`; new param is defaulted so existing tests
  compile without change.
- `SleepFeatures` is a data class with the new field defaulted; all existing construction sites (tests,
  `SleepEvalHarness`, `PredictSleepWindowUseCase`) are unaffected.
- The existing 42-day eval fixture (combined IQR = 40 min < 45 min) continues to pass the old gate path;
  the new OR-branch does not change its result.
