# Vaccine "To schedule" status — design

Date: 2026-06-22
Status: Approved (brainstorm), pending implementation plan

## Summary

Add a third vaccine status, `TO_SCHEDULE`, that sits before `SCHEDULED` in the lifecycle. A
to-schedule dose is one the parent knows the baby needs and has a target date for, but has **not yet
booked an appointment** for. The parent can be nudged to book it, can flip it to `SCHEDULED` in one
tap, or can mark it given directly. To-schedule doses render distinctly (own section + tinted rows).

Lifecycle:

```
TO_SCHEDULE ──(book appt)──▶ SCHEDULED ──(mark given)──▶ ADMINISTERED
     │                                                        ▲
     └──────────────────(mark given directly)─────────────────┘
```

## Background / current state

- `VaccineStatus` = `{ SCHEDULED, ADMINISTERED }`, stored as a **String** in the `vaccines.status`
  Room column. A new enum value therefore needs **no DB migration** (DB stays v15, backup stays v6).
- `toVaccineStatusSafe()` maps unknown/corrupt stored values to `ADMINISTERED` (so a stray value never
  fabricates a future reminder). This degrade path is reused for forward-compat.
- Reminders are one-shot `AlarmManager` alarms fired at `scheduledDate − leadDays`
  (`VaccineReminderManager`, `computeReminderTriggerAtMs`). Every reminder path keys off
  `scheduledDate` and currently early-returns unless `status == SCHEDULED`.
- The dashboard (`VaccineDashboardViewModel`) splits all records into overdue / future (both
  `SCHEDULED`) and given (`ADMINISTERED`); hero = most overdue, else next up.
- Settings (`VaccineSettingsRepository`) hold a master `reminderEnabled` flag and a single
  `reminderLeadDays` (allowed set `{1,3,7,14}`, default 7).

## Decisions (from brainstorm)

| # | Decision |
|---|----------|
| 1 | TO_SCHEDULE reuses the existing `scheduledDate` field as its **target date** (no new column). Status flag alone distinguishes the two date-bearing states. |
| 2 | Target date is **required** and **must be in the future at creation** (mirrors `SCHEDULED` add validation). |
| 3 | Reminder for to-schedule fires at `targetDate − toScheduleLeadDays`, using a **new, separate** settings value (default 14 days), distinct from the appointment lead. The master `reminderEnabled` toggle is **shared**. |
| 4 | "Mark as scheduled" = **one-tap status flip**, date unchanged (edit sheet still available to change the date). |
| 5 | **Direct mark-given is allowed** from TO_SCHEDULE (TO_SCHEDULE → ADMINISTERED). |
| 6 | Dashboard shows a dedicated **"To schedule"** section with distinct row background; it **never takes the hero slot**. |

## Design

### 1. Domain & data

- `VaccineStatus` gains `TO_SCHEDULE`.
- `toVaccineStatusOrNull()` maps the `TO_SCHEDULE` name; `toVaccineStatusSafe()` default stays
  `ADMINISTERED`.
- `VaccineEntity` / `VaccineRecord` unchanged — `scheduledDate` carries the target date.
- `isOverdue(...)` currently gates on `status == SCHEDULED`. A to-schedule dose whose target day has
  passed is "past target". Keep `isOverdue` scheduled-only (hero stays scheduled-driven, per decision
  6) and add a separate `isPastTarget(now, zone)` predicate (`status == TO_SCHEDULE && targetDay <
  today`) used only for in-section flagging.

### 2. Use cases

- **`AddVaccineRecordUseCase`**: add a `TO_SCHEDULE` branch — require non-blank name, require
  `date.isAfter(now())`, persist with `scheduledDate = date`, then arm the to-schedule nudge
  (best-effort, same `runCatching` pattern as scheduled).
- **`MarkVaccineScheduledUseCase`** (new): given an id, load the record; if `status == TO_SCHEDULE`
  write `status = SCHEDULED` (date unchanged) and re-arm the alarm (now under the appointment lead).
  Idempotent — a non-TO_SCHEDULE record is a no-op. Mirror the guard style of
  `MarkVaccineAdministeredUseCase`.
- **`MarkVaccineAdministeredUseCase`**: broaden the transition guard from `== SCHEDULED` to
  `in { SCHEDULED, TO_SCHEDULE }` so a to-schedule dose can be marked given directly. Still rejects a
  future administered date and still skips already-administered rows.
- **Undo**: `MarkVaccineScheduled` needs an undo path for the dashboard snackbar (write the original
  TO_SCHEDULE record back), reusing the immediate-commit + pending-holder pattern already in
  `VaccineDashboardViewModel`. `UndoMarkVaccineAdministeredUseCase` already restores a full record, so
  a direct TO_SCHEDULE→ADMINISTERED undo restores the original TO_SCHEDULE record verbatim.
- **`EditVaccineRecordUseCase`**: ensure the TO_SCHEDULE branch validates/persists the target date the
  same way as SCHEDULED (future date, `scheduledDate` set, `administeredDate` null).

### 3. Reminders & settings

