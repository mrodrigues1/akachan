# Predictive Feeding Reminders — Plan Overview

**Goal:** Predict the next likely breastfeeding time from recent session intervals, surface it as a soft hint on the Home Feeding card, and optionally fire a pre-feed notification with quiet-hours filtering.

**Architecture:** Three layers per existing app conventions. DataStore stores 4 new prefs; a pure `PredictNextFeedUseCase` rolls the mean of recent intervals into a **reactive** `Flow<FeedPrediction?>` (sourced from `BreastfeedingRepository.getAllSessions()` so it re-emits on history changes); `HomeViewModel` renders the prediction as a subtitle; a `PredictiveFeedNotificationCoordinator` reconciles an AlarmManager alarm against the live prediction; `PredictiveFeedReceiver` posts the notification via `NotificationHelper.showPredictiveReminder()`. Settings UI exposes the toggle, lead time, and quiet hours with a full Android-13+ runtime permission flow. `Clock`, `ZoneId`, and the application-scoped `CoroutineScope` are all provided through explicit Hilt modules — no constructor default-args (Dagger does not see Kotlin defaults).

**Tech Stack:** Kotlin 2.3.20 · Jetpack Compose · Hilt · Room/DataStore · AlarmManager · NotificationCompat · JUnit 5 · MockK · Turbine · Compose UI Test.

**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

**Linear project:** [Predictive Feeding Reminders](https://linear.app/akachan/project/predictive-feeding-reminders-120c2cc69aed/overview)

---

## Task → PR → Linear issue map

| # | Plan file | Branch | Linear issue title |
|---|-----------|--------|--------------------|
| 1 | [`2026-05-19-predictive-feeding-1-domain.md`](./2026-05-19-predictive-feeding-1-domain.md) | `feat/predictive-feeding-1-domain` | Predictive feeding — settings prefs + domain model |
| 2 | [`2026-05-19-predictive-feeding-2-usecase.md`](./2026-05-19-predictive-feeding-2-usecase.md) | `feat/predictive-feeding-2-usecase` | Predictive feeding — `PredictNextFeedUseCase` |
| 3 | [`2026-05-19-predictive-feeding-3-home-subtitle.md`](./2026-05-19-predictive-feeding-3-home-subtitle.md) | `feat/predictive-feeding-3-home-subtitle` | Predictive feeding — Home card subtitle |
| 4 | [`2026-05-19-predictive-feeding-4-notification.md`](./2026-05-19-predictive-feeding-4-notification.md) | `feat/predictive-feeding-4-notification` | Predictive feeding — channel, helper, receiver |
| 5 | [`2026-05-19-predictive-feeding-5-coordinator.md`](./2026-05-19-predictive-feeding-5-coordinator.md) | `feat/predictive-feeding-5-coordinator` | Predictive feeding — scheduler + coordinator |
| 6 | [`2026-05-19-predictive-feeding-6-settings-ui.md`](./2026-05-19-predictive-feeding-6-settings-ui.md) | `feat/predictive-feeding-6-settings-ui` | Predictive feeding — Settings section + permission flow |

Tasks land in order. Each PR is independently shippable because the user-visible toggle defaults to OFF until Task 6 makes it reachable from Settings.

## Spec coverage check

- Rolling mean, freshness, overdue grace, `INTERVAL_MAX_MINUTES` drop, quiet-hours filter (incl. wrap-around, `start==end` disable), sample-size min/target → Task 2.
- All four predictive Settings prefs + defaults → Task 1.
- Home subtitle states (active session, null, overdue ≥ 5m, overdue 0–4m, ≤ 30m, > 30m, low confidence, accessibility, tap behaviour unchanged) → Task 3.
- Notification channel, BigTextStyle, accent, Start-feeding + Snooze-15m actions → Tasks 4 + 5.
- Coordinator debounce, reconcile, `SecurityException` fallback → Task 5.
- Settings UI (toggle, lead-time SegmentedButton, TimePickerDialogs, helper text, empty-data hint) + Android-13+ permission state machine + `Open settings` intent → Task 6.

Partner dashboard, sleep, and pumping prediction explicitly out of scope per spec; not represented in any task.
