# Stash Expiration Notification Scheduler + Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-79

**Goal:** Add the AlarmManager-based daily scheduler (`StashExpirationScheduler` interface + `StashExpirationNotificationManager` impl), the Hilt binding, and the `NotificationHelper` channel + builder for the stash-expiration notification.

**Architecture:** A **one-shot, self-rescheduling** inexact alarm fires a broadcast that `StashExpirationReceiver` (AKA-80) handles. `scheduleDaily(minute)` computes the **next local occurrence** of the configured minute-of-day from the current `ZoneId` and arms a single `setAndAllowWhileIdle` alarm; after the receiver fires it calls `scheduleDaily` again for the next day (AKA-80), and the boot/`TIME_SET` receiver re-arms it on reboot or clock/timezone change (AKA-83). This recompute-each-time design avoids the DST/timezone drift that a fixed `setRepeating(INTERVAL_DAY)` interval would accumulate (a 24h fixed interval ignores wall-clock, so a reminder set for 08:00 would fire at 07:00 or 09:00 after a DST transition until rescheduled). The manager targets the receiver by **fully-qualified class-name string** via `Intent.setClassName(...)`, deliberately avoiding a compile-time import of `StashExpirationReceiver` — this breaks the otherwise-circular dependency (manager → receiver class, receiver → `NotificationHelper.showStashExpiration`) so this plan (AKA-79) can land before AKA-80. The receiver is declared in the manifest by AKA-80, so it is never stripped.

**Tech Stack:** Kotlin, Android `AlarmManager` (`setAndAllowWhileIdle`, `RTC_WAKEUP`), `PendingIntent`, `NotificationCompat`, Hilt, `java.time`.

**Dependencies:** AKA-75 (`InventorySettingsModule` exists — the scheduler `@Binds` is added to it). Does **not** depend on AKA-80 at compile time (string class-name targeting).

**Blocks:** AKA-80 (receiver calls `NotificationHelper.showStashExpiration` **and** must re-arm the next alarm via `StashExpirationScheduler.scheduleDaily` after firing — this one-shot design depends on it), AKA-83 (boot/`TIME_SET` receiver calls `StashExpirationScheduler.scheduleDaily`).

**Suggested implementation branch:** `feat/aka-79-notification`

---

## Integration / Merge-Ordering Constraint (required)

This plan introduces a one-shot, self-rescheduling alarm. A one-shot alarm with no receiver to re-arm it, or no boot/`TIME_SET` recovery, would silently drop reminders. That failure mode is avoided by **call-site sequencing**, not by shipping a callable-but-undeliverable scheduler:

- **AKA-79 wires no caller.** This PR adds the interface, impl, binding, channel, and `showStashExpiration`, but **nothing in the codebase calls `scheduleDaily()` after this PR**. The scheduler is injectable but dormant.
- The **only** call sites are introduced later: `StashExpirationReceiver` re-arm (AKA-80), boot/`TIME_SET` re-arm (AKA-83), and the settings ViewModel (AKA-77) when the user enables/changes the notification.
- **Mandatory merge order:** `AKA-79 → AKA-80 → AKA-83 → AKA-77`. By the time any code path can call `scheduleDaily`, the receiver is registered in the manifest (AKA-80) and the boot/`TIME_SET` re-arm exists (AKA-83). No interim merged state exists where the alarm can be armed without a delivery target or recovery path.
- A reviewer can confirm the dormancy: grep the tree after AKA-79 for `scheduleDaily(` — the only reference is the interface/impl declaration, with **zero callers**.

### Recurrence-durability contract (binds AKA-80)

A one-shot alarm is consumed on delivery, so recurrence depends entirely on the receiver re-arming. A single receiver-side failure must **not** permanently stop reminders. AKA-80 (and the wider feature) **must** satisfy, and have acceptance tests for:

1. **Re-arm in a guaranteed `finally`.** `StashExpirationReceiver` must call `scheduleDaily(nextTime)` for the next day inside a `finally` block (within `goAsync` + `withTimeout`), so the re-arm runs even if reading settings, querying bags, or posting the notification throws. Re-arming is independent of whether a notification was actually posted (count could be 0 today — tomorrow still needs an alarm).
2. **Startup reconciliation (recovery path).** A dropped one-shot (process killed between fires, alarm lost, exact-alarm downgrade) must self-heal without waiting for a reboot. AKA-80 adds a startup reconciliation in `BabyTrackerApp.onCreate()`: if `getExpirationEnabled()` **and** `getExpirationNotifEnabled()` are both true, call `scheduleDaily(getExpirationNotifTimeMinutes())`. Re-arming an already-armed one-shot is idempotent (same `PendingIntent`, `FLAG_UPDATE_CURRENT`). This is a legitimate caller because AKA-80 lands the receiver; it does not violate AKA-79's dormancy (which describes the AKA-79 PR in isolation).
3. **Boot/clock recovery.** AKA-83 re-arms on `BOOT_COMPLETED` / `TIME_SET`.

