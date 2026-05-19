# Predictive Feeding Reminders — Design

**Linear project:** [Predictive Feeding Reminders](https://linear.app/akachan/project/predictive-feeding-reminders-120c2cc69aed/overview)
**Date:** 2026-05-19
**Status:** Approved, ready for implementation plan

---

## Goal

Predict the next likely feeding time from recent breastfeeding sessions and surface it as a soft hint on the home screen plus an optional notification before the predicted time. Predictions stay neutral, estimation-flavored, and never frame themselves as medical advice.

## Scope

- Rolling mean of recent breastfeeding session intervals (start → start).
- Home screen Feeding card subtitle: "Likely hungry around 2:40 PM".
- Optional opt-in notification a configurable number of minutes before the predicted time.
- Settings section exposing toggle, lead time, and quiet hours.

## Non-goals

- Medical advice framing or clinical language.
- Machine-learning models (simple rolling mean only).
- Predictions for sleep, pumping, or bottle feeds.
- Partner dashboard surface — partner sharing remains read-only v0; no prediction sync to Firestore.
- Bottle-feed tracking (does not exist in Akachan).

---

## Architecture

Three layers, matching existing app patterns:

```
Settings (DataStore)               BreastfeedingRepository
  ├ predictiveEnabled: Bool          └ getRecentSessions(limit=6): Flow<List<Session>>
  ├ predictiveLeadMinutes: Int                     │
  ├ quietHoursStartMinute: Int                     │
  └ quietHoursEndMinute: Int                       │
        │                                          │
        └──────────┐               ┌───────────────┘
                   ▼               ▼
            PredictNextFeedUseCase  ──>  Flow<FeedPrediction?>
                   │
        ┌──────────┴──────────────────┐
        ▼                             ▼
HomeViewModel (subtitle)   PredictiveFeedNotificationCoordinator
                                      │
                                      ▼
                          BreastfeedingNotificationManager
                          (new requestCode REQUEST_CODE_PREDICTIVE)
                                      │
                                      ▼
                          PredictiveFeedReceiver (new)
                                      │
                                      ▼
                          NotificationHelper.showPredictiveReminder()
```

### New files

- `app/src/main/java/com/babytracker/domain/model/FeedPrediction.kt`
- `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PredictNextFeedUseCase.kt`
- `app/src/main/java/com/babytracker/manager/PredictiveFeedNotificationCoordinator.kt`
- `app/src/main/java/com/babytracker/receiver/PredictiveFeedReceiver.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/PredictionCopy.kt` (centralized soft-language strings)

### Modified files

- `domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt` — 4 new preferences.
- `manager/NotificationScheduler.kt` — add `schedulePredictiveReminderAt` / `cancelPredictiveReminder`.
- `manager/BreastfeedingNotificationManager.kt` — new request code + handlers.
- `util/NotificationHelper.kt` — new channel `CHANNEL_PREDICTIVE_FEED` + builder.
- `ui/home/HomeViewModel.kt` + `HomeUiState` — add `nextFeedPrediction` field.
- `ui/home/HomeScreen.kt` — render subtitle on Feeding card.
- `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — new "Feeding reminders" section.
- `BabyTrackerApp.kt` — start coordinator in `onCreate`.
- `AndroidManifest.xml` — register `PredictiveFeedReceiver`.

---

## Domain Model

```kotlin
data class FeedPrediction(
    val predictedAt: Instant,
    val averageIntervalMinutes: Int,
    val sampleSize: Int,    // 3..5
    val isOverdue: Boolean, // now > predictedAt
    val minutesUntil: Int,  // negative if overdue
)
```

---

## Algorithm

`PredictNextFeedUseCase.invoke()` returns `Flow<FeedPrediction?>`:

1. `combine` `breastfeedingRepository.getRecentSessions(6)` with `settingsRepository.getQuietHours()`.
2. If any session is currently in progress (`endTime == null`) → emit `null`.
3. From completed sessions sorted by `startTime` desc, build pairs `(start_{n+1}, start_n)` and compute interval minutes.
4. Filter intervals: drop if **either** endpoint's local `LocalTime` falls inside the user's quiet-hours window. Window wraps midnight if `end < start`. If `start == end`, window is disabled and nothing is filtered.
5. Cap each remaining interval at **360 minutes** (6 hours).
6. If fewer than **3** intervals remain → emit `null`.
7. `avg = mean(filtered.take(5))`.
8. `predictedAt = mostRecentSession.startTime + avg.minutes`.
9. `isOverdue = Instant.now().isAfter(predictedAt)`.
10. `minutesUntil = Duration.between(Instant.now(), predictedAt).toMinutes()` (can be negative).

### Settings defaults

| Pref | Default |
|------|---------|
| `predictiveEnabled` | `false` |
| `predictiveLeadMinutes` | `15` |
| `quietHoursStartMinute` | `0` (12:00 AM) |
| `quietHoursEndMinute` | `480` (8:00 AM) |

Quiet-hour minute values are minute-of-day (0..1439) in local timezone. DST is handled by zoning sessions via `ZonedDateTime` of the device default zone at the time of evaluation.

---

## UI

### Home screen — Feeding card subtitle

States, in priority order:

| Prediction state | Subtitle copy | Style |
|------------------|---------------|-------|
| Active session in progress | (no prediction line; existing timer dominates) | — |
| `prediction == null` | (no prediction line; card unchanged) | — |
| Overdue ≥ 5 min | `Likely hungry now · ~{X}m ago` | `bodyMedium`, `tertiary` |
| Overdue 0..4 min | `Likely hungry around {time}` | `bodyMedium` muted |
| ≤ 30 min away | `Likely hungry around {time}` + `in ~{X}m` | `bodyMedium` + `bodySmall` muted |
| > 30 min away | `Likely hungry around {time}` | `bodyMedium` muted |

- Leading icon: `Icons.Outlined.Schedule` 16dp, `MaterialTheme.colorScheme.onSurfaceVariant`.
- Soft-language strings centralized in `ui/breastfeeding/PredictionCopy.kt`.
- Confidence cue: `sampleSize == 3` → append ` · low confidence` (`labelSmall`). `sampleSize` in `4..5` → no suffix.
- Time formatting: existing `Instant.formatTime()` extension from `util/DateTimeExt.kt`.
- Accessibility: `contentDescription = "Likely hungry around 2:40 PM, low confidence, based on 3 recent feeds"` when applicable.
- Tap behavior: inherited from existing Feeding card → navigates to Breastfeeding screen. No new tap handler.

### Settings screen — new section "Feeding reminders"

```
┌─────────────────────────────────────────┐
│ FEEDING REMINDERS                        │  labelMedium, onSurfaceVariant
├─────────────────────────────────────────┤
│ ⏰ Predictive reminder       [Switch]    │  titleMedium + Switch
│    Notify before next likely feed        │  bodySmall, onSurfaceVariant
├─────────────────────────────────────────┤
│ Notify ahead by                          │  titleSmall (disabled when toggle off)
│ ○ 5m  ○ 10m  ● 15m  ○ 30m                │  M3 SegmentedButton row
├─────────────────────────────────────────┤
│ Skip during quiet hours                  │  titleSmall (disabled when toggle off)
│ Start  [12:00 AM]   End  [8:00 AM]       │  Two M3 TimePickerDialog launchers
│ Intervals during this window are ignored │  bodySmall, onSurfaceVariant
└─────────────────────────────────────────┘
```

- Lead-time + quiet-hours rows: `alpha = 0.5f` and `enabled = false` when toggle is off.
- Quiet-hour pickers use M3 `TimePickerDialog`. Locale-aware 12h/24h via `DateFormat.is24HourFormat(context)`.
- When `start == end`: show helper text `Quiet hours disabled` in `bodySmall`.
- Empty-data hint shown under toggle when `< 3` valid intervals available: `Need 3+ recent feeds to predict.` (bodySmall, onSurfaceVariant). Toggle remains operable.

### Notification

- Channel: `CHANNEL_PREDICTIVE_FEED`, `IMPORTANCE_DEFAULT`, name "Feeding reminders", description "Soft reminders before a likely feeding time".
- Small icon: `ic_notification_feeding` (existing).
- Accent: `Pink700` via `NotificationCompat.Builder.setColor(...)`.
- Title: `Feeding likely soon`.
- Text: `Around {time} (in {leadMinutes} min). Estimated from recent feeds.`
- Style: `BigTextStyle` so disclaimer is fully visible on expand.
- Actions:
  - `Start feeding` → opens app to Breastfeeding screen (existing deep-link path; no autostart).
  - `Snooze 15m` → `PredictiveFeedReceiver` reschedules the alarm to `now + 15.minutes`.
- `setAutoCancel(true)`. No vibration. Channel default sound.

---

## Data Flow

### Triggers

| Trigger | Action |
|---------|--------|
| Session stopped (`StopBreastfeedingSessionUseCase`) | Repository emits new list → prediction flow re-emits → coordinator reschedules. |
| Session edited / deleted | Repository re-emits → coordinator reschedules. |
| Settings toggle OFF | Coordinator cancels `REQUEST_CODE_PREDICTIVE`. |
| Settings toggle ON | Coordinator schedules from current prediction. |
| Lead-time / quiet-hours changed | Coordinator cancels + reschedules. |
| App cold start | Coordinator's `applicationScope.collect` resubscribes; reconciles on first emission. |
| Notification "Snooze 15m" action | Receiver reschedules alarm to `now + 15.minutes`. |
| Alarm fires | Receiver posts notification; does not reschedule (one-shot per prediction). |

### Coordinator lifecycle

`PredictiveFeedNotificationCoordinator` is `@Singleton`. `BabyTrackerApp.onCreate` calls `coordinator.start()` which launches `applicationScope.launch { predictionAndSettings.debounce(200).collect(::reconcileAlarm) }`.

`reconcileAlarm(prediction, settings)`:

- If `prediction == null` or `!settings.predictiveEnabled` → cancel alarm.
- Otherwise:
  - `triggerAt = prediction.predictedAt - settings.predictiveLeadMinutes.minutes`
  - If `triggerAt.isBefore(Instant.now())` → skip (don't fire stale alarms).
  - Else: cancel existing + `setExactAndAllowWhileIdle` at `triggerAt`.

The 200ms debounce prevents alarm thrash when rapid emissions happen (e.g., during session edit flows).

---

## Error Handling

- `PredictNextFeedUseCase` wraps the upstream flow in `catchAndLog()` (existing `FlowExt`) → emits `null` on failure.
- Coordinator wraps `alarmManager.setExactAndAllowWhileIdle` in try/catch on `SecurityException` (user revoked `SCHEDULE_EXACT_ALARM`) and falls back to `setAndAllowWhileIdle`.
- Receiver wraps notification post in try/catch; logs and exits cleanly on missing `POST_NOTIFICATIONS` permission.
- DataStore reads default to the values in the "Settings defaults" table above.

## Permissions

- `POST_NOTIFICATIONS` — already declared.
- `SCHEDULE_EXACT_ALARM` — already declared and used by existing breastfeeding alarms.
- No new manifest permissions.

---

## Testing

### Unit (`src/test/`)

**`PredictNextFeedUseCaseTest`** (JUnit 5, MockK, Turbine):

- Returns `null` when an active session is present.
- Returns `null` when fewer than 3 valid intervals remain after filtering.
- Computes mean of last 5 intervals correctly.
- Caps each interval at 360 minutes before averaging.
- Filters intervals where either endpoint falls inside the quiet-hours window (incl. wrap-around midnight).
- `start == end` quiet hours leaves all intervals intact.
- `isOverdue` is true when `predictedAt < now`.
- `minutesUntil` sign correctness across past and future.
- DST forward and backward transitions handled (zone-aware fixtures with `ZoneId.of("America/Sao_Paulo")` and `ZoneId.of("America/New_York")`).

**`PredictiveFeedNotificationCoordinatorTest`**:

- Schedules alarm when enabled, prediction non-null, triggerAt in future.
- Cancels alarm when toggle off.
- Cancels alarm when prediction becomes null.
- Skips scheduling when triggerAt is in the past.
- Reschedules when settings change (lead time, quiet hours).
- Falls back to `setAndAllowWhileIdle` on `SecurityException`.

### Instrumentation (`src/androidTest/`)

**`HomeScreenPredictionTest`** (Compose UI):

- Subtitle is absent when prediction is `null`.
- "Likely hungry around …" rendered for future prediction.
- "Likely hungry now · ~Xm ago" rendered for overdue prediction.
- " · low confidence" suffix appears when `sampleSize == 3`.

**`SettingsScreenPredictionTest`**:

- Toggle reveals/hides config rows (alpha + enabled state).
- Quiet-hours with `start == end` shows helper text `Quiet hours disabled`.
- Segmented button selection persists to DataStore (verified via observed flow).

**`PredictiveFeedReceiverTest`**:

- Posts notification with the correct channel id and accent color.
- "Snooze 15m" action invokes alarm reschedule for `now + 15.minutes`.

---

## Open Questions

None as of approval. Future iterations may consider:

- Surfacing the prediction on the partner dashboard once read-only inference is acceptable.
- Adaptive lead time based on the user's snooze patterns.
- Exposing a debug overlay (in `DesignSystemPreviewScreen` or a new dev tool) showing the raw interval samples used for the current prediction.
