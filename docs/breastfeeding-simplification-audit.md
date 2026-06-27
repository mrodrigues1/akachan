# Breastfeeding section — over-engineering audit

Whole-section ponytail audit (over-engineering/complexity only; no correctness/security/perf).
Scope: domain models, use cases, repository/DAO/entity, ViewModels, screens, coordinator,
receivers, prediction/settings helpers. **Sections A + B applied; Section C deferred — see
Execution status below.**

Line numbers drift — each item is grep-able by file + symbol. Each is behavior-preserving; file as
its own issue/PR/commit when executing.

Two items overlap the existing deferred backlog in `docs/tech-debt-simplification.md` (items #2 and
#4) — cross-referenced below, **do not double-file**.

---

## Execution status (2026-06-27)

**Sections A and B executed** on `refactor/audit-breastfeeding`, one commit each (`./gradlew test`
+ detekt green):

- A1–A5 applied as listed. A2 had a **third** call site beyond the two noted (`EditDeleteOverflowMenu`
  now called directly in `UnifiedFeedingHistoryScreen` too). A3 also renamed
  `BreastfeedingNotificationSettings.kt` → `BreastfeedingActiveNotificationSettings.kt` (detekt
  `MatchingDeclarationName`, single remaining decl) and dropped the now-dead schedule-settings test.
- B1/B2 applied despite the "tracked elsewhere" note (real reuse refactors; no new backlog entries
  filed). B1's shared helper lives in `RecentFeedInterval.kt` (type `RecentFeedInterval`,
  fun `recentValidIntervals`) next to `QuietHours.kt`.

**Section C not executed** — left to an explicit decision: C1 (app-wide icon dedup, readability call),
C2 (passthrough use cases — contradicts CLAUDE.md's use-case convention; needs a convention-relaxation
decision), C3 (`StartSessionAction` — audit verdict is "leave").

---

## A. Clean, untracked wins (execute these)

### A1. refactor(ui): `previous.copy(...)` instead of full UiState rebuild

`BreastfeedingViewModel.init` (first `combine` block) hand-constructs a new `BreastfeedingUiState`
copying all 17 fields from `previous.*` just to change 4 (`activeSession`, `maxPerBreastMinutes`,
`maxTotalFeedMinutes`, `currentSide`).

**Fix:** replace the 17-field constructor call with
```kotlin
previous.copy(
    activeSession = session,
    maxPerBreastMinutes = maxPerBreast,
    maxTotalFeedMinutes = maxTotal,
    currentSide = session?.currentSide(),
)
```
Shorter, and won't silently drop a newly-added field later.

**Files:** `ui/breastfeeding/BreastfeedingViewModel.kt` (~138). **Saves:** ~13 lines.
**Acceptance:** `BreastfeedingViewModelTest` passes; UI state unchanged.

### A2. refactor(ui): inline `FeedSessionOverflowMenu`

`FeedSessionOverflowMenu(onEdit, onDelete)` only forwards to `EditDeleteOverflowMenu(onEdit, onDelete)`
— identical signature, zero added value.

**Fix:** call `EditDeleteOverflowMenu` directly at its 2 call sites (history card + last-feeding card),
delete the wrapper.

**Files:** `ui/breastfeeding/BreastfeedingHistoryScreen.kt` (~221); call sites in the same file and
`BreastfeedingScreen.kt` (`LastFeedingSummaryCard`). **Saves:** ~6 lines.
**Acceptance:** menus render/behave identically; tests pass.

### A3. refactor(domain): collapse one-consumer notification-schedule wrapper

`BreastfeedingNotificationScheduleSettings` (data class) + `getBreastfeedingNotificationScheduleSettings()`
extension have exactly one consumer — `BreastfeedingSessionNotificationCoordinator`, which immediately
destructures the two ints.

**Fix:** inline `combine(getMaxPerBreastMinutes(), getMaxTotalFeedMinutes())` in the coordinator (or read
both via `.first()`), delete the data class and extension. (Keep `BreastfeedingActiveNotificationSettings`
+ its extension — that one has 2 consumers: coordinator and `BreastfeedingActionReceiver`.)

**Files:** `domain/model/BreastfeedingNotificationSettings.kt`,
`domain/repository/FeedSettingsRepositoryExt.kt`, `manager/BreastfeedingSessionNotificationCoordinator.kt`.
**Saves:** ~12 lines. **Acceptance:** coordinator scheduling unchanged; tests pass.

### A4. refactor(ui): reference `PredictionTuning.SAMPLE_SIZE_MIN` instead of local `3`

`FeedSettingsScreen` defines `private const val MIN_VALID_INTERVALS = 3`, which duplicates
`PredictionTuning.SAMPLE_SIZE_MIN = 3` (same "minimum samples to predict" concept). Two literals that
must stay in lockstep.