Together: per-fire `finally` re-arm + app-start reconciliation + boot re-arm means no single failure permanently drops the reminder.

---

## File Structure

- Create: `app/src/main/java/com/babytracker/manager/StashExpirationScheduler.kt` — interface
- Create: `app/src/main/java/com/babytracker/manager/StashExpirationNotificationManager.kt` — `@Singleton` AlarmManager impl
- Modify: `app/src/main/java/com/babytracker/di/InventorySettingsModule.kt` — add `@Binds` for the scheduler
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt` — channel id, notification id, channel creator, `showStashExpiration`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt` — create the channel at startup
- Modify: `app/src/main/res/values/strings.xml` — channel + notification strings

Pattern sources: `manager/NapReminderManager.kt` (AlarmManager scheduler + `@Singleton` + `PendingIntent` build/cancel), `manager/SleepNotificationScheduler.kt` (interface shape), `util/NotificationHelper.kt` (`createSleepNotificationChannel`, `showNapReminder`, `resolveAccent`, `applyDesignSystem`, `EXTRA_NAV_ROUTE`, `mainActivity`-style tap intent), `di/NotificationSchedulerModule.kt` (`@Binds` for a scheduler).

---

## Task 1: StashExpirationScheduler interface

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/StashExpirationScheduler.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.babytracker.manager

/**
 * Schedules / cancels the daily milk-stash expiration notification alarm.
 */
interface StashExpirationScheduler {
    /** Schedule (or reschedule) the daily alarm at [timeMinuteOfDay] (0..1439, minute-of-day). */
    fun scheduleDaily(timeMinuteOfDay: Int)

    /** Cancel any scheduled daily alarm. */
    fun cancel()
}
```

- [ ] **Step 2: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 2: StashExpirationNotificationManager (AlarmManager impl)

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/StashExpirationNotificationManager.kt`

Design notes:
- **One-shot, self-rescheduling, inexact** — use `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, triggerAtMs, pi)`. It is inexact (Doze-tolerant) and requires **no** `SCHEDULE_EXACT_ALARM` permission. A single occurrence is armed each time; the receiver (AKA-80) re-arms the next one after it fires, and boot/`TIME_SET` (AKA-83) re-arms after reboot or clock change. Do **not** use `setRepeating(INTERVAL_DAY)` — a fixed 24h interval drifts off the configured wall-clock time across DST/timezone changes.
- **Trigger** = next occurrence of `timeMinuteOfDay` in the **current** system zone: today at that minute, or tomorrow if that instant has already passed. Recomputed from `ZoneId.systemDefault()` on every `scheduleDaily` call, so each re-arm self-corrects for the active timezone/standard-vs-DST offset.
- **Ambiguous/skipped local times (DST edge):** `LocalDate.atTime(time).atZone(zone)` resolves a gap time forward and an overlap (fall-back) time to the **first** offset. On the rare fall-back transition day, a reminder configured inside the repeated hour may resolve to the earlier instant and, if that has passed, schedule for the next day — i.e. it can be up to ~1h early or skip that single day. This is **accepted**: the alarm is already inexact (`setAndAllowWhileIdle` is Doze-batched, never exact-to-the-minute), and a daily milk-expiration reminder does not warrant `ZoneRules.getValidOffsets` precision. Do not add overlap-resolution logic — it is disproportionate to the feature.
- **Receiver targeting without a compile import:** build the `Intent` with `setClassName(context.packageName, STASH_RECEIVER_CLASS)` where `STASH_RECEIVER_CLASS = "com.babytracker.receiver.StashExpirationReceiver"`. This is the deliberate cycle-breaker (see Architecture).
- **Cancel** rebuilds the identical `PendingIntent` (same request code + matching intent) and calls `alarmManager.cancel(pi)` — mirrors `NapReminderManager.cancel()`.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StashExpirationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : StashExpirationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleDaily(timeMinuteOfDay: Int) {
        val minute = timeMinuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY)
        val triggerAtMs = nextTriggerEpochMs(minute)
        // One-shot inexact alarm; recomputed and re-armed by the receiver after each fire
        // and by the boot/TIME_SET receiver, so wall-clock time is preserved across DST.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, buildPendingIntent())
        Log.d(TAG, "Scheduled next stash expiration alarm at minute=$minute (triggerAt=$triggerAtMs)")
    }

    override fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        Log.d(TAG, "Cancelled stash expiration alarm")
    }

    private fun nextTriggerEpochMs(minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val todayTrigger = LocalDate.now(zone).atTime(time).atZone(zone)
        val trigger = if (todayTrigger.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
            todayTrigger.plusDays(1)
        } else {
            todayTrigger
        }
        return trigger.toInstant().toEpochMilli()
    }

    private fun buildPendingIntent(): PendingIntent {
        // Target the receiver by class-name string to avoid a compile-time dependency on
        // StashExpirationReceiver (created in AKA-80) — breaks the manager<->receiver cycle.
        // The receiver is declared in AndroidManifest, so it is never stripped.
        val intent = Intent().setClassName(context.packageName, STASH_RECEIVER_CLASS)
        return PendingIntent.getBroadcast(
            context,
            RC_STASH_EXPIRATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val RC_STASH_EXPIRATION = 4001
        const val MAX_MINUTE_OF_DAY = 1439
        private const val STASH_RECEIVER_CLASS = "com.babytracker.receiver.StashExpirationReceiver"
        private const val TAG = "StashExpirationManager"
    }
}
```

- [ ] **Step 2: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 3: Bind the scheduler in InventorySettingsModule

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/InventorySettingsModule.kt`

