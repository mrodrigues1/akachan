# Vaccine Export / Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-199

**Goal:** Include vaccine records in the data backup so they survive reinstall — JSON backup (with restore), CSV export, and the PDF report — mirroring the Diaper export/import slice.

**Architecture:** The `export/` module already round-trips every tracker through `BackupData` (versioned JSON), `TrackingSnapshot` (one-shot DAO reads), `BackupConverters` (entity↔backup), `BackupImporterImpl` (identity-dedup merge + `ImportCounts`), `ExportCsvUseCase`, and the PDF report. This plan adds a `VaccineBackup`, bumps `CURRENT_BACKUP_FORMAT_VERSION` `4 → 5`, and threads vaccines through each stage exactly as `DiaperBackup` is threaded. After a restore, it re-arms vaccine reminders.

**Tech Stack:** Kotlin, Kotlinx Serialization, Room, Hilt; JUnit 5 + MockK.

**Dependencies:** Plan 1 (`VaccineEntity`, `VaccineDao.getAllOnce`, `VaccineRepository`), Plan 2 (`VaccineReminderScheduler` interface — for the post-import re-arm; effective once Plan 6 lands the real manager).

**Suggested implementation branch:** `feat/vaccine-export-import`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `VaccineBackup` + format version bump

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt`

- [ ] **Step 1:** Bump `const val CURRENT_BACKUP_FORMAT_VERSION = 4` → `5`. Add `vaccines: List<VaccineBackup> = emptyList()` to `BackupData` (defaulted, so older readers ignore it and older backups deserialize with an empty list). Add the model:

```kotlin
@Serializable
data class VaccineBackup(
    val id: Long,
    val name: String,
    val doseLabel: String? = null,
    val status: String,
    val scheduledDate: Long? = null,
    val administeredDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
)
```

- [ ] **Step 2: Commit** `feat(vaccine): add VaccineBackup model and bump backup format to v5`

---

### Task 2: Backup version migration + vaccine validation in `ValidateBackupUseCase`

Bumping the format version turns every existing backup into an "older" backup, so the version gate and content validation must learn about v5 and about vaccines. Without this, **current users' v4 backups are rejected on restore** (`migrateOlder()` only handles v1/v2 today and throws for everything else), and corrupt/forged vaccine rows would import unchecked. Import is gated by this use case, so it must validate vaccines.

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ValidateBackupUseCaseTest.kt`

- [ ] **Step 1: Migrate older versions forward.** `migrateOlder()` currently does `1, 2 -> ...drop milestones...` then `else -> throw`. After the bump, v3 **and** v4 backups would hit `else` and throw `Unsupported backup format`. Add a carry-forward branch (vaccines defaults to empty, so no data synthesis needed):

```kotlin
private fun migrateOlder(data: BackupData): BackupData = when (data.backupFormatVersion) {
    1, 2 -> data.copy(backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION, milestones = emptyList())
    3, 4 -> data.copy(backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION) // vaccines defaults to emptyList()
    else -> throw InvalidBackupException("Unsupported backup format v${data.backupFormatVersion}")
}
```

- [ ] **Step 2: Validate vaccine enums + invariants.** In `validateContent`'s enum block add `data.vaccines.forEach { it.status.toVaccineStatusOrNull() ?: throw IllegalArgumentException(it.status) }`. Add `validateVaccineInvariants(data)` to `validateScalarInvariants`:

```kotlin
private fun validateVaccineInvariants(data: BackupData) {
    data.vaccines.forEach { v ->
        if (v.name.isBlank()) bad("vaccine ${v.id} has a blank name")
        if (v.createdAt < 0) bad("vaccine ${v.id} has negative created time")
        if (v.scheduledDate != null && v.scheduledDate < 0) bad("vaccine ${v.id} has negative scheduled date")
        if (v.administeredDate != null && v.administeredDate < 0) bad("vaccine ${v.id} has negative administered date")
        when (v.status.toVaccineStatusSafe()) {
            VaccineStatus.SCHEDULED -> if (v.scheduledDate == null) bad("scheduled vaccine ${v.id} has no scheduled date")
            VaccineStatus.ADMINISTERED -> if (v.administeredDate == null) bad("administered vaccine ${v.id} has no administered date")
        }
    }
}
```

(add imports for `VaccineStatus`, `toVaccineStatusOrNull`, `toVaccineStatusSafe`)

- [ ] **Step 3: Test** — a v4 backup JSON validates under format v5 with `vaccines` empty (restore-after-reinstall regression); a valid vaccine passes; blank name / missing-date-for-status / unknown status / negative timestamp are each rejected. Update any existing test that hardcodes the format version number. Extend `ValidateBackupUseCaseTest`.
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(vaccine): validate vaccines and migrate v3/v4 backups to v5`

---

### Task 3: `TrackingSnapshot` + `BackupSource(Impl)`

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupSource.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt`

- [ ] **Step 1:** Add `val vaccines: List<VaccineBackup> = emptyList()` to `TrackingSnapshot`.
- [ ] **Step 2:** In `BackupSourceImpl`, read `db.vaccineDao().getAllOnce()` inside the existing transaction and map with `toBackup()` (from Task 4), alongside the diaper read.
- [ ] **Step 3: Commit** `feat(vaccine): include vaccines in the backup tracking snapshot`

