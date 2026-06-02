# Milk Stash Expiration — Design Spec

**Date:** 2026-06-02  
**Feature:** Milk stash expiration tracking with settings, visual indicators, and daily notifications

---

## Overview

Allow users to configure a general expiration period for milk stash items. When enabled, bags approaching or past their expiration date are visually flagged in the inventory list. An optional daily notification informs the user how many bags (and how many mL) are expiring that day.

---

## User-Facing Behaviour

### Master toggle
The feature is opt-in. Until the user enables it in Milk Stash Settings, no expiration logic runs: no colors change, no notifications fire, no validation occurs.

### Visual states on bag cards (when feature enabled)

| State | Condition | Card appearance |
|-------|-----------|-----------------|
| `NONE` | > 1 day until expiration | Unchanged (surface color) |
| `EXPIRING_SOON` | Exactly 1 day until expiration (tomorrow) | Light amber tint (`WarningContainerAmber @ 45% alpha`) |
| `EXPIRING_OR_EXPIRED` | Expires today or already past expiration date | Full amber container (`WarningContainerAmber`) |

Color tokens used: `WarningContainerAmber`, `OnWarningContainerAmber` (top-level vals in `ui/theme/Color.kt` — not via `MaterialTheme.colorScheme`).

### Daily notification (when notif enabled)
Fires once daily at the user-configured time (default 08:00). Body: *"N bags (X mL) are expiring today"*. Only fires if at least one bag qualifies (`collectionDate + expirationDays ≤ today`). Tapping navigates to the Inventory screen.

---

## Settings Screen

Route: `inventory/settings`  
Navigation: gear icon (`Icons.Default.Settings`) in `InventoryScreen` TopAppBar actions.

### Fields

```
[Switch]  Expiration tracking          ← master toggle (default off)

  (shown when enabled)
  Expires after: [____] days           ← integer field, default 4, min 1

  [Switch]  Notify when expiring       ← notif toggle (default off)

    (shown when notif enabled)
    Notify at: [08:00 AM]              ← time picker dialog
```

---

## Architecture

### New files

| File | Purpose |
|------|---------|
| `domain/repository/InventorySettingsRepository.kt` | Interface: 4 get Flows + 4 set suspends |
| `data/repository/InventorySettingsRepositoryImpl.kt` | DataStore impl, keys prefixed `stash_` |
| `domain/model/ExpirationStatus.kt` | Enum: `NONE`, `EXPIRING_SOON`, `EXPIRING_OR_EXPIRED` |
| `domain/model/MilkBagWithExpiration.kt` | `data class(val bag: MilkBag, val status: ExpirationStatus)` |
| `domain/usecase/inventory/ObserveInventoryWithExpirationUseCase.kt` | Combines bags Flow + settings Flow → `Flow<List<MilkBagWithExpiration>>` |
| `manager/StashExpirationScheduler.kt` | Interface: `scheduleDaily(minuteOfDay)` + `cancel()` |
| `manager/StashExpirationNotificationManager.kt` | AlarmManager impl; reschedules on settings save |
| `receiver/StashExpirationReceiver.kt` | `@AndroidEntryPoint` BroadcastReceiver; goAsync + coroutine; guards on both toggles; queries bags, fires notification |
| `receiver/StashExpirationBootReceiver.kt` | `@AndroidEntryPoint` BroadcastReceiver; handles `BOOT_COMPLETED` + `TIME_SET`; restores daily alarm if both toggles enabled |
| `ui/inventory/InventorySettingsScreen.kt` | Settings UI composable |
| `ui/inventory/InventorySettingsViewModel.kt` | Injects `InventorySettingsRepository` directly (no use-case wrapper — pure CRUD) |
| `di/InventorySettingsModule.kt` | `@Binds` repo + scheduler |

### Modified files

