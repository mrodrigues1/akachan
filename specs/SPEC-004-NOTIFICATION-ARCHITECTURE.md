# SPEC-004: Notification Architecture

**Status:** Active
**Version:** 1.0
**Date:** 2026-05-07

---

## Overview

Breastfeeding and sleep notifications are driven by a three-role pattern:
**Scheduler** (schedules AlarmManager alarms) → **Receiver** (fires on alarm) → **Coordinator** (orchestrates lifecycle).

This pattern keeps each class single-responsibility and allows the coordinator to handle complex state transitions (pause/resume, side-switch) without polluting the scheduler or receiver.

---

## Role Definitions

### 1. Scheduler (Interface + Impl)

| Interface | Implementation | Location |
|-----------|---------------|----------|
| `NotificationScheduler` | `BreastfeedingNotificationManager` | `manager/` |
| `SleepNotificationScheduler` | `SleepNotificationManager` | `manager/` |

**Responsibility:** Schedule and cancel `AlarmManager` alarms. No UI. No domain logic.

Both are `@Singleton` bound in `NotificationSchedulerModule`.

Breastfeeding alarms fired:
- `ALARM_MAX_TOTAL_FEED` — fires when total session time exceeds user's configured limit
- `ALARM_MAX_PER_BREAST` — fires when time on current breast exceeds user's configured limit

### 2. Coordinator

`BreastfeedingSessionNotificationCoordinator` — orchestrates the full session notification lifecycle.

| Method | Called when |
|--------|-------------|
| `scheduleInitial()` | Session starts |
| `showRunning()` | Session running tick |
| `showPaused()` | Session paused |
| `rescheduleAfterResume()` | Session resumed (adjusts alarm for elapsed pause time) |
| `cancelAllSessionNotifications()` | Session stopped |

Reads feed time limits from `SettingsRepository`. Never schedules alarms directly — delegates to `NotificationScheduler`.

### 3. Receivers (`receiver/`)

All receivers are `@AndroidEntryPoint` and use `goAsync()` + a `CoroutineScope` for `suspend` use case calls.

| Receiver | Handles |
|----------|---------|
| `BreastfeedingActionReceiver` | Notification action intents: switch side, pause, resume, stop |
| `BreastfeedingNotificationReceiver` | AlarmManager trigger: time limit reached |
| `SleepActionReceiver` | Sleep notification action intents |

---

## NotificationHelper

`util/NotificationHelper.kt` — all notification builder logic, channel setup, and notification ID constants.

- Applies design-system accent via `setColor()` using per-type palette constants from `Color.kt`
- Uses warning tokens (`WarningAmber`, `WarningContainerAmber`) directly — not through `MaterialTheme`
- Notification ID constants live here (not scattered in callers)

---

## Anti-Goals

- Do not schedule alarms directly from a ViewModel or use case
- Do not add notification logic outside `manager/`, `receiver/`, and `util/NotificationHelper.kt`
- Do not access warning color tokens through `MaterialTheme.colorScheme` — import by name from `Color.kt`
- Do not call `goAsync()` without a matching `finish()` in the same coroutine scope
