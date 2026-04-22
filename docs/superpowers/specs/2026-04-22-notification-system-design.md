# Notification System — Design Spec

**Date:** 2026-04-22  
**Status:** Approved  
**Reference design:** `design-System-handoff/akachan-design-system/project/preview/notifications.html`

---

## 1. Overview

Upgrade the notification system from its current partial, breastfeeding-only, static implementation to a complete, design-compliant system covering both breastfeeding and sleep tracking. All notification action buttons must work when the app process is killed.

### Notification types

| Type | Trigger | Domain |
|---|---|---|
| Switch Sides | AlarmManager fires at `maxPerBreastMinutes` | Breastfeeding |
| Feeding Limit | AlarmManager fires at `maxTotalFeedMinutes` | Breastfeeding |
| Sleep Active | `SleepViewModel.onStartRecord()` | Sleep |

### Rich vs simple

Rich notifications are enabled by default. The user can disable them in Settings to revert to simple (title + body only, tap opens app). The setting applies to all notification types.

---

## 2. Architecture

### Data flow

```
AlarmManager fires
    → BreastfeedingNotificationReceiver   (existing, alarm-only)
        → reads rich_notifications_enabled from DataStore
        → NotificationHelper.showSwitchSide() or showFeedingLimit()
              rich=true  → BigTextStyle + setProgress() + addAction(...)
              rich=false → simple title + body

User taps action button (Switch Now / Stop Session / Dismiss / Keep Going)
    → BreastfeedingActionReceiver          (@AndroidEntryPoint, new)
        → injects BreastfeedingRepository
        → performs action, cancels notification

SleepViewModel.onStartRecord()
    → SleepNotificationScheduler.show()   (new interface + impl)
        → reads rich_notifications_enabled from DataStore
        → NotificationHelper.showSleepActive()
              rich=true  → setUsesChronometer(true) + setWhen(startTime) + addAction(Stop Sleep)
              rich=false → simple title + start time body

User taps "Stop Sleep"
    → SleepActionReceiver                  (@AndroidEntryPoint, new)
        → injects SleepRepository
        → calls StopSleepRecordUseCase, cancels notification

SleepViewModel.onStopRecord()
    → SleepNotificationScheduler.cancel() (belt-and-suspenders alongside the receiver)
```

### Key design decisions

- **`setUsesChronometer(true)` + `setWhen(startTime.toEpochMilli())`** — gives the sleep notification a live elapsed timer rendered natively by the OS; no foreground service or background work needed.
- **Existing `BreastfeedingNotificationReceiver` is not replaced** — it continues handling alarm-fired events. The two new action receivers only handle user taps on notification buttons.
- **`SleepNotificationScheduler` is an interface** — mirrors the existing `NotificationScheduler` pattern so it can be mocked in ViewModel tests.
- **Notifications are swipeable** — `setOngoing(false)` on all types; no foreground service required.
- **Action buttons work when app is killed** — all action receivers are declared in the manifest as `@AndroidEntryPoint` BroadcastReceivers with Hilt repository injection.

---

## 3. Notification Channels

| Channel ID | Name | Importance | Used by |
|---|---|---|---|
| `breastfeeding_notifications` | Breastfeeding Reminders | HIGH | Switch Sides, Feeding Limit |
| `sleep_notifications` | Sleep Reminders | DEFAULT | Sleep Active |

Sleep uses DEFAULT — the session-active notification is informational, not urgent. Breastfeeding stays HIGH because it is time-sensitive.

Both channels are created in `BabyTrackerApp.onCreate()`.

---

## 4. Notification Content

### Switch Sides (rich)

- **Title:** `🍼 Time to switch sides`
- **Body:** `Left breast: 10 min · Switch to the right` (side and elapsed from intent extras)
- **Progress:** `setProgress(maxPerBreast, elapsedMinutes, false)` + subtext `"10 / 15 min"`
- **Actions:** `Switch Now` → `BreastfeedingActionReceiver(ACTION_SWITCH)`, `Dismiss` → `BreastfeedingActionReceiver(ACTION_DISMISS)`

### Feeding Limit (rich)

- **Title:** `⏱ Feeding limit reached`
- **Body:** `Session has reached 20 minutes. Consider wrapping up.` (max from intent extras)
- **Progress:** `setProgress(maxTotal, maxTotal, false)` (always full) + subtext `"20 / 20 min"`
- **Actions:** `Stop Session` → `BreastfeedingActionReceiver(ACTION_STOP)`, `Keep Going` → `BreastfeedingActionReceiver(ACTION_DISMISS)`

### Sleep Active (rich)

- **Title:** `🌙 Sleep session active`
- **Body:** `Night sleep · started at 9:00 PM` (sleep type + formatted start time from intent extras)
- **Chronometer:** `setUsesChronometer(true)` + `setWhen(startTime.toEpochMilli())`
- **`setOngoing(false)`** — swipeable
- **Action:** `Stop Sleep` → `SleepActionReceiver(ACTION_STOP)`

### Simple variants (all types)

Plain `NotificationCompat.Builder` with title + body only. No progress bar, no action buttons. Tapping opens `MainActivity`.

---

## 5. Intent Extras

Dynamic data is bundled into `PendingIntent` extras so receivers can act and render without a database query.

**Breastfeeding alarm intents:**

| Key | Type | Purpose |
|---|---|---|
| `session_id` | `Long` | Identifies the active session |
| `current_side` | `String` | `"LEFT"` or `"RIGHT"` |
| `elapsed_minutes` | `Int` | Minutes elapsed on current side |
| `max_per_breast_minutes` | `Int` | Configured per-breast limit |
| `max_total_minutes` | `Int` | Configured total session limit |

