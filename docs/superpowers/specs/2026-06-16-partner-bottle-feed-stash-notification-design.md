# Partner Bottle-Feed → Stash-Consumption Notification — Design

**Date:** 2026-06-16
**Status:** Draft (awaiting review)
**Linear project:** Collaborative Partner View

## Problem

When the connected **partner** logs a bottle feed that draws from the milk stash
(i.e. the feed is linked to a stored milk bag), the **main partner** (the device in
`AppMode.PRIMARY`) has no signal that stash milk was used. They only find out by
opening the inventory screen. Parents managing a frozen/refrigerated stash need to
know when it is being drawn down so they can plan pumping and restocking.

## Goal

When a partner-authored bottle feed that **consumes a stash bag** is applied on the
primary device, post a local notification telling the main partner how much milk was
used from the stash.

### Non-goals

- **No background/push delivery.** The app has no FCM. Partner feed ops reach the
  primary only through the Firestore snapshot listener in `ProcessFeedOpsUseCase`,
  which runs while `MainActivity` is `STARTED` (see `MainActivity.onCreate`). The
  notification therefore fires when the primary device has the app open or reopens
  it and the queued ops apply. Adding remote push is explicitly out of scope and
  would violate the CLAUDE.md "no new remote integrations" rule.
- **No notification for partner feeds that do NOT consume stash** (formula / unlinked
  bottles). Per the goal: only "if it was consumed".
- **No remaining-stash figure in the text** (deferred). Content is volume-consumed
  only, per product decision.

## Trigger & constraints

- **Fires only on the primary** (`AppMode.PRIMARY`). `ProcessFeedOpsUseCase` already
  filters to primary, so this is inherited for free.
- **Fires only for a freshly applied partner `CREATE` op with a non-null
  `consumedBagId`.** Idempotent re-applies (crash/retry replays) must NOT re-notify.
- **Coalesced per processed batch:** if several consuming feeds apply together (e.g.
  primary reopens after the partner logged 3 bottles), post a single summary, not one
  per feed.
- **Gated by a user setting** (default ON).

## Architecture

Data flow on the primary:

```
Firestore feed ops ──> ProcessFeedOpsUseCase.processBatchWithRetry
                          │  for each op: ApplyFeedOpUseCase(op) -> FeedOpApplyResult
                          │     (roomChanged, consumedBagId?)
                          │  accumulate consumedBagId per entryClientId (dedup across retries)
                          │  on batch success (after delete):
                          ▼
                    PartnerFeedNotifier.notifyStashConsumed(consumedBagIds)
                          │  gate on setting toggle
                          │  resolve each bag volume via InventoryRepository.getById
                          │  sum volumes, count feeds
                          ▼
                    NotificationHelper.showPartnerStashConsumed(context, count, totalMl)
```

### Components

**1. `ApplyFeedOpUseCase` return type change**
Currently returns `Boolean` (room changed). Change to a small outcome data class so
the caller can tell *what* happened:

```kotlin
data class FeedOpApplyResult(
    val roomChanged: Boolean,
    // Non-null only for a FRESHLY created partner feed that consumed a stash bag.
    // Null for re-applies, updates, deletes, and creates without a linked bag.
    val consumedBagId: Long? = null,
)
```

- `applyCreate`, `existing == null` branch with `op.consumedBagId != null` →
  `FeedOpApplyResult(roomChanged = true, consumedBagId = op.consumedBagId)`.
- All other branches → `FeedOpApplyResult(roomChanged = <existing bool>, consumedBagId = null)`.

> This is a plain domain outcome struct, **not** a `sealed class Result<T>` wrapper —
> the CLAUDE.md ban is on generic result wrappers, not on a use case returning a
> descriptive value. It keeps the consumption signal at its source of truth (inside
> `applyCreate`), which is the only place that can distinguish a fresh insert from a
> replay.

**2. `ProcessFeedOpsUseCase` changes**
- Inject `private val partnerFeedNotifier: PartnerFeedNotifier`.
- In `processBatchWithRetry`, declare `val consumed = linkedMapOf<String, Long>()`
  **before** the `repeat` loop so it survives retries.
- Update the apply loop:
  ```kotlin
  sortedOps.forEach { op ->
      val result = applyFeedOp(op)
      if (result.roomChanged) syncPending = true
      result.consumedBagId?.let { consumed[op.entryClientId] = it }
  }
  ```
  Keying by `entryClientId` dedups across retry attempts (re-applies yield
  `consumedBagId = null`, so each consuming entry is recorded exactly once).
- After the existing `deleteFeedOps` call, fire the notification inside its own
  `runCatching` so a notifier failure never re-triggers the batch:
  ```kotlin
  runCatching { partnerFeedNotifier.notifyStashConsumed(consumed.values.toList()) }
      .onFailure { Log.w(TAG, "stash-consumed notification failed", it) }
  ```

**3. `PartnerFeedNotifier` (new interface, `manager/`)**
Mirrors the existing `NotificationScheduler` interface placement.

```kotlin
interface PartnerFeedNotifier {
    suspend fun notifyStashConsumed(consumedBagIds: List<Long>)
}
```

**4. `PartnerFeedNotificationManager` (new impl, `manager/`)**
`@Singleton`, injects `@ApplicationContext context`, `InventoryRepository`,
`SettingsRepository`.

