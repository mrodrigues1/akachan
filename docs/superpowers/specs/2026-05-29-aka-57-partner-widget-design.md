# AKA-57 — Partner-mode widget shows the primary user's shared data

**Linear:** [AKA-57](https://linear.app/akachan/issue/AKA-57/partner-mode-widget-shows-the-primary-users-shared-data)
**Branch:** `feat/AKA-57-partner-mode-widget-shows-shared-data`
**Date:** 2026-05-29

## Problem

The home-screen widget reads only **local** tracking data. `loadWidgetData(context)` (`widget/WidgetDataLoader.kt`) pulls from `BabyRepository` / `BreastfeedingRepository` / `SleepRepository` (Room) and maps via `toWidgetData(...)`.

In `AppMode.PARTNER` the device has **no local tracking data** — the partner never starts feeds or sleeps. Their view of the primary user's data is a Firestore `ShareSnapshot` (`FetchPartnerDataUseCase` → `SharingRepository.fetchSnapshot(code)`), rendered read-only in `PartnerDashboardScreen`.

Result: a partner who places the widget sees `WidgetData.EMPTY` ("Baby", no feed, no sleep) instead of the primary user's last feed and current sleep status — useless for the exact person who most benefits from glanceability.

## Goal

In `PARTNER` mode the widget renders the primary user's last feed (side + elapsed) and sleep status from the shared snapshot. In `NONE` / `PRIMARY` mode behaviour is unchanged (local repos).

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| What to cache | **Projected `WidgetData`** (6 primitive prefs), not the full snapshot | Matches existing `SettingsRepositoryImpl` primitive-prefs idiom; no JSON / `Instant` serializer; tiny render-path read. Dashboard keeps fetching live, so a reusable full-snapshot cache earns nothing. |
| Refresh trigger | **Worker only** | Meets the AC exactly, smallest scope, zero UI coupling. Widget may be up to ~15 min stale; the 60s sync timer still re-renders elapsed times from cache. |
| Cache location | **Dedicated `PartnerWidgetCache`** (interface + DataStore impl under `widget/`) | Single responsibility, mockable in tests, keeps the already-large `SettingsRepository` free of widget-internal concerns. |

**Ruled out:** live-fetch on the render path (AC forbids a blocking network call inside Glance); full-`ShareSnapshot` JSON cache (heavier render read + custom `Instant` serializer, and its only upside — dashboard offline reuse — is unused because the dashboard fetches live).

## Architecture — two paths, branched on `AppMode`

### Render path (every Glance update; zero network)

`loadWidgetData(context)`:

```
appMode = settings.getAppMode().first()
PARTNER        → partnerWidgetCache.read() ?: WidgetData.EMPTY
NONE / PRIMARY → existing local-repo path (unchanged)
```

Runs on every Glance update: the 60s `WidgetSyncManager` elapsed timer, the 15-min worker, and system updates. No network on this path, ever.

### Snapshot-refresh path (`WidgetRefreshWorker.doWork()`; 15-min periodic, only while a widget exists)

```
appMode = settings.getAppMode().first()
if PARTNER:
   runCatching { fetchPartnerDataUseCase() }
     success                       → toWidgetData(snapshot) → cache.save(it) → updater.updateAll() → Result.success()
     IllegalStateException (revoked; UC already set AppMode.NONE + cleared code)
                                   → cache.clear() → updater.updateAll() → Result.success()   // no retry storm
     other Exception (transient)   → keep stale cache → updater.updateAll() → Result.retry()
else:
   updater.updateAll()             // unchanged
```

`CancellationException` re-thrown (existing worker contract).

### Unchanged: `WidgetSyncManager`

Its 60s elapsed-time timer keeps re-rendering the partner widget from cache (recomputing elapsed values off cached `Instant`s). Its local-flow triggers (feed / sleep / baby) simply never emit for a partner. No change required — satisfies "no reliance on local-repo flow emissions."

## Components

| Component | Change |
|---|---|
| `widget/data/WidgetData.kt` | **New pure overload** `toWidgetData(snapshot: ShareSnapshot): WidgetData`. Picks latest session `sessions.maxByOrNull { it.startTime }` and latest sleep `sleepRecords.maxByOrNull { it.startTime }`, converts the two chosen rows → domain `BreastfeedingSession` / `SleepRecord` (with `pausedAt = null`), then **reuses the existing private derivation helpers** (`toFeedState`, `toEffectiveFeedSide`, `toSleepState`, `toSleepSince`). Single source of truth for derivation rules. |
| `widget/PartnerWidgetCache.kt` | **New** interface: `suspend fun read(): WidgetData?`, `suspend fun save(data: WidgetData)`, `suspend fun clear()`. |
| `widget/PartnerWidgetCacheImpl.kt` | **New** `@Singleton`, DataStore-backed. 6 keys (`partner_widget_*`): side + states stored as `.name`, instants as epoch-ms `Long`. `read()` returns null when the feed-state key is absent (never cached). |
| `widget/WidgetDataLoader.kt` | Branch on `AppMode` (read via `SettingsRepository.getAppMode().first()`); partner reads cache. |
| `widget/WidgetEntryPoint.kt` | Add `settingsRepository()` + `partnerWidgetCache()` accessors (consumed by `loadWidgetData`). |
| `widget/WidgetRefreshWorker.kt` | Inject `SettingsRepository`, `FetchPartnerDataUseCase`, `PartnerWidgetCache` (class is already `@HiltWorker` — direct constructor injection, no EntryPoint needed). |
| `di/WidgetModule.kt` | `@Binds @Singleton` `PartnerWidgetCache` → `PartnerWidgetCacheImpl`. |

### Snapshot → WidgetData mapping rules

- `babyName` = `snapshot.baby.name`; blank → `"Baby"` (reuse existing rule).
- Latest session = max `startTime`; latest sleep = max `startTime`.
- `SessionSnapshot.endTime == null` → open feed → `FeedState.ACTIVE` (snapshot has **no `pausedAt`**, so `PAUSED` is never derived for partners).
- `SessionSnapshot.endTime != null` → `FeedState.RECENT`; no sessions → `FeedState.NONE`.
- Effective feed side: `switchTime != null` → opposite of `startingSide`, else `startingSide`.
- Sleep: `endTime == null` → `SLEEPING` (since = `startTime`); `endTime != null` → `AWAKE` (since = `endTime`); no records → `NONE`.

### Cached fields (`PartnerWidgetCacheImpl`, DataStore prefs)

| Key | Type | Source field |
|---|---|---|
| `partner_widget_baby_name` | String | `WidgetData.babyName` |
| `partner_widget_feed_side` | String? | `WidgetData.lastFeedSide?.name` |
| `partner_widget_feed_start_ms` | Long? | `WidgetData.lastFeedStart?.toEpochMilli()` |
| `partner_widget_feed_state` | String | `WidgetData.feedState.name` |
| `partner_widget_sleep_state` | String | `WidgetData.sleepState.name` |
| `partner_widget_sleep_since_ms` | Long? | `WidgetData.sleepSince?.toEpochMilli()` |

`read()` keys presence off `partner_widget_feed_state`: absent → `null` (nothing cached yet → `EMPTY`).

## Error handling

- Render `runCatching` → `WidgetData.EMPTY` fallback (already present in `loadWidgetData`).
- Corrupt / missing cache → `read()` returns null → `EMPTY`.
- Access revoked → use case sets `AppMode.NONE` + clears code and throws `IllegalStateException`; worker clears cache, returns `success()` (no retry). Next render takes the non-partner path → `EMPTY` (partner device has no local data). No crash.
- Transient fetch failure (network / sign-in) → stale cache untouched, `Result.retry()`.

## Testing (all JVM unit tests)

- **`WidgetDataMapperTest`** (snapshot overload): latest-by-`startTime` among multiple sessions/sleeps; open session → `ACTIVE`; closed session → `RECENT`; empty session list → `NONE`; `switchTime` → opposite side; blank baby name → `"Baby"`; empty sleep list → `SleepState.NONE`.
- **`PartnerWidgetCacheTest`**: `save` → `read` round-trip (all fields incl. nulls); empty store → `null`; `clear` → `read` returns `null`.
- **`WidgetRefreshWorkerTest`**: partner success → `cache.save` called + `Result.success`; revoke (`IllegalStateException`) → `cache.clear` + `Result.success`, no retry; transient failure → `Result.retry`, cache untouched; non-partner mode → `updateAll` only, no fetch.
- **`WidgetDataLoaderTest`**: partner + cache populated → returns cached data; partner + empty cache → `EMPTY`; `NONE` / `PRIMARY` → existing local path unchanged.

## Acceptance criteria mapping

- [ ] Partner widget shows last feed + sleep status from snapshot → render path + snapshot mapper.
- [ ] `NONE` / `PRIMARY` unchanged → `AppMode` branch leaves local path intact.
- [ ] No blocking network on Glance render → cache read only; fetch lives in the worker.
- [ ] Refreshes via periodic worker, no local-flow reliance → worker is the sole snapshot trigger; `WidgetSyncManager` re-renders from cache.
- [ ] Snapshot → `WidgetData` is pure + JVM-tested → `toWidgetData(snapshot)` + `WidgetDataMapperTest`.
- [ ] Revoke path doesn't crash widget; falls back to `EMPTY` → worker clears cache, render takes non-partner path.

## Out of scope

- "Synced X ago" affordance from `ShareSnapshot.lastSyncAt` (optional follow-up).
- Dashboard-driven cache writes (rejected: worker-only chosen).
- Inventory fields on the widget.
