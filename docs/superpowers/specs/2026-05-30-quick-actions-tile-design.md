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

- Start/stop a feeding session from a QS tile, silently (no app navigation) **when unlocked**.
- Start/stop a sleep session from a QS tile, silently when unlocked.
- Tile visually reflects state: Active / Inactive / Unavailable.
- Tile shows elapsed time for an active session (Android 10+).
- Long-press routes into the app's full session screen.

## Non-goals

- Lock screen shortcut (separate Android API, lower ROI) — from the project brief.
- Side-switch from the tile — from the project brief.
- Live-ticking elapsed time (Android refreshes a tile only on panel open / explicit update).
- Live external-change push (tile re-resolves on next panel open; see Known Limitations).
- Silent writes from a secure lockscreen — explicitly excluded; locked taps open the app (see Security).

## Decisions (resolved during brainstorming)

| Question | Decision |
|---|---|
| "Start Feed" needs a breast side | **Alternate** from the last completed session (LEFT↔RIGHT); default **LEFT** if no history. |
| "Start Sleep" needs a sleep type | **Time-of-day heuristic**: local 19:00–06:00 → `NIGHT_SLEEP`, else `NAP`. |
| Single tap (unlocked) | **Silent start/stop toggle** — no app launch. |
| Single tap (locked + secure keyguard) | **Opens the app behind the keyguard** (`startActivityAndCollapse`) at the relevant session screen — **no silent write**. See Security below. |
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
| `TileToggleHandler` | `@Singleton` | Branches start/stop behind a single-flight `Mutex`. Feed: active → `stopActive()` else `startIfNone(alternateSide(last))`. Sleep: in-progress → `stopActive()` else `startIfNone(sleepTypeFor(now))`. Calls the **atomic** repository ops (see Concurrency). |
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

Resolve state first, then branch in this order:

1. **Unavailable** (PARTNER / pre-onboarding) → `startActivityAndCollapse` to the app (partner
   dashboard or onboarding).
2. **Locked + secure** (`isSecure && isLocked` — `TileService.isLocked()` /
   `KeyguardManager.isKeyguardLocked` + `isDeviceSecure`) → `startActivityAndCollapse` to the relevant
   session screen. The system raises the keyguard; the write happens inside the app after unlock. **No
   silent tile write.** See Security.
3. **Unlocked & available** → `TileToggleHandler` performs the start/stop silently (no activity
   launched), then `updateTile()` flips the state, then `GlanceWidgetUpdater.updateAll()` keeps the
   widget in sync.

A non-secure device (no PIN/pattern/biometric) has no trust boundary to honor, so case 2 does not
apply and case 3 toggles silently as before.

### Elapsed subtitle constraint

`Tile.setSubtitle` is API 29+; the app's `minSdk` is 26. Therefore elapsed time renders as a subtitle
on Android 10+ and is omitted on 26–28 (label only). The value is computed at render time and cannot
live-tick; it refreshes on panel open or on our own `updateTile`.

## Security — lockscreen trust boundary

The tile records are the primary local source of truth; an unintended start/stop corrupts history
timestamps. A QS tile is tappable from the lockscreen, so a silent write there would let anyone with
physical access (or an accidental tap) mutate tracking data with no authentication gate.

**Policy:** on a **secure + locked** device, a tile tap never writes silently — it opens the app
behind the keyguard (`onClick` case 2 above), forcing the user past the keyguard before any record
changes. Silent toggles are allowed only when the device is unlocked, or when it has no secure
keyguard at all (nothing to protect). This keeps the "no app navigation" convenience for the common
unlocked case while closing the unauthenticated-write hole.

This policy is part of the spec and must be covered by tests (see Testing): locked+secure → open-app,
not-locked → toggle, not-secure → toggle.

## Concurrency & idempotency

The existing data layer has **no uniqueness guard**: `BreastfeedingDao.getActiveSession()` is
`SELECT … WHERE end_time IS NULL LIMIT 1` and `insertSession` is a plain insert (sleep is analogous).
Two rapid taps — or a tile tap racing the app / widget / notification path — can both observe "no
active session" and both insert, producing **two concurrent active sessions**; symmetrically a
double-tap can stop a session twice or stop one a competing path just created.

Three layers of defense:

