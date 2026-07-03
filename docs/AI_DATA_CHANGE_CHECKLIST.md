# Data-change checklist

Everything to touch when the persisted data model changes. AGENTS.md covers the Room basics; this is the full ripple. Skipping a step compiles fine and fails silently later (CSV missing a column, partner op rejected, old backup failing to import).

## Version constants

| Constant | File | Value |
|----------|------|-------|
| `@Database(version = …)` | `data/local/BabyTrackerDatabase.kt` | 19 |
| `CURRENT_BACKUP_FORMAT_VERSION` | `export/domain/model/BackupData.kt` | 6 |
| `ExportMetadata.roomSchemaVersion` | `export/di/ExportModule.kt` | ⚠ stale (11) — diagnostics-only, not gated on import, but its comment claims it must equal the DB version |

There is no Firestore snapshot schema version — snapshots degrade via per-field defaults. Only op payloads are schema-gated (by `firestore.rules`).

## A. Adding a field to an existing entity (local-only)

1. Edit the `*Entity` in `data/local/entity/`.
2. Bump `version` in `@Database` (`BabyTrackerDatabase.kt`), add `MIGRATION_<old>_<new>` there, register it in `di/DatabaseModule.kt`.
3. Build — commit the new `app/schemas/…/<N>.json`.
4. Backup DTO: add the field to the matching `*Backup` class in `export/domain/model/BackupData.kt` **with a default** (older backups must still deserialize).
5. Backup converters: map it in **both** directions in `export/data/BackupConverters.kt`.
6. Importer: add it to the entity built in the relevant `mergeXxx` in `export/data/BackupImporterImpl.kt`. If the field is part of record identity, also extend that entity's `identity()` at the bottom of the file — otherwise re-imports dedup wrongly.
7. CSV: add header + row cell in `export/domain/usecase/ExportCsvUseCase.kt` — a new field is **silently omitted** otherwise.
8. If the field has constraints or is an enum-as-string: add checks in `export/domain/usecase/ValidateBackupUseCase.kt`.
9. Bump `CURRENT_BACKUP_FORMAT_VERSION` and add a carry-forward branch in `ValidateBackupUseCase.migrateOlder(...)` (repo convention for any shape change).
10. PDF (`export/data/PdfReportGenerator.kt`) is a curated summary — only touch if the field belongs in the printed report.

## B. Extra steps if the entity is synced to a partner

Synced today: baby, breastfeeding, sleep, bottle feeds, milk bags, growth, milestones, diapers, doctor visits, sleep prediction. **Not** synced: pumping, vaccines, visit questions.

11. Snapshot DTO: add the field **with a default** to the matching `*Snapshot` in `sharing/domain/model/ShareSnapshot.kt`.
12. Mapper: `sharing/domain/model/DomainToSnapshot.kt`.
13. Firestore (de)serialization: **both** `xxxToMap` (owner write) and `mapToXxx` (partner read, must default missing fields) in `sharing/data/firebase/FirestoreSnapshotMapping.kt`.
14. No `firestore.rules` change for snapshot fields (owner-only writes are not field-validated).

## C. Extra steps for partner op payloads (feedOps / sleepOps)

15. Op model: `sharing/domain/model/FeedOp.kt` / `SleepOp.kt`.
16. `feedOpToMap`/`mapToFeedOp` (or sleep equivalents) in `FirestoreSnapshotMapping.kt`. Wire convention: action/sleepType are lowercase.
17. **`firestore.rules`**: extend the `hasOnly([...])` allowlist (+ `hasAll` if required) and type checks in `isValidFeedOp`/`isValidSleepOp` — an unlisted field is **rejected**.
18. Rules tests: `cd firebase && npm test`, then deploy manually (see AGENTS.md What NOT to Do).
19. Apply/submit logic: `sharing/usecase/ApplyFeedOpUseCase.kt` / `ApplySleepOpUseCase.kt` and the submit use cases.

## D. Adding a whole new entity

All of A, plus:
- Backup: new `*Backup` model + `List<…>` field (default empty) in `BackupData`; `TrackingSnapshot` (`export/domain/BackupSource.kt`); `BackupSourceImpl.readTracking()`; `ExportBackupUseCase.buildBackup()`; new `mergeXxx` + `identity()` in `BackupImporterImpl`; new field in `ImportCounts` (`export/domain/BackupImporter.kt`); new CSV section; format-version bump + `migrateOlder` branch.
- Sync (if shared): all of B, plus a source in `sharing/usecase/SnapshotSources.kt` + `buildShareSnapshot`; new `SyncType` + `syncXxx` in `SyncToFirestoreUseCase.kt`; new `syncXxx` in `sharing/data/firebase/FirestoreSharingService.kt`.

## E. DataStore preference instead of a Room column

No migration/schema JSON. Touch: `data/repository/SettingsRepositoryImpl.kt` (Keys + getter/setter + `restoreFromBackup` write block), and for backup coverage `BabyBackup`/`SettingsBackup` in `BackupData.kt` + `BackupSourceImpl.parseBaby/parseSettings`.

## Notes

- No Room `@TypeConverters` in main — enums persist as `.name` strings and are validated on import via `valueOf`/`toXxxOrNull`.
- Widgets (`widget/`) and QS tiles (`tile/`) map only derived state (feed side, active state, inventory totals) — a new field needs no change there unless it alters those derivations.
- Instrumented Compose tests define anonymous `object : XxxRepository { … }` fakes per file — adding a method to a repository interface breaks each of them; adding a data-class field only breaks fakes that construct it without defaults.
