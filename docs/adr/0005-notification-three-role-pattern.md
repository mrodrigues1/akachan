# Notifications use a three-role pattern: scheduler → receiver → coordinator

Status: accepted (decided 2026-05-07 in SPEC-004, now retired into this ADR — original in git history)

Notification features are split into three single-responsibility roles so complex session state transitions (pause/resume, side-switch) never leak into alarm plumbing:

- **Schedulers** (`manager/`, interface + `*Manager` implementation, bound in `NotificationSchedulerModule`) schedule and cancel `AlarmManager` alarms. No UI, no domain logic.
- **Receivers** (`receiver/`, `@AndroidEntryPoint`) handle alarm triggers and notification action intents; they use `goAsync()` with a matching `finish()` in the same coroutine scope for suspend calls.
- **Coordinators** orchestrate the notification lifecycle of a session (e.g. `BreastfeedingSessionNotificationCoordinator`) and delegate all alarm work to a scheduler — they never touch `AlarmManager` directly.

All notification builder logic, channel setup, and notification ID constants are centralized in `util/NotificationHelper.kt`, which applies per-type palette accents and imports warning tokens by name from `ui/theme/Color.kt` (they are extended, non-M3 semantics — not reachable via `MaterialTheme.colorScheme`).

## Anti-goals

- No alarms scheduled from a ViewModel or use case.
- No notification logic outside `manager/`, `receiver/`, and `util/NotificationHelper.kt`.
