# Import / Merge Engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-37

**Goal:** Restore a previously exported JSON backup by merging it into the current database — non-destructively, deduping by full-row identity, validating + remapping foreign keys, with each store made individually atomic and a journal that makes a cross-store partial import detectable and safe to retry.

**Architecture:** Strict ordering: **(1) validate everything** (`ValidateBackupUseCase`: parse JSON, `backupFormatVersion` gate, enum + milk-bag FK checks) → **(2) mark `importInProgress`** in DataStore → **(3) Room merge** in one `@Transaction` (`BackupImporter`: dedupe by full-row identity, remap milk-bag FKs to pumping rows incl. deduped parents) → **(4) single-batch DataStore restore** (`SettingsRepository.restoreFromBackup`: one `edit {}` writing baby + settings and clearing the marker). Room and DataStore are independent stores — no single transaction spans both — so the journal makes a partial state detectable and the additive merge makes re-import idempotent. Domain stays pure (use cases depend on interfaces + repositories); Android/Room/DataStore lives in `export/data`.

**Tech Stack:** Kotlin 2.3.20, `kotlinx-serialization-json`, Room 2.8.4 (`room-ktx` `withTransaction`), DataStore Preferences, Hilt, JUnit 5 + MockK + Robolectric (JUnit4 Vintage from PR1).

**Dependencies:** PR1 (`feat/export-infra-json-csv`) — reuses `BackupData` + DTOs, the `export/data` `*.toEntity()` converters, the DAO `getAll*Once()` snapshot reads, the configured `Json`, the JUnit4/Vintage + `androidx.test:core` test setup, and the `export/domain` isolation arch rule. **Merge PR1 first.** Independent of PR2.

**Suggested implementation branch:** `feat/import-merge-engine`

---

## Background: verified codebase facts

- PR1 landed `BackupData` + per-table DTOs (`export/domain/model`), `*.toBackup()`/`*.toEntity()` converters (`export/data/BackupConverters.kt`), `CURRENT_BACKUP_FORMAT_VERSION = 1`, the configured `Json` (`ExportModule`, `ignoreUnknownKeys = true`), and `getAllSessionsOnce()`/`getAllRecordsOnce()`/`getAllBagsOnce()` on the DAOs.
- Entity fields (for identity hashing — exclude autogen `id`; for milk bags also exclude the remapped `sourceSessionId`):
  - `BreastfeedingEntity`: `startTime`, `endTime`, `startingSide`, `switchTime`, `notes`, `pausedAt`, `pausedDurationMs`.
  - `SleepEntity`: `startTime`, `endTime`, `sleepType`, `notes`.
  - `PumpingEntity`: `startTime`, `endTime`, `breast`, `volumeMl`, `notes`, `pausedAt`, `pausedDurationMs`.
  - `MilkBagEntity`: `collectionDate`, `volumeMl`, `usedAt`, `notes`, `createdAt` (**not** `id`, **not** `sourceSessionId`).
- DAO inserts: `BreastfeedingDao.insertSession(entity): Long`, `SleepDao.insertRecord(entity): Long`, `PumpingDao.insert(entity): Long`, `MilkBagDao.insert(entity): Long`. Room `@Insert` with autogen PK ignores `id` when it is `0`.
- `milk_bags.source_session_id` → `pumping_sessions.id`, `ON DELETE SET NULL`. Inserting a milk bag whose `source_session_id` references a non-existent pumping row would violate the FK — hence validation + remap before insert.
- Domain enums: `BreastSide`, `SleepType`, `PumpingBreast`, `AllergyType`, `ThemeConfig` (all `valueOf`-able by `name`).
- `SettingsRepositoryImpl` (`data/repository/`) owns the settings DataStore keys (companion). `BabyRepositoryImpl` owns baby keys: `baby_name`, `baby_birth_date` (epoch-day Long), `allergies` (comma-joined `AllergyType.name`), `custom_allergy_note`, `onboarding_complete`. The app uses a **single** `DataStore<Preferences>` instance (provided by `DataStoreModule`), so one `edit {}` can write baby + settings + journal keys atomically.
- `SettingsRepository` interface excludes `appMode`/`shareCode`/`onboarding_complete` from backup; import restores baby + settings and sets `onboarding_complete = true` when a baby is present.
- PR1's `BackupSourceImpl.readPreferences()` already re-declares the baby/settings keys and their defaults — mirror those exactly here.
- `RepositoryModule` binds repositories `@Binds @Singleton`. `ExportModule` (abstract class + companion) binds export interfaces and provides `Json` + `ExportMetadata`.

---

## File Structure

**Create:**
- `app/src/main/java/com/babytracker/export/domain/BackupException.kt` — `BackupTooNewException`, `InvalidBackupException`. Pure.
- `app/src/main/java/com/babytracker/export/domain/BackupImporter.kt` — `BackupImporter` interface + `ImportCounts`. Pure.
- `app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt` — JSON → validated `BackupData`. Pure.
- `app/src/main/java/com/babytracker/export/domain/usecase/ImportBackupUseCase.kt` — ordered cross-store import. Pure.
- `app/src/main/java/com/babytracker/export/data/BackupFileReader.kt` — SAF `Uri` → JSON string.
- `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt` — transactional dedupe + FK remap merge.
- Tests under `app/src/test/java/com/babytracker/export/`.

