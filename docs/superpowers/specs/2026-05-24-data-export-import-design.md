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
| Import strategy | Merge, dedupe by full-row identity | Non-destructive; never silently drops a distinct row; safe to import into a populated DB. |
| Backup scope | All data + user settings; exclude sharing state | Round-trip fidelity without restoring stale Firebase state on a new device. |
| Format roles | JSON = backup + import; CSV = export-only | Avoids fragile CSV parsing for relational data. |
| JSON library | `kotlinx.serialization` (add to version catalog + Kotlin plugin) | Cleanest Kotlin-native serialization for the nested DTO. |
| Decomposition | 4 PRs (see below) | Each slice independently shippable and reviewable. |

## Data scope

> **Schema is verified against the current `main` working tree, not CLAUDE.md.** `CLAUDE.md` still documents Room v2 (breastfeeding + sleep only) and is **stale**. The actual code on `main` is **Room v3**: `BabyTrackerDatabase` declares all four entities below and ships `MIGRATION_1_2` + `MIGRATION_2_3`, and the `pumping_sessions` / `milk_bags` DAOs, entities, and repositories all exist. These tables are **already on main** — this project introduces **no** schema migration; it backs up the shipped schema as-is. (Updating CLAUDE.md's schema table is a worthwhile side cleanup but out of scope for these PRs.)

Room DB is at version 3 (confirmed in code). Tables to back up:

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
    val backupFormatVersion: Int = 1,  // versions the JSON/DTO/settings CONTRACT — bump on any DTO/field/enum/settings change
    val roomSchemaVersion: Int,        // Room DB version at export time — metadata only, NOT the gate
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

**Versioning is a backup contract, not a DB contract.** `backupFormatVersion` is the authoritative compatibility gate and is independent of the Room schema version: the backup carries DataStore-backed settings, enum encodings, and JSON field names that can all change without a Room migration. Rules:

- **Newer than this build** → reject with a user-facing "backup created by a newer app version" error.
- **Equal** → import directly.
- **Older** → run an explicit per-version up-migration on the parsed `BackupData` before import (no migration code needed until the first bump; the slot exists so old backups never get silently misread under a stale version number).
- `roomSchemaVersion` is recorded for diagnostics only and never used to accept/reject. Bump `backupFormatVersion` on **any** change to a DTO shape, field name, enum encoding, default, or the `SettingsBackup` set — even when Room is untouched.

## PR breakdown

PRs are **sequential** — PR1 lands the `BackupData` model + serialization that PR2/PR3 depend on.

### PR1 — Export infrastructure + JSON/CSV export + share sheet

- Add `kotlinx-serialization-json` and the Kotlin serialization plugin to the version catalog + `app/build.gradle.kts`.
- `BackupData.kt` + per-table backup DTOs + entity↔backup extension functions.
- Add `suspend getAll*Once()` snapshot reads to DAOs where only `Flow` exists (one-shot reads for export).
- `ExportBackupUseCase` — gather all repos → `BackupData` (sets `backupFormatVersion` + current `roomSchemaVersion`) → JSON string.
- `ExportCsvUseCase` — per-table CSV (export-only). One file per table (zipped) or separate files.
- **Durable destination is the primary path.** JSON backup uses **`ACTION_CREATE_DOCUMENT`** so the user picks a persistent location (Drive, Downloads, etc.); the use case writes the bytes to that user-selected `Uri`. This is the persistence guarantee that satisfies the "survive uninstall" goal — it does **not** depend on a share target succeeding.
- `BackupFileWriter` writes to either a SAF document `Uri` (durable, for JSON backup) or a `FileProvider` cache `Uri` (transient, for share-only artifacts).
- **Share is secondary/optional.** PDF and CSV (ephemeral, regenerable) may use a `FileProvider` cache file + `Intent.ACTION_SEND`. Offering ACTION_SEND for the JSON backup too is fine, but only *after* the durable `ACTION_CREATE_DOCUMENT` write — sharing is never the only copy.

### PR2 — PDF export

- `PdfReportGenerator` — `PdfDocument` + Canvas, manual pagination. Feeding + sleep summary tables. Uses design tokens (Pink/Blue families) and `AkachanTypography` sizes via `Paint`.
- `GeneratePdfReportUseCase(range)` — pull completed records in range → build doc → write file → return `Uri`.
- Add date-range queries per DAO (extend the existing `getCompletedRecordsSince` pattern).

### PR3 — Import / merge engine

- `BackupFileReader` — SAF `ACTION_OPEN_DOCUMENT` → read `Uri` → parse JSON → `BackupData`. Apply the `backupFormatVersion` rules above (reject newer, migrate older, accept equal).
- `ImportBackupUseCase` — **merge by full-row identity** (revised; see "Import semantics" below):
  - **Duplicate detection uses every persisted field**, not a partial key. Compute a per-row identity = hash of all persisted fields (breastfeeding: start/end/side/switch/notes/pause fields; sleep: start/end/type/notes; pumping: start/end/breast/volume/notes/pause; milk bag: collectionDate/volume/usedAt/notes/createdAt — **excluding** the autoGen `id` and the FK `sourceSessionId`, which is handled by remapping). A backup row is skipped **only** when an existing row hashes identically. Two rows that share a coarse timestamp but differ in any field are **both kept** — never silently dropped.
  - FK rule (single, non-contradictory): a backup is required to be self-contained. **Validation** (step 1 below) rejects the whole import if any milk bag has a non-null `sourceSessionId` that does not reference a pumping row present in the same backup. A milk bag whose `sourceSessionId` is already `null` in the backup is valid and stays `null`. There is **no** silent null-on-dangling rule — dangling means reject, so validation and remapping cannot disagree.
  - FK remapping (after validation passes): each backup row carries its backup-local `id`. Process `pumping_sessions` **as an upsert that always yields a mapping**: for each backup pumping row, if it is a new (non-duplicate) row insert it and capture the autoGen id; if it was skipped as a duplicate, look up the **matched existing row's database id**. Either way the old(backup-local)→new(database) map gets an entry for **every** backup pumping id — including deduped ones. Then rewrite each `milkBag.sourceSessionId` via the map before insert. This closes the gap where a milk bag references a pumping row that was skipped as a duplicate: it correctly attaches to the already-present session rather than failing or orphaning. Validation guarantees every non-null `sourceSessionId` has a backup parent, and the upsert guarantees that parent has a map entry.
  - Room rows (all 4 tables) inserted in a single Room `@Transaction` — atomic within Room.
- New `SettingsRepository.restoreFromBackup(BackupData)` — performs a **single** `DataStore.edit {}` that writes the baby profile + all backed-up settings keys and clears the import journal in one atomic batch. Per-setting setters are not reused for import.
- New journal keys in DataStore: `importInProgress: Boolean`, `importStartedAt: Long`, `lastImportCompletedAt: Long`. A startup check (in the app's existing init path) reads `importInProgress` and surfaces the incomplete-import notice when true.

#### Import semantics — cross-store consistency

Baby profile and settings live in **DataStore**, the tracking rows in **Room**. These are two independent stores; **a single transaction cannot span both**. Each store is made *individually* atomic, an import journal makes a cross-store partial state **detectable**, and the additive merge makes recovery a safe retry. Strict ordering:

1. **Validate everything first.** Parse JSON, check `backupFormatVersion`, validate every row (enums, required fields, milk-bag FK references per the FK rule above) *before any write*. Reject the whole import on any validation error — nothing is written.
2. **Mark import in progress.** Write an `importInProgress = true` + `importStartedAt` marker to DataStore in one `edit {}` before touching Room. This is the journal.
3. **Room import.** Insert all tracking rows inside one Room `@Transaction`. On failure → rollback; only the journal marker is set, no data changed → safe to retry.
4. **DataStore restore — single atomic batch.** Restore baby profile + all settings via **one** bulk `restoreFromBackup(BackupData)` call that performs a **single `DataStore.edit {}`** writing every key plus clearing the marker (`importInProgress = false`, `lastImportCompletedAt`). Because it is one `edit {}`, settings restore is all-or-nothing within DataStore — no half-written preference set. (This requires a new bulk restore method; the existing per-setting setters each open their own `edit {}` and must **not** be used for import.)

**Recovery / detection.** If `importInProgress` is still `true` at next app launch, Room committed but the DataStore batch did not complete (process death, IO error). The app surfaces a non-blocking notice — "last import may be incomplete, re-import your backup" — and re-import is **idempotent**: additive merge re-skips already-imported tracking rows, and the single-batch settings restore simply overwrites again. No journal-replay or compensation logic needed beyond detect-and-prompt.

The import confirmation dialog states the merge is additive; settings restore is atomic within DataStore but not transactionally joined to the data import.

### PR4 — Settings UI wiring

- Settings "Data" section: **Export PDF** (range-picker dialog), **Export backup (JSON)**, **Export CSV**, **Import backup** (file picker + confirmation dialog showing the record counts to be merged).
- `DataExportViewModel` exposing `StateFlow<DataExportUiState>` (idle / working / success+Uri / error).

## Error handling

Exceptions propagate to the ViewModel and surface as a `DataExportUiState` error (existing convention). Import validates `backupFormatVersion`, malformed JSON, and all row content (incl. milk-bag FK) up front, so a rejected import writes nothing. The Room merge runs in a `@Transaction` (rolls back on failure); DataStore restore is a single atomic `edit {}` that also clears the import journal. A journal marker left set indicates a partial cross-store restore and is detected at startup. See "Import semantics — cross-store consistency" for the full ordering and recovery.

## Testing

- **Unit (MockK):** export round-trip (export → import → assert equality), full-row identity dedupe, FK remap correctness, settings exclusion (no `appMode`/`shareCode` in output).
- **Version contract:** `backupFormatVersion` newer-than-build → reject; older → up-migration path invoked; equal → accept. A settings-only/DTO-shape change with unchanged `roomSchemaVersion` still trips the older/newer gate (drift test).
- **Cross-store / journal:** simulate Room commit then DataStore-restore failure → `importInProgress` stays `true`; startup detects it; re-import is idempotent (no duplicate rows, settings re-applied). `restoreFromBackup` writes all keys in one `edit {}` (partial-write impossible).
- **Milk-bag FK:** non-null `sourceSessionId` with no matching backup pumping row → whole import rejected (no silent null). Null `sourceSessionId` → preserved as null. **Milk bag referencing a pumping row that gets deduped (already present)** → milk bag attaches to the existing session's DB id (upsert map covers skipped parents), not orphaned.
- **Durable export:** JSON backup writes to a user-selected `ACTION_CREATE_DOCUMENT` `Uri`; bytes land at that destination independent of any share action.
- **Import edge cases:**
  - Same coarse key, different payload (e.g. same `startTime` + `startingSide`, different `endTime`/`notes`) → **both rows kept**, none dropped.
  - Exact duplicate row → skipped (no second insert).
  - Validation failure on one row → entire import rejected, zero writes to Room and DataStore.
  - Room insert failure → transaction rollback, DataStore untouched, DB unchanged.
  - Re-import after partial completion → idempotent, no duplicates.
- **PDF:** Robolectric smoke test — generated bytes non-empty, expected page count for a known record set.
- All tests pass before each PR. Architecture tests (`@Tag("architecture")`) must still pass for the new package.
