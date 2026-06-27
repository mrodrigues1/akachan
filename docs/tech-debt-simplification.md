# Code-simplification backlog

Deferred findings from the whole-app ponytail over-engineering/duplication audit that produced
**AKA-269 … AKA-277** (shipped in PR #350). These five could not be filed as Linear issues at the
time because the workspace hit its free-tier issue cap. Each is a self-contained, behavior-preserving
refactor — file as its own issue and PR when the cap is lifted.

Line numbers are omitted/approximate (they drift); the file + symbol/pattern is grep-able.

---

## 1. refactor(di): collapse `NotificationSchedulerModule` to `@Binds` + `@Inject`

`di/NotificationSchedulerModule.kt` has four `@Provides` that just `return X(context)` for classes
that can be `@Inject constructor` + `@Binds`. `SleepNotificationManager` *already* has an
`@Inject constructor` yet is still hand-provided.

**Fix:** make all four `@Inject constructor(@ApplicationContext context, …)` and replace the whole
`object NotificationSchedulerModule` with `@Binds` bindings, deleting the `@Provides` block.

Impls: `BreastfeedingNotificationManager`, `PredictiveFeedScheduler(Impl)`,
`PredictiveSleepScheduler(Impl)`, `SleepNotificationManager`.

**Acceptance:** builds, Hilt graph resolves, detekt/ktlint pass, tests pass; no behavior change.

---

## 2. refactor(receiver): dedupe receiver coroutine + boot scaffolding

Two related dedups:

- **Reuse the existing `goAsyncWithTimeout`** (`util/ReceiverExt.kt`) in four receivers that still
  hand-roll `goAsync()` + `CoroutineScope(IO+SupervisorJob).launch { try withTimeout(10s) … finally finish() }`:
  `BreastfeedingActionReceiver`, `SleepActionReceiver`, `PredictiveSleepBootReceiver`,
  `BreastfeedingNotificationReceiver`. Replace each block with `goAsyncWithTimeout(TAG) { handle(...) }`.
- **Extract a `ReminderBootReceiver` base** for `VaccineReminderBootReceiver` and
  `DoctorVisitReminderBootReceiver`, which are byte-identical except the injected repo/scheduler types
  and two log strings (same `onReceive`/`shouldHandle`/`HANDLED_ACTIONS`, same
  `if (!getReminderEnabled().first()) return; scheduler.rescheduleAll()`).

**Acceptance:** builds, detekt/ktlint pass, tests pass; receiver behavior/timeouts unchanged.

---

## 3. refactor(ui): extract shared dashboard skeleton/error/countdown states

`VaccineDashboardScreen` and `DoctorVisitDashboardScreen` carry near-identical `DashboardSkeleton`
(animated low-alpha placeholder blocks), `DashboardError` (message + retry button), and
`countdownLabel` (Today / Tomorrow / in-N-days `when`). They differ only by hero height (150 vs 132dp)
and string-resource ids.

**Fix:** extract `DashboardSkeleton`, `DashboardError(colors, onRetry)`, and a
`countdownLabel(days, todayRes, tomorrowRes, pluralRes)` into one `ui/component/DashboardStates.kt`,
parameterizing the varying bits.

**Acceptance:** builds, detekt/ktlint pass; both dashboards render identically.

---

## 4. refactor(domain): dedupe trends / breastfeeding / features use-case logic

- **Trends feed-instant merge** — the breast + bottle feed fetch/map/`!isAfter(now)` filter is
  copy-pasted in `GetDayRhythmTrendUseCase`, `GetFeedingFrequencyTrendUseCase`,
  `GetFeedingIntervalTrendUseCase`. Extract one `feedInstants(start, now, zone)` helper in
  `domain/.../trends/` (alongside `TrendWindow.kt`).
- **Breastfeeding interval build** — `zipWithNext` interval + `filter { <= INTERVAL_MAX_MINUTES }` +
  quiet-hours endpoint filter + `take(SAMPLE_SIZE_TARGET)` duplicated between
  `CountRecentValidIntervalsUseCase` and `PredictNextFeedUseCase`. Extract one shared helper
  (next to `QuietHours.kt`).
- **Feature toggles** — `SetDomainEnabledUseCase` and `SetFeatureEnabledUseCase` have byte-identical
  bodies (read → `FeatureSelection.setX(...)` → `if (next == current) return false` → write → true).
  Collapse into one private helper taking the `FeatureSelection.*` transform as a lambda.
- **VaccineRecord** — `isOverdue` and `isPastTarget` repeat the identical day-granular expression
  `scheduledDate.atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate())`; only the
  `status ==` guard differs. Extract the date comparison into one private helper.
- **Entity enum lookup** — lenient `X.entries.firstOrNull { it.name == s } ?: DEFAULT` should use the
  collection-search idiom `find` (pure rename): `BabyEventEntity`, `BottleFeedEntity` (×2),
  `GrowthMeasurementEntity`, `SleepEntity`.

**Acceptance:** builds, detekt/ktlint pass, domain tests pass; no behavior change.

---

## 5. refactor(ui): dedupe viewmodel sync + date-grouping helpers

- **`syncSharedSnapshot()`** — `runCatching { syncToFirestore() }` is identical (with the same
  preceding comment in two of them) in `GrowthViewModel`, `MilestonesViewModel`,
  `MilestoneDetailViewModel`. Hoist to one shared helper/extension on the shared sync seam.
- **Day-grouping idiom** — `groupBy { <instant>.atZone(zone).toLocalDate() }.toSortedMap(reverseOrder())
  .map { (d, l) -> d to l.sortedByDescending { <instant> } }` is copy-pasted across
  `DiaperHistoryViewModel`, `VaccineHistoryViewModel` (and a near-variant in `SleepViewModel`).
  Extract `fun <T> List<T>.groupByDateDescending(zone, instantOf): List<Pair<LocalDate, List<T>>>`
  into `util/DateTimeExt.kt`.

**Acceptance:** builds, detekt/ktlint pass, VM tests pass; no behavior change.

---

### Notes / explicitly NOT worth doing (from the same audit)

- `runCatching { syncToFirestore(...) }` / `runCatching { reminderScheduler.* }` best-effort one-liners
  (~13 sites) each pass a different argument and are a documented intentional idiom — not worth extracting.
- The `Instant.ofEpochMilli`/`toEpochMilli` per-field conversions in `toDomain`/`toEntity` are the
  intended no-Mapper pattern; collapsing needs reflection.
- The two settings repos share a DataStore allowlist *shape* but over different keys/domains — not worth
  a risky shared helper.
- `MilkStashWidgetDataLoader` is **not** dead (called from `MilkStashWidget`).
