# Home Screen Widget — Design Spec

**Date:** 2026-05-24
**Linear project:** [Home Screen Widget](https://linear.app/akachan/project/home-screen-widget-c14f827fb7bf)
**Status:** Approved design, pending implementation plan

---

## Goal

A glanceable Android home screen widget that shows the last feed and current sleep status without opening the app. Critical QoL for parents with one hand occupied.

## Scope

- **Small widget (2x1):** last feed — side + elapsed time ("Left · 2h 10m ago").
- **Medium widget (2x2):** last feed + current sleep status ("Sleeping · 45m" or "Awake since 1h"), with baby name header.
- Tap to open the relevant screen in the app.
- Stays current via event-driven push + periodic refresh safety net.

## Non-goals

- Starting/stopping sessions from the widget (see separate Quick Actions Tile project).
- iOS widget.
- Pumping / inventory / milk-bag data on the widget (feed + sleep only for v1).
- A live ticking timer (home-screen widgets cannot tick; see Refresh Strategy).

---

## Architecture

Approach: **Glance + Hilt EntryPoint, direct repository read.** No new ViewModel, consistent with the project's KISS principles. The widget reads existing repositories directly inside `provideGlance`, and is refreshed by an app-scoped observer plus a periodic worker.

### New package: `app/src/main/java/com/babytracker/widget/`

| File | Responsibility |
|------|---------------|
| `BabyWidget.kt` | `GlanceAppWidget` with `SizeMode.Responsive`; reads data in `provideGlance`, renders content. |
| `BabyWidgetReceiver.kt` | `GlanceAppWidgetReceiver` subclass; `glanceAppWidget = BabyWidget()`. Registered in manifest. |
| `WidgetData.kt` | Plain data class: `lastFeedSide`, `lastFeedStart`, `sleepState` (SLEEPING/AWAKE/NONE), `sleepSince`, `babyName`. Plus a `fun toWidgetData(...)` builder. |
| `WidgetEntryPoint.kt` | Hilt `@EntryPoint` (`@InstallIn(SingletonComponent::class)`) exposing `BreastfeedingRepository` + `SleepRepository`. |
| `WidgetContent.kt` | Glance composables: `SmallContent`, `MediumContent`, shared rows, empty states. |
| `WidgetSyncManager.kt` | `@Singleton`; observes repository Flows and triggers `BabyWidget().updateAll(context)` on change (event push). |
| `WidgetRefreshWorker.kt` | `CoroutineWorker`; periodic `updateAll` safety net (15 min). |

### Data read

`BabyWidget.provideGlance` uses `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)` to obtain the two repositories, then suspend-reads:

- `BreastfeedingRepository.getLastSession()` — already exists.
- `SleepRepository.getLatestRecord()` — **new method** (see Production changes). Returns the most recent sleep record regardless of completion.

Sleep state is derived from the single latest record:
- record exists, `endTime == null` → **SLEEPING** (`sleepSince = startTime`).
- record exists, `endTime != null` → **AWAKE** (`sleepSince = endTime`, drives "Awake since").
- no record → **NONE** ("No sleep logged").

A single latest-record read is required: an active-only query cannot distinguish AWAKE from NONE, so the spec'd "Awake since" state would be unrenderable.

It maps the results into `WidgetData` and renders. Pure read; no write, no ViewModel.

### Production changes outside the widget package

1. **`SleepRepository` + impl + `SleepDao`:** add
   ```kotlin
   suspend fun getLatestRecord(): SleepRecord?
   ```
   DAO query: `SELECT * FROM sleep_records ORDER BY start_time DESC LIMIT 1`. Returns the newest record whether or not it is complete; the widget derives SLEEPING/AWAKE/NONE from its `endTime`. Maps via existing `toDomain()`.

2. **`WidgetSyncManager` wiring:** started from `BabyTrackerApp.onCreate()`. It launches a coroutine on an application-scoped `CoroutineScope` (SupervisorJob + Dispatchers.Default) that collects:
   - `BreastfeedingRepository.getAllSessions()`
   - `SleepRepository.getAllRecords()`

   merged + `debounce(500ms)` + `distinctUntilChanged`, and calls `BabyWidget().updateAll(appContext)` on each emission. This is the single event-push hook — it covers start, stop, edit, and delete for both features without touching any use case or ViewModel, keeping the domain layer framework-free.

No domain use case or model is modified. No framework imports leak into `domain/`.

---

## Refresh Strategy

Home-screen widgets cannot run a live timer, so elapsed text ("2h 10m ago") is frozen between refreshes. Two mechanisms keep it acceptably current (hybrid, per design decision):

1. **Event push (primary):** `WidgetSyncManager` observes Room Flows and pushes `updateAll` within ~0.5s of any feed/sleep data change.
2. **Periodic safety net:** `WidgetRefreshWorker` runs every 15 min (WorkManager `PeriodicWorkRequest`, the platform minimum) to refresh elapsed text even when the app process is not alive to observe Flows. Enqueued (unique, KEEP) the first time a widget is bound (`BabyWidget.onUpdate` / receiver `onEnabled`), cancelled in `onDisabled` when the last widget is removed.

Elapsed text reuses `Duration.formatElapsedAgo()` from `util/DateTimeExt.kt`.

---

## Content & Layout

### Small (2x1)
- Feed side icon + `"<Side> · <elapsed> ago"`.
- Empty: `"No feeds yet"`.

### Medium (2x2)
- Header: baby name.
- Feed row: side icon + `"<Side> · <elapsed> ago"`.
- Sleep row:
  - Sleeping: `"Sleeping · <duration>"` (duration since `sleepSince`).
  - Awake (has a completed last sleep but none active): `"Awake since <elapsed>"`.
  - None logged: `"No sleep logged"`.
- Empty feed: `"No feeds yet"`.

`SizeMode.Responsive` with two declared sizes selects Small vs Medium; one `GlanceAppWidget` handles both.

### Taps (two zones)
- **Small:** whole widget → Home (`MainActivity`, Home route).
- **Medium:** feed row → Breastfeeding screen; sleep row → Sleep screen. Implemented with `actionStartActivity` + an Intent extra carrying the target route, consumed by `MainActivity` to set the start/deep-link destination. Reuse existing `Routes` constants.

---

## Theme

Wrap content in `GlanceTheme` using `ColorProviders` that mirror the Baby palette (Pink primary for feed, Blue for sleep), with light/dark day-night variants matching the app's schemes (`Theme.kt`). No new color constants — reference existing palette values from `ui/theme/Color.kt`.

---

## Edge Cases

- **PARTNER app mode:** the widget reads local Room, which is empty on a partner device → empty states render. Acceptable: the widget is a primary-device feature. Not specially hidden in v1.
- **No baby profile / fresh install:** empty states for both rows; baby name header falls back to a neutral label (e.g. "Baby").
- **In-progress feed:** `getLastSession()` returns the most recent session regardless of completion; elapsed is measured from its `startTime`. (v1 shows last feed by start time; no special "feeding now" state.)
- **WorkManager unavailable / disabled:** event push still functions while the process is alive; only cross-process staleness is affected.

---

## Dependencies (add to `gradle/libs.versions.toml`)

- `androidx.glance:glance-appwidget`
- `androidx.glance:glance-material3`
- `androidx.work:work-runtime-ktx`

Versions pinned to the current AndroidX-compatible releases at implementation time; update the CLAUDE.md tech-stack table accordingly. minSdk 26 is compatible with Glance.

---

## Testing

- **Unit (`src/test/`):**
  - `SleepDao.getLatestRecord` — in-memory Room: returns newest record (open or completed), null when none, newest when multiple.
  - `WidgetData` mapping — covers all three sleep states (active→SLEEPING, completed→AWAKE, none→NONE) and feed present/absent → correct `sleepState`, `sleepSince`, and feed fields.
  - Elapsed/duration formatting already covered by `DateTimeExt` tests; add cases if new formatting introduced.
  - `WidgetSyncManager` — emits `updateAll` trigger on Flow change (verify via an injected updater abstraction / MockK).
- **Not unit-tested:** Glance composable rendering (no Robolectric Glance host available). Manual verification on device for layout, taps, and resize.
- Architecture tests: new `widget/` files must satisfy existing Konsist rules; tag any new arch tests `@Tag("architecture")` and use the shared `productionScope`.

---

## Out of Scope / Future

- Quick actions (start/stop) from the widget — separate Quick Actions Tile project.
- Pumping/inventory widget content.
- Configurable widget (size/content preferences).