---

### Task 4: `BackupConverters`

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt`
- Test: `app/src/test/java/com/babytracker/export/data/VaccineBackupConvertersTest.kt`

- [ ] **Step 1:** Add the converters (mirror `DiaperEntity.toBackup` / `DiaperBackup.toEntity`):

```kotlin
fun VaccineEntity.toBackup() = VaccineBackup(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status,
    scheduledDate = scheduledDate,
    administeredDate = administeredDate,
    notes = notes,
    createdAt = createdAt,
)

fun VaccineBackup.toEntity() = VaccineEntity(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status,
    scheduledDate = scheduledDate,
    administeredDate = administeredDate,
    notes = notes,
    createdAt = createdAt,
)
```

- [ ] **Step 2: Test** round-trip for both a scheduled and an administered backup (mirror `DiaperBackupConvertersTest`).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.data.VaccineBackupConvertersTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add vaccine backup converters`

---

### Task 5: Importer merge + counts + reminder re-arm

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupImporter.kt` (`ImportCounts`)
- Modify: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt`

- [ ] **Step 1:** Add `val vaccinesInserted: Int = 0` to `ImportCounts`.
- [ ] **Step 2:** Add `mergeVaccines(data)` mirroring `mergeDiapers`: insert backup rows that are not already present, deduping by identity `[name, status, scheduledDate, administeredDate, createdAt]` (id is autogenerated, so it is excluded from the identity — same as diaper). Wire it into the import flow next to `mergeDiapers` and include the count in the returned `ImportCounts`.
- [ ] **Step 3:** After the import transaction completes, call `vaccineReminderScheduler.rescheduleAll()` (inject `VaccineReminderScheduler`) so imported future scheduled vaccines arm their reminders. With Plan 6's manager this re-arms; with Plan 2's no-op it is harmless.
- [ ] **Step 4: Test** in `BackupImporterImplTest`: importing two vaccines inserts two; re-importing the same data inserts zero (identity dedup); `vaccinesInserted` is reported; `rescheduleAll()` is invoked (verify the mocked scheduler).
- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.export.data.BackupImporterImplTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(vaccine): import vaccines with identity dedup and reminder re-arm`

---

### Task 6: CSV export section

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt`

- [ ] **Step 1:** Add a `"vaccines"` CSV section with header `id,name,dose_label,status,scheduled_date,administered_date,notes,created_at`, mirroring the diaper section (reuse the existing CSV-escaping helper for `name`/`notes`).
- [ ] **Step 2: Test** the vaccine rows render and escape correctly (extend `ExportCsvUseCaseTest`).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.domain.usecase.ExportCsvUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add vaccines section to CSV export`

---

### Task 7: PDF report section

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/PdfReportRenderer.kt` (add `vaccines`)
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt`
- Test: `app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt`

- [ ] **Step 1:** Add `vaccines: List<VaccineRecord> = emptyList()` to the PDF render model (mirror `diapers: List<DiaperChange>`). Populate it in `GeneratePdfReportUseCase` from the vaccine source.
- [ ] **Step 2:** In `PdfReportGenerator`, render a **Vaccines** section: an "Administered" list (name, dose, date) and an "Upcoming" list (name, dose, scheduled date). Mirror the diaper section's layout/pagination.
- [ ] **Step 3: Test** extend `PdfReportGeneratorTest` to include vaccines without overflow/crash.
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.export.data.PdfReportGeneratorTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(vaccine): add vaccines section to PDF report`

---

## Acceptance Criteria

- `./gradlew test` passes, including new validation/converter/import/CSV/PDF tests and the existing backup tests (which key off `CURRENT_BACKUP_FORMAT_VERSION`).
- A JSON backup includes vaccines; restoring on a fresh install recreates them and re-arms future scheduled reminders.
- Re-importing the same backup inserts no duplicate vaccines (identity dedup).
- A **v4** backup validates and migrates to v5 (regression for restore-after-reinstall); a v6 backup is still rejected by `ValidateBackupUseCase`.
- Imported vaccines are validated before insert: known `status`, non-blank name, status↔date invariant, non-negative timestamps.
- CSV and PDF outputs contain the vaccine data.

## Self-Review Notes

- The format bump `4 → 5` is forward-compatible: `vaccines` is a defaulted field so older importers ignore it and older backups deserialize cleanly; `ValidateBackupUseCase`'s existing older/newer handling is unchanged.
- Identity dedup excludes the autogenerated `id` (matches `mergeDiapers`); two genuinely identical vaccines with the same name/status/dates/createdAt are treated as the same row — acceptable and consistent with the other trackers.
- Post-import `rescheduleAll()` keeps reminders consistent after a restore; it depends only on the Plan 2 interface, so this plan stays mergeable before Plan 6 (the call is a no-op until the real manager is bound).
- `VaccineBackup` mirrors `VaccineEntity` 1:1 (status as TEXT, dates as nullable epoch-ms), so converters are pure field copies.
- `migrateOlder()` previously threw for v3/v4 (only v1/v2 were handled); Task 2 adds the carry-forward branch so existing users' v4 backups validate under v5 instead of failing on restore. Vaccine invariants are validated before import, matching how the other trackers are checked — without it a corrupt/forged backup could persist rows that summaries, history grouping, and reminder scheduling disagree on.
