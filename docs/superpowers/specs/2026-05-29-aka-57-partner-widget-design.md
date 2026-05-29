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
| Cache scoping | **Keyed by active share code** — the cache stores the share code it was written for; reads return data only when it matches the current `getShareCode()` | Privacy / tenant isolation. A partner can disconnect from primary A and reconnect to primary B (`ConnectAsPartnerUseCase` sets a new code; `SettingsViewModel` / `ManageSharingViewModel` clear it on disconnect). Without scoping, the widget would render A's baby/feed/sleep under B's context until the next worker refresh. |
| Revoke detection | **Typed `PartnerAccessRevokedException`** (subtype of `IllegalStateException`) thrown by `FetchPartnerDataUseCase` only on confirmed revoke | `FetchPartnerDataUseCase` currently throws a bare `IllegalStateException` for both "No share code" and "Partner access revoked"; network/sign-in failures throw other types. The worker must clear-cache-and-stop only on a *confirmed* revoke, not on any `IllegalStateException`. Subtyping `IllegalStateException` preserves `PartnerDashboardViewModel`'s existing `catch (IllegalStateException)` behaviour (no regression). |

**Ruled out:** live-fetch on the render path (AC forbids a blocking network call inside Glance); full-`ShareSnapshot` JSON cache (heavier render read + custom `Instant` serializer, and its only upside — dashboard offline reuse — is unused because the dashboard fetches live).

## Architecture — two paths, branched on `AppMode`

### Render path (every Glance update; zero network)

`loadWidgetData(context)`:

```
appMode = settings.getAppMode().first()
PARTNER:
   shareCode = settings.getShareCode().first()
   shareCode == null            → WidgetData.EMPTY
   else                         → partnerWidgetCache.read(shareCode) ?: WidgetData.EMPTY
                                  // read() returns null when nothing cached OR the cached
                                  // share code != shareCode (stale data from a prior share)
NONE / PRIMARY                  → existing local-repo path (unchanged)
```

Runs on every Glance update: the 60s `WidgetSyncManager` elapsed timer, the 15-min worker, and system updates. No network on this path, ever.

### Snapshot-refresh path (`WidgetRefreshWorker.doWork()`; 15-min periodic, only while a widget exists)

```
appMode  = settings.getAppMode().first()
if PARTNER:
   shareCode = settings.getShareCode().first()   // read before fetch, to tag the cache
   runCatching { fetchPartnerDataUseCase() }
     success                          → toWidgetData(snapshot)
                                        → cache.save(shareCode, widgetData)   // tagged with the active code
                                        → updater.updateAll() → Result.success()
     PartnerAccessRevokedException (UC already set AppMode.NONE + cleared code)
                                      → cache.clear() → updater.updateAll() → Result.success()   // confirmed revoke; no retry
     other Exception (network / sign-in / "No share code" ISE / bug)
                                      → keep stale cache → updater.updateAll() → Result.retry()
else:
   updater.updateAll()                // unchanged
```

`CancellationException` re-thrown (existing worker contract). Only the typed `PartnerAccessRevokedException` triggers the destructive clear-and-stop branch; every other failure retries and leaves the cache intact, so a transient or misclassified error never silently empties the widget.

### Unchanged: `WidgetSyncManager`

Its 60s elapsed-time timer keeps re-rendering the partner widget from cache (recomputing elapsed values off cached `Instant`s). Its local-flow triggers (feed / sleep / baby) simply never emit for a partner. No change required — satisfies "no reliance on local-repo flow emissions."

## Components

| Component | Change |
|---|---|
| `widget/data/WidgetData.kt` | **New pure overload** `toWidgetData(snapshot: ShareSnapshot): WidgetData`. Picks latest session `sessions.maxByOrNull { it.startTime }` and latest sleep `sleepRecords.maxByOrNull { it.startTime }`, converts the two chosen rows → domain `BreastfeedingSession` / `SleepRecord` (with `pausedAt = null`), then **reuses the existing private derivation helpers** (`toFeedState`, `toEffectiveFeedSide`, `toSleepState`, `toSleepSince`). Single source of truth for derivation rules. |
| `widget/PartnerWidgetCache.kt` | **New** interface: `suspend fun read(shareCode: String): WidgetData?`, `suspend fun save(shareCode: String, data: WidgetData)`, `suspend fun clear()`. |
| `widget/PartnerWidgetCacheImpl.kt` | **New** `@Singleton`, DataStore-backed. 7 keys (`partner_widget_*`): side + states stored as `.name`, instants as epoch-ms `Long`, plus `partner_widget_share_code`. `read(shareCode)` returns null when the feed-state key is absent (never cached) **or** the stored share code != `shareCode` (stale from a prior share). |
| `widget/WidgetDataLoader.kt` | Branch on `AppMode` (read via `SettingsRepository.getAppMode().first()`); partner reads current `getShareCode()` and passes it to `cache.read(shareCode)`; null code → `EMPTY`. |
| `widget/WidgetEntryPoint.kt` | Add `settingsRepository()` + `partnerWidgetCache()` accessors (consumed by `loadWidgetData`). |
| `widget/WidgetRefreshWorker.kt` | Inject `SettingsRepository`, `FetchPartnerDataUseCase`, `PartnerWidgetCache` (class is already `@HiltWorker` — direct constructor injection, no EntryPoint needed). Tags the cache with the share code; catches the typed revoke exception specifically. |
| `sharing/usecase/PartnerAccessRevokedException.kt` | **New** `class PartnerAccessRevokedException(message: String) : IllegalStateException(message)`. Subtyping `IllegalStateException` keeps `PartnerDashboardViewModel`'s existing `catch (IllegalStateException)` (→ `isDisconnected`) working unchanged. |
| `sharing/usecase/FetchPartnerDataUseCase.kt` | On the revoke branch (after `clearShareCode()` + `setAppMode(NONE)`), throw `PartnerAccessRevokedException` instead of the bare `error(...)`. The "No share code" branch keeps throwing plain `IllegalStateException`. |
| `di/WidgetModule.kt` | `@Binds @Singleton` `PartnerWidgetCache` → `PartnerWidgetCacheImpl`. |