- **`VaccineSettingsRepository`**: add `getToScheduleLeadDays(): Flow<Int>` /
  `setToScheduleLeadDays(days: Int)`. New DataStore key `vaccine_to_schedule_lead_days`, allowed set
  `{7, 14, 30}` (booking needs more runway), default **14**. Sanitize like the existing lead.
- **`VaccineReminderManager.schedule`**: branch on status —
  - `SCHEDULED` → appt lead (`getReminderLeadDays`), existing copy.
  - `TO_SCHEDULE` → `getToScheduleLeadDays`, to-schedule copy.
  - else → cancel only.
  Both compute `targetDate − lead` via the shared `computeReminderTriggerAtMs`.
- **`VaccineReminderReceiver.handle`**: accept `status in { SCHEDULED, TO_SCHEDULE }`; pick the
  notification title/body by status ("Time to book {name}" for to-schedule vs the existing upcoming
  copy for scheduled). Still re-queries and suppresses if the record changed/was removed or the master
  toggle is off.
- **Boot / `rescheduleAll`**: `getScheduledFutureAfter` currently filters `status = 'SCHEDULED'`. Add a
  sibling `getToScheduleFutureAfter` (or broaden to `status IN ('SCHEDULED','TO_SCHEDULE')`) so
  to-schedule nudges are re-armed after reboot. `rescheduleAll` arms both sets.

### 4. UI

- **Add/edit sheet** (`VaccineSheet` + `VaccineViewModel`): mode toggle becomes 3-way, ordered
  **To schedule · Scheduled · Administered** (lifecycle order). Default add mode stays `ADMINISTERED`.
  `onModeChange` extends the future-date defaulting (when switching to TO_SCHEDULE and the date is not
  already future, default to tomorrow, same as SCHEDULED). The date field label reads "target date"
  in to-schedule mode. Save validation routes TO_SCHEDULE to the future-date error copy.
- **Dashboard** (`VaccineDashboardViewModel` + `VaccineDashboardScreen`): split records into
  to-schedule / scheduled (overdue+future) / given. Add `toSchedule: List<VaccineRecord>` to the UI
  state, sorted by target date asc then name. New "To schedule" section below the hero, above
  Upcoming. Rows: distinct background tint derived from `VaccinePalette`, a third `StatusDot`
  treatment, and two actions — **Schedule** (one-tap flip) and **Mark given**. Past-target rows get a
  subtle "was due …" marker. To-schedule records never feed the hero (`nextVaccine`/`mostOverdue`
  stay scheduled-only). `givenCount`/View-all gating unchanged.
- **History** (`VaccineHistoryScreen` / `VaccineHistoryViewModel`): render TO_SCHEDULE rows with the
  same dot + tint treatment so the two surfaces stay visually consistent.
- **Settings** (`VaccineSettingsScreen` / `VaccineSettingsViewModel`): add a "Remind me to schedule"
  lead-days row (choices 7/14/30), gated by the same master enable toggle.
- Detailed Compose work (exact tint token, dot treatment, segmented-control layout) is delegated to
  the `ui-designer` skill during implementation.

### 5. Export / backup / validation

- No backup-format-version bump: status is an existing String field gaining a new value.
- **`ValidateBackupUseCase`**: `validateVaccineInvariants` has an exhaustive `when
  (status.toVaccineStatusSafe())` over SCHEDULED/ADMINISTERED — add a `TO_SCHEDULE` branch requiring
  `scheduledDate != null` (target date). The `toVaccineStatusOrNull()` enum check in
  `validateContent` accepts the new value once the enum includes it.
- An older app importing a `TO_SCHEDULE` backup degrades that record to `ADMINISTERED` via
  `toVaccineStatusSafe()` (acceptable, never fabricates a reminder).
- **`PdfReportGenerator`**: render a label for the TO_SCHEDULE status (e.g. "To schedule").

### 6. i18n

New string resources in **`en` + `pt-BR`**: section title, status label, "Schedule" action,
to-schedule notification title/body, settings row label + description, target-date field label.

### 7. Testing

- `VaccineStatusTest`: TO_SCHEDULE round-trips through `toVaccineStatusOrNull`/`Safe`.
- Use cases: `AddVaccineRecordUseCase` TO_SCHEDULE branch (future-date required, arms nudge);
  `MarkVaccineScheduledUseCase` (flip + re-arm + idempotent); `MarkVaccineAdministeredUseCase` direct
  TO_SCHEDULE→ADMINISTERED.
- Reminder: `VaccineReminderManager.schedule` picks the right lead per status;
  `VaccineReminderReceiver` shows the correct copy; boot re-arm includes to-schedule.
- `VaccineDashboardViewModelTest`: sectioning (to-schedule excluded from hero, sorted, past-target
  flagged).
- Settings repo: new lead getter/setter sanitize + default.
- `ValidateBackupUseCase`: TO_SCHEDULE with/without target date.

## Out of scope

- Recurring nudges (single one-shot reminder only, re-armed on boot).
- A separate per-reminder-type enable toggle (one shared master toggle).
- Partner-sync changes.
- New DB or backup schema versions.

## Open defaults (flagged, low-risk to revisit)

- To-schedule lead default 14, choices `{7, 14, 30}`.
- Target date required + future at creation.
- One shared master enable toggle.
