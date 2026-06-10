# Bottle Feed Partner Sync + Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **UI CRAFT GATE:** The partner-dashboard task MUST begin by invoking the `impeccable` skill (craft mode).

**LINEAR_ISSUE:** AKA-114

**Goal:** Bring bottle feeds to feature parity with pumping/inventory across the two existing remote/persisted surfaces: the JSON/CSV backup (export + import) and the partner-sharing Firestore snapshot (sync + partner dashboard display).

**Architecture:** Backup: extend `BackupData`/`TrackingSnapshot` with a `bottleFeeds` list, read it in `BackupSourceImpl` inside the existing Room transaction, write/read it in the converters + importer, and add a CSV section — every step mirrors the existing `milkBags` path. Sharing: add a `BottleFeedSnapshot`, include it in `ShareSnapshot` + the `FULL` sync, add a `SyncType.BOTTLE_FEEDS` path mirroring `SESSIONS`, wire the plan-03 bottle use cases to trigger that sync, and render a bottle-feed section on the partner dashboard.

**Tech Stack:** Kotlinx Serialization JSON, Firebase Firestore KTX, Hilt, Compose Material 3, JUnit 5 + MockK + Turbine, Robolectric (Firestore round-trip tests follow the existing inventory round-trip test).

**Dependencies:** Plan 01 (`BottleFeed`, `BottleFeedEntity`, `BottleFeedDao`), Plan 03 (bottle use cases — this plan adds their sync calls). Mirrors existing `milkBags` (backup) and `sessions`/`inventory` (sharing) code.

**Suggested implementation branch:** `feat/bottle-feed-sync-backup`

---

## File Structure

**Backup:**
- Modify `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt` — add `bottleFeeds` + `BottleFeedBackup`.
- Modify `app/src/main/java/com/babytracker/export/domain/BackupSource.kt` — add `bottleFeeds` to `TrackingSnapshot`.
- Modify `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt` — read bottle feeds in the transaction.
- Modify `app/src/main/java/com/babytracker/export/data/BackupConverters.kt` — `BottleFeedEntity.toBackup()` / `BottleFeedBackup.toEntity()`.
- Modify the backup assembler (`ExportBackupUseCase`) and importer (`BackupImporterImpl`) — include/restore `bottleFeeds`.
- Modify `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt` — bottle-feed CSV section.

**Sharing:**
- Modify `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt` — add `BottleFeedSnapshot` + field + `BottleFeed.toSnapshot()`.
- Modify `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt` + `sharing/data/repository/SharingRepositoryImpl.kt` + `sharing/data/firebase/FirestoreSharingService.kt` — `syncBottleFeeds`.
- Modify `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt` — `SyncType.BOTTLE_FEEDS` + include in `FULL`.
- Modify the plan-03 use cases (`LogBottleFeedUseCase`, `EditBottleFeedUseCase`, `DeleteBottleFeedUseCase`) — trigger `SyncToFirestoreUseCase(BOTTLE_FEEDS)`.
- Modify `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt` — bottle-feed section.

---

## Part A — Backup export/import

### Task 1: Extend BackupData

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt`

- [ ] **Step 1: Add the backup model** (after `MilkBagBackup`)

```kotlin
@Serializable
data class BottleFeedBackup(
    val id: Long,
    val timestamp: Long,
    val volumeMl: Int,
    val type: String,
    val linkedMilkBagId: Long?,
    val notes: String?,
    val createdAt: Long,
)
```

- [ ] **Step 2: Add the field to `BackupData`** (append, keeping a default for backward-compatible deserialization of older backups)

```kotlin
    val milkBags: List<MilkBagBackup>,
    val bottleFeeds: List<BottleFeedBackup> = emptyList(),
)
```

> `bottleFeeds` defaults to empty so backups written before this change (which lack the field) still deserialize. `CURRENT_BACKUP_FORMAT_VERSION` stays `1` because the additive optional field is backward compatible — confirm this matches how prior additive fields were handled; bump only if the project's convention is to bump on any schema change.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/model/BackupData.kt
git commit -m "feat(export): add bottle feeds to backup data model"
```

### Task 2: Read bottle feeds into the tracking snapshot

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupSource.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt`

- [ ] **Step 1: Add to `TrackingSnapshot`**

```kotlin
data class TrackingSnapshot(
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
    val bottleFeeds: List<BottleFeedBackup>,
)
```
Add the import for `BottleFeedBackup`.

- [ ] **Step 2: Add converters** in `BackupConverters.kt` (mirror the existing `MilkBagEntity.toBackup()` / `MilkBagBackup.toEntity()`)

```kotlin
fun BottleFeedEntity.toBackup(): BottleFeedBackup = BottleFeedBackup(
    id = id,
    timestamp = timestamp,
    volumeMl = volumeMl,
    type = type,
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = createdAt,
)