### Snapshot → WidgetData mapping rules

- `babyName` = `snapshot.baby.name`; blank → `"Baby"` (reuse existing rule).
- Latest session = max `startTime`; latest sleep = max `startTime`.
- `SessionSnapshot.endTime == null` → open feed → `FeedState.ACTIVE` (snapshot has **no `pausedAt`**, so `PAUSED` is never derived for partners — see [Accepted limitations](#accepted-limitations)).
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
| `partner_widget_share_code` | String | the share code the data was fetched under |

`read(shareCode)` returns `null` when `partner_widget_feed_state` is absent (nothing cached yet) **or** the stored `partner_widget_share_code` != `shareCode` (data belongs to a previous share) → both render as `EMPTY`.

## Error handling

- Render `runCatching` → `WidgetData.EMPTY` fallback (already present in `loadWidgetData`).
- Corrupt / missing cache, or cached share code != current code → `read()` returns null → `EMPTY`.
- Null current share code in PARTNER mode → `EMPTY` (never reads the cache).
- Access revoked → use case sets `AppMode.NONE` + clears code and throws `PartnerAccessRevokedException`; worker clears cache, returns `success()` (no retry). Next render takes the non-partner path → `EMPTY` (partner device has no local data). No crash.
- Any other failure (network / sign-in / "No share code" `IllegalStateException` / bug) → stale cache untouched, `Result.retry()`. Misclassified failures never trigger the destructive cache clear.

## Testing (all JVM unit tests)

- **`WidgetDataMapperTest`** (snapshot overload): latest-by-`startTime` among multiple sessions/sleeps; open session → `ACTIVE`; closed session → `RECENT`; empty session list → `NONE`; `switchTime` → opposite side; blank baby name → `"Baby"`; empty sleep list → `SleepState.NONE`.
- **`PartnerWidgetCacheTest`**: `save` → `read` round-trip (all fields incl. nulls); empty store → `null`; `read` with a **mismatched share code** → `null`; `clear` → `read` returns `null`.
- **`WidgetRefreshWorkerTest`**: partner success → `cache.save(shareCode, ...)` called + `Result.success`; `PartnerAccessRevokedException` → `cache.clear` + `Result.success`, no retry; generic `IllegalStateException` (e.g. "No share code") → `Result.retry`, cache untouched; transient/network failure → `Result.retry`, cache untouched; non-partner mode → `updateAll` only, no fetch.
- **`WidgetDataLoaderTest`**: partner + cache populated for current code → cached data; partner + cached code mismatch → `EMPTY`; partner + empty cache → `EMPTY`; partner + null share code → `EMPTY`; `NONE` / `PRIMARY` → existing local path unchanged.
- **`FetchPartnerDataUseCaseTest`**: revoke path throws `PartnerAccessRevokedException` (and it `is IllegalStateException`); missing-code path throws a plain `IllegalStateException` that is **not** `PartnerAccessRevokedException`.

## Acceptance criteria mapping

- [ ] Partner widget shows last feed + sleep status from snapshot → render path + snapshot mapper.
- [ ] `NONE` / `PRIMARY` unchanged → `AppMode` branch leaves local path intact.
- [ ] No blocking network on Glance render → cache read only; fetch lives in the worker.
- [ ] Refreshes via periodic worker, no local-flow reliance → worker is the sole snapshot trigger; `WidgetSyncManager` re-renders from cache.
- [ ] Snapshot → `WidgetData` is pure + JVM-tested → `toWidgetData(snapshot)` + `WidgetDataMapperTest`.
- [ ] Revoke path doesn't crash widget; falls back to `EMPTY` → worker clears cache (only on typed `PartnerAccessRevokedException`), render takes non-partner path.
- [ ] Cache is scoped to the active share — switching/reconnecting to a different primary never shows the previous primary's data → cache tagged with share code, `read(shareCode)` rejects mismatches.

## Accepted limitations

- **Paused feeds render as `ACTIVE`.** `SessionSnapshot` carries no `pausedAt`, so a paused primary session appears as actively feeding (elapsed continues from cached start). This is a **pre-existing limitation of the shared snapshot model** — `PartnerDashboardScreen` has the same blind spot, so the widget stays consistent with the dashboard rather than introducing a new discrepancy. Fixing it properly means extending the Firestore sync contract (`SessionSnapshot` + `SyncToFirestoreUseCase` + `DomainToSnapshot`), which CLAUDE.md gates behind "an explicit design decision" and AKA-57 explicitly scoped out. Tracked as a follow-up below.

## Out of scope

- **Carry pause state in the shared snapshot** (`SessionSnapshot.pausedAt` / `pausedDurationMs` → partner `PAUSED`). Separate issue: touches the sync contract and benefits the dashboard too, not just the widget.
- "Synced X ago" affordance from `ShareSnapshot.lastSyncAt` (optional follow-up).
- Dashboard-driven cache writes (rejected: worker-only chosen).
- Inventory fields on the widget.
