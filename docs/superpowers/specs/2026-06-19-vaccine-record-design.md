# Vaccine Record — Design

- **Linear project:** [Vaccine Record](https://linear.app/akachan/project/vaccine-record-e06c72cdafc7)
- **Status:** Design approved → ready for issue breakdown
- **Date:** 2026-06-19

## Overview

Let parents track their baby's vaccines. A vaccine entry exists in one of two states:
**scheduled** (planned for a future date) or **administered** (already given). The two entry
points are symmetric:

- **Register a schedule** — create a `SCHEDULED` entry with a future date. Later, when the shot
  is given, the parent **marks it administered**.
- **Just register a vaccine** — create an `ADMINISTERED` entry directly (no prior schedule).

A vaccine is a **point-in-time event** (no duration), so it mirrors the existing `Diaper` /
`BottleFeed` features end-to-end (Log/Edit/Delete/Observe use cases, a Home tile, a
history screen). The two things vaccines add beyond a plain tracker are the **scheduled →
administered lifecycle** and **configurable "remind me N days before" notifications** for
scheduled vaccines (mirroring the Sleep nap-reminder pattern).

The section's visual identity is **Indigo** (`#303F9F`), chosen because it is distinct from
every existing section color (sleep blue `#1976D2`, milestone purple `#7B1FA2`, growth teal
`#00897B`, diaper yellow, breastfeeding pink, bottle-feed red, warning amber).

## Scope

- Vaccine entry: name (free text, autocomplete-assisted), optional dose label (e.g. "1st dose"),
  status (scheduled / administered), scheduled date, administered date, optional notes.
- **Schedule** a future vaccine; **mark a scheduled vaccine as administered**; **log** an
  already-given vaccine directly.
- Name entry assisted by a curated, localized suggestion list (BCG, Hep B, DTaP, MMR, Polio,
  Rotavirus, …) — **not** a rigid official schedule.
- Home tile: next upcoming scheduled vaccine with a countdown (or overdue highlight), falling
  back to last administered / empty state.
- History screen: an **Upcoming/Scheduled** group (with a "Mark as given" action; overdue items
  flagged) and an **Administered** group (daily-grouped), with edit and delete.
- **Configurable reminders:** a settings section with an on/off toggle and a "days before" value
  (allowlist `{1, 3, 7, 14}`, default `7`). Each scheduled future vaccine schedules a local
  notification `leadDays` before its date.
- Localization: all strings in both `values/strings.xml` and `values-pt-rBR/strings.xml`.
- Data export/import: vaccine records included in the JSON backup (with restore), CSV export, and
  the PDF report.

## Non-goals

- **Partner / Firestore sync of vaccines (v1).** Keeps `sharing/` and `di/SharingModule.kt`
  untouched. It is the natural follow-up project and reuses the existing snapshot pipeline, but
  it is not required for the register/schedule/notify flows this project is about.
- Official PNI / WHO immunization schedule import (only the curated autocomplete suggestion list).
- Per-vaccine reminder lead times (a single global lead time applies to all — decided in
  brainstorming).
- Extended medical fields (lot/batch number, clinic/provider, injection site) — decided in
  brainstorming (minimal fields).
- Quick Settings tile / Glance widget for vaccines.
- Trends-screen integration (vaccine charts).
- Multi-dose series automation (auto-scheduling the next dose) — each entry is logged
  independently.

## Decisions (from brainstorming)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Vaccine naming | Free text + curated localized suggestions | Fast entry without maintaining a rigid locale-specific schedule. |
| Reminder configurability | One global lead time + on/off toggle | Mirrors the Sleep nap-reminder setting; simplest mental model. |
| Record fields | Minimal (name, dose label, date, notes) | Matches the other trackers' simplicity. |
| Schedule modeling | Single entity + `status` enum (not two tables) | KISS / no-Mapper; "mark administered" is a status flip, not a cross-table move. |
| Section color | Indigo `#303F9F` | Only unused, clearly-distinct hue; reads clinical. |
| Reminder alarm precision | Inexact (`setAndAllowWhileIdle`) | "A few days before" tolerates inexactness; avoids the Android 12+ exact-alarm permission. |
| Export/import | Included in v1 | Medical records must survive reinstall; mirrors the Diaper backup path. |
| Partner sync | Deferred (non-goal v1) | Reversible follow-up; keeps the project focused. |

## Domain Model

Pure Kotlin, zero framework imports (DDD principle from `SPEC-001`).

```kotlin
// domain/model/VaccineStatus.kt
enum class VaccineStatus { SCHEDULED, ADMINISTERED }
fun String.toVaccineStatusOrNull(): VaccineStatus?   // name-tolerant, mirrors toDiaperTypeOrNull
fun String.toVaccineStatusSafe(): VaccineStatus       // defaults to ADMINISTERED

// domain/model/VaccineRecord.kt
data class VaccineRecord(
    val id: Long = 0,
    val name: String,
    val doseLabel: String? = null,        // e.g. "1st dose"
    val status: VaccineStatus,
    val scheduledDate: Instant? = null,   // present when scheduled; retained after administration
    val administeredDate: Instant? = null,// present once administered
    val notes: String? = null,
    val createdAt: Instant,
)

// Pure helpers (domain/model/VaccineRecord.kt)
// Effective date for sorting/history grouping.
fun VaccineRecord.effectiveDate(): Instant = administeredDate ?: scheduledDate ?: createdAt
// Overdue = scheduled and its date is in the past.
fun VaccineRecord.isOverdue(now: Instant): Boolean =
    status == VaccineStatus.SCHEDULED && scheduledDate != null && scheduledDate.isBefore(now)

// domain/model/VaccineSummary.kt — for the Home tile
data class VaccineSummary(
    val nextUpcoming: VaccineRecord? = null,  // soonest future SCHEDULED entry
    val upcomingCount: Int = 0,
    val overdueCount: Int = 0,
    val administeredCount: Int = 0,
) {
    val hasAny: Boolean get() = nextUpcoming != null || administeredCount > 0 || overdueCount > 0
}
```

**Invariants** (enforced in use cases, not by Room triggers — this is a point-in-time event):

- `ADMINISTERED` ⇒ `administeredDate != null`.
- `SCHEDULED` ⇒ `scheduledDate != null`.
- `name` is non-blank (trimmed).

## Architecture

Follows the project's three-layer clean architecture (UI → Domain → Data) with unidirectional
flow. New artifacts by layer:

### Data layer
- `data/local/entity/VaccineEntity.kt` — `@Entity(tableName = "vaccines")`; columns `id` (PK
  autogenerate), `name`, `dose_label`, `status`, `scheduled_date` (nullable Long epoch-ms),
  `administered_date` (nullable Long epoch-ms), `notes`, `created_at`. Indices on `scheduled_date`
  and `status`. `toDomain()` / `toEntity()` extension functions (no Mapper class).
- `data/local/dao/VaccineDao.kt` —
  `observeAll(): Flow<List<VaccineEntity>>` (ordered by effective date desc — `COALESCE(administered_date, scheduled_date, created_at) DESC`),
  `observeUpcoming(): Flow<List<VaccineEntity>>` (`status = 'SCHEDULED'` ordered by `scheduled_date` asc),
  `getScheduledFutureAfter(nowMs): List<VaccineEntity>` (one-shot, for reboot re-arm),
  `getAllOnce(): List<VaccineEntity>` (one-shot, consumed by the backup source),
  `getById(id)`, `insert`, `update`, `deleteById`.
- `data/repository/VaccineRepositoryImpl.kt` — implements `VaccineRepository`, `@Singleton`.
- `BabyTrackerDatabase` — add `VaccineEntity` to `entities`, bump `version = 13 → 14`, add
  `vaccineDao()`, add `MIGRATION_13_14` (CREATE TABLE + CREATE INDEX on `scheduled_date` and
  `status`). No active-session trigger (instantaneous event, unlike sleep/breastfeeding).
- `di/DatabaseModule.kt` — provide `VaccineDao`, register `MIGRATION_13_14`.
  `di/RepositoryModule.kt` — `@Binds` `VaccineRepository` → impl.

### Domain layer
- `domain/repository/VaccineRepository.kt` — Flow-returning interface: `observeAll`,
  `observeUpcoming`, `getScheduledFutureAfter`, `getAllOnce`, `getById`, `insert`, `update`,
  `deleteById`.
- `domain/usecase/vaccine/`:
  - `AddVaccineRecordUseCase` — creates a `SCHEDULED` or `ADMINISTERED` entry (validation branches
    on status: scheduled requires a date; administered requires a not-in-future date). Returns the
    new id and triggers reminder (re)scheduling.
  - `MarkVaccineAdministeredUseCase` — flips a `SCHEDULED` entry to `ADMINISTERED` (sets
    `administeredDate`, default now; retains `scheduledDate`); cancels its reminder.
  - `EditVaccineRecordUseCase` — updates fields; re-arms/cancels the reminder.
  - `DeleteVaccineRecordUseCase` — deletes; cancels the reminder.
  - `ObserveVaccineRecordsUseCase` — passthrough to `observeAll`.
  - `ObserveVaccineSummaryUseCase` — combines `observeAll` + a `now`/`ZoneId` to compute
    `VaccineSummary` (next upcoming, upcoming/overdue/administered counts), mirroring
    `ObserveTodayDiaperSummaryUseCase`.

### Reminders / notifications layer
Mirrors the Sleep nap-reminder + predictive-sleep wiring.

- `domain/repository/VaccineSettingsRepository.kt` (+ `data/repository/VaccineSettingsRepositoryImpl.kt`,
  `@Singleton`, DataStore-backed) —
  `getReminderEnabled(): Flow<Boolean>` / `setReminderEnabled(Boolean)` (default `false`);
  `getReminderLeadDays(): Flow<Int>` / `setReminderLeadDays(Int)` with `ALLOWED_LEAD_DAYS =
  setOf(1, 3, 7, 14)`, `DEFAULT_LEAD_DAYS = 7`, sanitized at the repository boundary (same
  allowlist pattern as `SleepSettingsRepositoryImpl`). Keys `vaccine_reminder_enabled`,
  `vaccine_reminder_lead_days`.
- `manager/VaccineReminderScheduler.kt` (interface) + `manager/VaccineReminderManager.kt` (impl):
  - `schedule(record)` — if reminders enabled and the record is `SCHEDULED` with a future date,
    compute `triggerAt = (scheduledDate at local 09:00) − leadDays`. If `triggerAt ≤ now` but the
    scheduled date is still in the future, fire at the next ~09:00; if the scheduled date itself is
    past, do not schedule. Uses `AlarmManager.setAndAllowWhileIdle` (inexact — no exact-alarm
    permission). Request code derived from the record id.
  - `cancel(recordId)` — cancels the pending alarm.
  - `rescheduleAll()` — cancels all and re-arms from `getScheduledFutureAfter(now)`; called when
    the toggle/lead-days change and on boot.
- `receiver/VaccineReminderReceiver.kt` — on `com.babytracker.VACCINE_REMINDER_FIRE`, posts the
  notification via `NotificationHelper.showVaccineReminder(name, scheduledDate)`. Re-validates the
  record still exists and is still `SCHEDULED` before posting.
- `receiver/VaccineReminderBootReceiver.kt` — on `BOOT_COMPLETED` / `TIME_SET`, calls
  `rescheduleAll()` (mirrors `PredictiveSleepBootReceiver`).
- `util/NotificationHelper.kt` — add a `vaccine_reminders` channel (default importance) +
  `showVaccineReminder(...)`, tinted with the Indigo accent (notification visual-identity pattern).
- `AndroidManifest.xml` — register both receivers with their intent filters (mirroring the
  predictive-sleep receiver entries).
- `di/NotificationSchedulerModule.kt` — bind `VaccineReminderScheduler` → `VaccineReminderManager`.

### UI layer
- `domain/model/HomeTile.kt` — add `VACCINE` to the enum and to `DEFAULT_ORDER`. `reconcile()`
  auto-appends it for existing users, so no DataStore migration is needed.
- `ui/vaccine/VaccineViewModel.kt` + `VaccineUiState` — add/edit form state: name, dose label,
  an **"Already given / Schedule for later"** mode toggle (drives `status` + which date field is
  shown), date picker, notes; `isSaving`, `validationError`, `editingId`, `saved`. Injects
  `AddVaccineRecordUseCase` + `EditVaccineRecordUseCase`. Exposes the localized suggestion list
  (`R.array.vaccine_suggestions`) for the name autocomplete.
- `ui/vaccine/VaccineSheet.kt` — bottom sheet: name field with suggestion chips, optional dose
  label, the mode toggle, an editable date (defaults to "now" for administered / a near-future
  date for scheduled), optional notes. Reused for edit.
- `ui/vaccine/VaccineScreen.kt` — thin wrapper delegating to the sheet (mirrors `DiaperScreen`).
- `ui/vaccine/VaccineHistoryScreen.kt` + `VaccineHistoryViewModel.kt` — an **Upcoming** group
  (soonest first; overdue items flagged with the warning amber accent; each row has a "Mark as
  given" action wired to `MarkVaccineAdministeredUseCase`) and an **Administered** group (daily
  grouping, most recent first). Tap a row to edit via the sheet; delete with snackbar undo.
- `ui/home/HomeTileContent.kt` — `VaccineHomeCard`: next upcoming with a "in N days" countdown (or
  "Overdue" / last administered / empty state); add `onVaccine` to `HomeTileCallbacks` and wire
  through `HomeScreen` / `HomeViewModel`. Compact tile (like `DIAPER`).
- `navigation/Routes.kt` — add `VACCINE` and `VACCINE_HISTORY`; `AppNavGraph.kt` adds the
  composable destinations.
- `ui/theme/Color.kt` — add the Indigo token set (light + dark) and a `vaccineColors()` helper
  mirroring `diaperColors()`:
  `VaccineIndigo #303F9F`, `OnVaccine #FFFFFF`, `VaccineContainerIndigo #C5CAE9`,
  `OnVaccineContainerIndigo #1A237E`; dark — `VaccineIndigoDark #9FA8DA`, `OnVaccineDark #1A237E`,
  `VaccineContainerIndigoDark #283593`, `OnVaccineContainerIndigoDark #C5CAE9`. Top-level `val`s
  (not `MaterialTheme.colorScheme`), consistent with the other section tokens.

### Settings layer
- The vaccine-reminder settings section (toggle + days-before picker) lives in the settings UI,
  bound to `VaccineSettingsRepository`. Changing either value calls
  `VaccineReminderScheduler.rescheduleAll()`. Mirrors the Sleep nap-reminder settings section.

### Export / import layer (`export/`)
- `export/domain/model/BackupData.kt` — bump `CURRENT_BACKUP_FORMAT_VERSION` to the next value
  (currently `4` → `5`; verify at implementation); add `vaccines: List<VaccineBackup> =
  emptyList()` and the `@Serializable VaccineBackup` data class (`id`, `name`, `doseLabel`,
  `status`, `scheduledDate`, `administeredDate`, `notes`, `createdAt`).
- `export/domain/BackupSource.kt` — add `vaccines` to `TrackingSnapshot`.
- `export/data/BackupSourceImpl.kt` — read `db.vaccineDao().getAllOnce()` inside the existing
  transaction.
- `export/data/BackupConverters.kt` — `VaccineEntity.toBackup()` / `VaccineBackup.toEntity()`.
- `export/data/BackupImporterImpl.kt` — `mergeVaccines(data)` with identity dedup
  (`[name, status, scheduledDate, administeredDate, createdAt]`); add a `vaccines` count to
  `ImportCounts`. After import, call `VaccineReminderScheduler.rescheduleAll()`.
- `export/domain/usecase/ExportCsvUseCase.kt` — add a `"vaccines"` CSV section
  (`id, name, dose_label, status, scheduled_date, administered_date, notes, created_at`).
- `export/domain/usecase/GeneratePdfReportUseCase.kt` + `export/data/PdfReportGenerator.kt` —
  include a vaccine section (administered list + upcoming list) in the report.

### i18n
- `res/values/strings.xml` + `res/values-pt-rBR/strings.xml` — tile label, screen titles,
  field labels, the mode toggle, "Mark as given", overdue/empty states, the reminder notification
  channel name + title + body (with `%1$s` name and `%2$d` days placeholders), and the settings
  section. The suggestion list ships as a `<string-array name="vaccine_suggestions">` in both
  locales. Strings are added within each UI/settings PR (no separate i18n issue).

## Data Flow

```
VaccineSheet ──onSave──▶ VaccineViewModel ──▶ Add/Edit/MarkAdministered UseCase ──▶ VaccineRepository ──▶ VaccineDao
                                                  │
                                                  └──▶ VaccineReminderScheduler.schedule/cancel ──▶ AlarmManager
HomeScreen   ◀── VaccineUiState ◀── ObserveVaccineSummaryUseCase ◀── observeAll() Flow ◀── Room
AlarmManager ──fire──▶ VaccineReminderReceiver ──▶ NotificationHelper.showVaccineReminder
Boot/TimeSet ──▶ VaccineReminderBootReceiver ──▶ rescheduleAll() ──▶ getScheduledFutureAfter(now)
```

## Error Handling

- Use cases let exceptions propagate (no `Result<>` wrapper, per project anti-goals).
- Validation failures (blank name; administered date in the future; scheduled entry without a
  date) surface as `validationError` in `VaccineUiState`; nothing is persisted.
- Delete is undoable in history (snackbar undo re-inserts the captured `VaccineRecord` and re-arms
  its reminder if still scheduled/future).
- Editing a non-existent row (deleted on another device / via import) is a no-op update; the
  observed Flow reconciles the UI.
- A reminder that fires for a record that was since administered or deleted is suppressed by the
  receiver's re-validation (no stale notification).
- If the OS drops inexact alarms (Doze, force-stop), the boot receiver re-arms on the next
  `BOOT_COMPLETED`; a missed reminder degrades gracefully (the schedule entry is still visible in
  the app with its overdue/countdown state).

## Testing

- **Unit (`src/test/`):** JUnit 5 + MockK + Turbine. Cover entity↔domain mapping; each use case
  (including the scheduled→administered transition, validation branches, and reminder
  schedule/cancel calls via verified mocks); `isOverdue` / `effectiveDate` helpers;
  `ObserveVaccineSummaryUseCase` (next-upcoming selection, overdue/upcoming counting, day-boundary
  logic); `VaccineSettingsRepositoryImpl` allowlist sanitization; the reminder
  trigger-time computation (lead-days subtraction, past-trigger handling); and the
  `toVaccineStatusOrNull/Safe` parsers.
- **DAO:** Robolectric + Room in-memory; insert/update/delete/observe, `observeUpcoming` ordering,
  `getScheduledFutureAfter` filtering.
- **Migration:** `MigrationTestHelper` 13→14 round-trip.
- **Compose UI (`src/androidTest/`):** `VaccineSheet` logs an administered vaccine and schedules a
  future one; `VaccineHistoryScreen` shows upcoming vs administered groups, flags overdue, and the
  "Mark as given" + edit/delete actions work; the Home tile shows the countdown / empty state.
- **Export/import:** convert round-trip, `mergeVaccines` identity dedup, CSV section, PDF section,
  backup-format version bump (older importers ignore the defaulted `vaccines` field).

## Proposed Issue Breakdown (one per PR)

1. **Data layer** — `VaccineStatus` (+ parsers), `VaccineRecord` (+ `effectiveDate`/`isOverdue`),
   `VaccineSummary`, `VaccineEntity` + mapping, `VaccineDao`, `VaccineRepository`(+Impl), DI
   bindings, DB v14 + `MIGRATION_13_14`. Unit + DAO + migration tests.
2. **Domain use cases** — `AddVaccineRecordUseCase`, `MarkVaccineAdministeredUseCase`,
   `EditVaccineRecordUseCase`, `DeleteVaccineRecordUseCase`, `ObserveVaccineRecordsUseCase`,
   `ObserveVaccineSummaryUseCase`. Unit tests. (Reminder calls stubbed via the scheduler interface
   introduced here as a no-op default until issue 6.)
3. **Indigo theme tokens** — Indigo token set (light + dark) + `vaccineColors()` in `Color.kt`;
   design-system preview entry. Small, isolated.
4. **Add/Edit UI + Home tile** — `HomeTile.VACCINE`, `VaccineHomeCard`, `VaccineSheet`,
   `VaccineScreen`, `VaccineViewModel`/`UiState`, suggestion `string-array`, Home wiring, route +
   nav graph for `VACCINE`. Strings (en + pt-BR). Tests + Compose UI test.
5. **History screen** — `VaccineHistoryScreen` (upcoming/administered groups, overdue flag,
   mark-as-given, edit, delete-with-undo), `VACCINE_HISTORY` route + nav graph. Strings. Tests +
   UI test.
6. **Reminders** — `VaccineSettingsRepository`(+Impl), `VaccineReminderScheduler`(+Manager),
   `VaccineReminderReceiver` + `VaccineReminderBootReceiver`, manifest entries, notification
   channel + `showVaccineReminder`, DI binding, and wiring the use cases (issue 2) to
   schedule/cancel. Settings section UI (toggle + days picker). Strings. Unit tests (trigger-time
   math, allowlist) + receiver/scheduler tests.
7. **Export / import** — `VaccineBackup` + backup-format version bump, `TrackingSnapshot` /
   `BackupSourceImpl` read, `BackupConverters`, `BackupImporterImpl.mergeVaccines`,
   `ExportCsvUseCase` section, PDF report section, re-arm reminders after import. Unit tests for
   convert/import/CSV/PDF.

Issues 2–7 depend only on issue 1 (the data layer). Issue 6 depends on issue 2 (it wires the
use cases). Issues 3, 4, 5, 7 are otherwise independent and can proceed in parallel. Each is
independently shippable. Final granularity is set when these are converted to Linear issues with
implementation plans.

## Risks / Notes

- DB version bump touches `BabyTrackerDatabase` (shared file); keep the migration additive.
- `HomeTile` ordering is auto-reconciled; verify the new tile lands in a sensible default slot.
- Inexact alarms can be delayed by Doze; acceptable for "a few days before" and re-armed on boot.
  Verify the `RECEIVE_BOOT_COMPLETED` permission is already declared (it is, for predictive sleep).
- Backup-format version bump is forward-compatible (older importers ignore the defaulted
  `vaccines` field); restore dedups by identity so re-imports never duplicate rows.
- Confirm the exact current `CURRENT_BACKUP_FORMAT_VERSION` at implementation time (this spec
  assumes `4 → 5`).
- The `status` column is stored as the enum `name` (TEXT); parsing is tolerant (`toVaccineStatusSafe`
  defaults to `ADMINISTERED`) so a corrupt/unknown value never crashes.