- [ ] **Step 1: Add the binding**

Add the import and a second `@Binds` to the existing `InventorySettingsModule` (created in AKA-75):

```kotlin
import com.babytracker.manager.StashExpirationNotificationManager
import com.babytracker.manager.StashExpirationScheduler
```

Inside the `abstract class InventorySettingsModule` body, add:

```kotlin
    @Binds
    @Singleton
    abstract fun bindStashExpirationScheduler(
        impl: StashExpirationNotificationManager,
    ): StashExpirationScheduler
```

- [ ] **Step 2: Verify Hilt graph builds** — `./gradlew :app:compileDebugKotlin`

---

## Task 4: NotificationHelper channel + builder

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`

The notification reuses the existing warning visual language: accent `WarningAmber` / `WarningAmberDark` (already imported in `NotificationHelper`), small icon `R.drawable.ic_notif_limit`, channel `IMPORTANCE_DEFAULT`. Tap opens `InventoryScreen` via `EXTRA_NAV_ROUTE = Routes.INVENTORY` (mirrors `napReminderTapPendingIntent`, which uses `Routes.SLEEP_TRACKING`).

- [ ] **Step 1: Add constants**

Inside `object NotificationHelper`, add near the other channel/id constants:

```kotlin
    const val STASH_EXPIRATION_CHANNEL_ID = "stash_expiration_notifications"
    const val STASH_EXPIRATION_NOTIFICATION_ID = 1009
```

And a private request-code constant near the other `RC_*` vals:

```kotlin
    private const val RC_STASH_EXPIRATION_TAP = 3003
```

> 1009 is free (existing ids: 1001–1007, plus `PREDICTIVE_FEED_NOTIFICATION_ID = 1006`). `3003` is free (existing tap RCs: `RC_NAP_REMINDER_TAP = 3002`).

- [ ] **Step 2: Add the channel creator**

```kotlin
    fun createStashExpirationNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STASH_EXPIRATION_CHANNEL_ID,
                context.getString(R.string.notif_channel_stash_expiration_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_stash_expiration_description) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $STASH_EXPIRATION_CHANNEL_ID")
        }
    }
```

- [ ] **Step 3: Add the tap PendingIntent helper**

```kotlin
    private fun stashExpirationTapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_STASH_EXPIRATION_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_NAV_ROUTE, Routes.INVENTORY)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
