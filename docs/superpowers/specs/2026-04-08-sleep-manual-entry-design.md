# Sleep Manual Entry & Wake Time — Design Spec

**Date:** 2026-04-08
**Status:** Approved

---

## Overview

Replace the start/stop live-tracking UX in the Sleep section with manual entry (user picks start and end time). Add a daily wake-up time feature that feeds into the sleep schedule generator.

---

## Goals

- Users can log naps and night sleep by picking exact start and end times, rather than using a live timer.
- Users can record what time the baby woke up each morning as a standalone action.
- The wake-up time is used by the schedule generator to personalise nap timing for the day.

## Non-Goals

- No edit or delete on existing sleep entries (read-only history, unchanged).
- No date picker — entries are always logged for today.
- Wake time does not auto-expire or reset daily.

---

## Data Layer

### SettingsRepository

Add two new operations to the `SettingsRepository` interface and `SettingsRepositoryImpl`:

```kotlin
fun getWakeTime(): Flow<LocalTime?>       // null = not set
suspend fun setWakeTime(time: LocalTime)
```

Stored in DataStore as an `Int` key representing minutes from midnight (0–1439). `null` preference key = not set.

### SleepRepository

Remove `getActiveRecord(): Flow<SleepRecord?>` from the interface, `SleepRepositoryImpl`, and `SleepDao`. This query exists only to support the start/stop pattern, which is being removed.

### Database schema

No changes. `SleepRecord` and `SleepEntity` are unchanged. `end_time` was already nullable in the schema; new entries will always have it populated.

---

## Domain Layer

### Deleted use cases

- `StartSleepRecordUseCase` — removed entirely
- `StopSleepRecordUseCase` — removed entirely

### New use case

```kotlin
class SaveSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        type: SleepType
    ): Long
}
```

Inserts a completed `SleepRecord` (both times populated). Callers are responsible for ensuring `endTime > startTime`; validation lives in the ViewModel.

### GenerateSleepScheduleUseCase

Remove the `wakeUpTime: LocalTime` parameter from `invoke()`. Instead, read the wake time from `SettingsRepository` inside the use case:

- If wake time is set → use it as `effectiveWakeTime` input (replacing the `resolveWakeTime()` inference from night sleep `endTime`)
- If wake time is not set → fall back to `LocalTime.of(7, 0)`

`SettingsRepository` is injected into the use case.

---

## ViewModel & UiState

### SleepUiState

```kotlin
data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    // Entry sheet state
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null
)
```

Removed fields: `activeRecord`, `selectedType`.

### SleepViewModel — removed functions

- `onStartTracking()`
- `onStopTracking()`
- `onTypeSelected(SleepType)`

### SleepViewModel — new functions

| Function | Behaviour |
|---|---|
| `onAddEntryClick()` | `showEntrySheet = true` |
| `onDismissSheet()` | `showEntrySheet = false`, clears `entryError` |
| `onEntryTypeChanged(SleepType)` | Updates `entryType` |
| `onEntryStartTimeChanged(LocalTime)` | Updates `entryStartTime`, clears `entryError` |
| `onEntryEndTimeChanged(LocalTime)` | Updates `entryEndTime`, clears `entryError` |
| `onSaveEntry()` | Validates, saves, dismisses sheet (see below) |
| `onSetWakeTime(LocalTime)` | Saves to `SettingsRepository`, refreshes schedule |

**`onSaveEntry()` time conversion and validation:**

1. Convert `entryStartTime` and `entryEndTime` to `Instant` using today's date and `ZoneId.systemDefault()`.
2. If `startInstant > endInstant`, subtract 1 day from `startInstant` — this handles cross-midnight night sleep (e.g. start 8:30 PM last night, end 6:45 AM today).
3. If `endInstant <= startInstant` after step 2 (should never happen in normal use), set `entryError = "End time must be after start time"` and return without saving.
4. Otherwise call `SaveSleepEntryUseCase(startInstant, endInstant, entryType)` and dismiss the sheet.

The validation error shown in the UI (red badge) is only triggered on tapping Save, not on each time change.

`wakeTime` is collected from `SettingsRepository.getWakeTime()` and merged into `_uiState` via `viewModelScope`.

---

## UI Layer — SleepTrackingScreen

### Layout

```
TopAppBar: "Sleep" ← back | "History" action
│
├── SECTION LABEL: "TODAY'S WAKE TIME"
├── Wake chip (filled if set, dashed/empty if not)
│     Set → shows time + edit icon
│     Not set → "Tap to set today's wake time" + "+"
│     Tap (either state) → opens TimePickerDialog
│
├── Summary row (3 stats: Sleep today · Naps · Night sleep)
│     Derived from `history` StateFlow filtered to today's local date:
│     - "Sleep today" = sum of durations of all completed entries today
│     - "Naps" = count of NAP entries today
│     - "Night sleep" = sum of NIGHT_SLEEP durations today
│     "—" shown for each stat when there are no entries
│
├── SECTION LABEL: "TODAY"
├── Sleep entries for today (newest first)
│     Each entry: emoji + type label + time range / duration
│     Empty state: moon icon + "No sleep entries yet"
│
├── FAB: "+ Add Sleep Entry"  → onAddEntryClick()
│
└── Row: [History] [Schedule] outline buttons
```

### Add Sleep Entry bottom sheet

Triggered by FAB. Dismissable by swipe or back gesture.

```
Handle bar
"Add Sleep Entry" title
[😴 Nap] [🌙 Night Sleep]  ← toggle chips, primary/secondary color when selected
[Start time]  [End time]   ← each opens TimePickerDialog on tap
⏱ Duration: Xh Ym          ← shown when end > start (green badge)
⚠ Error message             ← shown when end ≤ start (red badge, replaces duration)
[Save {type}] button        ← calls onSaveEntry()
```

Button label updates dynamically: "Save Nap" or "Save Night Sleep".

### Wake time interaction

Tapping the wake chip (set or empty state) opens Android's `TimePickerDialog` (Material 3 `TimePicker` composable in a `Dialog`). On confirm, calls `viewModel.onSetWakeTime(selectedTime)`.

---

## Schedule Integration

`GenerateSleepScheduleUseCase.invoke()` no longer accepts `wakeUpTime` as a parameter. The wake time resolution order becomes:

1. `SettingsRepository.getWakeTime()` — if present, use it directly
2. Fallback: `LocalTime.of(7, 0)`

The existing `resolveWakeTime()` private function (which inferred wake time from night sleep `endTime`) is removed, as wake time is now explicit.