1. **Global DB invariant (the real guard — covers *every* writer).** Enforce "at most one active
   row per table" at the database, so the app, tile, widget, and notification paths are all bound by
   it without rerouting any of them. A plain unique index cannot express this — SQLite treats each
   `NULL` `end_time` as distinct — so use a **partial unique index on a constant expression**, added
   in a Room migration (DB **v2 → v3**):

   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_feed
       ON breastfeeding_sessions(1) WHERE end_time IS NULL;
   CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_sleep
       ON sleep_records(1) WHERE end_time IS NULL;
   ```

   A second concurrent active insert now fails with `SQLiteConstraintException` — the loser of a race
   never creates a duplicate.

   **Room caveat (must verify during impl):** Room's `@Index` cannot model a partial/filtered index,
   and an index present in the DB but absent from the generated `TableInfo` can trip Room's schema
   validation on open. Two acceptable resolutions, decided at implementation time by testing against
   `MigrationTestHelper`:
   - **Preferred:** if validation tolerates the unmodeled partial index, ship it as above.
   - **Fallback:** if it does not, enforce the same invariant with a `BEFORE INSERT` **trigger**
     (`SELECT RAISE(ABORT, …) WHERE EXISTS(… end_time IS NULL)`), which Room does **not** validate, so
     no `identityHash` conflict. Same guarantee, same `SQLiteConstraintException` surfaced to callers.

   Because the guard is global, **existing `insertSession` / `insertRecord` callers can now throw on a
   true race.** The losing writer must treat the constraint violation as "a session is already
   active" (re-read the active row, no-op) rather than crashing. This is a small, contained change to
   the existing insert call sites — not a reroute of their logic — and is in scope for this work.

2. **Atomic start-if-none on the tile path (ergonomics).** The tile reads-then-writes through
   transactional repository ops so its own check-then-act is one DB unit of work and it can branch
   cleanly without relying on catching an exception:
   - Feed: `@Transaction startSessionIfNone(side): Long?` (returns the new id, or `null` if one
     already ran) and `stopActiveSession(): Boolean`.
   - Sleep: analogous `startRecordIfNone(type): Long?` / `stopActiveRecord(): Boolean`.

   The transaction plus the layer-1 index together make the tile race-free; the index is the backstop
   if a non-tile path inserts between this transaction's read and write.

3. **Per-process single-flight.** `TileToggleHandler` is `@Singleton` and serializes its toggle body
   with a `Mutex`, collapsing a burst of rapid tile taps into one logical operation and avoiding
   wasted DB churn / spurious constraint hits from the same process.

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
- **Concurrency** — instrumented Room tests (`inMemoryDatabaseBuilder`): the partial-unique-index /
  trigger invariant rejects a **second active insert** with `SQLiteConstraintException`, including a
  **mixed-path** case (a plain `insertSession` racing a tile `startSessionIfNone`, not just two tile
  calls); existing callers swallow the violation as "already active". Handler `Mutex` collapses two
  near-simultaneous tile toggles to a single start; atomic `startSessionIfNone` returns `null` when
  one already runs; symmetric double-stop test.
- **Migration** — `MigrationTestHelper` v2 → v3: schema validates on open (confirming the chosen
  index/trigger mechanism does not break Room's `identityHash`), and pre-existing rows survive.
- **Lockscreen policy** — a decision helper (`shouldOpenAppOnTap(isSecure, isLocked, state)`) is
  pure and unit-tested: secure+locked → open-app, unlocked → toggle, not-secure → toggle, Unavailable
  → open-app. Keeps the `isLocked` branching out of the framework `TileService` and testable on the JVM.

The `TileService` subclasses are kept thin enough that they need no framework tests; an optional
Robolectric `onClick` smoke test may be added later.

## Implementation scope

Fits a single implementation plan / one Linear issue, delivered across a few commits:

1. **Global DB invariant.** Migration v2 → v3 adding the partial unique index (or trigger fallback)
   on both tables; bump DB version + exported schema JSON; make existing `insertSession` /
   `insertRecord` callers tolerate `SQLiteConstraintException` as "already active". Migration +
   mixed-path race tests.
2. Atomic repository ops (`startSessionIfNone` / `stopActiveSession` + sleep equivalents, via
   `@Transaction` DAO methods) + their tests.
3. Pure helpers (`TileSideAndType`, `TileStateResolver`, `shouldOpenAppOnTap`, `TileToggleHandler`
   with its `Mutex`) + their unit tests.
4. `TileEntryPoint` + the two `TileService`s (with the locked/secure `onClick` branching) + manifest
   entries + icons.
5. Long-press `QS_TILE_PREFERENCES` activity + deep-link routing.
