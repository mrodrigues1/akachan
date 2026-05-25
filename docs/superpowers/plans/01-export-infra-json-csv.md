# Export Infrastructure + JSON/CSV Export + Share Sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-35

**Goal:** Add the serialization backbone (`BackupData` model + DTOs) plus JSON and CSV export, writing JSON to a user-chosen location via the Storage Access Framework, with an optional secondary share sheet for ephemeral artifacts.

**Architecture:** New top-level `export/` package. `export/domain` holds **pure** serializable DTOs, a `BackupSource` interface, and use-case orchestration — it imports only Kotlin/kotlinx, never Room/Android/data. `export/data` implements `BackupSource` (`BackupSourceImpl`): it reads all four Room tables inside a single transaction **and** reads all DataStore-backed baby/settings from a single `Preferences` snapshot, so each store is internally consistent. Conversion uses extension functions (no Mapper classes); no `Result<>` wrappers. JSON via `kotlinx.serialization`. The durable destination guarantee (survive uninstall) is owned by **PR4's UI**, which uses the `ActivityResultContracts.CreateDocument` SAF contract — that always yields a *freshly created* document `Uri`. `BackupFileWriter` is a thin byte-writer that propagates any failure; it does not itself promise non-overwrite.

**Tech Stack:** Kotlin 2.3.20, `kotlinx-serialization-json`, Room 2.8.4 (`room-ktx` `withTransaction`), DataStore Preferences, Hilt, JUnit 5 + MockK + Robolectric.

**Dependencies:** None — this is PR1. PR2 (PDF) and PR3 (import) depend on the `BackupData` model + converters landed here.

**Suggested implementation branch:** `feat/export-infra-json-csv`

---

## Background: verified codebase facts

Confirmed against the current `main` working tree (CLAUDE.md is stale — it says Room v2; the code is Room v3):

- `BabyTrackerDatabase` (`app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`) is `version = 3` with four entities: `BreastfeedingEntity`, `SleepEntity`, `PumpingEntity`, `MilkBagEntity`. Exposes `breastfeedingDao()`, `sleepDao()`, `pumpingDao()`, `milkBagDao()`.
- `DatabaseModule` (`app/src/main/java/com/babytracker/di/DatabaseModule.kt`) `@Provides` the `BabyTrackerDatabase` singleton and each DAO. `room-ktx` is a dependency, so `androidx.room.withTransaction { }` is available.
- `DataStoreModule` provides `DataStore<Preferences>` (a singleton) — injectable directly.
- Entity fields (`app/src/main/java/com/babytracker/data/local/entity/`):
  - `BreastfeedingEntity`: `id`, `startTime: Long`, `endTime: Long?`, `startingSide: String`, `switchTime: Long?`, `notes: String?`, `pausedAt: Long?`, `pausedDurationMs: Long`.
  - `SleepEntity`: `id`, `startTime: Long`, `endTime: Long?`, `sleepType: String`, `notes: String?`.
  - `PumpingEntity`: `id`, `startTime: Long`, `endTime: Long?`, `breast: String`, `volumeMl: Int?`, `notes: String?`, `pausedAt: Long?`, `pausedDurationMs: Long`.
  - `MilkBagEntity`: `id`, `collectionDate: Long`, `volumeMl: Int`, `sourceSessionId: Long?`, `usedAt: Long?`, `notes: String?`, `createdAt: Long`.
- DAOs expose `Flow` reads but **no** one-shot `suspend` "get every row" read. We add those.
- **Baby profile DataStore keys** (`BabyRepositoryImpl`): `baby_name` (String), `baby_birth_date` (Long epoch-day), `allergies` (String, comma-joined `AllergyType.name`), `custom_allergy_note` (String). Baby is `null` when `baby_name` or `baby_birth_date` is absent. `Baby` domain model = `name`, `birthDate: LocalDate`, `allergies: List<AllergyType>`, `customAllergyNote: String?`. `AllergyType` is a domain enum.
- **Settings DataStore keys + defaults** (`SettingsRepositoryImpl`) — back up these, exclude `app_mode`, `share_code`, `onboarding_complete`:
  | Key | Type | Default |
  |-----|------|---------|
  | `theme_config` | String (`ThemeConfig.name`) | `"SYSTEM"` |
  | `max_per_breast_minutes` | Int | `0` |
  | `max_total_feed_minutes` | Int | `0` |
  | `wake_time_minutes` | Int? (minute-of-day) | `null` |
  | `auto_update_enabled` | Boolean | `true` |
  | `rich_notifications_enabled` | Boolean | `true` |
  | `predictive_enabled` | Boolean | `false` |
  | `predictive_lead_minutes` | Int | `15` |
  | `quiet_hours_start_minute` | Int | `0` |
  | `quiet_hours_end_minute` | Int | `480` |
  | `nap_reminder_enabled` | Boolean | `false` |
  | `nap_reminder_delay_minutes` | Int | `60` |
- App has **no** `FileProvider` configured yet. This plan adds one.
- Architecture tests: `app/src/test/java/com/babytracker/architecture/LayerIsolationTest.kt` uses Konsist's `assertFalse { }` DSL (always executes) and the shared `productionScope` from `KonsistScopes.kt`. The existing domain rule bans imports starting with `com.babytracker.data` — note `com.babytracker.export.data` does **not** match that prefix, so a new rule is required.

---

## File Structure

**Create:**
- `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt` — `@Serializable` `BackupData` + per-table DTOs + `BabyBackup` + `SettingsBackup`. **Pure**: imports only `kotlinx.serialization`.
- `app/src/main/java/com/babytracker/export/domain/BackupSource.kt` — `BackupSource` interface + `TrackingSnapshot` + `PreferencesSnapshot` (all domain DTOs). Pure.
- `app/src/main/java/com/babytracker/export/data/BackupConverters.kt` — entity↔DTO extension functions (imports Room entities).
- `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt` — implements `BackupSource`: transactional Room read + single `Preferences` read.
- `app/src/main/java/com/babytracker/export/domain/usecase/ExportBackupUseCase.kt` — `BackupSource` → `BackupData` → JSON string.
- `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt` — `BackupSource` tracking snapshot → per-table CSV strings (export-only).
- `app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt` — write bytes to a SAF `Uri` or a FileProvider cache file.
- `app/src/main/java/com/babytracker/export/di/ExportModule.kt` — provides `Json`, binds `BackupSource` → `BackupSourceImpl`.
- `app/src/main/res/xml/file_paths.xml` — FileProvider paths.
- Tests under `app/src/test/java/com/babytracker/export/`.

**Modify:**
- `gradle/libs.versions.toml` — add `kotlinx-serialization` version + library + plugin.
- `app/build.gradle.kts` — add serialization plugin + dependency.
- `app/src/main/AndroidManifest.xml` — register `FileProvider`.
- `app/src/main/java/com/babytracker/data/local/dao/{Breastfeeding,Sleep,Pumping,MilkBag}Dao.kt` — add `getAll*Once()`.
- `app/src/test/java/com/babytracker/architecture/LayerIsolationTest.kt` — add `export/domain` isolation rule.

