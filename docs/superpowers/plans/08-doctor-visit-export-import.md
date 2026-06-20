# Doctor Visit Export / Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-208

**Goal:** Include doctor visits **and** their questions in the JSON backup (with restore), the CSV export, and the PDF report — bumping the backup format version `5 → 6` and keeping older backups loadable.

**Architecture:** Mirrors the shipped Vaccine export/import path. Add `@Serializable` backup DTOs, extend `TrackingSnapshot`, read both tables in `BackupSourceImpl`, add converters, merge with identity dedup + `visitId` re-linking in `BackupImporterImpl`, add CSV sections, add a PDF section, and extend `ValidateBackupUseCase`'s upgrade branch. After import, re-arm reminders.

**Tech Stack:** Kotlinx Serialization JSON 1.8.1, Room, Coroutines; JUnit 5 + MockK.

## Global Constraints

- `CURRENT_BACKUP_FORMAT_VERSION` is currently `5`; this plan bumps it to `6`. **Confirm** the value at implementation time (`rg "CURRENT_BACKUP_FORMAT_VERSION"`).
- New backup fields default to empty lists so a v5 backup loads cleanly (forward/backward compatible).
- Import dedups by identity (never duplicate on re-import) and re-links `visit_id` old→new.
- No new remote integrations; this stays within `export/`.

**Dependencies:** Plan 1 (data — `getAllVisitsOnce`, `getAllQuestionsOnce`), Plan 7 (reminders — the post-import `rescheduleAll()`; if plan 7 has not merged, call the no-op scheduler from plan 2 and the call becomes real once plan 7 lands).

**Suggested implementation branch:** `feat/doctor-visit-export-import`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Backup DTOs + format version bump

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/model/BackupDataVersionTest.kt` (if a version test exists; otherwise add a focused one)

- [ ] **Step 1: Add the DTOs + fields.** Append two `@Serializable` data classes and two defaulted lists to `BackupData`. Bump the version constant.

```kotlin
// bump:
const val CURRENT_BACKUP_FORMAT_VERSION = 6

// in BackupData (add fields, keep all existing ones):
val doctorVisits: List<DoctorVisitBackup> = emptyList(),
val visitQuestions: List<VisitQuestionBackup> = emptyList(),

// new DTOs (same file or a sibling, matching where VaccineBackup lives):
@Serializable
data class DoctorVisitBackup(
    val id: Long,
    val date: Long,
    val providerName: String? = null,
    val notes: String? = null,
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Long? = null,
    val createdAt: Long,
)

@Serializable
data class VisitQuestionBackup(
    val id: Long,
    val text: String,
    val answered: Boolean = false,
    val visitId: Long? = null,
    val createdAt: Long,
)
```

- [ ] **Step 2: Test** that a JSON string at `backupFormatVersion = 5` (without the new fields) deserializes with empty `doctorVisits`/`visitQuestions` (kotlinx serialization uses the defaults). Mirror the vaccine version test.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.domain.model.BackupDataVersionTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add backup DTOs and bump format to v6`

---

### Task 2: `TrackingSnapshot` + `BackupSourceImpl` reads

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupSource.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt`

- [ ] **Step 1: Extend `TrackingSnapshot`** with `doctorVisits: List<DoctorVisit>` and `visitQuestions: List<VisitQuestion>` (domain types, matching how the snapshot holds other domain lists).
- [ ] **Step 2: Read both tables** in `BackupSourceImpl` inside the existing transaction: `db.doctorVisitDao().getAllVisitsOnce().map { it.toDomain() }` and `getAllQuestionsOnce().map { it.toDomain() }` (or via the repository if the source uses repositories — match the existing pattern for vaccine/diaper).
- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): read visits + questions into backup source`

---

### Task 3: `BackupConverters`

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupConvertersDoctorVisitTest.kt`

- [ ] **Step 1: Add converters** between domain/entity and the backup DTOs (match the direction the existing converters use — entity↔backup or domain↔backup).

```kotlin
fun DoctorVisit.toBackup(): DoctorVisitBackup = DoctorVisitBackup(
    id = id, date = date.toEpochMilli(), providerName = providerName, notes = notes,
    snapshotLabel = snapshotLabel, snapshotCreatedAt = snapshotCreatedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
)
fun DoctorVisitBackup.toDomain(): DoctorVisit = DoctorVisit(
    id = id, date = Instant.ofEpochMilli(date), providerName = providerName, notes = notes,
    snapshotLabel = snapshotLabel, snapshotCreatedAt = snapshotCreatedAt?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAt),
)
fun VisitQuestion.toBackup(): VisitQuestionBackup =
    VisitQuestionBackup(id = id, text = text, answered = answered, visitId = visitId, createdAt = createdAt.toEpochMilli())
fun VisitQuestionBackup.toDomain(): VisitQuestion =
    VisitQuestion(id = id, text = text, answered = answered, visitId = visitId, createdAt = Instant.ofEpochMilli(createdAt))
```

- [ ] **Step 2: Test** round-trip for a visit (with + without snapshot) and a question (inbox + attached).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.data.BackupConvertersDoctorVisitTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add backup converters`

---

