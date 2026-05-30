# Quick Actions Tile — Design

**Linear:** [Quick Actions Tile](https://linear.app/akachan/project/quick-actions-tile-bd670ff2957f/overview) (AKA, High)
**Date:** 2026-05-30
**Status:** Approved design, pending implementation plan

## Summary

Two Android Quick Settings tiles — **Feed** and **Sleep** — that let a parent start or stop a
session straight from the notification panel, without unlocking the phone or opening the app. Each
tile reflects the current session state and, on Android 10+, shows elapsed time as a subtitle.

This mirrors the existing home-screen Glance widget in spirit, but with a key difference: the
**widget is display + open-app only** (`actionStartActivity`), whereas the **tiles perform silent
start/stop writes** directly through the existing use cases.

## Goals

- Start/stop a feeding session from a QS tile, silently (no unlock, no app navigation).
- Start/stop a sleep session from a QS tile, silently.
- Tile visually reflects state: Active / Inactive / Unavailable.
- Tile shows elapsed time for an active session (Android 10+).
- Long-press routes into the app's full session screen.

## Non-goals

- Lock screen shortcut (separate Android API, lower ROI) — from the project brief.
- Side-switch from the tile — from the project brief.
- Live-ticking elapsed time (Android refreshes a tile only on panel open / explicit update).
- Live external-change push (tile re-resolves on next panel open; see Known Limitations).

## Decisions (resolved during brainstorming)

| Question | Decision |
|---|---|
| "Start Feed" needs a breast side | **Alternate** from the last completed session (LEFT↔RIGHT); default **LEFT** if no history. |
| "Start Sleep" needs a sleep type | **Time-of-day heuristic**: local 19:00–06:00 → `NIGHT_SLEEP`, else `NAP`. |
| Single tap | **Silent start/stop toggle** — no app launch, no unlock. |
| Long-press | Opens the **full session screen** in-app (feeding / sleep). |
| PARTNER mode / pre-onboarding | Tile renders **Unavailable**; tap **opens the app** (partner dashboard / onboarding). No silent writes possible. |
| Number of tiles | **Two**: Feed, Sleep. |

## Architecture — Approach A

Two `TileService` subclasses, each declared in the manifest, both sourcing dependencies through a
Hilt `@EntryPoint` (same pattern as `WidgetEntryPoint`) and sharing small pure helpers. The services
stay framework-thin; all branching logic lives in JVM-unit-testable helpers.

Rejected alternatives:

- **One runtime-switched service** — not viable; Android binds one `TileService` subclass per
  declared tile, so two tiles require two classes regardless.
- **Delegate to `BreastfeedingActionReceiver` / `SleepActionReceiver`** — those receivers are built
  around notification intents + `goAsync()`; routing a tile through a broadcast adds latency and
  indirection for no gain. The use cases are the correct seam.

### Components (new package `com.babytracker.tile`)

| Unit | Type | Responsibility |
|---|---|---|
| `FeedTileService` | `TileService` | `onStartListening` → render; `onClick` → toggle. Delegates all logic. |
| `SleepTileService` | `TileService` | Same, sleep variant. |
| `TileEntryPoint` | Hilt `@EntryPoint` (`SingletonComponent`) | Exposes to the non-DI services: `SettingsRepository`, `BreastfeedingRepository`, `SleepRepository`, the four start/stop use cases, and `GlanceWidgetUpdater`. |
| `TileStateResolver` | pure class (`Clock` injected) | Reads app-mode + onboarding flag + active session → `TileRenderState(state, label, subtitle)` where `state ∈ {Active, Inactive, Unavailable}`. |
| `TileToggleHandler` | pure class | Branches start/stop. Feed: active → `stop(session)` else `start(alternateSide(last))`. Sleep: in-progress → `stop(id)` else `start(sleepTypeFor(now))`. |
| `TileSideAndType.kt` | pure functions | `alternateSide(last: BreastfeedingSession?): BreastSide` (LEFT↔RIGHT, default LEFT); `sleepTypeFor(now: Instant, zone: ZoneId): SleepType` (19:00–06:00 → NIGHT_SLEEP, else NAP). |

### Existing seams reused

- Feed: `BreastfeedingRepository.getActiveSession(): Flow<BreastfeedingSession?>`,
  `getLastSession(): BreastfeedingSession?`; `StartBreastfeedingSessionUseCase(side)`,
  `StopBreastfeedingSessionUseCase(session)`.
- Sleep: `SleepRepository.getLatestRecord(): SleepRecord?` (+ `SleepRecord.isInProgress`);
  `StartSleepRecordUseCase(type)`, `StopSleepRecordUseCase(sessionId): SleepRecord?`.
- Widget sync: `GlanceWidgetUpdater.updateAll()`.
- App state: `SettingsRepository.getAppMode()` (NONE/PRIMARY/PARTNER) + onboarding-complete flag.

## Data flow & Android wiring

### Manifest

Two `<service>` entries:

```xml
<service
    android:name=".tile.FeedTileService"
    android:exported="true"
    android:icon="@drawable/<feed_icon>"
    android:label="Feed"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

Plus the analogous `SleepTileService`. One activity entry handles
`android.service.quicksettings.action.QS_TILE_PREFERENCES` (long-press) and routes into
`MainActivity` at the feeding / sleep screen, reusing the `WidgetNavigation` deep-link extras.

### `onStartListening` (panel opened, or our `requestListeningState`)

Launch on a service `CoroutineScope`, call `TileStateResolver`, then `qsTile.updateTile()`. Sets
`Tile.STATE_ACTIVE` / `STATE_INACTIVE` / `STATE_UNAVAILABLE`, label, and subtitle.

### `onClick`

- **Unavailable** (PARTNER / pre-onboarding) → `startActivityAndCollapse` to the app (partner
  dashboard or onboarding).
- **Otherwise** → `TileToggleHandler` performs the start/stop silently (no activity launched, so no
  unlock required), then `updateTile()` flips the state, then `GlanceWidgetUpdater.updateAll()` keeps
  the widget in sync.

### Elapsed subtitle constraint

`Tile.setSubtitle` is API 29+; the app's `minSdk` is 26. Therefore elapsed time renders as a subtitle
on Android 10+ and is omitted on 26–28 (label only). The value is computed at render time and cannot
live-tick; it refreshes on panel open or on our own `updateTile`.

## Error handling

- The toggle is wrapped in `runCatching` → log and leave the tile state unchanged on failure.
- A `TileStateResolver` failure renders **Unavailable** (safe default — no destructive action).

## Known limitations

- **External changes** (session toggled from app / widget / notification) reflect on the tile only at
  the next panel open. A live push (`TileService.requestListeningState()` from the existing action
  paths) is a v2 nicety — deliberately out of scope for v1 (YAGNI).
- Elapsed time does not tick while the panel is open.

## Testing

JUnit 5 + MockK, runnable in the `-PfastTests` fast loop (no architecture-tag dependency):

- `alternateSide` — LEFT→RIGHT, RIGHT→LEFT, null → LEFT.
- `sleepTypeFor` — boundary times 18:59 (NAP), 19:00 (NIGHT), 06:00 (NAP), midday (NAP), 02:00 (NIGHT).
- `TileStateResolver` — matrix over AppMode {NONE, PRIMARY, PARTNER} × onboarded {true, false} ×
  session {active, inactive} → expected `{Active, Inactive, Unavailable}` + label/subtitle.
- `TileToggleHandler` — start branch verifies correct side/type (`coVerify`); stop branch verifies the
  active session / in-progress id is passed.

The `TileService` subclasses are kept thin enough that they need no framework tests; an optional
Robolectric `onClick` smoke test may be added later.

## Implementation scope

Fits a single implementation plan / one Linear issue, delivered across a few commits:

1. Pure helpers (`TileSideAndType`, `TileStateResolver`, `TileToggleHandler`) + their unit tests.
2. `TileEntryPoint` + the two `TileService`s + manifest entries + icons.
3. Long-press `QS_TILE_PREFERENCES` activity + deep-link routing.
