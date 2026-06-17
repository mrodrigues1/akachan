# Vaccine Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-194

**Goal:** Add the persistence foundation for vaccine tracking — domain model, status enum, Room entity + DAO, repository, DI wiring, and a database migration to v14.

**Architecture:** A vaccine is a point-in-time event (no duration), so this layer mirrors the simpler `Diaper` / `BottleFeed` data layer rather than the start/stop `Sleep` layer. The one structural difference is the **scheduled → administered lifecycle**: a single entity carries a `status` enum (`SCHEDULED` / `ADMINISTERED`) plus two nullable dates (`scheduledDate`, `administeredDate`). Pure-Kotlin domain, a Room entity with extension-function mapping (no Mapper class), a Flow-returning repository, and an additive `CREATE TABLE` migration with no active-row trigger.

**Tech Stack:** Kotlin 2.3.20, Room 2.8.4, Hilt 2.59, Coroutines/Flow, JUnit 5 + MockK + Turbine (unit), Robolectric/Room in-memory + `MigrationTestHelper` (DB tests).

**Dependencies:** None. This is the base plan; plans 2–7 depend on it.

**Suggested implementation branch:** `feat/vaccine-data-layer`

**Project convention:** Implement first, then tests (no strict TDD — per `CLAUDE.md`). Commit after each task. Do not run `ktlintFormat`/`detekt` manually — the pre-commit hook runs them.

---

### Task 1: `VaccineStatus` enum + parsers

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/VaccineStatus.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/VaccineStatusTest.kt`

- [ ] **Step 1: Create the enum** (mirrors `DiaperType.kt` parser pattern). No `label`/`emoji` — the user-facing name is free text, so the enum is status only.

```kotlin
package com.babytracker.domain.model

enum class VaccineStatus { SCHEDULED, ADMINISTERED }

fun String.toVaccineStatusOrNull(): VaccineStatus? = when (this) {
    VaccineStatus.SCHEDULED.name -> VaccineStatus.SCHEDULED
    VaccineStatus.ADMINISTERED.name -> VaccineStatus.ADMINISTERED
    else -> null
}

// Defaults to ADMINISTERED: an unknown/corrupt stored value is treated as a logged shot
// rather than a pending schedule, so it never fabricates a future reminder.
fun String.toVaccineStatusSafe(): VaccineStatus = toVaccineStatusOrNull() ?: VaccineStatus.ADMINISTERED
```

- [ ] **Step 2: Write the test**

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VaccineStatusTest {
    @Test
    fun `parses enum names`() {
        assertEquals(VaccineStatus.SCHEDULED, "SCHEDULED".toVaccineStatusOrNull())
        assertEquals(VaccineStatus.ADMINISTERED, "ADMINISTERED".toVaccineStatusOrNull())
    }

    @Test
    fun `returns null for unknown`() {
        assertNull("nope".toVaccineStatusOrNull())
    }

    @Test
    fun `safe defaults to ADMINISTERED`() {
        assertEquals(VaccineStatus.ADMINISTERED, "garbage".toVaccineStatusSafe())
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.model.VaccineStatusTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add VaccineStatus enum and parsers`

---

### Task 2: `VaccineRecord` domain model + helpers

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/VaccineRecord.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/VaccineRecordTest.kt`

- [ ] **Step 1: Create the model + pure helpers** (zero framework imports). `effectiveDate()` is the date used for history sorting/grouping; `isOverdue(now)` flags a scheduled shot whose date has passed.

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class VaccineRecord(
    val id: Long = 0,
    val name: String,
    val doseLabel: String? = null,
    val status: VaccineStatus,
    val scheduledDate: Instant? = null,
    val administeredDate: Instant? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

fun VaccineRecord.effectiveDate(): Instant = administeredDate ?: scheduledDate ?: createdAt

fun VaccineRecord.isOverdue(now: Instant): Boolean =
    status == VaccineStatus.SCHEDULED && scheduledDate != null && scheduledDate.isBefore(now)
```

