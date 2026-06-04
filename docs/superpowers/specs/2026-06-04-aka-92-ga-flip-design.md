# AKA-92 — GA Flip: Remove Debug Guard from Predictive Sleep Toggle

**Date:** 2026-06-04
**Status:** Approved for planning
**Feature area:** `sleep`, `settings`, `ui`
**Linear issue:** AKA-92
**Linear project:** Improve Sleep Scheduler (Phase 0, issue 7 of 7)
**Blocked by:** AKA-90 (evaluation harness must pass all baseline thresholds)

---

## 1. Summary

Remove the `BuildConfig.DEBUG` guard that hides the `predictiveSleepEnabled` toggle in
`SettingsScreen`. The toggle has been gated to debug builds since Phase 0 shipped (AKA-91).
This flip makes it reachable in release once the evaluation harness (AKA-90) confirms the
baseline predictor clears explicit quality thresholds.

No prediction logic changes. No new features. Pure config/gating change + validation record.

---

## 2. Precondition

Before this branch is merged, the evaluation harness output must be attached as a comment on
AKA-92 in Linear. The harness output must show:

- In-window % ≥ `SleepPredictionTuning.EVAL_MIN_IN_WINDOW_PCT`
- Missed-window rate ≤ `SleepPredictionTuning.EVAL_MAX_REGRESSION`
- No segment below `SleepPredictionTuning.EVAL_MIN_ANCHORS` incorrectly passed

This is a gate, not a formality. Do not merge without the attached output.

---

## 3. What changes

### 3.1 `SettingsScreen.kt` — remove debug wrapper

**File:** `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

Remove the `if (BuildConfig.DEBUG)` wrapper at line 543 and its closing brace at line 578.
The toggle block (divider, `SettingsSwitchRow`, `LeadTimeSegmentedRow`, `QuietHoursRow`)
becomes unconditional.

The `BuildConfig.DEBUG` block at line 633 (Developer / Design System menu) is **unrelated**
and must remain untouched. The import `com.babytracker.BuildConfig` stays because line 633
and line 652 (version name) still use it.

### 3.2 `SettingsViewModel.kt` — fix `showPermissionWarning`

**File:** `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`

Current (line 114):
```kotlin
showPermissionWarning = (predictiveEnabled || napReminderEnabled || (BuildConfig.DEBUG && predictiveSleepEnabled)) && !permissionGranted,
```

After:
```kotlin
showPermissionWarning = (predictiveEnabled || napReminderEnabled || predictiveSleepEnabled) && !permissionGranted,
```

Remove the `import com.babytracker.BuildConfig` at line 5 — it becomes unused in this file.

### 3.3 Toggle default stays `false`

`predictiveSleepEnabled` defaults to `false` in `SettingsUiState` (DataStore default is also
`false`). Users must opt in. No change here.

---

## 4. What does NOT change

- No prediction logic (`SleepWindowPredictor`, `PredictNextSleepWindowUseCase`, etc.)
- No UI surface for the prediction cards (Home, Sleep screen) — already shipped
- No notification logic
- No database schema
- No new settings fields
- The `if (BuildConfig.DEBUG)` block at `SettingsScreen.kt:633` (Developer section)

---

## 5. Testing

### 5.1 New unit test

**File:** `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt`

Add two test cases covering the now-unconditional `showPermissionWarning` path for
`predictiveSleepEnabled`:

1. `predictiveSleepEnabled=true + notifications denied → showPermissionWarning=true`
2. `predictiveSleepEnabled=true + notifications granted → showPermissionWarning=false`

These mirror the existing `predictiveEnabled` tests in the same file. No other test files
need changes — existing tests stub `getPredictiveSleepEnabled()` with `flowOf(false)` and
remain valid.

### 5.2 Existing tests

No existing test asserts that the toggle is hidden in a release context (that path is
unreachable from tests running against a debug build). All existing tests continue to pass
unchanged.

---

## 6. Commit

Conventional commit scope: `chore(sleep)` per AKA-92 spec.

The commit body must include the fixture metrics snapshot (copy from the harness run attached
to AKA-92 in Linear). This is the validation record.

---

## 7. Acceptance criteria

- `predictiveSleepEnabled` toggle visible and functional in a release build.
- `showPermissionWarning` fires correctly when `predictiveSleepEnabled=true` and notifications
  are denied (unit test).
- Evaluation harness output attached to AKA-92 as a Linear comment before merge.
- All existing tests pass.