**Sleep notification intent:**

| Key | Type | Purpose |
|---|---|---|
| `session_id` | `Long` | Identifies the active sleep session |
| `sleep_type` | `String` | `"NAP"` or `"NIGHT_SLEEP"` |
| `start_time_epoch_ms` | `Long` | Session start for chronometer `setWhen()` |

---

## 6. Settings Toggle

### New DataStore key

```kotlin
val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
```

Default: `true`. No migration needed — `DataStore<Preferences>` returns the default for missing keys, so existing installs automatically receive rich notifications.

### SettingsRepository changes

```kotlin
// SettingsRepository (interface)
fun getRichNotificationsEnabled(): Flow<Boolean>
suspend fun setRichNotificationsEnabled(enabled: Boolean)
```

### SettingsUiState change

```kotlin
data class SettingsUiState(
    // ... existing fields ...
    val richNotificationsEnabled: Boolean = true
)
```

### Settings screen

New "Notifications" section with one toggle row:

```
Notifications
  [toggle] Rich notifications          ← default ON
           "Show progress bars and quick actions on notifications"
```

Already-posted notifications are not retroactively changed when the toggle is flipped; the new style applies on the next notification fired.

---

## 7. New & Modified Files

### New files

```
receiver/
  BreastfeedingActionReceiver.kt     @AndroidEntryPoint — Switch/Stop/Dismiss/KeepGoing
  SleepActionReceiver.kt             @AndroidEntryPoint — Stop Sleep

manager/
  SleepNotificationScheduler.kt     interface: show(session) / cancel()
  SleepNotificationManager.kt       impl: posts/cancels sleep notification
```

### Modified files

```
receiver/BreastfeedingNotificationReceiver.kt   reads rich pref, passes extras to NotificationHelper
manager/BreastfeedingNotificationManager.kt     bundles dynamic extras in alarm PendingIntents
util/NotificationHelper.kt                      rich + simple variants for all 3 types; sleep channel
di/NotificationSchedulerModule.kt               adds SleepNotificationScheduler binding
domain/repository/SettingsRepository.kt         adds getRichNotificationsEnabled()
data/repository/SettingsRepositoryImpl.kt       implements getRichNotificationsEnabled()
ui/settings/SettingsViewModel.kt                new toggle state + onRichNotificationsToggled()
ui/settings/SettingsScreen.kt                   new Notifications section + toggle row
ui/sleep/SleepViewModel.kt                      calls SleepNotificationScheduler on start/stop
BabyTrackerApp.kt                               creates sleep notification channel
AndroidManifest.xml                             declares BreastfeedingActionReceiver + SleepActionReceiver
```

### AndroidManifest additions

```xml
<receiver android:name=".receiver.BreastfeedingActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.BREASTFEEDING_ACTION" />
    </intent-filter>
</receiver>

<receiver android:name=".receiver.SleepActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.SLEEP_ACTION" />
    </intent-filter>
</receiver>
```

---

## 8. DI

`SleepNotificationScheduler` is provided in `NotificationSchedulerModule` as `@ViewModelScoped` in `ViewModelComponent`, injected into `SleepViewModel` — same scope pattern as the existing `NotificationScheduler`.

---

## 9. Testing Strategy

### New unit test classes

| Class | Coverage |
|---|---|
| `BreastfeedingActionReceiverTest` | Switch → `switchSide()` called; Stop → `stopSession()` called; Dismiss/KeepGoing → cancel notification, repo untouched |
| `SleepActionReceiverTest` | Stop → `StopSleepRecordUseCase` called; notification cancelled |
| `SleepNotificationManagerTest` | `show()` posts to correct channel with chronometer flag; `cancel()` removes notification |
| `SettingsRepositoryImplTest` | `getRichNotificationsEnabled()` returns `true` by default; persists toggle correctly |
| `SettingsViewModelTest` | `onRichNotificationsToggled(false)` updates `uiState.richNotificationsEnabled`; calls repository |

### Existing tests to extend

| Class | Addition |
|---|---|
| `BreastfeedingNotificationManagerTest` | Verify dynamic extras (side, elapsed, max) are bundled in alarm PendingIntents |
| `BreastfeedingNotificationManagerTest` | `cancelAllScheduledNotifications()` regression — still cancels both request codes |
| `BreastfeedingNotificationReceiverConstantsTest` | New action strings `BREASTFEEDING_ACTION` and `SLEEP_ACTION` are unique and non-empty |

### What is not unit-tested

`NotificationHelper` rich/simple rendering — it is pure `NotificationCompat.Builder` API calls with no business logic. Correctness is verified visually on device. Mocking `NotificationManager` to assert builder chains adds noise without catching real bugs.

---

## 10. Acceptance Criteria

- [ ] Switch Sides notification shows current side, elapsed/max progress bar, and Switch Now / Dismiss buttons
- [ ] Feeding Limit notification shows full progress bar, elapsed/max label, Stop Session / Keep Going buttons
- [ ] Sleep Active notification appears when a sleep session starts; shows live chronometer; has Stop Sleep button
- [ ] Sleep Active notification is cancelled when sleep session stops (via button or via app)
- [ ] All action buttons work when the app process is killed
- [ ] Toggling "Rich notifications" off in Settings reverts all subsequent notifications to simple style
- [ ] Rich notifications are on by default; existing installs receive rich notifications without migration
- [ ] All 5 new unit test classes pass
- [ ] All 3 extended test classes pass with no regressions
