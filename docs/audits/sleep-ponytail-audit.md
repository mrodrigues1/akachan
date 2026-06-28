# Sleep Section — Ponytail Over-Engineering Audit

> One-shot repo-wide audit of the **sleep** subsystem (85 production files) for over-engineering only.
> Scope: deletion / simplification / stdlib-or-native replacement. **Out of scope:** correctness bugs, security, performance.
> Every finding below was **validated** (callers grepped, impls counted, manifest/DI checked). Findings that looked cuttable but turned out load-bearing are listed under "Rejected" so the negative is recorded, not silently skipped.
>
> Branch: `chore/sleep-ponytail-audit`. Nothing is applied — this is an execution checklist for another session.

---

## Ranked findings (biggest cut first)

### 1. `delete` — Move the eval/benchmark harness out of the shipped app
**~206 lines relocated from `main/` → `test/`**

Move these 5 files from production source to `app/src/test/java/com/babytracker/domain/sleep/eval/`:

| File | Lines |
|------|-------|
| `SleepEvalHarness.kt` | 157 |
| `SegmentKey.kt` (`ageBandFor`, `SegmentStatus`) | 17 |
| `SegmentResult.kt` | 14 |
| `EvalReport.kt` | 11 |
| `AnchorScore.kt` | 7 |

**Validated:** `rg "SleepEvalHarness|EvalReport|SegmentKey|SegmentResult|AnchorScore" app/src/main` hits **only these 5 files referencing each other** — zero production callers. The live prediction path (`PredictSleepWindowUseCase → SleepFeatureExtractor → SleepWindowPredictor` + the 3 factors) never touches the harness. Every real consumer is in `app/src/test` (`SleepEvalHarnessTest`, `ParametricSweepHarness`, `*EvalComparisonTest`, `SleepPredictionCohortsTest`).

**Do NOT move** (these live in the same `eval/` dir but are production-live via `SleepWindowPredictor`/`PredictSleepWindowUseCase`): `SleepWindowPredictor.kt`, `SleepPredictionFactor.kt`, `CircadianBiasFactor.kt`, `NapBudgetFactor.kt`, `SleepDebtFactor.kt`.

