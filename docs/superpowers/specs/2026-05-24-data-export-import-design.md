# Data Export & Import — Design

**Date:** 2026-05-24
**Linear project:** [Data Export & Import](https://linear.app/akachan/project/data-export-and-import-70887aa926e4) (AKA)
**Status:** Approved design

## Goal

Allow parents to export tracking data for pediatric appointments (PDF) and to back up / restore all data as a JSON file, avoiding data loss if the app is uninstalled. CSV is a spreadsheet-friendly export-only format.

## Scope

- **PDF export** — formatted feeding + sleep summary for a selectable date range (7d / 14d / 30d / custom). Intended for doctor visits.
- **Full data export (JSON)** — all sessions, sleep records, pumping, milk bags, baby profile, and user settings. Canonical round-trip backup format.
- **CSV export** — per-table, human/spreadsheet friendly. Export-only, **not** importable.
- **Full data import (JSON)** — merge a previously exported backup into the current database.
- **Share sheet integration** — share generated files via `Intent.ACTION_SEND` (email, Drive, etc.).

## Non-goals

- Cloud backup (local file only).
- Real-time sync via export.
- Excel format.
- CSV import (relational data makes multi-table CSV parsing fragile).
- Backing up sharing/Firebase state (`AppMode`, `shareCode`) — device-specific.

## Key decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| PDF engine | `android.graphics.pdf.PdfDocument` + Canvas | No new deps, full layout control, direct use of design tokens. |
| Import strategy | Merge, dedupe by content | Non-destructive; safe to import into a populated DB. |
| Backup scope | All data + user settings; exclude sharing state | Round-trip fidelity without restoring stale Firebase state on a new device. |
| Format roles | JSON = backup + import; CSV = export-only | Avoids fragile CSV parsing for relational data. |
| JSON library | `kotlinx.serialization` (add to version catalog + Kotlin plugin) | Cleanest Kotlin-native serialization for the nested DTO. |
| Decomposition | 4 PRs (see below) | Each slice independently shippable and reviewable. |

## Data scope

Room DB is at version 3. Tables to back up:

- `breastfeeding_sessions`
- `sleep_records`
- `pumping_sessions`
- `milk_bags` (FK `source_session_id` → `pumping_sessions.id`, `ON DELETE SET NULL`)

DataStore preferences to back up (exclude `appMode`, `shareCode`):

- Baby profile (name, birth date, allergies)
- Feed limits (`maxPerBreastMinutes`, `maxTotalFeedMinutes`)
- Wake time
- Theme config
- Notification / predictive prefs (auto-update, rich notifications, predictive enabled + lead, quiet hours start/end, nap reminder enabled + delay)

## Architecture

New top-level `export/` package mirroring the existing layout. Follow project conventions: single-responsibility use cases with `suspend operator fun invoke(...)`, repository/service classes `@Singleton` via Hilt, entity↔DTO via extension functions (no Mapper classes), no `Result<>` wrappers — exceptions propagate to the ViewModel.

```
export/
├── domain/
│   ├── model/
│   │   └── BackupData.kt          # @Serializable snapshot + per-table backup DTOs
│   └── usecase/
│       ├── ExportBackupUseCase.kt     # repos → BackupData → JSON string
│       ├── ExportCsvUseCase.kt        # per-table CSV blobs (export-only)
│       ├── GeneratePdfReportUseCase.kt# range → PDF file Uri
│       └── ImportBackupUseCase.kt     # JSON → BackupData → merge into DB
├── data/
│   ├── BackupFileWriter.kt        # write to cache file via FileProvider → shareable Uri
│   ├── BackupFileReader.kt        # read SAF Uri → JSON → BackupData (+ schema validation)
│   └── PdfReportGenerator.kt      # PdfDocument + Canvas, manual pagination
└── di/
    └── ExportModule.kt            # provide Json, bind writer/reader/generator
```

UI lives in the existing `ui/settings/` ("Data" section) plus a `DataExportViewModel`.

### Backup model

```kotlin
@Serializable
data class BackupData(
    val schemaVersion: Int = 3,        // == Room DB version at export time
    val appVersion: String,
    val exportedAt: Long,              // epoch ms
    val baby: BabyBackup?,
    val settings: SettingsBackup,
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
)
```

Per-table backup DTOs hold every persisted field (full fidelity — distinct from the lossy, read-only Firestore `*Snapshot` models). Entity↔backup conversion via extension functions (`fun BreastfeedingEntity.toBackup()`, `fun BreastfeedingBackup.toEntity()`).

## PR breakdown

PRs are **sequential** — PR1 lands the `BackupData` model + serialization that PR2/PR3 depend on.

### PR1 — Export infrastructure + JSON/CSV export + share sheet

- Add `kotlinx-serialization-json` and the Kotlin serialization plugin to the version catalog + `app/build.gradle.kts`.
- `BackupData.kt` + per-table backup DTOs + entity↔backup extension functions.
- Add `suspend getAll*Once()` snapshot reads to DAOs where only `Flow` exists (one-shot reads for export).
- `ExportBackupUseCase` — gather all repos → `BackupData` → JSON string.
- `ExportCsvUseCase` — per-table CSV (export-only). One file per table (zipped) or separate files.
- `BackupFileWriter` + `FileProvider` config (cache dir) → shareable `Uri`.
- Share via `Intent.ACTION_SEND`.

### PR2 — PDF export

- `PdfReportGenerator` — `PdfDocument` + Canvas, manual pagination. Feeding + sleep summary tables. Uses design tokens (Pink/Blue families) and `AkachanTypography` sizes via `Paint`.
- `GeneratePdfReportUseCase(range)` — pull completed records in range → build doc → write file → return `Uri`.
- Add date-range queries per DAO (extend the existing `getCompletedRecordsSince` pattern).

### PR3 — Import / merge engine

- `BackupFileReader` — SAF `ACTION_OPEN_DOCUMENT` → read `Uri` → parse JSON → `BackupData`. Validate `schemaVersion`: reject a newer version with a user-facing error; handle older versions if/when migrations exist.
- `ImportBackupUseCase` — **merge by content**:
  - Dedupe keys: breastfeeding = `startTime`+`startingSide`; sleep = `startTime`+`sleepType`; pumping = `startTime`+`breast`; milk bag = `collectionDate`+`volumeMl`+`createdAt`. Skip rows already present.
  - FK remapping: insert `pumping_sessions` first, capture new autoGen IDs, map old→new, rewrite `milkBag.sourceSessionId` before insert. Missing parent → `null` (matches `SET NULL`).
  - Settings: overwrite from backup where present; baby profile overwrite if present.
  - Wrap all inserts in a single Room `@Transaction` — atomic, rollback on failure (no partial writes).

### PR4 — Settings UI wiring

- Settings "Data" section: **Export PDF** (range-picker dialog), **Export backup (JSON)**, **Export CSV**, **Import backup** (file picker + confirmation dialog showing the record counts to be merged).
- `DataExportViewModel` exposing `StateFlow<DataExportUiState>` (idle / working / success+Uri / error).

## Error handling

Exceptions propagate to the ViewModel and surface as a `DataExportUiState` error (existing convention). Import validates schema version and malformed JSON up front; the merge transaction rolls back on any failure, leaving the DB untouched.

## Testing

- **Unit (MockK):** export round-trip (export → import → assert equality), dedupe logic, FK remap correctness, schema-version reject, settings exclusion (no `appMode`/`shareCode` in output).
- **PDF:** Robolectric smoke test — generated bytes non-empty, expected page count for a known record set.
- All tests pass before each PR. Architecture tests (`@Tag("architecture")`) must still pass for the new package.