**Modify:**
- `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt` — add `markImportInProgress`, `isImportInProgress`, `restoreFromBackup`.
- `app/src/main/java/com/babytracker/export/di/ExportModule.kt` — bind `BackupImporter` → `BackupImporterImpl`.

---

## Task 1: Backup exceptions (pure domain)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/BackupException.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.export.domain

/** Backup was written by a newer app version than this build can read. */
class BackupTooNewException(val backupFormatVersion: Int) :
    Exception("Backup format v$backupFormatVersion is newer than this app supports")

/** Backup content failed validation (bad enum, dangling milk-bag FK, etc.). Nothing is written. */
class InvalidBackupException(message: String) : Exception(message)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/BackupException.kt
git commit -m "feat(export): add backup import exceptions"
```

---

## Task 2: ValidateBackupUseCase (version gate + content validation)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ValidateBackupUseCaseTest.kt`

Parses JSON to `BackupData`, applies the `backupFormatVersion` gate (newer → reject; older → per-version up-migration slot; equal → accept), and validates **all** rows before any write: every enum must parse, and every milk bag with a non-null `sourceSessionId` must reference a pumping row present in the same backup.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValidateBackupUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var useCase: ValidateBackupUseCase

    @BeforeEach
    fun setup() {
        useCase = ValidateBackupUseCase(json)
    }

    private fun settings() = SettingsBackup(
        themeConfig = "SYSTEM", maxPerBreastMinutes = 0, maxTotalFeedMinutes = 0,
        wakeTimeMinuteOfDay = null, autoUpdateEnabled = true, richNotificationsEnabled = true,
        predictiveEnabled = false, predictiveLeadMinutes = 15, quietHoursStartMinute = 0,
        quietHoursEndMinute = 480, napReminderEnabled = false, napReminderDelayMinutes = 60,
    )

    private fun backup(
        version: Int = CURRENT_BACKUP_FORMAT_VERSION,
        sleep: List<SleepBackup> = emptyList(),
        pumping: List<PumpingBackup> = emptyList(),
        milkBags: List<MilkBagBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = version, roomSchemaVersion = 3, appVersion = "1.0.0",
        exportedAt = 0, baby = null, settings = settings(),
        breastfeeding = emptyList(), sleep = sleep, pumping = pumping, milkBags = milkBags,
    )

    @Test
    fun `accepts a valid equal-version backup`() {
        val data = backup(sleep = listOf(SleepBackup(1, 10, 20, "NAP", null)))
        val result = useCase(json.encodeToString(BackupData.serializer(), data))
        assertEquals(1, result.sleep.size)
    }

    @Test
    fun `rejects a newer-than-build backup`() {
        val data = backup(version = CURRENT_BACKUP_FORMAT_VERSION + 1)
        assertThrows(BackupTooNewException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects an invalid enum value`() {
        val data = backup(sleep = listOf(SleepBackup(1, 10, 20, "NONSENSE", null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a milk bag referencing a pumping row absent from the backup`() {
        val data = backup(
            pumping = listOf(PumpingBackup(5, 1, 2, "LEFT", 100, null, null, 0)),
            milkBags = listOf(MilkBagBackup(1, 10, 90, sourceSessionId = 999, usedAt = null, notes = null, createdAt = 5)),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `accepts a milk bag with null source session id`() {
        val data = backup(
            milkBags = listOf(MilkBagBackup(1, 10, 90, sourceSessionId = null, usedAt = null, notes = null, createdAt = 5)),
        )
        val result = useCase(json.encodeToString(BackupData.serializer(), data))
        assertEquals(1, result.milkBags.size)
    }

    @Test
    fun `rejects end time before start time`() {
        // SleepBackup positional args: id, startTime, endTime, sleepType, notes -> start 20, end 5.
        val bad = backup(sleep = listOf(SleepBackup(1, 20, 5, "NAP", null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), bad))
        }
    }

    @Test
    fun `rejects negative pumping volume`() {
        val data = backup(pumping = listOf(PumpingBackup(1, 1, 2, "LEFT", -5, null, null, 0)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects duplicate pumping ids`() {
        val data = backup(
            pumping = listOf(
                PumpingBackup(7, 1, 2, "LEFT", 100, null, null, 0),
                PumpingBackup(7, 3, 4, "RIGHT", 90, null, null, 0),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `propagates malformed json`() {
        assertThrows(SerializationException::class.java) { useCase("{not valid json") }
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest" -PfastTests`
Expected: FAIL — unresolved `ValidateBackupUseCase`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ValidateBackupUseCase @Inject constructor(
    private val json: Json,
) {
    operator fun invoke(jsonString: String): BackupData {
        // Parse first — a SerializationException here means malformed/incompatible JSON.
        val parsed = json.decodeFromString(BackupData.serializer(), jsonString)
        val migrated = applyVersionGate(parsed)
        validateContent(migrated)
        return migrated
    }

    private fun applyVersionGate(data: BackupData): BackupData = when {
        data.backupFormatVersion > CURRENT_BACKUP_FORMAT_VERSION ->
            throw BackupTooNewException(data.backupFormatVersion)
        data.backupFormatVersion < CURRENT_BACKUP_FORMAT_VERSION ->
            migrateOlder(data)
        else -> data
    }

    /**
     * Per-version up-migration slot. No versions below CURRENT (1) exist yet, so any
     * older value is unsupported. When the format is first bumped, add `when` branches
     * here that transform an older BackupData up to the current shape.
     */
    private fun migrateOlder(data: BackupData): BackupData =
        throw InvalidBackupException("Unsupported backup format v${data.backupFormatVersion}")

    private fun validateContent(data: BackupData) {
        runCatching {
            data.breastfeeding.forEach { BreastSide.valueOf(it.startingSide) }
            data.sleep.forEach { SleepType.valueOf(it.sleepType) }
            data.pumping.forEach { PumpingBreast.valueOf(it.breast) }
            data.baby?.allergies?.forEach { AllergyType.valueOf(it) }
            ThemeConfig.valueOf(data.settings.themeConfig)
        }.onFailure { throw InvalidBackupException("Backup contains an invalid enum value: ${it.message}") }

        validateScalarInvariants(data)
        validateMilkBagReferences(data)
    }

    private fun validateScalarInvariants(data: BackupData) {
        fun bad(msg: String): Nothing = throw InvalidBackupException(msg)
        fun checkSpan(start: Long, end: Long?, label: String) {
            if (start < 0) bad("$label has negative start time")
            if (end != null && end < start) bad("$label end ($end) precedes start ($start)")
        }

        data.breastfeeding.forEach {
            checkSpan(it.startTime, it.endTime, "breastfeeding ${it.id}")
            if (it.pausedDurationMs < 0) bad("breastfeeding ${it.id} has negative paused duration")
        }
        data.sleep.forEach { checkSpan(it.startTime, it.endTime, "sleep ${it.id}") }
        data.pumping.forEach {
            checkSpan(it.startTime, it.endTime, "pumping ${it.id}")
            if (it.pausedDurationMs < 0) bad("pumping ${it.id} has negative paused duration")
            if (it.volumeMl != null && it.volumeMl < 0) bad("pumping ${it.id} has negative volume")
        }
        data.milkBags.forEach {
            if (it.collectionDate < 0) bad("milk bag ${it.id} has negative collection date")
            if (it.createdAt < 0) bad("milk bag ${it.id} has negative created time")
            if (it.volumeMl < 0) bad("milk bag ${it.id} has negative volume")
        }

        // Pumping ids must be unique — the FK remap (Task 3) keys oldId -> newId, so a
        // duplicate backup-local pumping id would make a milk bag's source ambiguous.
        val pumpingIds = data.pumping.map { it.id }
        if (pumpingIds.size != pumpingIds.toSet().size) {
            bad("Backup contains duplicate pumping ids")
        }
    }

    private fun validateMilkBagReferences(data: BackupData) {
        val pumpingIds = data.pumping.map { it.id }.toSet()
        data.milkBags.forEach { bag ->
            val ref = bag.sourceSessionId
            if (ref != null && ref !in pumpingIds) {
                throw InvalidBackupException(
                    "Milk bag references pumping id $ref not present in backup",
                )
            }
        }
    }
}
```

> The use case is **not** `suspend` — parsing/validation is pure CPU work the caller invokes off-thread. Settings sanitization already happened at export (PR1), but `ThemeConfig`/enum validation here is the import-side gate: a hand-edited or corrupt backup is rejected wholesale before any write.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ValidateBackupUseCaseTest.kt
git commit -m "feat(export): add ValidateBackupUseCase with version gate and content checks"
```

---

## Task 3: BackupImporter — transactional merge with dedupe + FK remap

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/BackupImporter.kt` (interface + `ImportCounts`)
- Create: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupImporterImplTest.kt`

Merge rules (from the design spec):
- **Full-row identity dedupe.** A backup row is skipped only when an existing DB row has an identical identity (every persisted field except autogen `id`; for milk bags, also excluding the remapped `sourceSessionId`). Rows differing in any field are both kept.
- **Pumping is an upsert that always yields a mapping.** For each backup pumping row: insert if new (capture autogen id) or, if a duplicate, look up the matched existing row's id. Either way `oldId → newId` is recorded for **every** backup pumping id.
- **Milk-bag FK remap.** Each `milkBag.sourceSessionId` is rewritten via that map before insert, so a bag referencing a deduped (already-present) pumping row attaches to the existing row's db id.
- All inserts run in one `db.withTransaction { }`.

- [ ] **Step 1: Write the interface + ImportCounts**

`app/src/main/java/com/babytracker/export/domain/BackupImporter.kt`:
```kotlin
package com.babytracker.export.domain

import com.babytracker.export.domain.model.BackupData

data class ImportCounts(
    val breastfeedingInserted: Int,
    val sleepInserted: Int,
    val pumpingInserted: Int,
    val milkBagsInserted: Int,
)

/** Merges validated backup tracking rows into Room in a single transaction. */
interface BackupImporter {
    suspend fun merge(data: BackupData): ImportCounts
}
```

- [ ] **Step 2: Write the failing test (Robolectric, JVM)**

```kotlin
package com.babytracker.export.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImporterImplTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var importer: BackupImporterImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = BackupImporterImpl(db)
    }

    @After
    fun tearDown() = db.close()

    private fun settings() = SettingsBackup(
        "SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60,
    )

    private fun backup(
        sleep: List<SleepBackup> = emptyList(),
        pumping: List<PumpingBackup> = emptyList(),
        milkBags: List<MilkBagBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null, settings = settings(), breastfeeding = emptyList(),
        sleep = sleep, pumping = pumping, milkBags = milkBags,
    )

    @Test
    fun `skips an exact duplicate but keeps a row differing in any field`() = runTest {
        db.sleepDao().insertRecord(SleepEntity(startTime = 10, endTime = 20, sleepType = "NAP", notes = "a"))
        val counts = importer.merge(
            backup(
                sleep = listOf(
                    SleepBackup(99, 10, 20, "NAP", "a"),  // exact duplicate -> skipped
                    SleepBackup(98, 10, 20, "NAP", "b"),  // differs in notes -> kept
                ),
            ),
        )
        assertEquals(1, counts.sleepInserted)
        assertEquals(2, db.sleepDao().getAllRecordsOnce().size)
    }

    @Test
    fun `remaps milk bag FK to a newly inserted pumping row`() = runTest {
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(id = 7, startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100, notes = null, pausedAt = null, pausedDurationMs = 0)),
                milkBags = listOf(MilkBagBackup(id = 3, collectionDate = 5, volumeMl = 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        assertEquals(1, counts.pumpingInserted)
        assertEquals(1, counts.milkBagsInserted)
        val pumpId = db.pumpingDao().getAllSessionsOnce().single().id
        val bag = db.milkBagDao().getAllBagsOnce().single()
        assertEquals(pumpId, bag.sourceSessionId)  // remapped to the real db id, not backup-local 7
    }

    @Test
    fun `milk bag referencing a deduped pumping row attaches to existing db id`() = runTest {
        // Pumping row already present in DB, identical to the backup's pumping row.
        val existingId = db.pumpingDao().insert(
            PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100),
        )
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(id = 7, startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100, notes = null, pausedAt = null, pausedDurationMs = 0)),
                milkBags = listOf(MilkBagBackup(id = 3, collectionDate = 5, volumeMl = 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        assertEquals(0, counts.pumpingInserted) // deduped against existing
        assertEquals(1, counts.milkBagsInserted)
        assertEquals(existingId, db.milkBagDao().getAllBagsOnce().single().sourceSessionId)
    }

    @Test
    fun `dedupe repairs an unlinked existing bag's source without inserting a duplicate`() = runTest {
        // Existing bag identical on all natural fields, but UNLINKED (source null).
        db.milkBagDao().insert(
            com.babytracker.data.local.entity.MilkBagEntity(
                collectionDate = 5, volumeMl = 90, sourceSessionId = null,
                usedAt = null, notes = null, createdAt = 5,
            ),
        )
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(7, 1, 2, "LEFT", 100, null, null, 0)),
                milkBags = listOf(MilkBagBackup(3, 5, 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        // No second inventory bag (no double-counting), but the existing bag's link is repaired.
        assertEquals(0, counts.milkBagsInserted)
        val bags = db.milkBagDao().getAllBagsOnce()
        assertEquals(1, bags.size)
        val pumpId = db.pumpingDao().getAllSessionsOnce().single().id
        assertEquals(pumpId, bags.single().sourceSessionId)
    }
}
```

- [ ] **Step 3: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupImporterImplTest" -PfastTests`
Expected: FAIL — unresolved `BackupImporterImpl`.

- [ ] **Step 4: Implement**

```kotlin
package com.babytracker.export.data

import androidx.room.withTransaction
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporterImpl @Inject constructor(
    private val db: BabyTrackerDatabase,
) : BackupImporter {

    override suspend fun merge(data: BackupData): ImportCounts = db.withTransaction {
        val bf = mergeBreastfeeding(data)
        val sleep = mergeSleep(data)
        val (pumpInserted, pumpIdMap) = mergePumping(data)
        val bags = mergeMilkBags(data, pumpIdMap)
        ImportCounts(bf, sleep, pumpInserted, bags)
    }

    private suspend fun mergeBreastfeeding(data: BackupData): Int {
        val seen = db.breastfeedingDao().getAllSessionsOnce().map { it.identity() }.toMutableSet()
        var inserted = 0
        for (b in data.breastfeeding) {
            val entity = BreastfeedingEntity(
                startTime = b.startTime, endTime = b.endTime, startingSide = b.startingSide,
                switchTime = b.switchTime, notes = b.notes, pausedAt = b.pausedAt,
                pausedDurationMs = b.pausedDurationMs,
            )
            if (seen.add(entity.identity())) {
                db.breastfeedingDao().insertSession(entity)
                inserted++
            }
        }
        return inserted
    }

    private suspend fun mergeSleep(data: BackupData): Int {
        val seen = db.sleepDao().getAllRecordsOnce().map { it.identity() }.toMutableSet()
        var inserted = 0
        for (s in data.sleep) {
            val entity = SleepEntity(
                startTime = s.startTime, endTime = s.endTime, sleepType = s.sleepType, notes = s.notes,
            )
            if (seen.add(entity.identity())) {
                db.sleepDao().insertRecord(entity)
                inserted++
            }
        }
        return inserted
    }

    /** Returns (insertedCount, backupPumpingId -> dbId) for EVERY backup pumping id. */
    private suspend fun mergePumping(data: BackupData): Pair<Int, Map<Long, Long>> {
        val existingByIdentity = db.pumpingDao().getAllSessionsOnce()
            .associate { it.identity() to it.id }
            .toMutableMap()
        val idMap = HashMap<Long, Long>()
        var inserted = 0
        for (p in data.pumping) {
            val entity = PumpingEntity(
                startTime = p.startTime, endTime = p.endTime, breast = p.breast,
                volumeMl = p.volumeMl, notes = p.notes, pausedAt = p.pausedAt,
                pausedDurationMs = p.pausedDurationMs,
            )
            val key = entity.identity()
            val dbId = existingByIdentity[key] ?: run {
                val newId = db.pumpingDao().insert(entity)
                existingByIdentity[key] = newId
                inserted++
                newId
            }
            idMap[p.id] = dbId
        }
        return inserted to idMap
    }

    private suspend fun mergeMilkBags(data: BackupData, pumpIdMap: Map<Long, Long>): Int {
        // Track existing bags by identity -> entity so a natural-field duplicate can have its
        // source link REPAIRED (not duplicated). Identity excludes id AND sourceSessionId.
        val existingByIdentity =
            db.milkBagDao().getAllBagsOnce().associateBy { it.identity() }.toMutableMap()
        var inserted = 0
        for (m in data.milkBags) {
            val remappedSource = m.sourceSessionId?.let { pumpIdMap[it] }
            val entity = MilkBagEntity(
                collectionDate = m.collectionDate, volumeMl = m.volumeMl,
                sourceSessionId = remappedSource, usedAt = m.usedAt, notes = m.notes,
                createdAt = m.createdAt,
            )
            val key = entity.identity()
            val existing = existingByIdentity[key]
            when {
                existing == null -> {
                    db.milkBagDao().insert(entity)
                    existingByIdentity[key] = entity
                    inserted++
                }
                // Repair provenance: an unlinked existing bag gains the backup's source link.
                // Never insert a second bag (avoids double-counting inventory volume).
                existing.sourceSessionId == null && remappedSource != null -> {
                    val repaired = existing.copy(sourceSessionId = remappedSource)
                    db.milkBagDao().update(repaired)
                    existingByIdentity[key] = repaired
                }
            }
        }
        return inserted
    }

    // Identity keys: every persisted field except autogen id (+ sourceSessionId for milk bags).
    private fun BreastfeedingEntity.identity() =
        listOf(startTime, endTime, startingSide, switchTime, notes, pausedAt, pausedDurationMs)
    private fun SleepEntity.identity() = listOf(startTime, endTime, sleepType, notes)
    private fun PumpingEntity.identity() =
        listOf(startTime, endTime, breast, volumeMl, notes, pausedAt, pausedDurationMs)
    private fun MilkBagEntity.identity() = listOf(collectionDate, volumeMl, usedAt, notes, createdAt)
}
```

> Identity uses a `List<Any?>` whose `equals`/`hashCode` compare element-by-element — exact full-row equality, no lossy hashing. The pumping map records a db id for every backup pumping id (inserted **or** deduped), so a milk bag always remaps to a real row — closing the "FK points at a skipped duplicate" gap.
>
> **Milk-bag identity deliberately omits `sourceSessionId`** — a design-spec decision (the FK is "handled by remapping", not part of natural identity), so a backup bag never inserts a *second* physical bag merely because of a differing source link (that would double-count inventory volume). But provenance is not lost: when a natural-field duplicate is found and the existing bag is **unlinked** (`sourceSessionId == null`) while the backup carries a remapped source, the existing bag is **updated** in place to gain that link. This satisfies both goals — no duplicate inventory, and FK-remap provenance is restored. (An existing bag that already has a different link is left as-is; import is non-destructive and never overwrites an existing relationship.)

- [ ] **Step 5: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupImporterImplTest" -PfastTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/BackupImporter.kt app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt app/src/test/java/com/babytracker/export/data/BackupImporterImplTest.kt
git commit -m "feat(export): add transactional BackupImporter with dedupe and FK remap"
```

---

## Task 4: SettingsRepository journal + restoreFromBackup

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/SettingsRestoreTest.kt`

`restoreFromBackup` writes the baby profile + every backed-up setting **and** clears the import journal in **one** `DataStore.edit {}` (atomic for DataStore). The journal (`importInProgress`, `importStartedAt`, `lastImportCompletedAt`) makes a cross-store partial import detectable at next launch.

- [ ] **Step 1: Add interface methods**

In `SettingsRepository.kt`:
```kotlin
fun isImportInProgress(): Flow<Boolean>
suspend fun markImportInProgress(startedAt: Long)
suspend fun restoreFromBackup(data: com.babytracker.export.domain.model.BackupData)
```

> `SettingsRepository` is core-domain and now references an `export` type. That is acceptable (both are domain), but if the architecture test forbids `domain` → `export` coupling, instead place these methods on a dedicated `BackupRestore` interface in `export/domain` implemented by a new `export/data` class that injects the same `DataStore`. Check the arch rule in Task 6; prefer the `SettingsRepository` location if it passes, since restore writes the same keys the repository owns.

- [ ] **Step 2: Write the failing test (Robolectric, real DataStore)**

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.SettingsBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "restore_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() { scope.cancel(); file.delete() }

    private fun backup(baby: BabyBackup?) = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = baby,
        settings = SettingsBackup("DARK", 12, 24, 420, false, false, true, 30, 60, 500, true, 90),
        breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
    )

    @Test
    fun `markImportInProgress sets the flag and restore clears it`() = runTest {
        repo.markImportInProgress(1234L)
        assertTrue(repo.isImportInProgress().first())

        repo.restoreFromBackup(backup(BabyBackup("Mia", 100, listOf("DAIRY"), null)))

        assertFalse(repo.isImportInProgress().first())
        assertEquals("DARK", repo.getThemeConfig().first().name)
        assertEquals(30, repo.getPredictiveLeadMinutes().first())
        // baby + onboarding written in the same edit
        val prefs = dataStore.data.first()
        assertEquals("Mia", prefs[stringPreferencesKey("baby_name")])
        assertEquals(true, prefs[booleanPreferencesKey("onboarding_complete")])
        assertEquals(1234L, /* importStartedAt retained for diagnostics */ prefs[androidx.datastore.preferences.core.longPreferencesKey("import_started_at")])
    }

    @Test
    fun `restore is idempotent`() = runTest {
        val b = backup(null)
        repo.restoreFromBackup(b)
        repo.restoreFromBackup(b)
        assertFalse(repo.isImportInProgress().first())
        assertEquals(12, repo.getMaxPerBreastMinutes().first())
    }

    @Test
    fun `restore clamps out-of-range settings`() = runTest {
        val corrupt = BackupData(
            backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
            baby = null,
            // predictive lead 7 (not allowed), quiet start 9999, nap delay 9999 (above 480)
            settings = SettingsBackup("SYSTEM", 0, 0, 9999, true, true, true, 7, 9999, 9999, true, 9999),
            breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
        )
        repo.restoreFromBackup(corrupt)
        assertEquals(15, repo.getPredictiveLeadMinutes().first())   // collapsed to default
        assertEquals(480, repo.getNapReminderDelayMinutes().first()) // coerced to max
    }
}
```

- [ ] **Step 3: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRestoreTest" -PfastTests`
Expected: FAIL — unresolved methods.

- [ ] **Step 4: Implement in `SettingsRepositoryImpl`**

Add journal keys to the companion (alongside the existing keys) and the baby keys (mirroring `BabyRepositoryImpl`):
```kotlin
val IMPORT_IN_PROGRESS = booleanPreferencesKey("import_in_progress")
val IMPORT_STARTED_AT = longPreferencesKey("import_started_at")
val LAST_IMPORT_COMPLETED_AT = longPreferencesKey("last_import_completed_at")
// Baby keys are owned by BabyRepositoryImpl; re-declared so restore can write the whole
// profile + settings + journal in ONE edit {} (single DataStore instance, atomic).
val BABY_NAME = stringPreferencesKey("baby_name")
val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
val ALLERGIES = stringPreferencesKey("allergies")
val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
```
Add `import androidx.datastore.preferences.core.longPreferencesKey` if absent.

Implement the methods:
```kotlin
override fun isImportInProgress(): Flow<Boolean> =
    dataStore.data.map { it[IMPORT_IN_PROGRESS] ?: false }

override suspend fun markImportInProgress(startedAt: Long) {
    dataStore.edit {
        it[IMPORT_IN_PROGRESS] = true
        it[IMPORT_STARTED_AT] = startedAt
    }
}

override suspend fun restoreFromBackup(data: BackupData) {
    // ONE atomic edit: baby profile + all settings + clear the import journal. Per-setting
    // setters are intentionally NOT reused — the single edit is the cross-store recovery point.
    dataStore.edit { p ->
        val baby = data.baby
        if (baby != null) {
            p[BABY_NAME] = baby.name
            p[BABY_BIRTH_DATE] = baby.birthDateEpochDay
            p[ALLERGIES] = baby.allergies.joinToString(",")
            if (baby.customAllergyNote != null) p[CUSTOM_ALLERGY_NOTE] = baby.customAllergyNote
            else p.remove(CUSTOM_ALLERGY_NOTE)
            p[ONBOARDING_COMPLETE] = true
        }

        // Clamp/allowlist on the way in — a hand-edited or corrupt backup must not persist
        // values the app's own setters would reject. These bounds MIRROR SettingsRepositoryImpl's
        // setters exactly (predictive lead allowlist, minute-of-day 0..1439, nap delay 1..480).
        // themeConfig is already enum-validated by ValidateBackupUseCase.
        val s = data.settings
        p[THEME_CONFIG] = s.themeConfig
        p[MAX_PER_BREAST_MINUTES] = s.maxPerBreastMinutes
        p[MAX_TOTAL_FEED_MINUTES] = s.maxTotalFeedMinutes
        val wake = s.wakeTimeMinuteOfDay
        if (wake != null && wake in 0..MAX_MINUTE_OF_DAY) p[WAKE_TIME_MINUTES] = wake
        else p.remove(WAKE_TIME_MINUTES)
        p[AUTO_UPDATE_ENABLED] = s.autoUpdateEnabled
        p[RICH_NOTIFICATIONS_ENABLED] = s.richNotificationsEnabled
        p[PREDICTIVE_ENABLED] = s.predictiveEnabled
        p[PREDICTIVE_LEAD_MINUTES] =
            if (s.predictiveLeadMinutes in ALLOWED_LEAD_MINUTES) s.predictiveLeadMinutes else DEFAULT_LEAD_MINUTES
        p[QUIET_HOURS_START_MINUTE] = s.quietHoursStartMinute.coerceIn(0, MAX_MINUTE_OF_DAY)
        p[QUIET_HOURS_END_MINUTE] = s.quietHoursEndMinute.coerceIn(0, MAX_MINUTE_OF_DAY)
        p[NAP_REMINDER_ENABLED] = s.napReminderEnabled
        p[NAP_REMINDER_DELAY_MINUTES] = s.napReminderDelayMinutes.coerceIn(MIN_NAP_DELAY, MAX_NAP_DELAY)

        p[IMPORT_IN_PROGRESS] = false
        p[LAST_IMPORT_COMPLETED_AT] = System.currentTimeMillis()
    }
}
```

Add to the companion (mirroring `SettingsRepositoryImpl`'s existing `DEFAULT_LEAD_MINUTES`/`ALLOWED_LEAD_MINUTES` — reuse them if already present, do not redeclare):
```kotlin
const val MAX_MINUTE_OF_DAY = 1439
const val MIN_NAP_DELAY = 1
const val MAX_NAP_DELAY = 480
// DEFAULT_LEAD_MINUTES = 15 and ALLOWED_LEAD_MINUTES = setOf(5,10,15,30) already exist in
// SettingsRepositoryImpl — reuse them.
```
Add `import com.babytracker.export.domain.model.BackupData`. Keep the companion `private`; these keys/constants are internal.

> `RICH_NOTIFICATIONS_ENABLED` is already declared `private` in the existing companion — reuse it; do not redeclare. Match the exact existing key `val` names in the file.

- [ ] **Step 5: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRestoreTest" -PfastTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt app/src/test/java/com/babytracker/data/repository/SettingsRestoreTest.kt
git commit -m "feat(export): add import journal and single-batch restoreFromBackup"
```

---

## Task 5: ImportBackupUseCase — ordered cross-store import

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/usecase/ImportBackupUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ImportBackupUseCaseTest.kt`

Takes an **already-validated** `BackupData` (from `ValidateBackupUseCase`) and runs the strict order: mark journal → Room merge → single-batch DataStore restore (which clears the journal). If the Room merge throws, `restoreFromBackup` is **not** called, so `importInProgress` stays `true` and the next launch can surface the incomplete-import notice; re-import is idempotent.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.SettingsBackup
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportBackupUseCaseTest {

    private lateinit var importer: BackupImporter
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ImportBackupUseCase

    private val data = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null,
        settings = SettingsBackup("SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60),
        breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
    )

    @BeforeEach
    fun setup() {
        importer = mockk()
        settingsRepository = mockk()
        useCase = ImportBackupUseCase(importer, settingsRepository)
    }

    @Test
    fun `runs mark, merge, restore in that order`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } returns ImportCounts(0, 0, 0, 0)
        coEvery { settingsRepository.restoreFromBackup(data) } just Runs

        useCase(data)

        coVerifyOrder {
            settingsRepository.markImportInProgress(any())
            importer.merge(data)
            settingsRepository.restoreFromBackup(data)
        }
    }

    @Test
    fun `leaves journal set when room merge fails`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } throws RuntimeException("room boom")

        assertThrows(RuntimeException::class.java) { kotlinx.coroutines.runBlocking { useCase(data) } }

        // restore must NOT run -> importInProgress stays true -> detectable at next launch.
        coVerify(exactly = 0) { settingsRepository.restoreFromBackup(any()) }
    }

    @Test
    fun `returns merge counts`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } returns ImportCounts(1, 2, 3, 4)
        coEvery { settingsRepository.restoreFromBackup(data) } just Runs

        assertEquals(ImportCounts(1, 2, 3, 4), useCase(data))
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ImportBackupUseCaseTest" -PfastTests`
Expected: FAIL — unresolved `ImportBackupUseCase`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import javax.inject.Inject

