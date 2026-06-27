# Partner-sharing over-engineering backlog

> **Status (executed):** Items 1–6, 8, and 9 are **done** (behavior-preserving; builds + tests green).
> Item 8 landed as part of item 1 (the split `observe*Ops` methods disappeared with the interface).
> **Item 7 is deliberately NOT done** — it collapses single-responsibility use cases, which fights the
> CLAUDE.md "single-responsibility use cases" convention; per its own caveat it is left as-is.

Deferred findings from a ponytail over-engineering/duplication audit scoped to the **partner-sharing
feature** (`sharing/`, `ui/partner/`, `ui/sharing/`, partner notifiers, `di/SharingModule.kt`).

Scope is complexity only — correctness, security, and performance were explicitly out of scope.
Each item is a self-contained, behavior-preserving refactor; file as its own issue/PR.

Line numbers are approximate (they drift); the file + symbol/pattern is grep-able.
**Estimated total: ~-560 lines, -4 files (2 interfaces + 2 notifier classes), -3 `@Binds`.**

Ranked biggest cut first.

---

## 1. refactor(repository): delete the `SharingRepository` seam — `≈ -194 lines`

`SharingRepository` + `SharingRepositoryImpl` are a 100% mechanical forwarder: every method just
unwraps `code.value` and calls the identically-named `FirestoreSharingService` method. The service is
a final `@Singleton` taking/returning only domain types, so MockK can fake it directly; the only
non-mock consumer of the seam is one androidTest hand-fake.

**Fix:** delete both files, inject `FirestoreSharingService` into the ~10 use cases (they already hold
`ShareCode`, so they pass `.value`), and drop the `@Binds` in `SharingModule`. Subsumes item 8.

- `sharing/data/repository/SharingRepositoryImpl.kt:1-123`
- `sharing/domain/repository/SharingRepository.kt:1-71`
- `di/SharingModule.kt` (`@Binds bindSharingRepository`)

**Acceptance:** builds, Hilt graph resolves, detekt/ktlint pass, tests pass; no behavior change.

---

## 2. refactor(notification): collapse the sleep-notifier trio to the Helper — `≈ -40 lines`

`PartnerSleepNotifier` (single impl) + `PartnerSleepNotificationManager` (pure forwarding —
`notifications.lastOrNull()?.let { helper.show(...) }`) add nothing over
`PartnerSleepNotificationHelper`. Violates CLAUDE.md "no interface unless multiple impls".

**Fix:** inject `Context` into `ProcessSleepOpsUseCase` and call
`PartnerSleepNotificationHelper.showPartnerSleepChange(context, notifications.last())` directly; delete
both files + the `@Binds`.

- `manager/PartnerSleepNotifier.kt:9`
- `manager/PartnerSleepNotificationManager.kt:11`
- `di/NotificationSchedulerModule.kt:93`

**Acceptance:** builds, Hilt resolves, detekt/ktlint pass, tests pass; notification behavior unchanged.

---

## 3. refactor(partner): unify the two history ViewModels — `≈ -40 lines`

`PartnerFeedHistoryViewModel` and `PartnerSleepHistoryViewModel` share line-for-line identical
`init` (refresh + debounced `refreshTrigger collectLatest`), `historyJob`/`lastPendingOpIds`/
`refreshTrigger` fields, and `refresh()` body (fetch → observe → collect → `hasConsumedPendingOps` →
identical 4-branch try/catch with `widgetUpdater`). Only the observe use-case (and a couple feed-only
fields) differ.

**Fix:** extract one shared free `suspend fun` taking the observe lambda (no `BaseViewModel` — that is
an anti-goal) and call it from both.

- `ui/partner/PartnerSleepHistoryViewModel.kt:48-95`
- `ui/partner/PartnerFeedHistoryViewModel.kt:55-118`

**Acceptance:** builds, detekt/ktlint pass, both VM tests pass; no behavior change.

---

