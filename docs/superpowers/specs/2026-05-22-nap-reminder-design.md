# Nap Reminder Notification — Design Spec

**Date:** 2026-05-22
**Status:** Approved

---

## Overview

Allow the user to configure a delay (in minutes) after a nap ends. When the delay elapses, a notification fires reminding the user that the next nap window is approaching. The feature is opt-in, configurable from Settings, and respects existing quiet hours.

**Scope:** NAP sleep type only. NIGHT_SLEEP is excluded.

---

## Settings Layer

### New DataStore keys

| Key | Type | Default | Constraint |
|-----|------|---------|------------|
| `nap_reminder_enabled` | Boolean | `false` | — |
| `nap_reminder_delay_minutes` | Int | `60` | clamped `1..480` |

### `SettingsRepository` additions

```kotlin
fun getNapReminderEnabled(): Flow<Boolean>
suspend fun setNapReminderEnabled(enabled: Boolean)
fun getNapReminderDelayMinutes(): Flow<Int>
suspend fun setNapReminderDelayMinutes(minutes: Int)
```

`SettingsRepositoryImpl` implements these using the existing DataStore pattern. `setNapReminderDelayMinutes` clamps the value to `1..480` before writing.

### Settings UI

New section in `SettingsScreen` (under Sleep):
- `Switch` toggle: enables/disables the reminder
- `OutlinedTextField` (KeyboardType.Number): delay in minutes, disabled and dimmed when toggle is off
- Wired through `SettingsViewModel` exposing both values via `StateFlow<SettingsUiState>`

---

## Notification Infrastructure

### `NapReminderScheduler` (interface — `manager/`)

```kotlin
interface NapReminderScheduler {
    fun schedule(napEndTime: Instant, delayMinutes: Int)
    fun cancel()
}
```

### `NapReminderManager` (impl — `manager/`)

- `@Singleton`, `@Inject constructor(@ApplicationContext context: Context)`
- `schedule()`:
  - Computes `triggerAt = napEndTime + delayMinutes`
  - Creates `PendingIntent` for `NapReminderReceiver` with request code `RC_NAP_REMINDER = 3001`
  - Cancels any existing alarm for that request code first
  - Calls `alarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pi)` if exact alarms permitted (SDK S+ check); otherwise `setAndAllowWhileIdle`
- `cancel()`: cancels same PendingIntent via `alarmManager.cancel(pi)`

### `NapReminderReceiver` (`receiver/`)

- `@AndroidEntryPoint BroadcastReceiver`
- `onReceive` → `goAsync()` + `CoroutineScope(Dispatchers.IO + SupervisorJob())` (same pattern as `SleepActionReceiver`)
- Quiet hours check:
  - Reads `quietHoursStartMinute` and `quietHoursEndMinute` from `SettingsRepository`
  - Computes `currentMinuteOfDay = LocalTime.now().hour * 60 + LocalTime.now().minute`
  - Handles wrap-around (start > end means overnight window)
  - If inside quiet window: suppresses silently, returns
- Otherwise: calls `NotificationHelper.showNapReminder(context)`
- Registered in `AndroidManifest.xml`

### `NotificationHelper.showNapReminder()` (new method)

- Channel: `SLEEP_CHANNEL_ID`
- Accent: `Blue700` / `SecondaryBlueDark` (matches sleep theme)
- Notification ID: `NAP_REMINDER_NOTIFICATION_ID = 1007`
- `setAutoCancel(true)`, `setOngoing(false)`
- `Priority: PRIORITY_DEFAULT`, `Category: CATEGORY_REMINDER`
- `contentIntent`: `PendingIntent.getActivity` for `MainActivity` with `Intent` extras `EXTRA_NAV_ROUTE = Routes.SLEEP_TRACKING` so the app opens on the sleep screen
- No action buttons — tap only

### DI

`NotificationSchedulerModule` adds:
```kotlin
@Binds @Singleton
abstract fun bindNapReminderScheduler(impl: NapReminderManager): NapReminderScheduler
```