```kotlin
override suspend fun notifyStashConsumed(consumedBagIds: List<Long>) {
    if (consumedBagIds.isEmpty()) return
    if (!settingsRepository.getPartnerFeedStashNotificationsEnabled().first()) return
    val totalMl = consumedBagIds.sumOf { id -> inventoryRepository.getById(id)?.volumeMl ?: 0 }
    if (totalMl <= 0) return // bag(s) gone / zero — nothing meaningful to report
    NotificationHelper.showPartnerStashConsumed(context, consumedBagIds.size, totalMl)
}
```

Bound in `NotificationSchedulerModule` with `@Binds @Singleton`.

**5. `SettingsRepository` toggle (new)**
Follow the existing rich-notifications pattern:
```kotlin
fun getPartnerFeedStashNotificationsEnabled(): Flow<Boolean>   // default true
suspend fun setPartnerFeedStashNotificationsEnabled(enabled: Boolean)
```
Add the DataStore key + read/write in `SettingsRepositoryImpl`.

**6. `NotificationHelper.showPartnerStashConsumed(context, feedCount, totalMl)` (new)**
- New channel: `PARTNER_STASH_CHANNEL_ID = "partner_stash_notifications"`,
  `IMPORTANCE_DEFAULT`, created via `createPartnerStashNotificationChannel(context)`.
- New notification id: `PARTNER_STASH_NOTIFICATION_ID = 1010` (1009 is the highest
  in use).
- Content (volume-only, per decision):
  - `feedCount == 1` → title "Bottle logged by partner", body
    "Partner used %1$d ml from the stash".
  - `feedCount > 1` → title via plural "Partner logged %1$d bottles", body
    "Used %1$d ml from the stash".
  - Use a `plurals` resource keyed on `feedCount` for the title.
- Tap → `MainActivity` with `EXTRA_NAV_ROUTE = Routes.INVENTORY` (same pattern as
  `showStashExpiration`). New request code `RC_PARTNER_STASH_TAP = 3004`.
- Accent: `Pink700` / `PrimaryPinkDark` (feeding family). Small icon: reuse
  `R.drawable.ic_notif_breastfeeding` unless a bottle drawable exists at impl time.
- `setAutoCancel(true)`, not ongoing, `CATEGORY_REMINDER`, `applyDesignSystem(...)`.

**7. Channel registration**
Call `NotificationHelper.createPartnerStashNotificationChannel(context)` in
`BabyTrackerApp.onCreate` alongside the existing channel creations.

**8. Settings UI**
Add a toggle row in the notifications area of `ui/settings/SettingsScreen.kt`, wired
through `SettingsViewModel` (expose state from
`getPartnerFeedStashNotificationsEnabled`, add a handler calling the setter). Place it
near the existing rich-notifications toggle.

**9. Strings (`res/values/strings.xml`)**
- `notif_channel_partner_stash_name`, `notif_channel_partner_stash_description`
- `notif_title_partner_stash_single`, `notif_body_partner_stash_single`
- `plurals/notif_title_partner_stash` (title with count)
- `notif_body_partner_stash_multi`
- Settings: `settings_partner_stash_notif_title`, `settings_partner_stash_notif_subtitle`

## Edge cases

| Case | Behavior |
|------|----------|
| Partner feed with no linked bag | `consumedBagId == null` → no notification |
| Idempotent replay after crash/retry | re-apply returns `consumedBagId = null` → no duplicate notification |
| Several consuming feeds in one batch | single coalesced notification (count + summed ml) |
| Toggle off | manager returns early, nothing posted |
| Bag row already deleted when volume resolved | `getById` null → contributes 0 ml; if total ends at 0, suppress |
| App in PARTNER mode | `ProcessFeedOpsUseCase` never runs the apply path → no notification (correct) |
| Notifier throws | swallowed via `runCatching`; op batch still completes |

## Testing

- **`ApplyFeedOpUseCaseTest`** (update): assert fresh CREATE w/ `consumedBagId`
  returns `FeedOpApplyResult(true, bagId)`; re-apply / update / delete / create-without-
  bag return `consumedBagId = null`. Update existing `Boolean` assertions to
  `.roomChanged`.
- **`ProcessFeedOpsUseCaseTest`** (update): mock `PartnerFeedNotifier`;
  `coVerify` it is called once with the consumed bag ids on batch success; not called
  when no op consumes a bag; called once (deduped) across a forced retry.
- **`PartnerFeedNotificationManagerTest`** (new): toggle off → no post and no inventory
  lookup beyond gate; empty list → no-op; resolves volumes and posts with correct
  `count`/`totalMl` (Robolectric `shadowOf(notificationManager)` to assert the posted
  notification, consistent with available tooling).
- **`SettingsRepositoryImplTest`** (update if present): default true; set persists.

## Files touched

- `sharing/usecase/ApplyFeedOpUseCase.kt` (return type)
- `sharing/usecase/ProcessFeedOpsUseCase.kt` (accumulate + notify)
- `manager/PartnerFeedNotifier.kt` (new interface)
- `manager/PartnerFeedNotificationManager.kt` (new impl)
- `di/NotificationSchedulerModule.kt` (bind)
- `domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt` (toggle)
- `util/NotificationHelper.kt` (channel + show fn + ids)
- `BabyTrackerApp.kt` (channel registration)
- `ui/settings/SettingsScreen.kt` + `ui/settings/SettingsViewModel.kt` (toggle UI)
- `res/values/strings.xml` (channel, notif, settings strings + plural)
- Tests as listed above

## Delivery

Single Linear issue in **Collaborative Partner View**, single feature branch, single
PR. Implementation via the `develop-linear-issue` workflow (branch → build → adversarial
review before each commit → In Review).
```
