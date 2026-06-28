# Partner Dashboard — Live Updates Refactor

**Date:** 2026-06-28
**Branch:** `refactor/partner-dashboard-live-updates`
**Status:** Design approved

## Problem

The partner dashboard reads its data with a one-shot Firestore `get()`
(`FetchPartnerDataUseCase` → `FirestoreSharingService.fetchSnapshot`), refreshed
only by `init`, a manual pull-to-refresh / top-bar button, and three brittle
`LaunchedEffect(...) { viewModel.refresh() }` hooks in the screen.

This pull model was adequate while the partner view was read-only. Now the
partner can perform actions (log bottle feeds, start/stop/edit sleep), and the
pull model breaks in two ways:

1. **The primary's changes are invisible until a manual refresh.** When the
   primary device logs or edits data and pushes a new snapshot, the partner sees
   nothing until they pull or re-enter the screen.
2. **The partner's own actions race the refresh.** A partner action writes an op
   to the `feedOps`/`sleepOps` subcollection; the primary must consume it and
   re-push the snapshot before it is visible. The `LaunchedEffect(saved) →
   refresh()` hooks fire immediately and refetch a snapshot that does not yet
   reflect the change, so the partner's own action appears to "not take" until a
   second manual refresh.

The op subcollections (`feedOps`, `sleepOps`) **already** stream live via
Firestore `addSnapshotListener` (`observeFeedOps` / `observeSleepOps`). Only the
main snapshot the dashboard renders is still one-shot. The fix is to use the
realtime-listener pattern that already exists in this codebase for the snapshot
read path too.

## Goals

- The dashboard reflects the **primary's** changes within ~1s, with no manual
  refresh.
- The dashboard reflects the **partner's own** actions (log bottle, start/stop
  sleep) the instant they tap — using Firestore latency compensation, which
  fires the own-ops listener locally before the write reaches the server.
- Remove the redundant manual-refresh affordances and the refresh-race hooks.

## Non-goals

- **Widget worker** stays one-shot. WorkManager cannot hold a long-lived
  listener; `WidgetRefreshWorker` keeps using `FetchPartnerDataUseCase(code)`,
  which is therefore retained.
- **Sleep-history list behavior is unchanged.** It deliberately overlays edit
  ops only (`mergeSleepHistory`) and does not synthesize a partner-started
  active session into the list; this refactor does not change that.
- **Primary-offline** remains unsolvable by any listener — if the primary is not
  running, partner ops sit unprocessed and the snapshot never updates. This is
  inherent to the op-inbox / primary-authoritative model and out of scope.
- No Firestore security-rule changes, no Room migration, no new dependency.

## Key constraint discovered

`firestore.rules` keeps `allow read: if request.auth != null` on
`/shares/{shareCode}` — share-document reads are open to **any** authed user (the
share code is the capability). Therefore a listener on the share document alone
**cannot** detect a per-partner revoke: revoking one partner only deletes that
partner's `shares/{code}/partners/{uid}` doc, leaving the share doc readable.

Live revoke detection therefore requires a second, cheap listener on the
partner's own `partners/{uid}` document (readable per rules:
`request.auth.uid == partnerUid`). Its deletion → revoked. A full
`deleteShareDocument` is covered separately by the share-doc listener emitting a
missing/no-data snapshot.

## Design

### A. Core: live snapshot (the primary's changes)

**Service — `FirestoreSharingService`** (add as **extension functions**, matching
the existing sleep-op convention that keeps the class under detekt's
`TooManyFunctions` ceiling):

- `observeSnapshot(code: String): Flow<ShareSnapshot?>` — `callbackFlow` +
  `addSnapshotListener` on `shareDoc(code)`. Emits `mapToSnapshot(data)` when
  the `data` field is present; emits `null` when the document is missing or has
  no `data` (the full-delete revoke signal). Mirrors `observeFeedOps`:
  `close(error)` on listener error, `awaitClose { registration.remove() }`.
- `observePartnerConnected(code: String, partnerUid: String): Flow<Boolean>` —
  listener on `partnersCollection(code).document(partnerUid)`; emits
  `snapshot.exists()`. The live equivalent of `isPartnerConnected`.

**Use case — new `ObservePartnerDataUseCase`** (parallels
`FetchPartnerDataUseCase`, which is kept for the widget):

```kotlin
operator fun invoke(): Flow<ShareSnapshot>          // reads stored share code
operator fun invoke(code: ShareCode): Flow<ShareSnapshot>
```

- Debug placeholder code (`BuildConfig.DEBUG && code == DebugSeedConfig
  .PARTNER_SHARE_CODE`) → `flowOf(debugSnapshotBuilder.get().build())`, mirroring
  `FetchPartnerDataUseCase`'s seam.
- Otherwise: `signInAnonymously()` (suspend prelude inside the flow so auth/network
  failures route through `.catch`, not a crash — same idiom as the history use
  cases), then:
  ```kotlin
  combine(
      service.observeSnapshot(code.value),
      service.observePartnerConnected(code.value, uid),
  ) { snapshot, connected ->
      if (!connected || snapshot == null) {
          settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
          throw PartnerAccessRevokedException("Partner access revoked")
      }
      snapshot
  }
  ```
- `.catch`: re-throw `PartnerAccessRevokedException`; wrap any other
  `FirebaseException` as `PartnerDataFetchException` so the consuming view
  models' existing error branches are unchanged.

Transient network drops do **not** error the flow — the Firestore SDK serves
cached data and auto-reconnects, re-emitting on recovery. A thrown error is
therefore genuinely terminal (permission denied / revoke), which is why no
`retryWhen` is added here.

**History view models** — `PartnerFeedHistoryViewModel` /
`PartnerSleepHistoryViewModel`:

- Replace the one-shot `refreshPartnerHistory` body with
  `observePartnerData().flatMapLatest { snapshot -> observePartnerXHistory(snapshot.…) }`.
- The existing op-merge use cases (`ObservePartnerFeedHistoryUseCase`,
  `ObservePartnerSleepHistoryUseCase`) are **unchanged** — they keep overlaying
  the partner's own pending ops. The live snapshot re-emission (when the primary
  consumes an op and re-pushes) now drives the re-merge automatically.
- **Delete** the manual refetch machinery this replaces: `historyJob`,
  `refreshTrigger` (`MutableSharedFlow` debounce), `lastPendingOpIds`, and
  `hasConsumedPendingOps` in `PartnerHistoryRefresh.kt`. The shared helper
  becomes a small flow builder (or is inlined if no longer shared).

> Note: `flatMapLatest` re-subscribes the op listener on each snapshot emission.
> Snapshots change infrequently (only on a primary push), so the occasional
> op-listener re-registration is an acceptable cost for the simpler structure.

### B. Partner's own actions, instant

**Sleep tile** — source the active session from `sleepState.active`
(`PartnerSleepViewModel`, which already computes the optimistic + live active via
`mergeActiveSleep` on its own op listener) instead of
`snapshot.sleepRecords.firstOrNull { it.endTime == null }`. The screen already
collects `sleepState`; this is a wiring change to thread the active session into
the sleep tile. `lastCompletedSleep` and the timeline rows stay snapshot-sourced.

**Bottle tile** — the dashboard view model reuses the existing
`ObservePartnerFeedHistoryUseCase` to overlay the partner's own pending feed ops
onto `snapshot.bottleFeeds`, so a just-logged bottle appears immediately:

```kotlin
observePartnerData().flatMapLatest { snap ->
    observePartnerFeedHistory(snap.bottleFeeds)
        .map { merged -> snap.copy(bottleFeeds = merged.entries) }
}
```

`lastBottle = snapshot.bottleFeeds.maxByOrNull { it.timestamp }` then includes
the optimistic entry. (Both `mergeFeedHistory` and `mergeActiveSleep` are
idempotent on already-merged input, so no double-merge hazard arises from this
layering.)

**`PartnerSleepViewModel`** — remove the `snapshotRefreshTick` mechanism (state
field + bump in the op collector). It existed only to drive the now-deleted
dashboard refetch. The op listener, `stopping`, editor, and `canEditActive`
logic remain.

### C. Dashboard view model & screen cleanup

**`PartnerDashboardViewModel`:**
- `init` collects the composed flow from B; first emission clears `isLoading`.
- `catch`: `PartnerAccessRevokedException` → `isDisconnected = true` +
  `widgetUpdater.updateAll()`; other failures → `error` state.
- Remove `lastRefreshAt` (and the `lastCheckedText` it fed).
- Keep a minimal `retry()` that restarts the collection job, so the existing
  `ErrorState` retry button still works. `clearError()` stays.

**`PartnerDashboardScreen`:**
- Remove `PullToRefreshBox` (use a plain `Box`), the top-bar Refresh
  `IconButton` + its `isRefreshingExistingDashboard` checking-spinner, and
  `RefreshSharedUpdatesButton`.
- Remove the three `LaunchedEffect { viewModel.refresh() }` hooks
  (`bottleFeedState.saved`, `sleepState.snapshotRefreshTick`,
  `sleepState.accessRevoked`). Keep the sheet-closing side of the `saved`
  effect. Revoke is now detected by the live partner-connection listener.
- `PartnerSyncStrip`: drop the "checked ago" line and its parameter; keep the
  "shared ago" line (`lastSyncAt`), which is still the real freshness signal.

## Affected files

- `sharing/data/firebase/FirestoreSharingService.kt` — add `observeSnapshot`,
  `observePartnerConnected` (extension functions).
- `sharing/usecase/ObservePartnerDataUseCase.kt` — **new**.
- `ui/partner/PartnerDashboardViewModel.kt` — collect live flow; drop refresh.
- `ui/partner/PartnerDashboardScreen.kt` — remove refresh affordances; thread
  `sleepState.active` into the sleep tile.
- `ui/partner/PartnerFeedHistoryViewModel.kt`,
  `ui/partner/PartnerSleepHistoryViewModel.kt` — live snapshot source; drop
  refetch dance.
- `ui/partner/PartnerHistoryRefresh.kt` — remove `hasConsumedPendingOps`;
  reshape/inline the shared helper.
- `ui/partner/PartnerSleepViewModel.kt` — remove `snapshotRefreshTick`.
- `FetchPartnerDataUseCase.kt`, `WidgetRefreshWorker.kt` — unchanged (widget).

## Testing

- **New** `ObservePartnerDataUseCaseTest` (Turbine): emits snapshot; revoke on
  disconnect (`connected = false`); revoke on missing/no-data snapshot; debug
  placeholder path; `FirebaseException → PartnerDataFetchException` mapping.
- Rewrite `PartnerDashboardViewModelTest`, `PartnerFeedHistoryViewModelTest`,
  `PartnerSleepHistoryViewModelTest` to mock the `Flow`-returning
  `ObservePartnerDataUseCase` instead of the `suspend` fetch.
- Update `PartnerSleepViewModelTest` for the removed `snapshotRefreshTick`.
- Delete the `hasConsumedPendingOps` test alongside the function.
- Update `PartnerDashboardScreenTest` (androidTest) for the removed
  pull-to-refresh / Refresh button, and assert the sleep tile reflects an
  optimistic active session.

## Rollout

No migration, no rule deploy. Pure client refactor of the partner read path;
the share-document shape and op protocol are unchanged.