class ImportBackupUseCase @Inject constructor(
    private val importer: BackupImporter,
    private val settingsRepository: SettingsRepository,
) {
    /** [data] must already be validated by ValidateBackupUseCase. */
    suspend operator fun invoke(data: BackupData): ImportCounts {
        // 1. Journal: mark before any write so a crash mid-import is detectable.
        settingsRepository.markImportInProgress(System.currentTimeMillis())
        // 2. Room merge in a single transaction (atomic within Room).
        val counts = importer.merge(data)
        // 3. Single-batch DataStore restore; this clears importInProgress in the same edit.
        //    If step 2 threw, we never reach here -> marker stays set -> retryable + idempotent.
        settingsRepository.restoreFromBackup(data)
        return counts
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ImportBackupUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ImportBackupUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ImportBackupUseCaseTest.kt
git commit -m "feat(export): add ImportBackupUseCase with journaled cross-store ordering"
```

---

## Task 6: BackupFileReader + DI binding + arch/full suite

**Files:**
- Create: `app/src/main/java/com/babytracker/export/data/BackupFileReader.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupFileReaderTest.kt`
- Modify: `app/src/main/java/com/babytracker/export/di/ExportModule.kt`

- [ ] **Step 1: Write the failing reader test (Robolectric)**

```kotlin
package com.babytracker.export.data

import androidx.test.core.app.ApplicationProvider
import com.babytracker.export.domain.InvalidBackupException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupFileReaderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val reader = BackupFileReader(context)

    @Test
    fun `reads UTF-8 text from a uri`() = runTest {
        val file = File(context.cacheDir, "in.json").apply { writeText("{\"k\":1}") }
        val result = reader.read(android.net.Uri.fromFile(file))
        assertEquals("{\"k\":1}", result)
    }

    @Test
    fun `ensureWithinLimit rejects oversized declared size`() {
        assertThrows(InvalidBackupException::class.java) {
            reader.ensureWithinLimit(BackupFileReader.MAX_BACKUP_BYTES + 1)
        }
    }

    @Test
    fun `ensureWithinLimit accepts a size at the limit`() {
        reader.ensureWithinLimit(BackupFileReader.MAX_BACKUP_BYTES) // does not throw
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileReaderTest" -PfastTests`
Expected: FAIL — unresolved `BackupFileReader`.

- [ ] **Step 3: Implement the reader**

```kotlin
package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.babytracker.export.domain.InvalidBackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Reads a SAF document Uri (from ACTION_OPEN_DOCUMENT) to a UTF-8 string off the main thread,
     * rejecting anything larger than [MAX_BACKUP_BYTES]. A wrong/huge file must surface a typed
     * error, never OOM the import flow: we check the declared size first, then bound the actual
     * read so an unknown/lying size still cannot blow up memory.
     */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        queryDeclaredSize(uri)?.let { ensureWithinLimit(it) }
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw InvalidBackupException("Could not open the selected backup file")
            stream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                val out = ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    ensureWithinLimit(out.size().toLong()) // bound the actual bytes read
                }
                out.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (e: InvalidBackupException) {
            throw e // already typed (oversized / could-not-open)
        } catch (e: java.io.IOException) {
            throw InvalidBackupException("Could not read the backup file: ${e.message}")
        } catch (e: SecurityException) {
            throw InvalidBackupException("No permission to read the selected file: ${e.message}")
        }
    }

    /** Visible for tests; throws if [sizeBytes] exceeds the cap. */
    internal fun ensureWithinLimit(sizeBytes: Long) {
        if (sizeBytes > MAX_BACKUP_BYTES) {
            throw InvalidBackupException("Backup file is too large (> ${MAX_BACKUP_BYTES} bytes)")
        }
    }

    private fun queryDeclaredSize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
            }

    companion object {
        const val MAX_BACKUP_BYTES = 50L * 1024 * 1024 // 50 MiB — far above any realistic backup
        private const val BUFFER_SIZE = 64 * 1024
    }
}
```

- [ ] **Step 4: Bind BackupImporter in ExportModule**

Add to the `abstract class ExportModule` body:
```kotlin
@Binds
@Singleton
abstract fun bindBackupImporter(impl: BackupImporterImpl): BackupImporter
```
Add imports `com.babytracker.export.data.BackupImporterImpl` and `com.babytracker.export.domain.BackupImporter`. (`BackupFileReader` and the use cases are `@Inject`-constructed — no binding needed.)

- [ ] **Step 5: Run reader test, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileReaderTest" -PfastTests`
Expected: PASS.

- [ ] **Step 6: Architecture + formatting + full suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: PASS. The `export/domain` isolation rule must hold: `ValidateBackupUseCase`, `ImportBackupUseCase`, `BackupImporter`, `BackupException` import only Kotlin/kotlinx/domain. **If the rule flags `SettingsRepository` referencing `export.domain.model.BackupData`:** that is a `domain → export.domain` reference (not `domain → data`), which the PR1 rule (`com.babytracker.export.data` / `com.babytracker.data` / Room / Android) does **not** ban — confirm the rule text. If a stricter rule *does* flag it, move `restoreFromBackup`/journal onto a `BackupRestore` interface in `export/domain` with an `export/data` impl injecting the same `DataStore`, and have `ImportBackupUseCase` depend on that instead (see Task 4 Step 1 note).

Run: `./gradlew ktlintFormat detekt`
Expected: `BUILD SUCCESSFUL`. Fix detekt findings by changing code (never `@Suppress`). `BackupImporterImpl.merge` delegates to per-table private helpers, so method-length should be fine.

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/BackupFileReader.kt app/src/test/java/com/babytracker/export/data/BackupFileReaderTest.kt app/src/main/java/com/babytracker/export/di/ExportModule.kt
git commit -m "feat(export): add BackupFileReader and bind BackupImporter"
```
(Plus a separate `style(export): apply ktlint formatting` commit if needed.)

---

## Acceptance Criteria

- `ValidateBackupUseCase` parses JSON, rejects newer-than-build backups (`BackupTooNewException`), rejects invalid enums, dangling milk-bag FKs, scalar-invariant violations (negative times/volumes/durations, `end < start`), and duplicate pumping ids (`InvalidBackupException`); accepts null FKs; has a per-version up-migration slot.
- `BackupImporter.merge` runs in one Room `@Transaction`, dedupes by full-row identity (skips only exact matches; keeps rows differing in any field), and remaps every milk-bag `sourceSessionId` via a pumping `oldId→newId` map covering inserted **and** deduped pumping rows.
- `SettingsRepository.restoreFromBackup` writes baby + all settings + clears the journal in a single `edit {}`; `markImportInProgress`/`isImportInProgress` expose the journal.
- `ImportBackupUseCase` enforces mark → merge → restore order; a merge failure leaves `importInProgress = true` (restore not called) and re-import is idempotent.
- `BackupFileReader` reads a SAF `Uri` to a UTF-8 string off the main thread and rejects files larger than `MAX_BACKUP_BYTES` (declared-size pre-check + bounded read) with `InvalidBackupException`.
- `export/domain` isolation arch rule still passes; `./gradlew :app:testDebugUnitTest`, `ktlintFormat`, `detekt` all pass.

## Notes for downstream PRs

- PR4 (UI) wires the import flow: `ACTION_OPEN_DOCUMENT` → `BackupFileReader.read(uri)` → `ValidateBackupUseCase(json)` (surface `BackupTooNewException`/`InvalidBackupException`/`SerializationException` as error states; show `breastfeeding/sleep/pumping/milkBags` counts in the confirmation dialog) → on confirm `ImportBackupUseCase(data)`. At app launch, check `SettingsRepository.isImportInProgress()`; if `true`, show a non-blocking "last import may be incomplete — re-import your backup" notice. Re-import is idempotent (additive merge re-skips, settings overwrite again).