**Layering rule:** `export/domain` imports only Kotlin/kotlinx. The use cases depend on the `BackupSource` **interface** (domain), never on `BackupSourceImpl`, the DAOs, the `Preferences` keys, or the entity converters. All of that lives in `export/data`. Enforced by Task 12.

---

## Task 1: Build setup — serialization + JUnit4/Robolectric discovery

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Why the JUnit4 bits:** the repo's local unit tests run on `useJUnitPlatform { }` with **only** the JUnit Jupiter engine — there is no `junit-vintage-engine`, and there are currently **no** `@RunWith(RobolectricTestRunner::class)` tests despite CLAUDE.md describing the pattern. Several tests in this plan (Room in-memory, DataStore, FileProvider) need an Android `Context`, which means Robolectric, which is JUnit4. Without the Vintage engine on the test runtime classpath, those JUnit4 tests are **silently not discovered** and `--tests` filters report "No tests found". This task adds the Vintage engine (and an explicit JUnit4 compile dep) so the Robolectric tests actually execute.

- [ ] **Step 1: Add versions, libraries, and plugin to the catalog**

In `gradle/libs.versions.toml` under `[versions]`:

```toml
kotlinxSerialization = "1.7.3"
junit4 = "4.13.2"
androidxTestCore = "1.6.1"
```

Under `[libraries]` (the Vintage engine version must match the existing `junit5 = "5.10.3"` ref — Jupiter and Vintage ship together):

```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
junit-vintage-engine = { group = "org.junit.vintage", name = "junit-vintage-engine", version.ref = "junit5" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

> `androidx.test:core` supplies `androidx.test.core.app.ApplicationProvider`, which the JVM Robolectric tests use to obtain a `Context`. It is currently only on the `androidTest` classpath (`runner`/`rules`), not the local unit-test classpath, and Robolectric's POM pulls `androidx.test:monitor` but **not** `core` — so without this dependency the Robolectric tests fail to compile.

Under `[plugins]` (reuse existing `kotlin = "2.3.20"` ref — the serialization plugin tracks the Kotlin compiler version):

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply plugin + dependencies in the app module**

`app/build.gradle.kts` `plugins { }` (after `alias(libs.plugins.kotlin.compose)`):

```kotlin
alias(libs.plugins.kotlin.serialization)
```

`dependencies { }` (add the implementation dep + the test deps next to the existing `testImplementation(libs.robolectric)` / `testRuntimeOnly(libs.junit5.engine)` lines):

```kotlin
implementation(libs.kotlinx.serialization.json)
testImplementation(libs.junit4)
testImplementation(libs.androidx.test.core)
testRuntimeOnly(libs.junit.vintage.engine)
```

> Keep the existing `useJUnitPlatform { }` block as-is. Adding the Vintage engine to the test **runtime** classpath makes JUnit Platform discover `@RunWith`/`org.junit.Test` (JUnit4) tests alongside the Jupiter ones. JUnit4 itself is also pulled in transitively by Robolectric, but the explicit `testImplementation(libs.junit4)` keeps the `org.junit.*` imports unambiguous.

- [ ] **Step 3: Verify the build resolves**

Run: `./gradlew :app:help -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify a Robolectric JUnit4 test is actually discovered**

Create a throwaway probe test `app/src/test/java/com/babytracker/export/VintageProbeTest.kt`:

```kotlin
package com.babytracker.export

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VintageProbeTest {
    @Test
    fun `robolectric provides an application context`() {
        assertNotNull(ApplicationProvider.getApplicationContext<android.content.Context>())
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.VintageProbeTest" -PfastTests`
Expected: PASS with **1 test executed** (not "No tests found"). If it reports no tests found, the Vintage engine is not on the runtime classpath — recheck Step 2 before continuing. Once confirmed, delete the probe file (it is not committed).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore(export): add serialization and JUnit4 Vintage engine for Robolectric tests"
```

---

## Task 2: Define the BackupData model and DTOs (pure domain)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt`

Each **tracking** DTO carries every persisted field (full fidelity). Timestamps as epoch-ms `Long`. Enums as `String` `name`. `id` preserved for PR3's FK remap. **Imports only `kotlinx.serialization`.**

> **Format-v1 decisions (not full-fidelity, by design):**
> - **Tracking rows** are full-fidelity, except in-progress sessions are finalized at `exportedAt` (see Task 8) so a backup never carries an active timer.
> - **`SettingsBackup`** holds *normalized effective* settings, not raw persisted bytes: `BackupSourceImpl` applies the same invariants `SettingsRepositoryImpl` enforces on write (Task 6). This is intentional — it prevents a corrupt stored value (bad enum, out-of-range minute) from round-tripping back into live notification/scheduling config on import. A future need for exact forensic capture would add a separate `rawSettings` block under a bumped `backupFormatVersion`.

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.export.domain.model

import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupData(
    val backupFormatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val roomSchemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val baby: BabyBackup?,
    val settings: SettingsBackup,
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
)

@Serializable
data class BabyBackup(
    val name: String,
    val birthDateEpochDay: Long,
    val allergies: List<String>,
    val customAllergyNote: String?,
)

@Serializable
data class SettingsBackup(
    val themeConfig: String,
    val maxPerBreastMinutes: Int,
    val maxTotalFeedMinutes: Int,
    val wakeTimeMinuteOfDay: Int?,
    val autoUpdateEnabled: Boolean,
    val richNotificationsEnabled: Boolean,
    val predictiveEnabled: Boolean,
    val predictiveLeadMinutes: Int,
    val quietHoursStartMinute: Int,
    val quietHoursEndMinute: Int,
    val napReminderEnabled: Boolean,
    val napReminderDelayMinutes: Int,
)

@Serializable
data class BreastfeedingBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val startingSide: String,
    val switchTime: Long?,
    val notes: String?,
    val pausedAt: Long?,
    val pausedDurationMs: Long,
)

@Serializable
data class SleepBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val sleepType: String,
    val notes: String?,
)

@Serializable
data class PumpingBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val breast: String,
    val volumeMl: Int?,
    val notes: String?,
    val pausedAt: Long?,
    val pausedDurationMs: Long,
)

