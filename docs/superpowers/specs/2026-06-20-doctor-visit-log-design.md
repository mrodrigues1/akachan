# Doctor Visit Log — Design

- **Linear project:** [Doctor Visit Log](https://linear.app/akachan/project/doctor-visit-log-b1e2b3e2ddfc/overview)
- **Status:** Design approved → ready for issue breakdown
- **Date:** 2026-06-20

## Overview

Let parents log pediatric appointments so they can refer back to doctor instructions and
track visit history. The feature has two cooperating parts:

1. **Pre-visit questions** — a running *inbox* of questions the parent wants to ask the
   doctor. Questions can be captured at any time, tapped to expand full-screen for easy
   reading during the appointment, marked answered, and deleted. When a visit is logged,
   selected inbox questions can be **attached** to that visit (pulled out of the inbox).
2. **Visit records** — a point-in-time entry with a date (past or future), an optional
   doctor/clinic name, and free-text notes. Visits appear in a history list sorted by date.

A visit may also carry a **lightweight data-snapshot reference** (a label + timestamp).
Tapping it re-generates a fresh export on demand by reusing the existing Data Export feature
(`GeneratePdfReportUseCase` / `ExportCsvUseCase`) — no frozen file is stored.

A visit is a **point-in-time event** (no duration), so the visit half mirrors the existing
`Vaccine` / `Diaper` features end-to-end (Add/Edit/Delete/Observe use cases, a Home tile, a
history screen). Beyond a plain tracker it adds: the **question inbox with attach-on-save**,
the **on-demand snapshot reference**, **configurable upcoming-visit reminders** (mirroring the
Vaccine reminder pattern), **export/import** of visits and questions (backup + CSV + PDF), and
**read-only partner sync** of visits (mirroring the existing Firestore snapshot pipeline).

The section's visual identity is **Slate Blue-Grey** (`#455A64`, Material Blue Grey 700),
chosen because it is the only clearly-distinct, clinically-reading hue left: it evokes
stethoscope steel / a medical clipboard, aligns with the project's grey icon, and stays clear
of every existing section color (sleep blue `#1976D2`, vaccine indigo `#303F9F`, milestone
purple `#7B1FA2`, growth teal `#00897B`, the forest-green Success/Tertiary token, breastfeeding
pink, bottle-feed red, diaper yellow, warning amber). A true green would collide with the
Success semantic token; a cyan would sit too close to growth teal.

## Scope

- **Question inbox:** add a free-text question, tap a row to expand it full-screen for reading,
  toggle answered, delete. Inbox = questions not yet attached to any visit.
- **Visit entry:** date (past or future), optional doctor/clinic name, free-text notes,
  optional snapshot reference. Add / edit / delete.
- **Attach-on-save:** when adding or editing a visit, select inbox questions to attach to it
  (sets their `visitId`). Attached questions show under the visit and leave the inbox.
- **Visit history:** list sorted by date, split into **Upcoming** (date in the future) and
  **Past** groups; tap a row to edit via the sheet; delete with snackbar undo.
- **Snapshot reference (lightweight):** a visit can record that a data snapshot was attached
  (`snapshotLabel` + `snapshotCreatedAt`). Tapping "View snapshot" re-runs the existing export
  generation on demand (fresh PDF/CSV), rather than persisting a file.
- **Upcoming-visit reminders:** a settings section with an on/off toggle and a "days before"
  value (allowlist `{1, 3, 7, 14}`, default `1`). Each upcoming visit schedules a local
  notification `leadDays` before its date (inexact alarm, mirroring the Vaccine reminder).
- **Export / import:** visits **and** questions included in the JSON backup (with restore), CSV
  export, and the PDF report.
- **Partner sync (read-only):** visits are added to the partner `ShareSnapshot` so the connected
  partner sees the visit history read-only. Questions are **not** synced (private prep notes).
- **Home tile:** next upcoming visit with a countdown (or last past visit / empty state), plus
  the open-question count. Compact tile like `DIAPER` / `VACCINE`.
- **Localization:** all strings in both `values/strings.xml` and `values-pt-rBR/strings.xml`.

## Non-goals

- **Calendar integration** (project non-goal).
- **Prescription or medication tracking** (project non-goal; separate project if prioritized).
- **Sharing with healthcare providers** (project non-goal — partner sync is parent-to-parent
  only, via the existing anonymous-auth snapshot pipeline; no provider-facing surface).
- **Syncing questions to the partner** — questions are private prep notes; only visit records
  sync.
- **Per-question rich fields** (priority, tags, due dates) — questions are plain text + an
  answered flag.
- **Exact-alarm reminders** — reminders use inexact `setAndAllowWhileIdle` (no exact-alarm
  permission), re-armed on boot. "A few days before" tolerates inexactness.

## Decisions (from brainstorming)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Question model | Hybrid: inbox + attach-on-save | Capture anytime; pull selected ones into a visit when logging it. Modeled as a nullable `visitId` FK (null = inbox). |
| Snapshot attachment | Lightweight reference (label + timestamp); re-generate on demand | Avoids file storage/lifecycle; reuses the existing export generators. KISS. |
| Visit/question persistence | Two entities, one `DoctorVisitRepository` | They form one bounded context; one repo keeps the surface small (no Mapper classes). |
| Section color | Slate Blue-Grey `#455A64` | Only distinct, clinical-reading hue left; green collides with the Success token, cyan with growth teal. |
| Reminder configurability | One global lead time + on/off toggle | Mirrors the Vaccine/Sleep reminder setting; simplest mental model. Default `1` day (appointment-day-before is the common case). |
| Reminder alarm precision | Inexact (`setAndAllowWhileIdle`) | Tolerates inexactness; avoids the Android 12+ exact-alarm permission. |
| Export / import | Visits **and** questions included | Medical records must survive reinstall; mirrors the Diaper/Vaccine backup path. |
| Partner sync | Visits only, read-only; questions stay private | Reuses the existing snapshot pipeline; questions are prep notes the partner doesn't need. |
| Record fields | Minimal (date, optional name, notes) | Matches the other trackers' simplicity. |

## Domain Model

Pure Kotlin, zero framework imports (DDD principle from `SPEC-001`).

```kotlin
// domain/model/DoctorVisit.kt
data class DoctorVisit(
    val id: Long = 0,
    val date: Instant,                  // the appointment date (past or future)
    val providerName: String? = null,   // doctor or clinic, optional
    val notes: String? = null,          // free-text instructions/notes
    val snapshotLabel: String? = null,  // present once a snapshot reference is attached
    val snapshotCreatedAt: Instant? = null,
    val createdAt: Instant,
)

// Pure helpers (domain/model/DoctorVisit.kt)
fun DoctorVisit.isUpcoming(now: Instant): Boolean = date.isAfter(now)
fun DoctorVisit.hasSnapshot(): Boolean = snapshotLabel != null

// domain/model/VisitQuestion.kt
data class VisitQuestion(
    val id: Long = 0,
    val text: String,
    val answered: Boolean = false,
    val visitId: Long? = null,          // null = inbox; set = attached to that visit
    val createdAt: Instant,
)

// domain/model/DoctorVisitSummary.kt — for the Home tile
data class DoctorVisitSummary(
    val nextUpcoming: DoctorVisit? = null,  // soonest future visit
    val lastPast: DoctorVisit? = null,      // most recent past visit
    val openQuestionCount: Int = 0,         // inbox questions not answered
) {
    val hasAny: Boolean get() = nextUpcoming != null || lastPast != null || openQuestionCount > 0
}
```

**Invariants** (enforced in use cases, not by Room triggers):

- `providerName`, `notes` are trimmed; blank → stored as `null`.
- A `VisitQuestion.text` is non-blank (trimmed).
- Deleting a visit detaches its questions back to the inbox (set `visitId = null`), so prepared
  questions are never lost when a visit is removed. (Implemented in the delete use case, not a
  Room cascade — keeps the behavior explicit and testable.)

## Architecture

Follows the project's three-layer clean architecture (UI → Domain → Data) with unidirectional
flow. New artifacts by layer:

### Data layer
- `data/local/entity/DoctorVisitEntity.kt` — `@Entity(tableName = "doctor_visits")`; columns
  `id` (PK autogenerate), `date` (Long epoch-ms), `provider_name` (nullable), `notes`
  (nullable), `snapshot_label` (nullable), `snapshot_created_at` (nullable Long), `created_at`.
  Index on `date`. `toDomain()` / `toEntity()` extension functions (no Mapper class).
- `data/local/entity/VisitQuestionEntity.kt` — `@Entity(tableName = "visit_questions")`;
  columns `id` (PK autogenerate), `text`, `answered` (Int 0/1), `visit_id` (nullable Long),
  `created_at`. Index on `visit_id`. `toDomain()` / `toEntity()`.
- `data/local/dao/DoctorVisitDao.kt` —
  `observeAllVisits(): Flow<List<DoctorVisitEntity>>` (ordered by `date DESC`),
  `getVisitById(id)`, `getAllVisitsOnce(): List<DoctorVisitEntity>` (one-shot, for backup +
  partner snapshot), `insertVisit`, `updateVisit`, `deleteVisitById`,
  `observeInboxQuestions(): Flow<List<VisitQuestionEntity>>` (`visit_id IS NULL` ordered by
  `created_at DESC`), `observeQuestionsForVisit(visitId): Flow<List<VisitQuestionEntity>>`,
  `getAllQuestionsOnce(): List<VisitQuestionEntity>` (one-shot, for backup),
  `getQuestionById(id)`, `insertQuestion`, `updateQuestion`, `deleteQuestionById`,
  `attachQuestions(ids, visitId)` (bulk `UPDATE ... SET visit_id = :visitId WHERE id IN (:ids)`),
  `detachQuestionsForVisit(visitId)` (`SET visit_id = NULL WHERE visit_id = :visitId`),
  `getUpcomingVisitsAfter(nowMs): List<DoctorVisitEntity>` (one-shot, for reminder reboot re-arm).
- `data/repository/DoctorVisitRepositoryImpl.kt` — implements `DoctorVisitRepository`,
  `@Singleton`.
- `BabyTrackerDatabase` — add both entities to `entities`, bump `version = 14 → 15`, add
  `doctorVisitDao()`, add `MIGRATION_14_15` (CREATE TABLE ×2 + CREATE INDEX on `date` and
  `visit_id`). No active-session trigger (instantaneous event).
- `di/DatabaseModule.kt` — provide `DoctorVisitDao`, register `MIGRATION_14_15`.
  `di/RepositoryModule.kt` — `@Binds` `DoctorVisitRepository` → impl.

### Domain layer
- `domain/repository/DoctorVisitRepository.kt` — Flow-returning interface covering both visits
  and questions: `observeAllVisits`, `getVisitById`, `getAllVisitsOnce`, `insertVisit`,
  `updateVisit`, `deleteVisitById`, `getUpcomingVisitsAfter`; `observeInboxQuestions`,
  `observeQuestionsForVisit`, `getAllQuestionsOnce`, `getQuestionById`, `insertQuestion`,
  `updateQuestion`, `deleteQuestionById`, `attachQuestions`, `detachQuestionsForVisit`.
- `domain/usecase/doctorvisit/`:
  - `AddDoctorVisitUseCase` — validates and inserts a visit; optionally attaches a set of inbox
    question ids in the same operation. Returns the new id and triggers reminder (re)scheduling.
  - `EditDoctorVisitUseCase` — updates fields; reconciles attached questions (attach newly
    selected, detach deselected); re-arms/cancels the reminder.
  - `DeleteDoctorVisitUseCase` — detaches the visit's questions back to the inbox, deletes the
    visit, cancels its reminder.
  - `ObserveDoctorVisitsUseCase` — passthrough to `observeAllVisits`.
  - `ObserveDoctorVisitSummaryUseCase` — combines `observeAllVisits` + `observeInboxQuestions` +
    a `now`/`ZoneId` to compute `DoctorVisitSummary`, mirroring `ObserveVaccineSummaryUseCase`.
  - `AddVisitQuestionUseCase` — inserts a question into the inbox (`visitId = null`).
  - `ToggleVisitQuestionAnsweredUseCase` — flips the `answered` flag.
  - `DeleteVisitQuestionUseCase` — deletes a question.
  - `ObserveInboxQuestionsUseCase` / `ObserveVisitQuestionsUseCase` — passthroughs.
  - `AttachSnapshotToVisitUseCase` — sets `snapshotLabel` + `snapshotCreatedAt` on a visit.
  - `GenerateVisitSnapshotUseCase` — thin wrapper invoking the existing `GeneratePdfReportUseCase`
    (or `ExportCsvUseCase`) to produce a fresh export on demand. No new export logic.

### Reminders / notifications layer
Mirrors the Vaccine reminder wiring.

- `domain/repository/DoctorVisitSettingsRepository.kt` (+ `data/repository/...Impl.kt`,
  `@Singleton`, DataStore-backed) — `getReminderEnabled(): Flow<Boolean>` /
  `setReminderEnabled(Boolean)` (default `false`); `getReminderLeadDays(): Flow<Int>` /
  `setReminderLeadDays(Int)` with `ALLOWED_LEAD_DAYS = setOf(1, 3, 7, 14)`, `DEFAULT_LEAD_DAYS =
  1`, sanitized at the repository boundary (same allowlist pattern as the Vaccine settings repo).
  Keys `doctor_visit_reminder_enabled`, `doctor_visit_reminder_lead_days`.
- `manager/DoctorVisitReminderScheduler.kt` (interface) + `manager/DoctorVisitReminderManager.kt`
  (impl): `schedule(visit)` (if enabled and visit is future: `triggerAt = (date at local 09:00)
  − leadDays`; inexact `setAndAllowWhileIdle`; request code from visit id), `cancel(visitId)`,
  `rescheduleAll()` (cancel all + re-arm from `getUpcomingVisitsAfter(now)`; on toggle/lead change
  and on boot).
- `receiver/DoctorVisitReminderReceiver.kt` — on `com.babytracker.DOCTOR_VISIT_REMINDER_FIRE`,
  re-validates the visit still exists and is still upcoming, then posts via
  `NotificationHelper.showDoctorVisitReminder(providerName, date)`.
- `receiver/DoctorVisitReminderBootReceiver.kt` — on `BOOT_COMPLETED` / `TIME_SET`, calls
  `rescheduleAll()` (mirrors the Vaccine boot receiver).
- `util/NotificationHelper.kt` — add a `doctor_visit_reminders` channel (default importance) +
  `showDoctorVisitReminder(...)`, tinted with the Slate accent.
- `AndroidManifest.xml` — register both receivers with their intent filters.
- `di/NotificationSchedulerModule.kt` — bind `DoctorVisitReminderScheduler` →
  `DoctorVisitReminderManager`.

### UI layer
- `domain/model/HomeTile.kt` — add `DOCTOR_VISIT` to the enum and to `DEFAULT_ORDER`.
  `reconcile()` auto-appends it for existing users, so no DataStore migration is needed.
- `ui/doctorvisit/DoctorVisitViewModel.kt` + `DoctorVisitUiState` — add/edit visit form state:
  date, provider name, notes, the set of inbox questions with selection toggles (attach-on-save),
  snapshot reference state; `isSaving`, `validationError`, `editingId`, `saved`. Injects
  `AddDoctorVisitUseCase` + `EditDoctorVisitUseCase` + `ObserveInboxQuestionsUseCase` +
  `AttachSnapshotToVisitUseCase`.
- `ui/doctorvisit/DoctorVisitSheet.kt` — bottom sheet: editable date, optional provider name,
  notes, a selectable list of inbox questions to attach, and a snapshot "Attach / View" control.
  Reused for edit (pre-fills fields and already-attached questions).
- `ui/doctorvisit/DoctorVisitScreen.kt` — thin wrapper delegating to the sheet.
- `ui/doctorvisit/VisitQuestionsScreen.kt` + `VisitQuestionsViewModel.kt` — the question inbox:
  an add-question field, the list of open/answered questions, **tap a row to expand it
  full-screen** (a focused read view / dialog) for use during the appointment, an answered
  toggle, and delete-with-undo.
- `ui/doctorvisit/DoctorVisitHistoryScreen.kt` + `DoctorVisitHistoryViewModel.kt` — an
  **Upcoming** group (soonest first) and a **Past** group (most recent first). Each row shows
  date, provider name, a notes preview, attached-question count, and a snapshot badge. Tap to
  edit via the sheet; delete with snackbar undo.
- `ui/home/HomeTileContent.kt` — `DoctorVisitHomeCard`: next upcoming with an "in N days"
  countdown (or last past visit / empty state) and the open-question count; add `onDoctorVisit`
  to `HomeTileCallbacks` and wire through `HomeScreen` / `HomeViewModel`. Compact tile.
- `navigation/Routes.kt` — add `DOCTOR_VISIT`, `DOCTOR_VISIT_HISTORY`, `VISIT_QUESTIONS`;
  `AppNavGraph.kt` adds the composable destinations.
- `ui/theme/Color.kt` — add the Slate Blue-Grey token set (light + dark) and a
  `doctorVisitColors()` helper mirroring `vaccineColors()`. Top-level `val`s (not
  `MaterialTheme.colorScheme`): `DoctorSlate #455A64`, `OnDoctorWhite #FFFFFF`,
  `DoctorContainerSlate #CFD8DC`, `OnDoctorContainerSlate #263238`; dark — `DoctorSlateDark
  #90A4AE`, `OnDoctorDark #263238`, `DoctorContainerSlateDark #37474F`,
  `OnDoctorContainerSlateDark #CFD8DC`. (Add raw `BlueGrey*` vals if not already present.)

### Settings layer
- The reminder settings section (toggle + days-before picker) lives in the settings UI, bound to
  `DoctorVisitSettingsRepository`. Changing either value calls
  `DoctorVisitReminderScheduler.rescheduleAll()`. Mirrors the Vaccine reminder settings section.

### Export / import layer (`export/`)
- `export/domain/model/BackupData.kt` — bump `CURRENT_BACKUP_FORMAT_VERSION` `5 → 6`; add
  `doctorVisits: List<DoctorVisitBackup> = emptyList()` and `visitQuestions:
  List<VisitQuestionBackup> = emptyList()` with their `@Serializable` data classes.
- `export/domain/BackupSource.kt` — add `doctorVisits` + `visitQuestions` to `TrackingSnapshot`.
- `export/data/BackupSourceImpl.kt` — read `getAllVisitsOnce()` / `getAllQuestionsOnce()` inside
  the existing transaction.
- `export/data/BackupConverters.kt` — `DoctorVisitEntity.toBackup()` / `…toEntity()` and the
  question equivalents.
- `export/data/BackupImporterImpl.kt` — `mergeDoctorVisits(data)` (identity dedup on
  `[date, providerName, createdAt]`) and `mergeVisitQuestions(data)` (dedup on `[text, createdAt]`),
  re-linking `visitId` by mapping old→new visit ids; add counts to `ImportCounts`. After import,
  call `DoctorVisitReminderScheduler.rescheduleAll()`.
- `export/domain/usecase/ExportCsvUseCase.kt` — add `"doctor_visits"` and `"visit_questions"` CSV
  sections.
- `export/domain/usecase/GeneratePdfReportUseCase.kt` + `export/data/PdfReportGenerator.kt` —
  include a visits section (upcoming + past) in the report.
- `ValidateBackupUseCase` — extend the version-upgrade branch so a `5`-version backup loads with
  the new fields defaulted to empty (forward-compatible).

### Partner sync layer (`sharing/`) — read-only, visits only
- `sharing/domain/model/ShareSnapshot.kt` — add `doctorVisits: List<DoctorVisitSnapshot> =
  emptyList()` and the `DoctorVisitSnapshot` data class (`date: Long`, `providerName: String?`,
  `notes: String?`). Questions are **not** included.
- `sharing/usecase/SnapshotSources.kt` — add `doctorVisit: DoctorVisitRepository` collaborator.
- `sharing/domain/model/DomainToSnapshot.kt` — map `DoctorVisit` → `DoctorVisitSnapshot`.
- `sharing/data/firebase/FirestoreSnapshotMapping.kt` — serialize/deserialize the new field to/
  from Firestore (defaulting to empty for older snapshots).
- `sharing/usecase/SyncToFirestoreUseCase.kt` — include visits when building the snapshot.
- Partner dashboard UI — render a read-only visits section/card (consistent with how diapers /
  growth render on the partner side). Keep all Firebase-specific work inside `sharing/` and
  `di/SharingModule.kt`.

### i18n
- `res/values/strings.xml` + `res/values-pt-rBR/strings.xml` — tile label, screen titles, field
  labels, the attach-questions section, snapshot attach/view labels, answered/open and
  upcoming/past group headers, empty states, the countdown, the reminder channel name + title +
  body (`%1$s` provider, `%2$d` days), the reminder settings section, the partner visits section.
  Strings are added within each UI/settings PR (no separate i18n issue).

## Data Flow

```
DoctorVisitSheet ──onSave──▶ DoctorVisitViewModel ──▶ Add/EditDoctorVisitUseCase ──▶ DoctorVisitRepository ──▶ DoctorVisitDao
                                   │                          (attaches selected inbox questions)
                                   ├──▶ DoctorVisitReminderScheduler.schedule/cancel ──▶ AlarmManager
                                   └──▶ AttachSnapshotToVisitUseCase ──▶ repository (sets label + timestamp)

VisitQuestionsScreen ──▶ VisitQuestionsViewModel ──▶ Add/Toggle/DeleteVisitQuestionUseCase ──▶ repository ──▶ DAO (inbox: visit_id IS NULL)
HomeScreen ◀── DoctorVisitUiState ◀── ObserveDoctorVisitSummaryUseCase ◀── observeAllVisits()+observeInboxQuestions() ◀── Room
AlarmManager ──fire──▶ DoctorVisitReminderReceiver ──▶ NotificationHelper.showDoctorVisitReminder
Boot/TimeSet ──▶ DoctorVisitReminderBootReceiver ──▶ rescheduleAll() ──▶ getUpcomingVisitsAfter(now)
Backup/CSV/PDF ◀── getAllVisitsOnce()/getAllQuestionsOnce()   |   Partner sync ──▶ ShareSnapshot.doctorVisits ──▶ Firestore (read-only)
"View snapshot" ──▶ GenerateVisitSnapshotUseCase ──▶ GeneratePdfReportUseCase / ExportCsvUseCase (existing)
```

## Error Handling

- Use cases let exceptions propagate (no `Result<>` wrapper, per project anti-goals).
- Validation failures (blank question text) surface as `validationError`; nothing is persisted.
  A visit with no provider name and no notes is allowed (date is the only required field).
- Delete is undoable: deleting a visit (undo re-inserts it, re-attaches its questions, re-arms
  its reminder if still upcoming); deleting a question (undo re-inserts it).
- Deleting a visit detaches its questions back to the inbox rather than deleting them.
- Editing a non-existent row is a no-op update; the observed Flow reconciles the UI.
- Attaching concurrently-deleted questions is a no-op for the missing ids.
- A reminder that fires for a visit since deleted/moved-to-past is suppressed by the receiver's
  re-validation. Dropped inexact alarms re-arm on the next `BOOT_COMPLETED`.
- The snapshot "View" action degrades gracefully if export generation fails (snackbar; the visit
  record is unaffected).
- Partner sync is read-only and additive; older partner snapshots without `doctorVisits` default
  to an empty list (no crash).

## Testing

- **Unit (`src/test/`):** JUnit 5 + MockK + Turbine. Entity↔domain mapping (both entities); each
  use case (visit add/edit/delete incl. question attach/detach reconciliation and the
  delete-detaches-to-inbox behavior, reminder schedule/cancel via verified mocks; question
  add/toggle/delete; attach snapshot; `GenerateVisitSnapshotUseCase` delegation);
  `isUpcoming`/`hasSnapshot`; `ObserveDoctorVisitSummaryUseCase`; `DoctorVisitSettingsRepository`
  allowlist sanitization; reminder trigger-time math.
- **DAO:** Robolectric + Room in-memory; visit + question CRUD/observe, `observeInboxQuestions`
  filtering, `attachQuestions`/`detachQuestionsForVisit`, `getUpcomingVisitsAfter`,
  `observeAllVisits` ordering.
- **Migration:** `MigrationTestHelper` 14→15 round-trip (both tables + indices).
- **Compose UI (`src/androidTest/`):** question inbox add/expand/answer/delete; visit sheet log +
  attach questions; history upcoming/past groups + edit/delete; Home tile countdown / count /
  empty state.
- **Export/import:** convert round-trip (visits + questions), `merge*` identity dedup and
  visitId re-linking, CSV sections, PDF section, backup-format version bump (a `5`-version backup
  loads with empty new fields).
- **Partner sync:** `DomainToSnapshot` mapping, Firestore mapping round-trip (missing field →
  empty), `SyncToFirestoreUseCase` includes visits, partner dashboard renders the visits section.

## Proposed Issue Breakdown (one per PR)

1. **Data layer** — `DoctorVisit` (+ helpers), `VisitQuestion`, `DoctorVisitSummary`; both
   entities + mapping; `DoctorVisitDao`; `DoctorVisitRepository`(+Impl); DI bindings; DB v15 +
   `MIGRATION_14_15`. Unit + DAO + migration tests.
2. **Domain use cases** — all visit + question + snapshot use cases. (Reminder calls stubbed via
   the scheduler interface introduced here as a no-op default until issue 7.) Unit tests.
3. **Slate theme tokens** — Slate Blue-Grey token set (light + dark) + `doctorVisitColors()` in
   `Color.kt`; design-system preview entry. Small, isolated.
4. **Question inbox UI** — `VisitQuestionsScreen` + `VisitQuestionsViewModel` (add, expand
   full-screen, toggle answered, delete-with-undo), `VISIT_QUESTIONS` route + nav graph. Strings.
   Tests + Compose UI test.
5. **Visit add/edit UI + Home tile** — `HomeTile.DOCTOR_VISIT`, `DoctorVisitHomeCard`,
   `DoctorVisitSheet` (date, provider name, notes, attach-inbox-questions, snapshot attach/view),
   `DoctorVisitScreen`, `DoctorVisitViewModel`/`UiState`, Home wiring, `DOCTOR_VISIT` route + nav
   graph. Strings. Tests + Compose UI test.
6. **Visit history screen** — `DoctorVisitHistoryScreen` (upcoming/past groups, edit,
   delete-with-undo), `DOCTOR_VISIT_HISTORY` route + nav graph. Strings. Tests + UI test.
7. **Reminders** — `DoctorVisitSettingsRepository`(+Impl), `DoctorVisitReminderScheduler`
   (+Manager), `DoctorVisitReminderReceiver` + boot receiver, manifest entries, notification
   channel + `showDoctorVisitReminder`, DI binding, wiring the use cases (issue 2) to
   schedule/cancel, settings section UI (toggle + days picker). Strings. Unit + receiver/scheduler
   tests.
8. **Export / import** — `DoctorVisitBackup` + `VisitQuestionBackup` + backup-format version bump
   (`5 → 6`), `TrackingSnapshot`/`BackupSourceImpl` reads, `BackupConverters`,
   `BackupImporterImpl` merges with visitId re-linking, `ExportCsvUseCase` sections, PDF report
   section, `ValidateBackupUseCase` upgrade branch, re-arm reminders after import. Unit tests.
9. **Partner sync (read-only)** — `DoctorVisitSnapshot` in `ShareSnapshot`, `SnapshotSources`
   collaborator, `DomainToSnapshot` mapping, `FirestoreSnapshotMapping` round-trip,
   `SyncToFirestoreUseCase` inclusion, partner dashboard visits section. Strings. Unit tests.

Dependencies: issues 2–9 depend on issue 1 (data layer). Issue 5 also depends on issues 2 and 3.
Issues 4 and 6 depend on issue 2. Issue 7 depends on issue 2 (wires the use cases). Issue 8
depends on issues 1–2 (and 7 for the post-import re-arm). Issue 9 depends on issues 1–2. Issues
3, 4, 5, 6 can otherwise proceed in parallel. Each is independently shippable. Final granularity
is set when these are converted to Linear issues with implementation plans.

## Risks / Notes

- DB version bump touches `BabyTrackerDatabase` (shared file); keep the migration additive.
- `HomeTile` ordering is auto-reconciled; verify the new tile lands in a sensible default slot.
- The snapshot feature reuses the existing export generators — confirm
  `GeneratePdfReportUseCase` / `ExportCsvUseCase` signatures at implementation time.
- Backup-format bump `5 → 6` is forward-compatible (older importers ignore the defaulted fields);
  restore dedups by identity and re-links `visitId` so re-imports never duplicate or orphan rows.
- Inexact alarms can be delayed by Doze; acceptable for "a few days before" and re-armed on boot.
  Confirm `RECEIVE_BOOT_COMPLETED` is already declared (it is, for vaccine/predictive sleep).
- Partner sync is read-only and additive to `ShareSnapshot`; verify the Firestore mapping
  defaults the new field to empty for snapshots written by older app versions.
- Questions are deliberately private (not synced); only visit records reach the partner.