---

## ViewModel Integration

`SleepViewModel` injects `NapReminderScheduler`.

### `onStopRecord()`

After `stopRecord(session.id)`:
```kotlin
val enabled = settingsRepository.getNapReminderEnabled().first()
if (enabled && session.sleepType == SleepType.NAP) {
    val delayMinutes = settingsRepository.getNapReminderDelayMinutes().first()
    napReminderScheduler.schedule(Instant.now(), delayMinutes)
}
```

`Instant.now()` is used as the end time — `StopSleepRecordUseCase` sets `endTime = Instant.now()` internally so no re-query needed.

### `onStartRecord()`

Before starting the record:
```kotlin
napReminderScheduler.cancel()
```

Always cancels regardless of sleep type — safe no-op if nothing is scheduled. Ensures a pending reminder from a previous nap is cleared when a new sleep session begins.

### `SleepActionReceiver` (no change)

The broadcast-based "End sleep" notification action does **not** schedule a nap reminder. This receiver handles both sleep types and is a quick-dismissal path. The primary scheduling path is through the ViewModel.

---

## Full Flow

```
User taps "Stop Nap" in app
  → SleepViewModel.onStopRecord()
  → StopSleepRecordUseCase  (DB: endTime = now)
  → napReminderScheduler.schedule(now, delayMinutes)
  → AlarmManager fires at now + delayMinutes
  → NapReminderReceiver.onReceive()
  → quiet hours check → suppress OR showNapReminder()
  → user taps notification → MainActivity → sleep screen

User starts new nap before alarm fires
  → SleepViewModel.onStartRecord()
  → napReminderScheduler.cancel()   ← pending alarm cleared
```

---

## Testing

### Unit tests (`src/test/`)

| Test class | What it covers |
|-----------|---------------|
| `NapReminderManagerTest` | `schedule()` computes correct `triggerAt`; calls `setExactAndAllowWhileIdle`; `cancel()` cancels same PendingIntent |
| `NapReminderReceiverTest` | Suppresses when inside quiet window (including overnight wrap-around); calls `showNapReminder` when outside quiet window |
| `SettingsRepositoryImplTest` | `getNapReminderEnabled` defaults `false`; `getNapReminderDelayMinutes` defaults `60`; clamping enforced on `setNapReminderDelayMinutes` |
| `SleepViewModelTest` | `onStopRecord` schedules when enabled + NAP; skips when disabled; skips when NIGHT_SLEEP; `onStartRecord` always cancels |

No instrumentation tests — all logic unit-testable via mocks.

---

## Files Changed / Added

| File | Change |
|------|--------|
| `domain/repository/SettingsRepository.kt` | +4 methods |
| `data/repository/SettingsRepositoryImpl.kt` | implement new methods |
| `ui/settings/SettingsViewModel.kt` | expose nap reminder state |
| `ui/settings/SettingsScreen.kt` | new sleep reminder section |
| `manager/NapReminderScheduler.kt` | new interface |
| `manager/NapReminderManager.kt` | new impl |
| `receiver/NapReminderReceiver.kt` | new receiver |
| `util/NotificationHelper.kt` | `showNapReminder()` + new ID constant |
| `di/NotificationSchedulerModule.kt` | new binding |
| `ui/sleep/SleepViewModel.kt` | inject + call schedule/cancel |
| `AndroidManifest.xml` | register `NapReminderReceiver` |
| `src/test/.../NapReminderManagerTest.kt` | new |
| `src/test/.../NapReminderReceiverTest.kt` | new |
| `src/test/.../SleepViewModelTest.kt` | extend |
| `src/test/.../SettingsRepositoryImplTest.kt` | extend or new |

---

## Non-Goals

- No reminder for NIGHT_SLEEP
- No preset chips — free numeric input only
- No inline notification actions — tap-to-open only
- No rescheduling if the alarm fires while another nap is active (that state is prevented by `cancel()` on start)
