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
  ├ predictiveEnabled: Bool          └ getRecentSessions(limit=20): Flow<List<Session>>
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

1. `combine` `breastfeedingRepository.getRecentSessions(20)` with `settingsRepository.getQuietHours()`.
2. If any session is currently in progress (`endTime == null`) → emit `null`.
3. **Freshness check**: if `Instant.now() - mostRecentSession.startTime > 12 hours` → emit `null`. Stale history must not anchor predictions.
4. From completed sessions sorted by `startTime` desc, build pairs `(start_{n+1}, start_n)` and compute interval minutes.
5. **Drop** intervals greater than **360 minutes** (6 hours). Intervals above the valid maximum are treated as data gaps, not clamped.
6. **Drop** intervals where **either** endpoint's local `LocalTime` falls inside the user's quiet-hours window. Window wraps midnight if `end < start`. If `start == end`, window is disabled and nothing is filtered.
7. Take the **most recent 5** intervals from the filtered list (not from the raw list — older valid daytime intervals are preferred over recent overnight ones).
8. If fewer than **3** intervals remain → emit `null`.
9. `avg = mean(takenIntervals)`.
10. `predictedAt = mostRecentSession.startTime + avg.minutes`.
11. `isOverdue = Instant.now().isAfter(predictedAt)`.
12. `minutesUntil = Duration.between(Instant.now(), predictedAt).toMinutes()` (can be negative).
13. **Overdue grace bound**: if `predictedAt` is more than **90 minutes** in the past → emit `null`. Beyond the grace window the prediction is treated as obsolete; UI must not show "hungry now" indefinitely.

### Tuning constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `LOOKBACK_LIMIT` | 20 sessions | Pre-filter history fetched from DB; large enough that overnight quiet-hour drops still leave 3+ daytime intervals. |
| `FRESHNESS_HORIZON` | 12 hours | Maximum age of `mostRecentSession.startTime` before predictions are suppressed. |
| `INTERVAL_MAX_MINUTES` | 360 (6 h) | Intervals longer than this are dropped, not capped. |
| `SAMPLE_SIZE_TARGET` | 5 | Most recent valid intervals used after filtering. |
| `SAMPLE_SIZE_MIN` | 3 | Minimum valid intervals required to predict. |
| `OVERDUE_GRACE_MINUTES` | 90 | Predictions older than this past `predictedAt` are suppressed. |

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
| Overdue ≥ 5 min and ≤ 90 min | `Likely hungry now · ~{X}m ago` | `bodyMedium`, `tertiary` |
| Overdue > 90 min | (no prediction line; use case has emitted `null`) | — |
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
│ ⚠ Notifications blocked   [Open settings]│  Warning row, visible whenever
│    Android won't deliver reminders        │  toggle is ON and permission denied
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

### Runtime notification permission flow (Android 13+)

`POST_NOTIFICATIONS` is a runtime permission on API 33+. A declared manifest entry is not enough — the user can deny or later revoke it, and the toggle alone can silently fail.

**Contract** (single source of truth for the warning row):

- The warning row is visible **iff** `predictiveEnabled == true` AND notification permission is not granted (denied, permanently denied, or revoked).
- Toggle OFF is always treated as a deliberate user choice — no warning row, regardless of permission state.
- Permission denial does NOT auto-flip `predictiveEnabled` off. The user stays opted in; the coordinator continues to schedule alarms; the receiver fails gracefully at post time if permission is still missing when the alarm fires. The user sees the warning row and can recover via `Open settings`.

**Permission state machine** (held in `SettingsViewModel` via `NotificationManagerCompat.areNotificationsEnabled()` re-checked on every `ON_RESUME`):

| State | Toggle ON tap | Resulting `predictiveEnabled` | Warning row |
|-------|---------------|-------------------------------|-------------|
| `GRANTED` | Flips to ON. | true | hidden |
| `DENIED` (no request yet) | Launches `ActivityResultContracts.RequestPermission(POST_NOTIFICATIONS)`. On grant → stays ON. On deny → stays ON. | true in both branches | hidden if granted, shown if denied |
| `DENIED_PERMANENTLY` (`shouldShowRequestPermissionRationale == false` after a deny) | Flips to ON immediately (no system dialog will appear; requesting silently returns denied). | true | shown with `Open settings` → `Intent(ACTION_APP_NOTIFICATION_SETTINGS)` |
| `REVOKED` (was granted, now denied — detected on `ON_RESUME` while toggle is ON) | n/a (resume path, not a tap) | unchanged | shown with `Open settings` |
| Any state, toggle OFF tap | Flips to OFF. | false | hidden |

- `POST_NOTIFICATIONS` is added to `AndroidManifest.xml` (currently relied upon by existing breastfeeding notifications without a request flow; this design introduces the explicit request path).
- The settings UI must surface the denied state, not silently swallow it.
- Pre-Android-13 devices skip the permission flow entirely; permission is implicit.

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
- Settings layer detects denied/revoked notification permission on `ON_RESUME` and reflects it in the warning row described in the permission flow section.
- DataStore reads default to the values in the "Settings defaults" table above.

## Permissions

- `POST_NOTIFICATIONS` — declared in manifest; requested at runtime via the settings toggle flow on Android 13+. See "Runtime notification permission flow" above.
- `SCHEDULE_EXACT_ALARM` — already declared and used by existing breastfeeding alarms.
- No new manifest permissions.

---

## Testing

### Unit (`src/test/`)

**`PredictNextFeedUseCaseTest`** (JUnit 5, MockK, Turbine):

- Returns `null` when an active session is present.
- Returns `null` when fewer than 3 valid intervals remain after filtering.
- Computes mean of the most-recent 5 valid intervals correctly.
- **Drops** (not caps) intervals greater than 360 minutes.
- Filters intervals where either endpoint falls inside the quiet-hours window (incl. wrap-around midnight).
- `start == end` quiet hours leaves all intervals intact.
- **Freshness**: returns `null` when `mostRecentSession.startTime` is older than 12 hours (multi-day logging gap).
- **Overdue grace**: returns `null` when `predictedAt` is more than 90 minutes in the past.
- **Lookback sufficiency**: with 20 sessions where the 5 most-recent intervals are overnight (filtered out) but older daytime intervals exist, returns a non-null prediction built from the older valid intervals.
- `isOverdue` is true when `predictedAt < now` and within grace.
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
- Permission `GRANTED` → no warning row visible when toggle on.
- Permission `DENIED` (first request) → toggle ON triggers permission request; on grant: toggle stays ON, no warning. **On deny: toggle stays ON, warning row visible with `Open settings` action** (recovery path always present).
- Permission `DENIED_PERMANENTLY` → toggle ON flips to true immediately, warning row visible; `Open settings` button launches `ACTION_APP_NOTIFICATION_SETTINGS` intent.
- Permission `REVOKED` (granted on launch, revoked while backgrounded) → warning row appears on `ON_RESUME` while toggle remains ON.
- Toggle OFF while permission denied → warning row hidden (deliberate user choice respected).

**`PredictiveFeedReceiverTest`**:

- Posts notification with the correct channel id and accent color.
- "Snooze 15m" action invokes alarm reschedule for `now + 15.minutes`.

---

## Open Questions

None as of approval. Future iterations may consider:

- Surfacing the prediction on the partner dashboard once read-only inference is acceptable.
- Adaptive lead time based on the user's snooze patterns.
- Exposing a debug overlay (in `DesignSystemPreviewScreen` or a new dev tool) showing the raw interval samples used for the current prediction.