### Task 4: `BackupImporterImpl` merge with visitId re-linking

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt`
- Modify: the `ImportCounts` type (add `doctorVisits` + `visitQuestions` counts)
- Test: `app/src/test/java/com/babytracker/export/data/BackupImporterDoctorVisitTest.kt`

- [ ] **Step 1: Merge visits.** Dedup by identity `[date, providerName, createdAt]`. Build an `oldId → resolvedId` map covering **both** cases: for a visit not already present, insert it and map `oldId → newInsertedId`; for a visit that dedupes against an existing row, map `oldId → existingRowId`. (Without the second case, questions attached to an already-present visit would silently fall back to the inbox on import.)
- [ ] **Step 2: Merge questions.** Dedup by `[text, createdAt]`. Re-link `visitId`: if the imported question had a `visitId`, map it through the `oldId → resolvedId` table from Step 1; only drop to `null`/inbox when **neither** an inserted nor an existing parent resolves. Insert non-duplicates.
- [ ] **Step 3: Counts + re-arm.** Add both counts to `ImportCounts`. After the merge, call `DoctorVisitReminderScheduler.rescheduleAll()` (inject it; if plan 7 hasn't merged it's the no-op).
- [ ] **Step 4: Test** three cases — (a) import a backup with one visit + two questions (one attached, one inbox): the attached question's `visitId` points to the newly-inserted visit id, the inbox one stays null; (b) re-import the same data: 0 new rows (identity dedup); (c) **import a question attached to a visit that already exists** in the DB: the question re-links to the existing visit's id (does NOT fall back to inbox).

```kotlin
// sketch
@Test
fun `import re-links attached question to new visit id and dedups on reimport`() = runTest {
    // build BackupData with DoctorVisitBackup(id=100,...) and
    // VisitQuestionBackup(id=200, visitId=100), VisitQuestionBackup(id=201, visitId=null)
    // first import: visit inserted as newId; attached question's visitId == newId; inbox question visitId null
    // second import of same data: counts show 0 new (identity dedup)
}

@Test
fun `import re-links question to an already-existing matching visit`() = runTest {
    // pre-insert a visit V (date/provider/createdAt = X) so it already exists
    // import BackupData with DoctorVisitBackup(id=100, ...matching X...) + VisitQuestionBackup(id=200, visitId=100)
    // expect: visit deduped (0 new visits), question inserted with visitId == V.id (NOT null/inbox)
}
```

- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.export.data.BackupImporterDoctorVisitTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(doctor-visit): merge visits + questions on import with visitId re-linking`

---

### Task 5: CSV export sections

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt`
- Test: extend the existing CSV use-case test

- [ ] **Step 1: Add two CSV sections** following the existing section format:
  - `doctor_visits`: `id,date,provider_name,notes,snapshot_label,snapshot_created_at,created_at`
  - `visit_questions`: `id,text,answered,visit_id,created_at`
  Escape free-text fields (`notes`, `text`, `provider_name`) per the use case's existing CSV-escaping helper.
- [ ] **Step 2: Test** that both sections appear with header + a sample row and that commas/quotes/newlines in notes are escaped.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.domain.usecase.ExportCsvUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add CSV export sections`

---

### Task 6: PDF report section

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-pt-rBR/strings.xml` (section heading)

- [ ] **Step 1: Add a "Doctor visits" section** to the report — an upcoming list and a past list, each line: date — provider — notes (truncated). Mirror how the vaccine section was added to the generator. Pass the visits into the generator from the use case (it already receives the `TrackingSnapshot`/equivalent).
- [ ] **Step 2: Strings**

```xml
<!-- values/strings.xml -->
<string name="pdf_doctor_visits_heading">Doctor visits</string>
<!-- values-pt-rBR/strings.xml -->
<string name="pdf_doctor_visits_heading">Consultas médicas</string>
```

- [ ] **Step 3: Test** (if `PdfReportGenerator` has unit-testable formatting helpers, assert the section renders the visit lines; otherwise rely on the build + a manual check and note it).
- [ ] **Step 4: Build + run** `./gradlew :app:compileDebugKotlin` and any PDF tests — expect success.
- [ ] **Step 5: Commit** `feat(doctor-visit): add PDF report section`

---

### Task 7: `ValidateBackupUseCase` upgrade branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt`
- Test: extend the existing validate test

- [ ] **Step 1: Extend the version handling** so a `5` (and existing `3, 4`) backup is accepted and upgraded to `CURRENT_BACKUP_FORMAT_VERSION` with the new fields defaulted. Find the `when (data.backupFormatVersion)` branch (currently `3, 4 -> data.copy(backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION)`) and add `5` to it.

```kotlin
3, 4, 5 -> data.copy(backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION)
```

- [ ] **Step 2: Test** that a v5 backup validates/upgrades to v6 and that a v7 (future) backup is still rejected as too new.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): accept v5 backups on upgrade to v6`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- A backup round-trip preserves visits + questions (including attachment links and snapshot refs).
- Re-importing the same backup creates no duplicates (identity dedup).
- Attached questions re-link to the newly-inserted visit ids; orphaned attachments fall back to the inbox.
- CSV has `doctor_visits` + `visit_questions` sections; the PDF report has a Doctor visits section.
- A v5 backup loads on a v6 app; a v7 backup is rejected.
- Reminders are re-armed after import.
- Strings in en + pt-BR.

## Self-Review Notes

- Spec coverage: DTOs + version bump, snapshot source reads, converters, importer merge + re-link, CSV, PDF, validate upgrade, post-import re-arm — all present.
- Backward compatibility verified by the v5-loads test; forward safety by the v7-rejected test.
- `visit_id` re-linking resolves through `oldId → resolvedId` for **both** newly-inserted and already-existing (deduped) parent visits; a question only falls back to the inbox when no parent resolves. Covered by dedicated importer tests (new parent, re-import, already-existing parent).
- The post-import `rescheduleAll()` depends on plan 7's real scheduler but works with plan 2's no-op until then (no ordering hazard).