**Fix:** delete `MIN_VALID_INTERVALS`, use `PredictionTuning.SAMPLE_SIZE_MIN` for the gating checks.

**Files:** `ui/breastfeeding/FeedSettingsScreen.kt` (~64). **Saves:** ~1 line + drift risk.
**Acceptance:** reminder-gating behavior unchanged; tests pass.

### A5. refactor(ui): inline private `SectionLabel(text)` wrapper

`EditBreastfeedingSessionSheet` has a private `SectionLabel(text)` that only binds
`color = MaterialTheme.colorScheme.primary` over the imported `SectionLabel`.

**Fix:** call `SectionLabel(text, color = MaterialTheme.colorScheme.primary)` at its 2 sites, delete the
wrapper. (Note: keep the binding explicit to avoid the same-name-recursion trap — the inner call must
resolve to the imported 2-arg overload.)

**Files:** `ui/breastfeeding/EditBreastfeedingSessionSheet.kt` (~174). **Saves:** ~6 lines.
**Acceptance:** edit sheet renders identically; tests pass.

---

## B. Already tracked — do NOT re-file (cross-ref only)

### B1. Interval-build duplication — see `tech-debt-simplification.md` item #4

`zipWithNext` interval + `<= INTERVAL_MAX_MINUTES` filter + quiet-hours endpoint filter +
`take(SAMPLE_SIZE_TARGET)` duplicated between `CountRecentValidIntervalsUseCase` and
`PredictNextFeedUseCase`. Extract one shared helper next to `QuietHours.kt`. ~15 lines.
(Side note, out of audit scope: `Count` loads the whole table via `getAllSessions().map { take(...) }`
while `PredictNextFeed` uses the bounded `getRecentSessionsFlow(LOOKBACK_LIMIT)` — worth aligning when
the helper lands.)

### B2. Receiver coroutine scaffolding — see `tech-debt-simplification.md` item #2

`BreastfeedingActionReceiver` and `BreastfeedingNotificationReceiver` hand-roll
`goAsync()` + `CoroutineScope(IO+SupervisorJob).launch { withTimeout(10s){…} finally finish() }`.
Replace each with the existing `goAsyncWithTimeout(TAG) { handle(...) }` (`util/ReceiverExt.kt`).
~16 lines across the two breastfeeding receivers.

---

## C. Conditional / needs a decision (real cuts, but contested)

### C1. delete?: 17 near-identical section-icon composables

`BreastfeedingIcon`, `BottleFeedIcon`, `FeedingHistoryIcon`, … (17 total) are each the same 6-line
`Image(painterResource(R.drawable.X), contentDescription = null, modifier.clearAndSetSemantics {})`.

**Possible fix:** one `SectionIcon(@DrawableRes res, modifier)` + thin named aliases, or pass the
`@DrawableRes` at call sites. ~80 lines.

**Why conditional:** app-wide file (not breastfeeding-only); call sites currently read as named
composables. Decide whether the named-call readability is worth the duplication before touching it.

**Files:** `ui/component/BreastfeedingIcon.kt`.

### C2. yagni?: pure-passthrough use cases

`GetBreastfeedingHistoryUseCase` and `DeleteBreastfeedingSessionUseCase` contain zero logic — they
delegate straight to `BreastfeedingRepository`, which `BreastfeedingViewModel` already injects directly.

**Possible fix:** call `repository.getAllSessions()` / `repository.deleteSession()` directly; delete the
two use cases + their test files. ~25 lines + 2 tests.

**Why conditional:** directly contradicts CLAUDE.md's mandated single-responsibility use-case layer, and
the prior whole-app audit (PR #350) deliberately kept these. **Do not execute without an explicit
decision to relax the convention** — otherwise leave as-is.

### C3. leave: `StartSessionAction` enum + `resolveStartSessionAction`

A 3-value enum + function for a 3-branch decision used once — but it exists to keep the start/permission
logic unit-testable (`BreastfeedingScreenStartActionTest`). Testability earns it. **No action.**

---

## Net

- Section A (clean, untracked): **~-38 lines**, no behavior change, no decision needed.
- Section B (tracked): ~-31 lines — already in the backlog.
- Section C (contested): up to ~-105 lines, gated on the icon-readability and use-case-convention calls.

**Total possible ~-160 lines, -0 dependencies** (nothing redundant to remove from the build).

Nothing fully dead: every repository/DAO method (`getAllSessionsOnce`, `getLastSession`,
`getRecentSessions`, `observeLatestSession`, `startSessionIfNone`, `stopActiveSession`) has a live caller
(export, widget, tile, sleep, sync).