- [ ] **Step 2: Write the test**

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineRecordTest {
    private val now = Instant.ofEpochMilli(10_000)

    @Test
    fun `effectiveDate prefers administered then scheduled then created`() {
        val base = VaccineRecord(name = "MMR", status = VaccineStatus.ADMINISTERED, createdAt = Instant.ofEpochMilli(1))
        assertEquals(Instant.ofEpochMilli(9), base.copy(administeredDate = Instant.ofEpochMilli(9)).effectiveDate())
        assertEquals(Instant.ofEpochMilli(8), base.copy(status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(8)).effectiveDate())
        assertEquals(Instant.ofEpochMilli(1), base.effectiveDate())
    }

    @Test
    fun `isOverdue only when scheduled and past`() {
        val past = VaccineRecord(name = "BCG", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(5), createdAt = now)
        val future = past.copy(scheduledDate = Instant.ofEpochMilli(20_000))
        val administered = past.copy(status = VaccineStatus.ADMINISTERED, administeredDate = now)
        assertTrue(past.isOverdue(now))
        assertFalse(future.isOverdue(now))
        assertFalse(administered.isOverdue(now))
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.model.VaccineRecordTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add VaccineRecord model and helpers`

---

### Task 3: `VaccineEntity` + mapping

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/VaccineEntity.kt`
- Test: `app/src/test/java/com/babytracker/data/local/entity/VaccineEntityMappingTest.kt`

- [ ] **Step 1: Create entity + mapping** (mirrors `DiaperEntity.kt`). Two indices — one on `scheduled_date` (upcoming queries) and one on `status` (status filters). The `indices` declaration makes Room's generated schema match the migration's indices.

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.toVaccineStatusSafe
import java.time.Instant

@Entity(
    tableName = "vaccines",
    indices = [Index(value = ["scheduled_date"]), Index(value = ["status"])],
)
data class VaccineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "dose_label") val doseLabel: String? = null,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: Long? = null,
    @ColumnInfo(name = "administered_date") val administeredDate: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun VaccineEntity.toDomain(): VaccineRecord = VaccineRecord(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status.toVaccineStatusSafe(),
    scheduledDate = scheduledDate?.let { Instant.ofEpochMilli(it) },
    administeredDate = administeredDate?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun VaccineRecord.toEntity(): VaccineEntity = VaccineEntity(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status.name,
    scheduledDate = scheduledDate?.toEpochMilli(),
    administeredDate = administeredDate?.toEpochMilli(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
```

- [ ] **Step 2: Write round-trip test**

```kotlin
package com.babytracker.data.local.entity

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineEntityMappingTest {
    @Test
    fun `scheduled record round trips`() {
        val record = VaccineRecord(
            id = 7,
            name = "DTaP",
            doseLabel = "1st dose",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000),
            administeredDate = null,
            notes = "left thigh",
            createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun `administered record round trips`() {
        val record = VaccineRecord(
            id = 3,
            name = "MMR",
            status = VaccineStatus.ADMINISTERED,
            administeredDate = Instant.ofEpochMilli(9_000),
            createdAt = Instant.ofEpochMilli(8_000),
        )
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun `unknown stored status maps to ADMINISTERED`() {
        val entity = VaccineEntity(id = 1, name = "x", status = "???", createdAt = 0)
        assertEquals(VaccineStatus.ADMINISTERED, entity.toDomain().status)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.data.local.entity.VaccineEntityMappingTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add VaccineEntity and mapping`

---

### Task 4: `VaccineDao`

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/dao/VaccineDao.kt`

- [ ] **Step 1: Create the DAO.** `observeAll` orders by the effective date via `COALESCE`. `observeUpcoming` lists pending shots soonest-first. `getScheduledFutureAfter` is the one-shot read the reminder boot-rescheduler (plan 6) uses. `getAllOnce` is consumed by plan 7's backup source.

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query(
        "SELECT * FROM vaccines ORDER BY COALESCE(administered_date, scheduled_date, created_at) DESC",
    )
    fun observeAll(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE status = 'SCHEDULED' ORDER BY scheduled_date ASC")
    fun observeUpcoming(): Flow<List<VaccineEntity>>

    @Query(
        "SELECT * FROM vaccines WHERE status = 'SCHEDULED' AND scheduled_date IS NOT NULL " +
            "AND scheduled_date > :nowMs ORDER BY scheduled_date ASC",
    )
    suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineEntity>

    @Query("SELECT * FROM vaccines ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<VaccineEntity>

    @Query("SELECT * FROM vaccines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VaccineEntity?

    @Insert
    suspend fun insert(entity: VaccineEntity): Long

    @Update
    suspend fun update(entity: VaccineEntity)

    @Query("DELETE FROM vaccines WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
```

- [ ] **Step 2: Commit** `feat(vaccine): add VaccineDao`

---

### Task 5: `VaccineRepository` interface + impl

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/VaccineRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/VaccineRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/VaccineRepositoryImplTest.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.VaccineRecord
import kotlinx.coroutines.flow.Flow

interface VaccineRepository {
    fun observeAll(): Flow<List<VaccineRecord>>
    fun observeUpcoming(): Flow<List<VaccineRecord>>
    suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineRecord>
    suspend fun getAllOnce(): List<VaccineRecord>
    suspend fun getById(id: Long): VaccineRecord?
    suspend fun insert(record: VaccineRecord): Long
    suspend fun update(record: VaccineRecord)
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Impl** (mirrors `DiaperRepositoryImpl.kt`)

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.VaccineDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineRepositoryImpl @Inject constructor(
    private val dao: VaccineDao,
) : VaccineRepository {
    override fun observeAll(): Flow<List<VaccineRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUpcoming(): Flow<List<VaccineRecord>> =
        dao.observeUpcoming().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineRecord> =
        dao.getScheduledFutureAfter(nowMs).map { it.toDomain() }

    override suspend fun getAllOnce(): List<VaccineRecord> = dao.getAllOnce().map { it.toDomain() }

    override suspend fun getById(id: Long): VaccineRecord? = dao.getById(id)?.toDomain()

    override suspend fun insert(record: VaccineRecord): Long = dao.insert(record.toEntity())

    override suspend fun update(record: VaccineRecord) = dao.update(record.toEntity())

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
```

- [ ] **Step 3: Test** (MockK over the DAO; Turbine for the Flow)

```kotlin
package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.VaccineDao
import com.babytracker.data.local.entity.VaccineEntity
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineRepositoryImplTest {
    private lateinit var dao: VaccineDao
    private lateinit var repository: VaccineRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = VaccineRepositoryImpl(dao)
    }

    @Test
    fun `observeAll maps entities to domain`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(VaccineEntity(id = 1, name = "MMR", status = "ADMINISTERED", administeredDate = 10, createdAt = 5)),
        )
        repository.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(VaccineStatus.ADMINISTERED, list.first().status)
            awaitComplete()
        }
    }

    @Test
    fun `insert converts to entity`() = runTest {
        val captured = slot<VaccineEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42
        val id = repository.insert(
            VaccineRecord(
                name = "BCG",
                status = VaccineStatus.SCHEDULED,
                scheduledDate = Instant.ofEpochMilli(10),
                createdAt = Instant.ofEpochMilli(5),
            ),
        )
        assertEquals(42, id)
        assertEquals("SCHEDULED", captured.captured.status)
        assertEquals(10L, captured.captured.scheduledDate)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        repository.deleteById(9)
        coVerify { dao.deleteById(9) }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.data.repository.VaccineRepositoryImplTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(vaccine): add VaccineRepository and impl`

---

### Task 6: Database migration to v14 + DAO accessor

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`

- [ ] **Step 1: Register the entity, bump version, add the DAO accessor.** In the `@Database` annotation add `VaccineEntity::class` to `entities`, change `version = 13` to `version = 14`, and add to the abstract class:

```kotlin
abstract fun vaccineDao(): VaccineDao
```

Add the imports `import com.babytracker.data.local.entity.VaccineEntity` and `import com.babytracker.data.local.dao.VaccineDao`.

- [ ] **Step 2: Add the migration** (place after `MIGRATION_12_13`; additive `CREATE TABLE`, no active-row trigger because a vaccine has no in-progress state). Verify the exact `database` parameter name matches the existing migrations in this file.

```kotlin
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vaccines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                dose_label TEXT,
                status TEXT NOT NULL,
                scheduled_date INTEGER,
                administered_date INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vaccines_scheduled_date ON vaccines(scheduled_date)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vaccines_status ON vaccines(status)",
        )
    }
}
```

- [ ] **Step 3: Regenerate + commit the exported schema.** Run `./gradlew :app:compileDebugKotlin` (or `build`) so Room writes `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/14.json`. Commit this generated file alongside the migration — `MigrationTestHelper` and CI read it. Missing it fails the build.
- [ ] **Step 4: Commit** `feat(vaccine): add vaccines table migration v14`

---

### Task 7: Hilt wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 1:** In `DatabaseModule`, add `MIGRATION_13_14` to the `.addMigrations(...)` list (with its import), and add the DAO provider:

```kotlin
@Provides
fun provideVaccineDao(database: BabyTrackerDatabase): VaccineDao = database.vaccineDao()
```

(add `import com.babytracker.data.local.dao.VaccineDao` and `import com.babytracker.data.local.MIGRATION_13_14`)

- [ ] **Step 2:** In `RepositoryModule`, add the binding:

```kotlin
@Binds
@Singleton
abstract fun bindVaccineRepository(impl: VaccineRepositoryImpl): VaccineRepository
```

(add imports for `VaccineRepositoryImpl` and `VaccineRepository`)

- [ ] **Step 3: Commit** `feat(vaccine): wire vaccine DAO and repository into Hilt`

---

### Task 8: DAO + migration instrumentation tests

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/data/local/dao/VaccineDaoTest.kt`
- Create: `app/src/androidTest/java/com/babytracker/data/local/VaccineMigrationTest.kt`

Follow the existing DAO/migration test patterns in `app/src/androidTest/java/com/babytracker/data/local/` (Room `inMemoryDatabaseBuilder` for DAO tests; `MigrationTestHelper` with the exported schema under `app/schemas/` for the migration test).

- [ ] **Step 1: DAO test** — insert a mix of scheduled + administered rows; assert `observeAll()` orders by effective date (administered/scheduled/created COALESCE) DESC; `observeUpcoming()` returns only `SCHEDULED` ascending by scheduled date; `getScheduledFutureAfter(now)` excludes past scheduled and all administered; `update` flips a row to administered; `deleteById` removes and returns 1.

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaccineDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: VaccineDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vaccineDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upcomingAndFutureFilter() = runTest {
        // past scheduled (overdue), future scheduled, administered
        dao.insert(VaccineEntity(name = "Overdue", status = "SCHEDULED", scheduledDate = 100, createdAt = 1))
        dao.insert(VaccineEntity(name = "Future", status = "SCHEDULED", scheduledDate = 5_000, createdAt = 1))
        dao.insert(VaccineEntity(name = "Given", status = "ADMINISTERED", administeredDate = 200, createdAt = 1))

        val upcoming = dao.observeUpcoming().first()
        assertEquals(listOf("Overdue", "Future"), upcoming.map { it.name }) // both scheduled, asc

        val future = dao.getScheduledFutureAfter(1_000)
        assertEquals(listOf("Future"), future.map { it.name }) // only > 1000 and SCHEDULED
    }

    @Test
    fun markAdministeredAndDelete() = runTest {
        val id = dao.insert(VaccineEntity(name = "BCG", status = "SCHEDULED", scheduledDate = 100, createdAt = 1))
        dao.update(VaccineEntity(id = id, name = "BCG", status = "ADMINISTERED", administeredDate = 150, createdAt = 1))
        assertEquals("ADMINISTERED", dao.getById(id)!!.status)
        assertEquals(0, dao.observeUpcoming().first().size)
        assertEquals(1, dao.deleteById(id))
    }
}
```

- [ ] **Step 2: Migration test** — build the DB at v13 with `MigrationTestHelper`, run `MIGRATION_13_14`, assert the `vaccines` table is queryable. Mirror the closest existing migration test (e.g. the one covering `MIGRATION_12_13`).
- [ ] **Step 3: Run** the instrumentation tests on an emulator: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.dao.VaccineDaoTest"` (and the migration test). Expect PASS. If no emulator is available, note it and rely on `./gradlew test` for the unit layer.
- [ ] **Step 4: Commit** `test(vaccine): add VaccineDao and migration tests`

---

## Acceptance Criteria

- `./gradlew build` succeeds (Room schema for v14 is exported under `app/schemas/`).
- `./gradlew test` passes, including the new unit tests.
- DAO + migration instrumentation tests pass on an emulator.
- `vaccines` table exists at schema v14 with `scheduled_date` and `status` indices; fresh installs and v13→v14 upgrades both produce the table.
- No active-row trigger is added (vaccines have no in-progress state).
- `VaccineRepository` is injectable wherever a constructor requests it.

## Self-Review Notes

- Spec coverage: status enum, domain model + `effectiveDate`/`isOverdue`, entity+mapping, DAO (incl. `getScheduledFutureAfter` for plan 6 and `getAllOnce` for plan 7), repository, DI, migration v14 — all present. `VaccineSummary` lives in plan 2 alongside the summary use case that produces it (cohesion; the spec lists it under issue 1, but it has no data-layer consumer).
- Status is persisted as `VaccineStatus.name` (TEXT); `toVaccineStatusSafe()` reads it back, defaulting unknown values to `ADMINISTERED` so a corrupt row never invents a future reminder.
- Both `scheduledDate` and `administeredDate` are nullable in entity and domain; the use cases (plan 2) enforce the status↔date invariants, not Room.
- `getScheduledFutureAfter` takes epoch-ms `Long` (not `Instant`) to keep the DAO query simple and avoid a converter; the repository passes it through.