```

- [ ] **Step 4: Add the show function**

```kotlin
    fun showStashExpiration(context: Context, count: Int, totalMl: Int) {
        val title = context.getString(R.string.notif_title_stash_expiration)
        val body = context.resources.getQuantityString(
            R.plurals.notif_body_stash_expiration, count, count, totalMl,
        )
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val notification = NotificationCompat.Builder(context, STASH_EXPIRATION_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(stashExpirationTapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(STASH_EXPIRATION_NOTIFICATION_ID, notification)
        Log.d(TAG, "showStashExpiration posted (count=$count, totalMl=$totalMl)")
    }
```

- [ ] **Step 5: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 5: String resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings + plural**

The body uses a **plural** so "1 bag" vs "N bags" reads correctly. Two positional args: `%1$d` = count, `%2$d` = total mL.

```xml
<string name="notif_channel_stash_expiration_name">Stash expiration</string>
<string name="notif_channel_stash_expiration_description">Daily reminder when milk bags are expiring</string>
<string name="notif_title_stash_expiration">Milk expiring today</string>
<plurals name="notif_body_stash_expiration">
    <item quantity="one">%1$d bag (%2$d mL) is expiring today</item>
    <item quantity="other">%1$d bags (%2$d mL) are expiring today</item>
</plurals>
```

> If `strings.xml` already defines these by name, reuse them — do not duplicate. Match the file's existing indentation.

- [ ] **Step 2: Verify resources compile** — `./gradlew :app:compileDebugKotlin` (R class regenerates; missing-resource refs in `NotificationHelper` would fail here)

---

## Task 6: Register the channel at startup

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

- [ ] **Step 1: Add the channel creation call**

In `onCreate()`, alongside the other `NotificationHelper.create*Channel(this)` calls:

```kotlin
        NotificationHelper.createStashExpirationNotificationChannel(this)
```

- [ ] **Step 2: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 7: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Build the app** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/StashExpirationScheduler.kt \
        app/src/main/java/com/babytracker/manager/StashExpirationNotificationManager.kt \
        app/src/main/java/com/babytracker/di/InventorySettingsModule.kt \
        app/src/main/java/com/babytracker/util/NotificationHelper.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(notification): add stash expiration daily scheduler and notification [AKA-79]"
```

---

## Acceptance Criteria

- `StashExpirationNotificationManager` is `@Singleton`, bound to `StashExpirationScheduler` in `InventorySettingsModule`.
- `scheduleDaily` arms a **one-shot inexact** `setAndAllowWhileIdle` alarm at the next local occurrence of the minute-of-day, recomputed from the current `ZoneId` each call; no exact-alarm permission required. It is **not** a fixed-interval `setRepeating` — re-arming is the receiver's job (AKA-80) so wall-clock time is preserved across DST/timezone changes.
- The alarm `PendingIntent` targets `com.babytracker.receiver.StashExpirationReceiver` via `setClassName` — **no compile-time import** of the receiver (cycle broken; AKA-79 builds before AKA-80 exists).
- `cancel()` cancels the same alarm.
- `NotificationHelper.showStashExpiration(context, count, totalMl)` posts to channel `stash_expiration_notifications` (id `1009`), accent amber, icon `ic_notif_limit`, tapping routes to `Routes.INVENTORY`.
- Body text pluralizes ("1 bag" vs "N bags").
- Channel is created in `BabyTrackerApp.onCreate()`.
- **Dormant on landing:** after this PR, `grep -rn "scheduleDaily(" app/src/main` shows only the interface + impl declaration and **no callers** — the scheduler cannot be activated until AKA-80/AKA-83/AKA-77 land. Merge order `AKA-79 → AKA-80 → AKA-83 → AKA-77` is mandatory.
- **Recurrence durability (contract for AKA-80):** a single receiver-side failure cannot permanently stop reminders. AKA-80 must re-arm in a guaranteed `finally`, add `BabyTrackerApp` startup reconciliation, and (AKA-83) re-arm on boot/`TIME_SET`. See "Recurrence-durability contract" above.
- **DST/timezone:** because the trigger is recomputed from `ZoneId.systemDefault()` on every `scheduleDaily` (called after each fire by AKA-80 and on boot/`TIME_SET` by AKA-83), a reminder set for e.g. 08:00 keeps firing at ~08:00 local on ordinary days and after DST transitions — it does not accumulate the fixed-interval drift a `setRepeating(INTERVAL_DAY)` would. The one accepted exception is a reminder time falling inside a fall-back overlap hour on the transition day (may be ~1h early or skip that single day); this is acceptable for an inexact daily reminder and is **not** to be hardened with `ZoneRules` logic.
- `./gradlew :app:assembleDebug` succeeds.