fun BottleFeedBackup.toEntity(): BottleFeedEntity = BottleFeedEntity(
    id = id,
    timestamp = timestamp,
    volumeMl = volumeMl,
    type = type,
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = createdAt,
)
```
Add imports for `BottleFeedEntity` / `BottleFeedBackup`.

- [ ] **Step 3: Read it in `BackupSourceImpl.readTracking()`** (inside the existing `db.withTransaction { }`)

```kotlin
            milkBags = db.milkBagDao().getAllBagsOnce().map { it.toBackup() },
            bottleFeeds = db.bottleFeedDao().getAllOnce().map { it.toBackup() },
        )
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/BackupSource.kt app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt app/src/main/java/com/babytracker/export/data/BackupConverters.kt
git commit -m "feat(export): read bottle feeds into tracking snapshot"
```

### Task 3: Assemble + import bottle feeds (TDD)

**Files:**
- Modify: the backup assembler that builds `BackupData` from `TrackingSnapshot` (find it: `rg "TrackingSnapshot" app/src/main/java/com/babytracker/export/domain/usecase`)
- Modify: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt`
- Test: extend `app/src/test/java/com/babytracker/export/data/BackupImporterImplTest.kt` and `BackupConvertersTest.kt`

- [ ] **Step 1: Write failing tests** — in `BackupConvertersTest` add a `BottleFeedEntity ↔ BottleFeedBackup` round-trip (mirror the milk-bag round-trip). In `BackupImporterImplTest` add a case asserting imported `bottleFeeds` are written to the DAO (mirror the `milkBags` import assertion).

```kotlin
@Test
fun `imports bottle feeds`() = runTest {
    val data = sampleBackup(            // reuse the test's builder
        bottleFeeds = listOf(
            BottleFeedBackup(1, 1_000, 120, "FORMULA", null, "n", 2_000),
        ),
    )

    importer.import(data, /* strategy as existing tests use */)

    // assert via the fake/in-memory dao that the bottle feed was inserted
}
```

- [ ] **Step 2: Run to verify they fail** → FAIL.

- [ ] **Step 3: Implement** — in `ExportBackupUseCase` (assembler) add `bottleFeeds = tracking.bottleFeeds` to the `BackupData(...)` construction. In `BackupImporterImpl`, in the same place it inserts `milkBags`, insert `data.bottleFeeds.map { it.toEntity() }` via `bottleFeedDao` (add the DAO to its transaction/insert path exactly like the milk-bag insert).

> Match the importer's existing conflict/merge strategy for `milkBags` (e.g. clear-then-insert or upsert). Use the identical approach for `bottleFeeds`. Confirm the importer references `BottleFeedDao` — add it to the constructor/transaction the same way `MilkBagDao` is referenced.

- [ ] **Step 4: Run to verify they pass** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export app/src/test/java/com/babytracker/export
git commit -m "feat(export): assemble and import bottle feeds in backups"
```

### Task 4: Bottle feeds in CSV export (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt`
- Test: extend `app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt`

- [ ] **Step 1: Write a failing test** asserting the CSV output contains a bottle-feeds section/rows (mirror how the pumping/milk-bag sections are asserted in the existing test).

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement** — add a bottle-feeds CSV block (header `id,timestamp,volume_ml,type,linked_milk_bag_id,notes,created_at` + a row per feed) mirroring the existing milk-bag/pumping CSV sections in this use case.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt
git commit -m "feat(export): include bottle feeds in CSV export"
```

---

## Part B — Partner Firestore sync + dashboard

### Task 5: BottleFeedSnapshot + ShareSnapshot

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt`

- [ ] **Step 1: Add the snapshot model + mapper** (mirror the existing breastfeeding-session snapshot in this file)

```kotlin
data class BottleFeedSnapshot(
    val timestamp: Long,
    val volumeMl: Int,
    val type: String,
)

fun com.babytracker.domain.model.BottleFeed.toSnapshot(): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
)
```

- [ ] **Step 2: Add the field to `ShareSnapshot`**