**Execution note:** This is benchmark scaffolding that compiles into the production APK for no reason. Moving it shrinks the shipped app and keeps the tests green (they're already in test source). After moving, run `./gradlew test` — package stays `com.babytracker.domain.sleep.eval` so imports don't change.

---

### 2. `delete` — Dead computed fields in the feature extractor
**~33 lines of true dead code**

- `SleepMetrics.daySleepTodayMillis` + its producer `sumTodayDaySleep()`, `SleepMetrics.lastSleepDurationMillis` + `SleepInterval.durationMillis`, `SleepMetrics.medianMorningWakeMinuteOfDay` — computed in `computeMetrics`, **never read** by `SleepWindowPredictor` or any prod consumer (only asserted in `SleepFeatureExtractorTest`/`SleepWindowPredictorTest`). [`domain/sleep/feature/{SleepMetrics,SleepFeatureExtractor,SleepInterval}.kt`] (~18 lines)
- `EvidenceQuality.lastWakeRecencyMillis`, `.invalidRecordRate`, `.wakeIntervalIqrMillis` — stored but never read; gating uses local vals, and the predictor reads `metrics.wakeIntervalIqrMillis`, not the quality copy. [`domain/sleep/feature/EvidenceQuality.kt` + `SleepFeatureExtractor.kt`] (~6 lines)
- `BreastfeedInterval.isActive` / `isCompleted` / `durationMillis` getters — production checks `endMillis == null` directly; getters referenced only in `BreastfeedIntervalTest`. [`domain/sleep/feature/BreastfeedInterval.kt`] (~9 lines)

**Execution note:** Delete the field + producer together, then delete the now-orphaned test assertions. `./gradlew test` after.

---

### 3. `stdlib` — Replace hand-rolled clamps with `coerceIn`
**~10 lines**

`GenerateSleepScheduleUseCase.kt:319-329` has hand-rolled `clampDuration` / `clampLocalTime` when-expressions. Both `Duration` and `LocalTime` implement `Comparable`, so:
- line 134: `blended.coerceIn(minBound, maxBound)`
- line 232: `calculated.coerceIn(bedtimeWindow.start, bedtimeWindow.endInclusive)`

**Validated:** each helper has exactly one call site (lines 134, 232); `coerceIn` is the exact stdlib equivalent. Delete both private helpers.

---

### 4. `yagni` *(soft)* — Drop `SleepSessionNotificationCoordinator` pass-through
**~18 lines**

Pure pass-through over 2 injected schedulers; all 4 methods are 1-line delegations to `NapReminderScheduler`/`SleepNotificationScheduler` (`cancel`/`scheduleIfEnabled`/`show`). Sole consumer is `TileToggleHandler` (lines 88/90/106/107). Inject the two schedulers directly into `TileToggleHandler` and delete the coordinator + its mock in `TileToggleHandlerTest`. [`manager/SleepSessionNotificationCoordinator.kt`]

**Tension:** trades 1 constructor param for 2. Cut only if you prefer flat delegation over the grouping wrapper.

---

### 5. `yagni` *(soft)* — Collapse `SleepRecommendationUseCases` grouping facade
**~9 lines**

Zero-logic facade wrapping 3 use cases (`PersistSleepRecommendationUseCase`, `UpdateRecommendationLifecycleUseCase`, `CreateSleepRecommendationFeedbackUseCase`), single consumer `PredictiveSleepNotificationCoordinator`. `UpdateRecommendationLifecycleUseCase` is already injected standalone in `PredictiveSleepReceiver`, proving the wrapped use cases stand alone. Inject the 3 directly and delete the facade. [`domain/usecase/sleep/SleepRecommendationUseCases.kt`]

**Tension:** trades 1 constructor param for 3 — a deliberate grouping pattern. Soft cut.

---

### 6. `shrink` — Flatten the `lastSleep` ticker flow
**~5 lines**

`SleepViewModel.kt:139-149` nests `flow{ emit(Unit); emitAll(flow{ while(true){delay;emit} }.flowOn(Default)) }`. Collapse to:
```kotlin
flow { while (true) { emit(Unit); delay(LAST_SLEEP_TICK_MS) } }
```
**Validated:** identical emission schedule (t=0, then every 60s); ticker only feeds `combine(latestCompleted, ticker) → buildLastSleepSummary` (cheap `Instant.now()`/`getString`), so the dropped `flowOn(Default)` is irrelevant on `viewModelScope`.

---

### 7. `delete` — Unused emoji fields
**~3 lines**

- `ScheduleEntry.emoji` — always defaults to `"😴"`, never set at its only construction site (`GenerateSleepScheduleUseCase.kt:171`); inline the literal at the one display site (`SleepScheduleScreen.kt:669→487`) and drop the field. [`domain/model/SleepSchedule.kt:27`] (~2 lines)
- `SleepType.emoji` enum param (+ its `"😴"`/`""` literals) — **zero reads** of the field anywhere; all `SleepType` display routes through `SleepTypeLabel.labelRes()`, `ScheduleEntry.emoji`, or notification string keys. Reduce to `enum class SleepType(val label: String)`. [`domain/model/SleepType.kt:3-6`] (~1 line)

---

## Net impact

```
net: -206 lines out of the production APK (eval harness → test source)
     -33 lines true dead code (feature extractor fields/getters)
     -18 lines (coerceIn + ticker + emoji fields)
     -27 lines additional if both soft yagni facades are collapsed
-------------------------------------------------------------------
~ -257 lines hard cuts, up to -284 with the two soft facade collapses.
0 dependencies removable (none of this is dep bloat).
```

**Suggested execution order:** 1 (harness move — biggest, lowest risk) → 2 (dead fields) → 3 (coerceIn) → 6/7 (ticker + emoji) → 4/5 (soft facades, only if you want flatter DI). Run `./gradlew test` after each; run `./gradlew build` after #1 and #4 (DI/source-set changes).

---

## Rejected — looked cuttable, validated as load-bearing (do NOT touch)

- **The 3 prediction `*Factor` classes + `SleepPredictionFactor`/typealiases** — live via `PredictSleepWindowUseCase`.
- **`SleepAgePriors` (all functions)** — used by predictor, factors, and `GenerateSleepScheduleUseCase`.
- **`SleepMath.median`** — no stdlib equivalent; shared by extractor + predictor.
- **`SleepPredictionTuning` (all 42 consts)** — compile-time `const val`s read directly; no setter/config layer exists, so there's no indirection to collapse (the "knob nobody sets" yagni does not apply to plain constants). 3 `EVAL_*` consts are test-only but gate harness acceptance tests.
- **All 3 sleep repository interfaces** — exactly 1 impl each (allowed by convention); every interface method has a production caller. No dead methods.
- **Thin passthrough use cases** (`DeleteSleepEntryUseCase`, `GetSleepHistoryUseCase`, `UpdateRecommendationLifecycleUseCase`) — zero logic, but inlining forces injecting `SleepRepository` into ViewModels, breaking the use-case-only access convention. CLAUDE.md explicitly allows thin use cases.
- **All 3 receivers** (`SleepActionReceiver`, `PredictiveSleepReceiver`, `PredictiveSleepBootReceiver`) — registered in `AndroidManifest.xml`. Not dead.
- **`SleepNotificationScheduler` / `PredictiveSleepScheduler` interfaces** — 1 impl each but used as DI binding seams (`NotificationSchedulerModule`). Allowed.
- **The two notification coordinators** (`SleepSessionNotificationCoordinator` vs `PredictiveSleepNotificationCoordinator`) — unrelated responsibilities (tile-toggle vs alarm/prediction reconciliation); not a merge candidate (#4 deletes the first as pass-through, not a merge).
- **`SleepReasonText.resolve`** — dual-use (UI `SleepRecommendationSection` + partner-sync `SnapshotSources`); justifies the standalone fn.
- **`SleepPredictionCard` vs `PartnerSleepPredictionCard`** — different data models (domain `SleepPredictionState` w/ animated 7-dot progress vs string-keyed `SleepPredictionSnapshot` w/ staleness regrade + a11y). Shared surface is only Card chrome; extracting would *add* abstraction.
- **`SleepHistoryScreen` vs `PartnerSleepHistoryScreen`** — different data (`SleepRecord` vs `SleepSnapshot`); no shared body worth extracting.
- **Enum→StringRes label helpers** (`SleepTypeLabel`, `ScheduleSleepText`, `RegressionType.*Res`) — the project's sanctioned i18n pattern (CLAUDE.md bans Mapper classes, endorses these); each used in 2-5 sites.
- **`SleepDao` / `SleepRecommendationDao` queries** — every `@Query` has a production caller (`getActiveRecord`/`getActiveRecordOnce` intentionally differ in ordering).
- **All sleep `SleepUiState` / partner UiState fields** — every field read in a screen (per-field grep).
- **The 7 partner sleep "Op" use cases** — all have production callers in the op-inbox flow.
- **Sleep model enums** (`SleepReason` 9 variants, `SleepPredictionState` 7, `SleepAuthor`, `SleepType`) — all produced and consumed.
- **`di/SleepSettingsModule`** — single `@Binds`, *could* fold into `RepositoryModule`, but per-feature modules are the project convention. Left alone (~13 lines, low-confidence).