## 4. refactor(sharing): share the snapshot builder between sync + share-code — `≈ -30 lines`

`GenerateShareCodeUseCase.buildSnapshot`/`buildPrediction` duplicate
`SyncToFirestoreUseCase.syncFull`/`currentPrediction` near-verbatim (same `ShareSnapshot` assembly,
identical prediction helper).

**Fix:** extract one shared snapshot builder (e.g. on `SnapshotSources`) and call from both.

- `sharing/usecase/GenerateShareCodeUseCase.kt:50-88`
- `sharing/usecase/SyncToFirestoreUseCase.kt` (`syncFull`, `currentPrediction`)

**Acceptance:** builds, detekt/ktlint pass, both use-case tests pass; no behavior change.

---

## 5. refactor(notification): delete the `PartnerFeedNotifier` interface — `≈ -21 lines`

Single impl (`PartnerFeedNotificationManager` holds the real logic — keep it); the interface is a
mechanical seam mocked in one test, and MockK mocks concrete classes fine.

**Fix:** inject `PartnerFeedNotificationManager` into `ProcessFeedOpsUseCase`, delete the interface +
`@Binds`.

- `manager/PartnerFeedNotifier.kt:9`
- `di/NotificationSchedulerModule.kt:89`

**Acceptance:** builds, Hilt resolves, detekt/ktlint pass, tests pass; no behavior change.

---

## 6. refactor(widget): delete the `PartnerWidgetCache` interface — `≈ -16 lines`

One final DataStore-backed impl, already MockK-fakeable in both its test sites.

**Fix:** inject `PartnerWidgetCacheImpl` into the widget worker/loader/entry-point, drop the
`@Binds bindPartnerWidgetCache`.

- `widget/PartnerWidgetCache.kt:11-15`
- `di/WidgetModule.kt:31`

**Acceptance:** builds, Hilt resolves, detekt/ktlint pass, tests pass; no behavior change.

---

## 7. refactor(sharing): collapse the feed-op and sleep-op use-case trios — `≈ -70 lines` (OPTIONAL)

> **Caveat:** this trades against the project's documented single-responsibility-use-case norm. Only do
> it if you decide the duplication outweighs the convention — otherwise leave as-is.

- **Feed:** `LogPartnerFeedUseCase`, `EditPartnerFeedUseCase`, `DeletePartnerFeedUseCase` are three
  near-identical files that build a `FeedOp(action=…)` and call `submitFeedOp`. Collapse to one
  action-parameterized use case (validation branches on action). `≈ -40`
- **Sleep:** `StartPartnerSleepUseCase`, `StopPartnerSleepUseCase`, `UpdatePartnerSleepUseCase` mirror
  the feed trio (build `SleepOp` + `submitSleepOp`). Same collapse. `≈ -30`

**Acceptance:** builds, detekt/ktlint pass, use-case tests pass; no behavior change.

---

## 8. refactor(sharing): merge the split observe methods — `≈ -12 lines` (subsumed by item 1)

The repository splits the service's single `observeFeedOps(code, authorUid? = null)` into
`observeFeedOps`/`observeOwnFeedOps` (same for sleep). Collapse each pair into one method with a
nullable `authorUid` default. If item 1 lands, this disappears with the interface.

- `sharing/domain/repository/SharingRepository.kt:55-69`

**Acceptance:** builds, detekt/ktlint pass, tests pass; no behavior change.

---

## 9. Small cuts (batch into one `chore`/`refactor` commit) — `≈ -45 lines`

- `stdlib:` **`PartnerAccessError.isPermissionDenied`** hand-rolls a cause-chain walk. Replace with
  `generateSequence(this) { it.cause }.any { (it as? FirebaseFirestoreException)?.code == PERMISSION_DENIED }`.
  `[sharing/usecase/PartnerAccessError.kt:32-42]`
