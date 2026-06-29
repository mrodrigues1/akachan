# Partner Op Inboxes — Background Drain

**Date:** 2026-06-29
**Branch:** `feat/partner-op-background-drain`
**Status:** Design approved
**Relates to:** SPEC-007 (op-inbox architecture), 2026-06-28 partner dashboard
live-updates design (the "Primary-offline" non-goal this addresses)

## Problem

The op-inbox model (SPEC-007) makes the primary device the only writer of
shared state: a partner action writes an op to `shares/{code}/feedOps` or
`shares/{code}/sleepOps`, and the primary consumes it, applies it to Room, and
re-publishes the snapshot. The consumers — `ProcessFeedOpsUseCase` and
`ProcessSleepOpsUseCase` — are Firestore snapshot listeners collected from
`MainActivity` under `repeatOnLifecycle(Lifecycle.State.STARTED)`, so they run
**only while the primary's app is in the foreground**.

Consequence: when the primary's app is backgrounded or closed, a partner's
start/stop/log sits unapplied in Firestore indefinitely. On the partner side the
action shows optimistically (own-ops listener / latency compensation) and then
evaporates on next open, because the primary never turned the op into a real,
synced entry. The live-updates design called this out as inherent and out of
scope: *"if the primary is not running, partner ops sit unprocessed and the
snapshot never updates."* This design narrows that gap.

## Goals

- A partner's start/stop/log is applied by the primary **even when the primary's
  app is closed**, without the user opening it.
- Reuse the existing apply/sync/delete batch path exactly — foreground and
  background must not diverge in behavior.
- Zero new dependencies, no server-side component, no billing-plan change.

## Non-goals

- **Instant sync.** This is an *eventually* backstop, not a push. Latency is
  bounded by WorkManager's 15-minute periodic floor, and Doze can stretch it
  further. Sub-minute delivery would require FCM + a server-side trigger (Cloud
  Function) to wake the primary — a separate, larger workstream (new dependency,
  Blaze plan, a second manual-deploy target). Explicitly deferred.
- **Force-stopped / OEM-killed primary.** WorkManager does not run for an app the
  user force-stopped or that an aggressive OEM battery manager killed, until the
  app is next launched manually. Same ceiling FCM would hit. Not solvable here.
- **No security-rule change, no Room migration.** Reads the same subcollections
  the foreground path already reads.

## Design

Three pieces, all additive.

### 1. `drainOnce()` — a one-shot entry on each consumer

`ProcessFeedOpsUseCase` and `ProcessSleepOpsUseCase` already hold every
dependency they need and a private `processBatchWithRetry`. Each gains a
`drainOnce()` that does a **single** pass and returns, reusing
`processBatchWithRetry` so the apply → push-snapshot → delete-ops semantics
(ordering, idempotent re-apply, retry, notifications) are identical to the live
collector. It no-ops unless this device is `AppMode.PRIMARY` with an active share
code.

### 2. Server-sourced fetch — `getFeedOps` / `getSleepOps`

The live collectors read via `addSnapshotListener`. A fresh listener with
Firestore offline persistence delivers a **cache-first** emission before the
server round-trip, so a one-shot `.first()` could miss the very op the drain
exists to apply (a partner op that is on the server but not yet in this dozing
device's cache). The drain therefore fetches with `get(Source.SERVER)` via new
`getFeedOps` / `getSleepOps` functions on `FirestoreSharingService`. `Source.SERVER`
throws when the server is unreachable; the worker treats that as retryable.

### 3. `PartnerOpDrainWorker` + scheduling

A `@HiltWorker` (`CoroutineWorker`, mirrors `WidgetRefreshWorker`) calls
`processSleepOps.drainOnce()` then `processFeedOps.drainOnce()`; any throw →
`Result.retry()`. Scheduled from `BabyTrackerApp.onCreate()` as unique periodic
work (15 min, `ExistingPeriodicWorkPolicy.KEEP`, `NetworkType.CONNECTED`),
enqueued on the IO scope to stay off the cold-start critical path.

Scheduling is **unconditional** — the worker self-guards on PRIMARY mode inside
`drainOnce`, so a partner or never-shared device just does one cheap settings
read and returns. This avoids wiring a mode-change observer purely to start/stop
a near-free job.

```
WorkManager (every ~15 min, primary device, network connected)
        │
        ▼
PartnerOpDrainWorker.doWork()
        │  processSleepOps.drainOnce() ── getSleepOps(Source.SERVER) ─┐
        │  processFeedOps.drainOnce()  ── getFeedOps(Source.SERVER)  ─┤
        ▼                                                            ▼
   (PRIMARY + share code?) ── processBatchWithRetry ── apply → push snapshot → delete ops
```

## Concurrency note

The consumers are `@Singleton`, so the foreground collector and the worker share
one instance (and its `syncPending` flag). They can in principle overlap (the OS
may run the worker while the app is foreground). This is safe: ops apply
idempotently (create = upsert, update/delete = no-op when already applied/gone)
and a redundant snapshot push is harmless. No locking added.
`// ponytail: idempotent apply absorbs overlap; add a mutex only if a real double-apply is observed.`

## Testing

- **Unit (JUnit 5 + MockK):** `drainOnce` on each use case — applies fetched ops
  through the batch path (apply → sync → delete, in order) when primary; no-ops in
  partner mode; no-ops without a share code; does nothing on an empty inbox.
- **Worker (Robolectric):** `PartnerOpDrainWorker` drains both inboxes and returns
  `Success`; returns `Retry` when a drain throws.