@Serializable
data class MilkBagBackup(
    val id: Long,
    val collectionDate: Long,
    val volumeMl: Int,
    val sourceSessionId: Long?,
    val usedAt: Long?,
    val notes: String?,
    val createdAt: Long,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/model/BackupData.kt
git commit -m "feat(export): add BackupData serializable model and per-table DTOs"
```

---

## Task 3: BackupSource domain abstraction (pure)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/BackupSource.kt`

Defines the interface the use cases depend on, plus two snapshot holders made of **domain DTOs** (no Room types). `TrackingSnapshot` is the consistent Room read; `PreferencesSnapshot` is the consistent DataStore read.

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.export.domain

import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup

data class TrackingSnapshot(
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
)

data class PreferencesSnapshot(
    val baby: BabyBackup?,
    val settings: SettingsBackup,
)

/** Build-time metadata stamped into every backup. Provided by Hilt (see ExportModule). */
data class ExportMetadata(
    val appVersion: String,
    val roomSchemaVersion: Int,
)

/**
 * Reads exportable state with per-store consistency:
 * [readTracking] reads all Room tables in one transaction; [readPreferences]
 * reads baby + settings from one DataStore Preferences snapshot.
 */
interface BackupSource {
    suspend fun readTracking(): TrackingSnapshot
    suspend fun readPreferences(): PreferencesSnapshot
}
```

> `ExportMetadata` is a typed config object so the use case never depends on raw `String`/`Int` Hilt bindings (which would either fail graph compilation or pollute the `SingletonComponent` with primitive bindings any other injection could capture). It is a plain Kotlin class — no framework imports — so it stays in `export/domain`.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/BackupSource.kt
git commit -m "feat(export): add pure BackupSource domain abstraction"
```

---

## Task 4: Entity↔DTO converters (data layer)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupConvertersTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.data

import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupConvertersTest {

    @Test
    fun `breastfeeding entity round-trips through backup dto`() {
        val entity = BreastfeedingEntity(
            id = 7, startTime = 1000, endTime = 2000, startingSide = "LEFT",
            switchTime = 1500, notes = "n", pausedAt = 1200, pausedDurationMs = 50,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `sleep entity round-trips through backup dto`() {
        val entity = SleepEntity(id = 3, startTime = 100, endTime = 900, sleepType = "NAP", notes = null)
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `pumping entity round-trips through backup dto`() {
        val entity = PumpingEntity(
            id = 4, startTime = 10, endTime = 99, breast = "BOTH",
            volumeMl = 120, notes = "x", pausedAt = null, pausedDurationMs = 0,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `milk bag entity round-trips through backup dto`() {
        val entity = MilkBagEntity(
            id = 5, collectionDate = 111, volumeMl = 90, sourceSessionId = 4,
            usedAt = null, notes = null, createdAt = 222,
        )
        assertEquals(entity, entity.toBackup().toEntity())
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupConvertersTest" -PfastTests`
Expected: FAIL — `toBackup`/`toEntity` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.data

import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SleepBackup

fun BreastfeedingEntity.toBackup() = BreastfeedingBackup(
    id = id, startTime = startTime, endTime = endTime, startingSide = startingSide,
    switchTime = switchTime, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun BreastfeedingBackup.toEntity() = BreastfeedingEntity(
    id = id, startTime = startTime, endTime = endTime, startingSide = startingSide,
    switchTime = switchTime, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun SleepEntity.toBackup() = SleepBackup(
    id = id, startTime = startTime, endTime = endTime, sleepType = sleepType, notes = notes,
)

fun SleepBackup.toEntity() = SleepEntity(
    id = id, startTime = startTime, endTime = endTime, sleepType = sleepType, notes = notes,
)

fun PumpingEntity.toBackup() = PumpingBackup(
    id = id, startTime = startTime, endTime = endTime, breast = breast,
    volumeMl = volumeMl, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun PumpingBackup.toEntity() = PumpingEntity(
    id = id, startTime = startTime, endTime = endTime, breast = breast,
    volumeMl = volumeMl, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun MilkBagEntity.toBackup() = MilkBagBackup(
    id = id, collectionDate = collectionDate, volumeMl = volumeMl, sourceSessionId = sourceSessionId,
    usedAt = usedAt, notes = notes, createdAt = createdAt,
)

fun MilkBagBackup.toEntity() = MilkBagEntity(
    id = id, collectionDate = collectionDate, volumeMl = volumeMl, sourceSessionId = sourceSessionId,
    usedAt = usedAt, notes = notes, createdAt = createdAt,
)
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupConvertersTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/BackupConverters.kt app/src/test/java/com/babytracker/export/data/BackupConvertersTest.kt
git commit -m "feat(export): add entity to backup DTO converters in data layer"
```

---

## Task 5: Add one-shot snapshot reads to the DAOs

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/dao/{Breastfeeding,Sleep,Pumping,MilkBag}Dao.kt`
- Test: `app/src/test/java/com/babytracker/data/local/dao/SnapshotReadsTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric, JVM)**

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotReadsTest {

    private lateinit var db: BabyTrackerDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `snapshot reads return inserted rows`() = runTest {
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 1, endTime = 2, startingSide = "LEFT"),
        )
        db.sleepDao().insertRecord(SleepEntity(startTime = 1, endTime = 2, sleepType = "NAP"))
        val pumpId = db.pumpingDao().insert(PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT"))
        db.milkBagDao().insert(
            MilkBagEntity(collectionDate = 1, volumeMl = 10, sourceSessionId = pumpId, createdAt = 1),
        )

        assertEquals(1, db.breastfeedingDao().getAllSessionsOnce().size)
        assertEquals(1, db.sleepDao().getAllRecordsOnce().size)
        assertEquals(1, db.pumpingDao().getAllSessionsOnce().size)
        assertEquals(1, db.milkBagDao().getAllBagsOnce().size)
    }
}
```

> JUnit 4 because Robolectric uses the JUnit 4 runner (per CLAUDE.md). Not `@Tag("architecture")`, so `-PfastTests` runs it.

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.local.dao.SnapshotReadsTest" -PfastTests`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Add the suspend reads**

`BreastfeedingDao.kt`:
```kotlin
@Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time ASC")
suspend fun getAllSessionsOnce(): List<BreastfeedingEntity>
```
`SleepDao.kt`:
```kotlin
@Query("SELECT * FROM sleep_records ORDER BY start_time ASC")
suspend fun getAllRecordsOnce(): List<SleepEntity>
```
`PumpingDao.kt`:
```kotlin
@Query("SELECT * FROM pumping_sessions ORDER BY start_time ASC")
suspend fun getAllSessionsOnce(): List<PumpingEntity>
```
`MilkBagDao.kt`:
```kotlin
@Query("SELECT * FROM milk_bags ORDER BY collection_date ASC")
suspend fun getAllBagsOnce(): List<MilkBagEntity>
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.local.dao.SnapshotReadsTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/dao/ app/src/test/java/com/babytracker/data/local/dao/SnapshotReadsTest.kt
git commit -m "feat(db): add one-shot snapshot reads to DAOs for export"
```

---

## Task 6: BackupSourceImpl — consistent cross-table + single-preferences read

**Files:**
- Create: `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupSourceImplTest.kt`

**Why transactional Room read:** four independent DAO calls let a concurrent write land between them — e.g. a `milk_bags` row referencing a `pumping_sessions` row inserted *after* the pumping read, producing a dangling `sourceSessionId`. One `db.withTransaction { }` gives a consistent snapshot.

**Why single Preferences read:** reading each setting via its own `Flow.first()` lets a concurrent settings/profile edit interleave, producing a backup that never matched real app state. Reading `dataStore.data.first()` **once** and mapping every key off that single `Preferences` object is atomic for the DataStore store.

> This class deliberately re-declares the DataStore key names. They mirror `BabyRepositoryImpl`/`SettingsRepositoryImpl`; a backup source legitimately needs the whole-store view those repositories don't expose. If a key or default changes there, update here and bump `CURRENT_BACKUP_FORMAT_VERSION`. The defaults below match the repositories exactly (see Background table).

- [ ] **Step 1: Write the failing test (Robolectric, JVM)**

Uses a real in-memory Room DB and a real (temp) DataStore so the transaction + single-read behavior is exercised end to end.

Each test gets its **own** uniquely-named DataStore file and an explicit `CoroutineScope` that is cancelled (and the file deleted) in teardown — Preferences DataStore requires one active instance per file per process, and reusing a fixed file leaks state across tests and can make ordering-dependent assertions flaky.

```kotlin
package com.babytracker.export.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupSourceImplTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dsScope: CoroutineScope
    private lateinit var dsFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var source: BackupSourceImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dsFile = File(context.cacheDir, "backup_test_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
        source = BackupSourceImpl(db, dataStore)
    }

    @After
    fun tearDown() {
        db.close()
        dsScope.cancel()
        dsFile.delete()
    }

    @Test
    fun `readTracking captures all tables with resolvable milk bag FK`() = runTest {
        val pumpId = db.pumpingDao().insert(PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT"))
        db.milkBagDao().insert(
            MilkBagEntity(collectionDate = 1, volumeMl = 10, sourceSessionId = pumpId, createdAt = 1),
        )
        val tracking = source.readTracking()
        assertEquals(1, tracking.pumping.size)
        assertEquals(1, tracking.milkBags.size)
        assertTrue(tracking.pumping.any { it.id == tracking.milkBags.first().sourceSessionId })
    }

    @Test
    fun `readPreferences maps baby and settings from stored prefs`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("baby_name")] = "Mia"
            it[longPreferencesKey("baby_birth_date")] = 100L
            it[stringPreferencesKey("allergies")] = "DAIRY,EGG"
            it[intPreferencesKey("max_per_breast_minutes")] = 15
            it[stringPreferencesKey("theme_config")] = "DARK"
        }
        val prefs = source.readPreferences()
        assertEquals("Mia", prefs.baby?.name)
        assertEquals(listOf("DAIRY", "EGG"), prefs.baby?.allergies)
        assertEquals(15, prefs.settings.maxPerBreastMinutes)
        assertEquals("DARK", prefs.settings.themeConfig)
    }

    @Test
    fun `readPreferences returns null baby and default settings when empty`() = runTest {
        val prefs = source.readPreferences()
        assertNull(prefs.baby)
        assertEquals("SYSTEM", prefs.settings.themeConfig)
        assertEquals(0, prefs.settings.maxPerBreastMinutes)
        assertEquals(true, prefs.settings.autoUpdateEnabled)
        assertEquals(480, prefs.settings.quietHoursEndMinute)
        assertNull(prefs.settings.wakeTimeMinuteOfDay)
    }

    @Test
    fun `readPreferences sanitizes corrupted stored settings`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("theme_config")] = "NONSENSE"   // unknown enum
            it[intPreferencesKey("predictive_lead_minutes")] = 7    // not in {5,10,15,30}
            it[intPreferencesKey("wake_time_minutes")] = 5000       // out of 0..1439
            it[intPreferencesKey("quiet_hours_start_minute")] = -10 // below range
            it[intPreferencesKey("nap_reminder_delay_minutes")] = 999 // above 480
        }
        val s = source.readPreferences().settings
        assertEquals("SYSTEM", s.themeConfig)
        assertEquals(15, s.predictiveLeadMinutes)
        assertNull(s.wakeTimeMinuteOfDay)
        assertEquals(0, s.quietHoursStartMinute)
        assertEquals(480, s.napReminderDelayMinutes)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupSourceImplTest" -PfastTests`
Expected: FAIL — `BackupSourceImpl` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.PreferencesSnapshot
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.SettingsBackup
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSourceImpl @Inject constructor(
    private val db: BabyTrackerDatabase,
    private val dataStore: DataStore<Preferences>,
) : BackupSource {

    private object Keys {
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
        val PREDICTIVE_ENABLED = booleanPreferencesKey("predictive_enabled")
        val PREDICTIVE_LEAD_MINUTES = intPreferencesKey("predictive_lead_minutes")
        val QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_HOURS_END_MINUTE = intPreferencesKey("quiet_hours_end_minute")
        val NAP_REMINDER_ENABLED = booleanPreferencesKey("nap_reminder_enabled")
        val NAP_REMINDER_DELAY_MINUTES = intPreferencesKey("nap_reminder_delay_minutes")
    }

    override suspend fun readTracking(): TrackingSnapshot = db.withTransaction {
        TrackingSnapshot(
            breastfeeding = db.breastfeedingDao().getAllSessionsOnce().map { it.toBackup() },
            sleep = db.sleepDao().getAllRecordsOnce().map { it.toBackup() },
            pumping = db.pumpingDao().getAllSessionsOnce().map { it.toBackup() },
            milkBags = db.milkBagDao().getAllBagsOnce().map { it.toBackup() },
        )
    }

    override suspend fun readPreferences(): PreferencesSnapshot {
        val p = dataStore.data.first()

        val name = p[Keys.BABY_NAME]
        val birthDate = p[Keys.BABY_BIRTH_DATE]
        val baby = if (name != null && birthDate != null) {
            BabyBackup(
                name = name,
                birthDateEpochDay = birthDate,
                allergies = p[Keys.ALLERGIES]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                customAllergyNote = p[Keys.CUSTOM_ALLERGY_NOTE],
            )
        } else {
            null
        }

        // Sanitize while reading so a backup never captures values the app would reject.
        // These invariants MIRROR SettingsRepositoryImpl's write-side rules; if a rule
        // changes there, change it here and bump CURRENT_BACKUP_FORMAT_VERSION.
        val themeConfig = p[Keys.THEME_CONFIG]
            ?.let { runCatching { ThemeConfig.valueOf(it) }.getOrNull() }
            ?.name
            ?: ThemeConfig.SYSTEM.name
        val wakeMinutes = p[Keys.WAKE_TIME_MINUTES]?.takeIf { it in 0..MAX_MINUTE_OF_DAY }
        val predictiveLead = (p[Keys.PREDICTIVE_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES)
            .takeIf { it in ALLOWED_LEAD_MINUTES } ?: DEFAULT_LEAD_MINUTES

        val settings = SettingsBackup(
            themeConfig = themeConfig,
            maxPerBreastMinutes = p[Keys.MAX_PER_BREAST_MINUTES] ?: 0,
            maxTotalFeedMinutes = p[Keys.MAX_TOTAL_FEED_MINUTES] ?: 0,
            wakeTimeMinuteOfDay = wakeMinutes,
            autoUpdateEnabled = p[Keys.AUTO_UPDATE_ENABLED] ?: true,
            richNotificationsEnabled = p[Keys.RICH_NOTIFICATIONS_ENABLED] ?: true,
            predictiveEnabled = p[Keys.PREDICTIVE_ENABLED] ?: false,
            predictiveLeadMinutes = predictiveLead,
            quietHoursStartMinute = (p[Keys.QUIET_HOURS_START_MINUTE] ?: 0)
                .coerceIn(0, MAX_MINUTE_OF_DAY),
            quietHoursEndMinute = (p[Keys.QUIET_HOURS_END_MINUTE] ?: 480)
                .coerceIn(0, MAX_MINUTE_OF_DAY),
            napReminderEnabled = p[Keys.NAP_REMINDER_ENABLED] ?: false,
            napReminderDelayMinutes = (p[Keys.NAP_REMINDER_DELAY_MINUTES] ?: 60)
                .coerceIn(MIN_NAP_DELAY_MINUTES, MAX_NAP_DELAY_MINUTES),
        )

        return PreferencesSnapshot(baby = baby, settings = settings)
    }

    private companion object {
        const val MAX_MINUTE_OF_DAY = 1439
        const val DEFAULT_LEAD_MINUTES = 15
        val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
        const val MIN_NAP_DELAY_MINUTES = 1
        const val MAX_NAP_DELAY_MINUTES = 480
    }
}
```

> `allergies` is stored as a list of `AllergyType.name` strings; the backup keeps them as raw strings (PR3 validates them back to the enum on import). The sanitization constants above mirror `SettingsRepositoryImpl` exactly: predictive-lead allowlist `{5,10,15,30}` (default 15), wake-time/quiet-hours bounded to a valid minute-of-day `0..1439`, nap delay `coerceIn(1, 480)`, theme falling back to `SYSTEM` on an unrecognized name. Add `import com.babytracker.domain.model.ThemeConfig` to the file.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupSourceImplTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt app/src/test/java/com/babytracker/export/data/BackupSourceImplTest.kt
git commit -m "feat(export): add BackupSourceImpl with transactional and single-prefs reads"
```

---

## Task 7: ExportModule — provide Json + bind BackupSource

**Files:**
- Create: `app/src/main/java/com/babytracker/export/di/ExportModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.babytracker.export.di

import com.babytracker.BuildConfig
import com.babytracker.export.data.BackupSourceImpl
import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindBackupSource(impl: BackupSourceImpl): BackupSource

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Provides
        @Singleton
        fun provideExportMetadata(): ExportMetadata = ExportMetadata(
            appVersion = BuildConfig.VERSION_NAME,
            // Must equal BabyTrackerDatabase's @Database(version = ...); diagnostics-only field.
            roomSchemaVersion = 3,
        )
    }
}
```

> `ignoreUnknownKeys = true` lets a backup with extra fields from a newer minor build still parse; the `backupFormatVersion` gate (PR3) rejects incompatible backups. `encodeDefaults = true` always writes `backupFormatVersion`. Providing `ExportMetadata` here (rather than deferring raw primitives to PR4) keeps the use case fully wired in PR1 and avoids primitive Hilt bindings. `BuildConfig` is `com.babytracker.BuildConfig` (the app's package).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/di/ExportModule.kt
git commit -m "feat(export): provide Json and bind BackupSource via Hilt"
```

---

## Task 8: ExportBackupUseCase

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/usecase/ExportBackupUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ExportBackupUseCaseTest.kt`

Depends only on `BackupSource` (domain) + `Json` + `ExportMetadata` (all Hilt-provided in PR1). Must **not** emit `appMode`/`shareCode` (the source never reads them).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.PreferencesSnapshot
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportBackupUseCaseTest {

    private lateinit var source: BackupSource
    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private lateinit var useCase: ExportBackupUseCase

    private fun settings() = SettingsBackup(
        themeConfig = "SYSTEM", maxPerBreastMinutes = 15, maxTotalFeedMinutes = 30,
        wakeTimeMinuteOfDay = 420, autoUpdateEnabled = true, richNotificationsEnabled = true,
        predictiveEnabled = false, predictiveLeadMinutes = 15, quietHoursStartMinute = 0,
        quietHoursEndMinute = 480, napReminderEnabled = false, napReminderDelayMinutes = 60,
    )

    @BeforeEach
    fun setup() {
        source = mockk()
        coEvery { source.readTracking() } returns TrackingSnapshot(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, null, null, 0),
            ),
            sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
        )
        coEvery { source.readPreferences() } returns PreferencesSnapshot(
            baby = BabyBackup("Mia", 100, listOf("DAIRY"), null),
            settings = settings(),
        )
        useCase = ExportBackupUseCase(
            source, json, ExportMetadata(appVersion = "1.2.3", roomSchemaVersion = 3),
        )
    }

    @Test
    fun `exports all data into parseable backup json`() = runTest {
        val parsed = json.decodeFromString(BackupData.serializer(), useCase())
        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, parsed.backupFormatVersion)
        assertEquals(3, parsed.roomSchemaVersion)
        assertEquals("1.2.3", parsed.appVersion)
        assertEquals(1, parsed.breastfeeding.size)
        assertEquals("Mia", parsed.baby?.name)
        assertEquals(420, parsed.settings.wakeTimeMinuteOfDay)
    }

    @Test
    fun `does not leak sharing state into json`() = runTest {
        val jsonString = useCase()
        assertFalse(jsonString.contains("appMode", ignoreCase = true))
        assertFalse(jsonString.contains("shareCode", ignoreCase = true))
        assertTrue(jsonString.contains("breastfeeding"))
    }

    @Test
    fun `finalizes in-progress sessions at export time`() = runTest {
        coEvery { source.readTracking() } returns TrackingSnapshot(
            breastfeeding = listOf(
                // active: endTime null, currently paused
                BreastfeedingBackup(1, 10, null, "LEFT", null, null, pausedAt = 50, pausedDurationMs = 5),
            ),
            sleep = listOf(SleepBackup(2, 10, null, "NAP", null)),
            pumping = listOf(
                PumpingBackup(3, 10, null, "BOTH", null, null, pausedAt = 60, pausedDurationMs = 7),
            ),
            milkBags = emptyList(),
        )
        coEvery { source.readPreferences() } returns PreferencesSnapshot(null, settings())

        val parsed = json.decodeFromString(BackupData.serializer(), useCase())
        val bf = parsed.breastfeeding.single()
        val pump = parsed.pumping.single()

        // endTime finalized, pausedAt cleared, and the in-flight pause interval folded in:
        // pausedDurationMs grows from its base (5/7) by (exportedAt - pausedAt), so it must be
        // strictly greater than the original accumulated value.
        assertNotNull(bf.endTime)
        assertNull(bf.pausedAt)
        assertTrue(bf.pausedDurationMs > 5L, "breastfeeding pause must be folded")
        assertNotNull(parsed.sleep.single().endTime)
        assertNotNull(pump.endTime)
        assertNull(pump.pausedAt)
        assertTrue(pump.pausedDurationMs > 7L, "pumping pause must be folded")
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ExportBackupUseCaseTest" -PfastTests`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val source: BackupSource,
    private val json: Json,
    private val metadata: ExportMetadata,
) {
    suspend operator fun invoke(): String {
        val tracking = source.readTracking()
        val prefs = source.readPreferences()
        val exportedAt = System.currentTimeMillis()

        // Backup-format-v1 invariant: in-progress sessions (endTime == null) are FINALIZED at
        // exportedAt before serialization. The app keeps at most one active row per type
        // (endTime IS NULL); round-tripping a live timer would otherwise restore a stale active
        // session — or a second active session into a DB that already has one — yielding a zombie
        // record and broken duration/notification state. Finalizing makes every backed-up row a
        // completed record, so import can merge them as plain history.
        //
        // If the row is PAUSED at export (pausedAt != null), the in-flight pause interval
        // [pausedAt, exportedAt] is folded into pausedDurationMs BEFORE pausedAt is cleared —
        // matching the repo's stop path (e.g. StopPumpingSessionUseCase). Dropping it would make
        // the restored completed session count those paused minutes as active feed/pump time.
        val backup = BackupData(
            roomSchemaVersion = metadata.roomSchemaVersion,
            appVersion = metadata.appVersion,
            exportedAt = exportedAt,
            baby = prefs.baby,
            settings = prefs.settings,
            breastfeeding = tracking.breastfeeding.map { it.finalizeIfActive(exportedAt) },
            sleep = tracking.sleep.map { it.finalizeIfActive(exportedAt) },
            pumping = tracking.pumping.map { it.finalizeIfActive(exportedAt) },
            milkBags = tracking.milkBags,
        )

        return json.encodeToString(BackupData.serializer(), backup)
    }

    private fun BreastfeedingBackup.finalizeIfActive(now: Long) =
        if (endTime == null) {
            copy(endTime = now, pausedDurationMs = foldPause(now), pausedAt = null)
        } else {
            this
        }

    private fun SleepBackup.finalizeIfActive(now: Long) =
        if (endTime == null) copy(endTime = now) else this

    private fun PumpingBackup.finalizeIfActive(now: Long) =
        if (endTime == null) {
            copy(endTime = now, pausedDurationMs = foldPumpPause(now), pausedAt = null)
        } else {
            this
        }

    private fun BreastfeedingBackup.foldPause(now: Long): Long =
        pausedDurationMs + (pausedAt?.let { maxOf(0L, now - it) } ?: 0L)

    private fun PumpingBackup.foldPumpPause(now: Long): Long =
        pausedDurationMs + (pausedAt?.let { maxOf(0L, now - it) } ?: 0L)
}
```

> Milk bags have no in-progress concept (`usedAt == null` just means "unused", a valid persistent state) and are exported as-is. Because active pumping rows are finalized (not dropped), a milk bag's `sourceSessionId` never points at a removed row — no dangling FK is introduced.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ExportBackupUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ExportBackupUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ExportBackupUseCaseTest.kt
git commit -m "feat(export): add ExportBackupUseCase building BackupData JSON"
```

---

## Task 9: ExportCsvUseCase (export-only)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt`

Produces one CSV per table, `Map<String, String>` keyed `breastfeeding`/`sleep`/`pumping`/`milk_bags`, from the consistent `readTracking()` snapshot. Export-only; never parsed back.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BreastfeedingBackup
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportCsvUseCaseTest {

    private lateinit var source: BackupSource
    private lateinit var useCase: ExportCsvUseCase

    @BeforeEach
    fun setup() {
        source = mockk()
        useCase = ExportCsvUseCase(source)
    }

    private fun tracking(breastfeeding: List<BreastfeedingBackup> = emptyList()) =
        TrackingSnapshot(breastfeeding, emptyList(), emptyList(), emptyList())

    @Test
    fun `breastfeeding csv has header and escaped notes row`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "had a, \"big\" feed", null, 0),
            ),
        )
        val csv = useCase().getValue("breastfeeding")
        val lines = csv.trim().split("\n")
        assertEquals(
            "id,start_time,end_time,starting_side,switch_time,notes,paused_at,paused_duration_ms",
            lines[0],
        )
        assertTrue(lines[1].contains("\"had a, \"\"big\"\" feed\""))
    }

    @Test
    fun `returns a csv for every table`() = runTest {
        coEvery { source.readTracking() } returns tracking()
        assertEquals(setOf("breastfeeding", "sleep", "pumping", "milk_bags"), useCase().keys)
    }

    @Test
    fun `neutralizes spreadsheet formula in note field`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "=HYPERLINK(\"http://evil\")", null, 0),
            ),
        )
        val dataRow = useCase().getValue("breastfeeding").trim().split("\n")[1]
        // Leading '=' must be defused with an apostrophe so it is not a live formula,
        // and because the apostrophe-prefixed value contains a comma+quote it is RFC-4180 quoted.
        assertTrue(dataRow.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""))
    }

    @Test
    fun `neutralizes formula hidden behind leading whitespace`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "   +1+1", null, 0),
            ),
        )
        val dataRow = useCase().getValue("breastfeeding").trim().split("\n")[1]
        assertTrue(dataRow.startsWith("1,10,20,LEFT,,'   +1+1"))
    }

    @Test
    fun `quotes notes containing carriage returns and CRLF`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "line1\r\nline2\rline3", null, 0),
            ),
        )
        // A bare \r or \r\n inside a note must be wrapped in quotes so a CSV reader
        // does not treat it as a record separator and split one row into several.
        val csv = useCase().getValue("breastfeeding")
        assertTrue(csv.contains("\"line1\r\nline2\rline3\""))
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ExportCsvUseCaseTest" -PfastTests`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    private val source: BackupSource,
) {
    suspend operator fun invoke(): Map<String, String> {
        val t = source.readTracking()
        return mapOf(
            "breastfeeding" to buildCsv(
                listOf(
                    "id", "start_time", "end_time", "starting_side",
                    "switch_time", "notes", "paused_at", "paused_duration_ms",
                ),
                t.breastfeeding.map {
                    listOf(
                        it.id, it.startTime, it.endTime, it.startingSide,
                        it.switchTime, it.notes, it.pausedAt, it.pausedDurationMs,
                    )
                },
            ),
            "sleep" to buildCsv(
                listOf("id", "start_time", "end_time", "sleep_type", "notes"),
                t.sleep.map { listOf(it.id, it.startTime, it.endTime, it.sleepType, it.notes) },
            ),
            "pumping" to buildCsv(
                listOf(
                    "id", "start_time", "end_time", "breast",
                    "volume_ml", "notes", "paused_at", "paused_duration_ms",
                ),
                t.pumping.map {
                    listOf(
                        it.id, it.startTime, it.endTime, it.breast,
                        it.volumeMl, it.notes, it.pausedAt, it.pausedDurationMs,
                    )
                },
            ),
            "milk_bags" to buildCsv(
                listOf(
                    "id", "collection_date", "volume_ml", "source_session_id",
                    "used_at", "notes", "created_at",
                ),
                t.milkBags.map {
                    listOf(
                        it.id, it.collectionDate, it.volumeMl, it.sourceSessionId,
                        it.usedAt, it.notes, it.createdAt,
                    )
                },
            ),
        )
    }

    private fun buildCsv(header: List<String>, rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { escape(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun escape(value: Any?): String {
        val raw = value?.toString() ?: ""
        // CSV formula-injection defense: a cell whose first non-whitespace char is one of
        // = + - @ is a live formula in Excel/Sheets. Prefix with an apostrophe so the cell
        // renders as literal text. Done before RFC-4180 quoting.
        val safe = if (startsWithFormulaTrigger(raw)) "'$raw" else raw
        return if (
            safe.contains(',') || safe.contains('"') ||
            safe.contains('\n') || safe.contains('\r')
        ) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun startsWithFormulaTrigger(s: String): Boolean {
        val firstNonSpace = s.firstOrNull { !it.isWhitespace() } ?: return false
        return firstNonSpace == '=' || firstNonSpace == '+' ||
            firstNonSpace == '-' || firstNonSpace == '@'
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.ExportCsvUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt
git commit -m "feat(export): add export-only ExportCsvUseCase with RFC-4180 escaping"
```

---

## Task 10: Configure FileProvider

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

FileProvider is for **transient** share artifacts (CSV; in PR2 the PDF). The durable JSON backup does not use it.

- [ ] **Step 1: Create the paths file**

`app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="exports/" />
</paths>
```

- [ ] **Step 2: Register the provider**

Inside `<application>` in `app/src/main/AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

> `androidx.core.content.FileProvider` ships with `androidx.core:core-ktx` (already present).

- [ ] **Step 3: Verify merge**

Run: `./gradlew :app:processDebugManifest -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(export): register FileProvider for transient share artifacts"
```

---

## Task 11: BackupFileWriter

**Files:**
- Create: `app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt`
- Test: `app/src/test/java/com/babytracker/export/data/BackupFileWriterTest.kt`

A thin byte-writer with **no durability claims of its own** — it writes whatever `Uri` it's given and propagates any failure so the caller never reports a false success. The "survive uninstall / never destroy a prior backup" guarantee is owned by **PR4's UI**, which uses `ActivityResultContracts.CreateDocument`. That SAF contract always returns a *freshly created* document `Uri` (the system picker auto-suffixes on filename collision), so the durable export never overwrites an existing backup in place. PR4 must not feed `writeToUri` a persisted/old `Uri`.

Both methods are `suspend` and do their file/`ContentResolver` I/O inside `withContext(Dispatchers.IO)` — large backups or slow cloud-backed document providers must never run on the caller's (often main) thread and risk an ANR. This mirrors the existing `UpdateChecker` convention (`suspend fun ... = withContext(Dispatchers.IO) { ... }`).

- `suspend writeToUri(uri, content)` — write UTF-8 bytes to the given SAF `Uri`. Throws on open/write failure.
- `suspend writeCacheFile(fileName, content)` — write to cache `exports/`, return a FileProvider `Uri` for `ACTION_SEND`.

- [ ] **Step 1: Write the failing test (Robolectric, JVM)**

```kotlin
package com.babytracker.export.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupFileWriterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val writer = BackupFileWriter(context)

    @Test
    fun `writeCacheFile returns a readable content uri with the given content`() = runTest {
        val uri = writer.writeCacheFile("backup.json", "{\"x\":1}")
        assertEquals("content", uri.scheme)
        val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals("{\"x\":1}", read)
    }

    @Test
    fun `two writes with the same name do not clobber each other`() = runTest {
        val first = writer.writeCacheFile("backup.json", "FIRST")
        val second = writer.writeCacheFile("backup.json", "SECOND")
        // Distinct Uris (unique per-export subdir) and the first is still intact after the second.
        assertTrue(first != second)
        val firstContent = context.contentResolver.openInputStream(first)!!
            .use { it.readBytes().decodeToString() }
        val secondContent = context.contentResolver.openInputStream(second)!!
            .use { it.readBytes().decodeToString() }
        assertEquals("FIRST", firstContent)
        assertEquals("SECOND", secondContent)
    }

    @Test
    fun `writeToUri writes bytes to the given stream`() = runTest {
        val target = File(context.cacheDir, "saf-target.json")
        writer.writeToUri(android.net.Uri.fromFile(target), "hello")
        assertEquals("hello", target.readText())
    }

    @Test
    fun `writeToUri truncates a longer existing target leaving no stale bytes`() = runTest {
        val target = File(context.cacheDir, "saf-overwrite.json")
        val uri = android.net.Uri.fromFile(target)
        writer.writeToUri(uri, "AAAAAAAAAAAAAAAAAAAA") // 20 chars
        writer.writeToUri(uri, "BBB")                   // 3 chars
        assertEquals("BBB", target.readText())          // no trailing "AAAA..." remains
    }

    @Test
    fun `writeToUri propagates error when stream cannot be opened`() = runTest {
        val bogus = android.net.Uri.parse("content://com.babytracker.nonexistent/doc/1")
        // The suspend call is invoked inside this lambda; the exception surfaces synchronously
        // within runTest's scope.
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { writer.writeToUri(bogus, "data") }
        }
    }

    @Test
    fun `writeCacheFile rejects path traversal and absolute names`() = runTest {
        for (bad in listOf("../escape.json", "nested/dir.json", "/abs/path.json", "..")) {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { writer.writeCacheFile(bad, "x") }
            }
        }
        // The traversal target must not have been created.
        assertTrue(!File(context.cacheDir, "escape.json").exists())
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileWriterTest" -PfastTests`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Writes UTF-8 bytes to the given SAF Uri off the main thread. No durability
     * guarantee here: callers (PR4) must obtain the Uri from ACTION_CREATE_DOCUMENT so
     * the write targets a freshly created document, never an existing backup. Any
     * failure propagates — do not treat a thrown exception as success.
     */
    suspend fun writeToUri(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        // "wt" = write + TRUNCATE. The Uri always comes from ACTION_CREATE_DOCUMENT (a fresh,
        // empty doc), so truncation is harmless there; it also guarantees that if this is ever
        // handed a non-empty document, a shorter backup cannot leave stale trailing bytes that
        // would corrupt a later JSON import.
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Could not open output stream for $uri")
    }

    /**
     * Transient path: write to cache off the main thread, return a shareable FileProvider Uri.
     *
     * Each call writes into a fresh UUID subdirectory of exports/, so a later export can never
     * overwrite the file behind a Uri whose share/receive is still pending — yet the recipient
     * still sees the human-readable [fileName]. The OS evicts the cache dir over time, so old
     * artifacts are reclaimed automatically (no explicit cleanup needed here).
     */
    suspend fun writeCacheFile(fileName: String, content: String): Uri = withContext(Dispatchers.IO) {
        // Reject path separators / relative-dir names before touching the filesystem: a value
        // like "../foo" or an absolute path could otherwise escape the export dir and truncate
        // an app-private file (FileProvider only validates AFTER the write).
        require(
            !fileName.contains('/') && !fileName.contains('\\') &&
                fileName != "." && fileName != "..",
        ) { "Invalid export file name: $fileName" }

        val uniqueDir = File(File(context.cacheDir, "exports"), java.util.UUID.randomUUID().toString())
            .apply { mkdirs() }
        val file = File(uniqueDir, fileName)
        // Defense in depth: the resolved file must sit directly inside its unique dir.
        require(file.canonicalFile.parentFile == uniqueDir.canonicalFile) {
            "Resolved export path escapes exports dir: $fileName"
        }
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileWriterTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt app/src/test/java/com/babytracker/export/data/BackupFileWriterTest.kt
git commit -m "feat(export): add BackupFileWriter for SAF and cache writes"
```

---

## Task 12: Architecture isolation rule + full suite

**Files:**
- Modify: `app/src/test/java/com/babytracker/architecture/LayerIsolationTest.kt`

- [ ] **Step 1: Add an export-domain isolation rule**

Append to the `LayerIsolationTest` class (matching the existing Konsist `assertFalse { }` style — which always executes, unlike a raw `assert`). This bans `export/domain` from importing the export data layer, the core data layer, Room, or Android:

```kotlin
@Test
fun `export domain layer does not import data or framework`() {
    productionScope
        .files
        .filter { it.packagee?.fullyQualifiedName?.contains("com.babytracker.export.domain") == true }
        .assertFalse { file ->
            file.imports.any { import ->
                import.name.startsWith("com.babytracker.export.data") ||
                    import.name.startsWith("com.babytracker.data") ||
                    import.name.startsWith("androidx.room") ||
                    import.name.startsWith("android.")
            }
        }
}
```

> Konsist's `assertFalse { }` over `productionScope.files` always runs the predicate and fails the test on any match — no JVM-assertion dependency. If `productionScope.files.assertFalse` isn't available in this Konsist version, fall back to `productionScope.classes().withPackage("com.babytracker.export.domain..").assertFalse { ... it.containingFile.imports ... }`, mirroring the existing `domain layer does not import UI or data layers` test exactly. (`kotlinx.serialization` imports are allowed — only the four prefixes above are banned.)

- [ ] **Step 2: Run the architecture tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: PASS. (Slow on WSL — budget ~15 min cold per CLAUDE.md.) A failure here is a real layering bug — move the offending code into `export/data`, don't weaken the rule.

- [ ] **Step 3: Formatting + static analysis**

Run: `./gradlew ktlintFormat detekt`
Expected: `BUILD SUCCESSFUL`. Fix detekt findings by changing code, never `@Suppress`.

- [ ] **Step 4: Full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/babytracker/architecture/LayerIsolationTest.kt
git commit -m "test(export): enforce export domain-layer isolation"
```

(Plus a separate `style(export): apply ktlint formatting` commit if Step 3 changed files.)

---

## Acceptance Criteria

- `kotlinx-serialization-json` + plugin in catalog and applied in `:app`.
- `BackupData` + six DTOs `@Serializable`, full-fidelity; `export/domain` imports only Kotlin/kotlinx (enforced by Task 12).
- `BackupSource` is a pure domain interface; the use cases depend on it, never on `BackupSourceImpl`, DAOs, entities, or preference keys.
- `BackupSourceImpl.readTracking()` reads all four tables in one Room transaction (no orphan `sourceSessionId` possible); `readPreferences()` reads baby + settings from a single `Preferences` snapshot (atomic for DataStore) and **sanitizes** corrupt stored settings to the same invariants `SettingsRepositoryImpl` enforces on write.
- `ExportBackupUseCase` produces parseable JSON with `backupFormatVersion`/`roomSchemaVersion`/`appVersion`/all tables/baby/settings — never emits `appMode`/`shareCode`. In-progress breastfeeding/sleep/pumping rows are finalized at `exportedAt` (`endTime` set, `pausedAt` cleared) so no active session is ever serialized.
- `ExportCsvUseCase` produces one RFC-4180-escaped CSV per table from the same snapshot, with formula-injection defense (cells whose first non-whitespace char is `=`/`+`/`-`/`@` are apostrophe-prefixed).
- `BackupFileWriter` exposes `suspend` methods that do I/O on `Dispatchers.IO`, writes to a given SAF `Uri`, and propagates errors; makes no durability claim — PR4's `CreateDocument` contract owns the freshly-created-document guarantee.
- `FileProvider` registered with authority `${applicationId}.fileprovider`.
- The JUnit4/Robolectric tests (`SnapshotReadsTest`, `BackupSourceImplTest`, `BackupFileWriterTest`) are actually discovered and executed (Vintage engine on the test runtime classpath).
- `./gradlew :app:testDebugUnitTest`, `ktlintFormat`, `detekt` all pass.

## Notes for downstream PRs

- PR2 (PDF) reuses `BackupFileWriter.writeCacheFile` + the FileProvider for sharing the PDF.
- **Share-intent grant contract (PR2/PR4):** returning a FileProvider `Uri` is not enough — `android:grantUriPermissions="true"` only *permits* grants. Any `ACTION_SEND` that ships one of these `Uri`s **must** set the MIME type, put the `Uri` in `EXTRA_STREAM`, add `Intent.FLAG_GRANT_READ_URI_PERMISSION`, and set `ClipData` (so the grant propagates), or recipient apps fail to open the artifact. The PR2/PR4 share path must include this and test it.
- PR3 (import) reuses `BackupData` + the `export/data` `*.toEntity()` converters, and adds `backupFormatVersion` gate logic in a `BackupFileReader`.
- PR4 (UI) injects `ExportBackupUseCase` (already fully wired via Hilt — `ExportMetadata` is provided in PR1's `ExportModule`), drives `ActivityResultContracts.CreateDocument` for the durable JSON write (feeding the resulting fresh `Uri` to `writeToUri`), and offers `ACTION_SEND` for CSV/PDF.