- `shrink:` **`resolveAccent()`** is byte-for-byte duplicated in `PartnerSleepNotificationHelper` and
  `StashNotificationHelper`. Hoist one `Context.resolveNotificationAccent(light, dark)` into util.
  `[util/PartnerSleepNotificationHelper.kt:85]`
- `shrink:` **`ManageSharingViewModel.startSharing`/`generateNewCode`** share the same tail (generate →
  `getShareCode().first()` → `getPartners` → update). Extract one `private suspend fun regenerateAndLoad()`.
  `[ui/sharing/ManageSharingViewModel.kt:71,100]`
- `delete:` **`PartnerSleepUiState.errorMessage` + `onErrorHandled()`** — field is set but no screen
  reads it; `onErrorHandled()` has zero callers. `[ui/partner/PartnerSleepViewModel.kt:54,150,163,229]`
- `yagni:` **`InventorySnapshotFields` + `toSnapshotFields()`** — 3-field bundle, the extension is a 1:1
  copy. Inline `InventorySnapshotFields(...)` at the 2 call sites.
  `[sharing/domain/model/InventorySnapshotFields.kt:11-16]`
- `delete:` **`SyncType.BABY`** enum value + its dispatch branch + private `syncBaby()` — no production
  caller dispatches BABY (only its own def + a test). `[sharing/usecase/SyncToFirestoreUseCase.kt:30,40,114-117]`
- `stdlib:` **`EaseOutQuart`** is redefined in `PartnerSleepPredictionCard`; import the existing
  `com.babytracker.ui.home.EaseOutQuart`. `[ui/partner/PartnerSleepPredictionCard.kt:44]`
- `delete:` **`PartnerWarningColors.accent`** — read only by a test, never in production.
  `[ui/partner/PartnerDashboardScreen.kt:132,1652,1659]`
- `shrink:` `replaceFirstChar { it.lowercase() }` in `PartnerSleepPredictionCard` duplicates the private
  `String.lowercaseFirstChar()` in `PartnerDashboardScreen` — make it `internal` and reuse.
  `[ui/partner/PartnerSleepPredictionCard.kt:74]`
- `delete:` `badgeEmoji = ""` is the `HistoryCard` default and `badgeContent` is supplied — drop the arg.
  `[ui/partner/PartnerSleepHistoryScreen.kt:223]`
- `delete:` Misplaced/duplicate KDoc above `MergedSleepHistory` (describes `mergeSleepEdit`, not the data
  class). `[sharing/domain/model/MergePartnerSleep.kt:56-60]`
- `yagni:` `SleepPredictionState.toSnapshot` default args exist only for tests — both prod callers pass
  all three. Drop the defaults. `[sharing/domain/model/DomainToSnapshot.kt:87-91]`

**Acceptance:** builds, detekt/ktlint pass, tests pass; no behavior change.

---

### Notes / explicitly NOT worth doing (from the same audit)

- `FirestoreSnapshotMapping` per-type `*ToMap`/`mapTo*` blocks look repetitive but each has a distinct
  field set; table-driving them needs reflection/annotations — strictly more complex. Keep.
- `ShareCode` is a `@JvmInline value class` (zero-cost), validated, used across ~75 files — real type
  safety, not a pointless wrapper. Keep.
- `Merged*History` two-field results are both read (`entries` + `pendingOpIds` drive
  `hasConsumedPendingOps`) — not single-value wrappers. Keep.
- `DebugPartnerSnapshotBuilder` is R8-stripped from release (guarded by `BuildConfig.DEBUG` via
  `Lazy<>`) and is load-bearing for the debug offline-partner path — not dead.
- `FirestoreSharingService` sleep-op top-level extension functions exist to dodge detekt
  `TooManyFunctions`; moving them back as members re-trips the rule for ~0 line gain. Leave unless the
  ceiling is raised.
- `syncBottleFeedsAndInventory` triples the API surface for one caller but halves Firestore
  round-trips (perf) — out of scope.