| File | Change |
|------|--------|
| `navigation/Routes.kt` | Add `INVENTORY_SETTINGS = "inventory/settings"` |
| `navigation/AppNavGraph.kt` | Add `InventorySettingsScreen` composable destination |
| `ui/inventory/InventoryScreen.kt` | Add gear icon to TopAppBar; update `MilkBagRow` to accept `ExpirationStatus`; add `onNavigateToSettings` param |
| `ui/inventory/InventoryViewModel.kt` | Swap `GetInventoryUseCase` → `ObserveInventoryWithExpirationUseCase`; change `bags` type to `List<MilkBagWithExpiration>` |
| `util/NotificationHelper.kt` | Add `STASH_EXPIRATION_CHANNEL_ID`, `STASH_EXPIRATION_NOTIFICATION_ID = 1009`, `showStashExpiration(context, count, totalMl)` |
| `BabyTrackerApp.kt` | Register new `stash_expiration_notifications` channel |
| `AndroidManifest.xml` | Register `StashExpirationReceiver` + `StashExpirationBootReceiver`; add `RECEIVE_BOOT_COMPLETED` permission |

---

## Domain Logic

### `ObserveInventoryWithExpirationUseCase`

```
combine(getActiveBags(), getExpirationEnabled(), getExpirationDays()) { bags, enabled, days ->
    if (!enabled) return bags.map { MilkBagWithExpiration(it, NONE) }
    val today = LocalDate.now()
    bags.map { bag ->
        val expiryDate = bag.collectionDate.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(days.toLong())
        val status = when {
            expiryDate.isAfter(today.plusDays(1)) -> NONE
            expiryDate.isAfter(today)              -> EXPIRING_SOON   // tomorrow
            else                                   -> EXPIRING_OR_EXPIRED
        }
        MilkBagWithExpiration(bag, status)
    }
}
```

### `StashExpirationReceiver` query

On receive, read `getExpirationEnabled().first()` **and** `getExpirationNotifEnabled().first()` — bail if either is false. Then filter `getAllBagsOnce()` (active bags only) for bags where `expiryDate ≤ today`. Notification fires only when count > 0.

### Master toggle cancellation

When the user disables the master expiration toggle (`setExpirationEnabled(false)`), `InventorySettingsViewModel` must also call `StashExpirationScheduler.cancel()` to remove any scheduled alarm immediately — do not rely solely on `StashExpirationReceiver`'s runtime guard.

### Boot resilience

`AlarmManager` alarms do not survive device reboot. A dedicated `StashExpirationBootReceiver` listens for `android.intent.action.BOOT_COMPLETED` (and `android.intent.action.TIME_SET` to handle clock corrections). On trigger, it reads both `getExpirationEnabled()` and `getExpirationNotifEnabled()` — only if both are true does it call `StashExpirationScheduler.scheduleDaily(timeMinuteOfDay)` to restore the alarm.

---

## Notification

| Property | Value |
|----------|-------|
| Channel | `stash_expiration_notifications` (IMPORTANCE_DEFAULT) |
| ID | `1009` |
| Accent | `WarningAmber` / `WarningAmberDark` |
| Icon | `ic_notif_limit` (existing warning icon) |
| Body | *"N bags (X mL) are expiring today"* |
| Tap action | Opens `InventoryScreen` via `EXTRA_NAV_ROUTE` |
| Scheduling | `AlarmManager.setRepeating` (inexact daily) at user-set `minuteOfDay` |
| Re-schedule trigger | VM saves notif settings → calls `StashExpirationScheduler.scheduleDaily(time)` |
| Cancel trigger | User disables notif toggle **or** master toggle → VM calls `StashExpirationScheduler.cancel()` |
| Boot recovery | `StashExpirationBootReceiver` on `BOOT_COMPLETED` / `TIME_SET` → reschedules if both toggles enabled |

---

## DataStore Keys

| Key | Type | Default |
|-----|------|---------|
| `stash_expiration_enabled` | Boolean | `false` |
| `stash_expiration_days` | Int | `4` |
| `stash_expiration_notif_enabled` | Boolean | `false` |
| `stash_expiration_notif_time_minutes` | Int | `480` (08:00) |

---

## Non-Goals

- No per-bag custom expiration date (all bags share the global setting)
- No expiration tracking for used bags (`usedAt != null`)
- No "expiring in N days" configurable warning window — always exactly 1 day prior
- No notification history or snooze