```kotlin
    val bottleFeeds: List<BottleFeedSnapshot> = emptyList(),
```
(Place it near `sessions`; default empty keeps partner-side decoding of older snapshots safe.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt
git commit -m "feat(sharing): add bottle feed snapshot model"
```

### Task 6: syncBottleFeeds through the sharing layers

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

- [ ] **Step 1: Add to the interface** (mirror `syncSessions`)

```kotlin
suspend fun syncBottleFeeds(code: ShareCode, bottleFeeds: List<BottleFeedSnapshot>)
```

- [ ] **Step 2: Implement in `SharingRepositoryImpl` and `FirestoreSharingService`** exactly mirroring the `syncSessions` path — write the `bottleFeeds` array into the same shared document/field set the other collections use. Confirm the Firestore field name (`bottleFeeds`) is added wherever the full snapshot is written and read back.

> Find the session path to mirror: `rg "syncSessions|sessions" app/src/main/java/com/babytracker/sharing/data`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt
git commit -m "feat(sharing): sync bottle feeds to firestore"
```

### Task 7: SyncType.BOTTLE_FEEDS + FULL inclusion (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt`
- Test: extend `app/src/test/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCaseTest.kt` (+ the inventory-style round-trip test if applicable)

- [ ] **Step 1: Inject `BottleFeedRepository`** into `SyncToFirestoreUseCase` and add the enum value:

```kotlin
enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY, BOTTLE_FEEDS }
```

- [ ] **Step 2: Add the branch + the FULL inclusion**

```kotlin
SyncType.BOTTLE_FEEDS -> syncBottleFeeds(code)
```
```kotlin
private suspend fun syncBottleFeeds(code: ShareCode) {
    val feeds = bottleFeedRepository.getAll().first().take(SYNC_LIMIT)
    sharingRepository.syncBottleFeeds(code, feeds.map { it.toSnapshot() })
}
```
In `syncFull(...)`, add `bottleFeeds = bottleFeedRepository.getAll().first().take(SYNC_LIMIT).map { it.toSnapshot() }` to the `ShareSnapshot(...)` construction.

- [ ] **Step 3: Write/extend the test** — assert `invoke(SyncType.BOTTLE_FEEDS)` calls `sharingRepository.syncBottleFeeds` with mapped snapshots when app mode is PRIMARY and a share code exists (mirror the existing `SESSIONS`/`INVENTORY` test cases).

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.SyncToFirestoreUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt app/src/test/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCaseTest.kt
git commit -m "feat(sharing): add BOTTLE_FEEDS sync type"
```

### Task 8: Trigger sync from bottle use cases (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCase.kt`, `EditBottleFeedUseCase.kt`, `DeleteBottleFeedUseCase.kt`
- Test: extend the corresponding tests from plan 03

- [ ] **Step 1: Inject `SyncToFirestoreUseCase`** into each of the three use cases and, after the local mutation succeeds, call:

```kotlin
runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
```
(Mirror `MarkBagUsedUseCase`'s `runCatching { syncToFirestore(... INVENTORY) }` — fire-and-forget, never fail the local op.)

- [ ] **Step 2: Extend the plan-03 tests** — add the `syncToFirestore` mock (`relaxed = true`) and `coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }` for each. Keep the existing assertions green.

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/bottlefeed app/src/test/java/com/babytracker/domain/usecase/bottlefeed
git commit -m "feat(sharing): sync bottle feeds after log, edit, delete"
```

### Task 9: Partner dashboard bottle-feed section

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`
- Modify: the partner ViewModel feeding the dashboard (find: `rg "ShareSnapshot|sessions" app/src/main/java/com/babytracker/ui/partner`)

- [ ] **Step 1: REQUIRED — invoke `impeccable` (craft mode)** to design a partner-side bottle-feed section consistent with the existing dashboard sections (recent sessions, inventory). Read-only; show recent bottle feeds with volume (use the partner's display — partner side may default to ml; reuse `formatVolume` if a unit is available) and type.

- [ ] **Step 2: Surface `bottleFeeds`** from the decoded `ShareSnapshot` through the partner ViewModel state and render the section. Empty list → hide the section.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/partner
git commit -m "feat(partner): show bottle feeds on partner dashboard"
```

---

## Acceptance Criteria

- Backup JSON includes a `bottleFeeds` array; importing a backup restores bottle feeds (older backups without the field still import).
- CSV export contains a bottle-feeds section.
- `SyncToFirestoreUseCase` has a `BOTTLE_FEEDS` type, includes bottle feeds in `FULL`, and bottle log/edit/delete trigger a fire-and-forget sync.
- The partner dashboard shows recent bottle feeds when present.
- All new converters/sync paths mirror the established `milkBags`/`sessions` code; `./gradlew test` passes. Firestore round-trip test passes (Robolectric/CI).

## Self-Review Notes

- Every change mirrors an existing parity path (milkBags for backup, sessions/inventory for sharing) — lowest-risk integration.
- `bottleFeeds` defaults to empty in both `BackupData` and `ShareSnapshot` so older payloads decode without error (backward compatible); `CURRENT_BACKUP_FORMAT_VERSION` bump deferred to project convention.
- This plan owns the bottle-feed Firestore sync that plan 03 deliberately deferred (injects `SyncToFirestoreUseCase` into the three use cases here).
- Sync limited to `SYNC_LIMIT` (20) recent feeds, matching sessions/sleep.
- Volume-unit (plan 02) is intentionally NOT added to the backup settings payload here (out of scope; can be a tiny follow-up).
