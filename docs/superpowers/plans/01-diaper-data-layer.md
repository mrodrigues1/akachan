# Diaper Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-159

**Goal:** Add the persistence foundation for diaper tracking — domain model, type enum, Room entity + DAO, repository, DI wiring, and a database migration to v13.

**Architecture:** A diaper change is a point-in-time event (no duration), so this layer mirrors the simpler `BottleFeed` data layer rather than the start/stop `Sleep` layer. Pure-Kotlin domain (`DiaperChange`, `DiaperType`), a Room entity with extension-function mapping (no Mapper class), a Flow-returning repository, and an additive `CREATE TABLE` migration with no active-row trigger.

**Tech Stack:** Kotlin 2.3.20, Room 2.8.4, Hilt 2.59, Coroutines/Flow, JUnit 5 + MockK + Turbine (unit), Robolectric/Room in-memory + `MigrationTestHelper` (DB tests).

**Dependencies:** None. This is the base plan; plans 2–6 depend on it.

**Suggested implementation branch:** `feat/diaper-data-layer`

**Project convention:** Implement first, then tests (no strict TDD — per `CLAUDE.md`). Commit after each task. Do not run `ktlintFormat`/`detekt` manually — the pre-commit hook runs them.

---

### Task 1: `DiaperType` enum + parsers

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/DiaperType.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/DiaperTypeTest.kt`

- [ ] **Step 1: Create the enum** (mirrors `SleepType.kt`)

```kotlin
package com.babytracker.domain.model

enum class DiaperType(val label: String, val emoji: String) {
    WET("Wet", "💧"),
    DIRTY("Dirty", "💩"),
    BOTH("Both", "🌀"),
}

fun String.toDiaperTypeOrNull(): DiaperType? = when (this) {
    DiaperType.WET.name, DiaperType.WET.label -> DiaperType.WET
    DiaperType.DIRTY.name, DiaperType.DIRTY.label -> DiaperType.DIRTY
    DiaperType.BOTH.name, DiaperType.BOTH.label -> DiaperType.BOTH
    else -> null
}

fun String.toDiaperTypeSafe(): DiaperType = toDiaperTypeOrNull() ?: DiaperType.WET
```

- [ ] **Step 2: Write the test**

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiaperTypeTest {
    @Test
    fun `parses enum names`() {
        assertEquals(DiaperType.WET, "WET".toDiaperTypeOrNull())
        assertEquals(DiaperType.DIRTY, "DIRTY".toDiaperTypeOrNull())
        assertEquals(DiaperType.BOTH, "BOTH".toDiaperTypeOrNull())
    }

    @Test
    fun `parses human labels`() {
        assertEquals(DiaperType.WET, "Wet".toDiaperTypeOrNull())
        assertEquals(DiaperType.BOTH, "Both".toDiaperTypeOrNull())
    }

    @Test
    fun `returns null for unknown`() {
        assertNull("nope".toDiaperTypeOrNull())
    }

    @Test
    fun `safe defaults to WET`() {
        assertEquals(DiaperType.WET, "garbage".toDiaperTypeSafe())
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.model.DiaperTypeTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DiaperType enum and parsers`

---

### Task 2: `DiaperChange` domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/DiaperChange.kt`

- [ ] **Step 1: Create the model** (pure Kotlin, zero framework imports)

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class DiaperChange(
    val id: Long = 0,
    val timestamp: Instant,
    val type: DiaperType,
    val notes: String? = null,
    val createdAt: Instant,
)
```

- [ ] **Step 2: Commit** `feat(diaper): add DiaperChange domain model`

---

### Task 3: `DiaperEntity` + mapping

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/DiaperEntity.kt`
- Test: `app/src/test/java/com/babytracker/data/local/entity/DiaperEntityMappingTest.kt`

- [ ] **Step 1: Create entity + mapping** (mirrors `SleepEntity.kt`). The `indices` declaration makes Room's generated schema match the migration's index (named `index_diaper_changes_timestamp`).

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.toDiaperTypeSafe
import java.time.Instant

@Entity(
    tableName = "diaper_changes",
    indices = [Index(value = ["timestamp"])],
)
data class DiaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun DiaperEntity.toDomain(): DiaperChange = DiaperChange(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    type = type.toDiaperTypeSafe(),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun DiaperChange.toEntity(): DiaperEntity = DiaperEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    type = type.name,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
```

- [ ] **Step 2: Write round-trip test**

```kotlin
package com.babytracker.data.local.entity

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperEntityMappingTest {
    @Test
    fun `domain to entity to domain round trips`() {
        val change = DiaperChange(
            id = 7,
            timestamp = Instant.ofEpochMilli(1_000),
            type = DiaperType.BOTH,
            notes = "blowout",
            createdAt = Instant.ofEpochMilli(2_000),
        )
        assertEquals(change, change.toEntity().toDomain())
    }

    @Test
    fun `unknown stored type maps to WET`() {
        val entity = DiaperEntity(id = 1, timestamp = 0, type = "???", notes = null, createdAt = 0)
        assertEquals(DiaperType.WET, entity.toDomain().type)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.data.local.entity.DiaperEntityMappingTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DiaperEntity and mapping`

---

### Task 4: `DiaperDao`

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/dao/DiaperDao.kt`

- [ ] **Step 1: Create the DAO** (`getAllOnce` is the one-shot read consumed by plan 6's backup source)

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.DiaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diaper_changes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DiaperEntity>>

    @Query("SELECT * FROM diaper_changes ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<DiaperEntity?>

    @Query(
        "SELECT * FROM diaper_changes WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC",
    )
    suspend fun getBetween(startMs: Long, endMs: Long): List<DiaperEntity>

    @Query("SELECT * FROM diaper_changes ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<DiaperEntity>

    @Query("SELECT * FROM diaper_changes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaperEntity?

    @Insert
    suspend fun insert(entity: DiaperEntity): Long

    @Update
    suspend fun update(entity: DiaperEntity)

    @Query("DELETE FROM diaper_changes WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
```

- [ ] **Step 2: Commit** `feat(diaper): add DiaperDao`

---

### Task 5: `DiaperRepository` interface + impl

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/DiaperRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/DiaperRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/DiaperRepositoryImplTest.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.DiaperChange
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface DiaperRepository {
    fun observeAll(): Flow<List<DiaperChange>>
    fun observeLatest(): Flow<DiaperChange?>
    suspend fun getBetween(start: Instant, end: Instant): List<DiaperChange>
    suspend fun getById(id: Long): DiaperChange?
    suspend fun insert(change: DiaperChange): Long
    suspend fun update(change: DiaperChange)
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Impl** (mirrors `BottleFeedRepositoryImpl.kt`)

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaperRepositoryImpl @Inject constructor(
    private val dao: DiaperDao,
) : DiaperRepository {
    override fun observeAll(): Flow<List<DiaperChange>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeLatest(): Flow<DiaperChange?> =
        dao.observeLatest().map { it?.toDomain() }

    override suspend fun getBetween(start: Instant, end: Instant): List<DiaperChange> =
        dao.getBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }

    override suspend fun getById(id: Long): DiaperChange? = dao.getById(id)?.toDomain()

    override suspend fun insert(change: DiaperChange): Long = dao.insert(change.toEntity())

    override suspend fun update(change: DiaperChange) = dao.update(change.toEntity())

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
```

- [ ] **Step 3: Test** (MockK over the DAO; Turbine for the Flow)

```kotlin
package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.entity.DiaperEntity
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
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

class DiaperRepositoryImplTest {
    private lateinit var dao: DiaperDao
    private lateinit var repository: DiaperRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = DiaperRepositoryImpl(dao)
    }

    @Test
    fun `observeAll maps entities to domain`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(DiaperEntity(id = 1, timestamp = 10, type = "WET", notes = null, createdAt = 5)),
        )
        repository.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(DiaperType.WET, list.first().type)
            awaitComplete()
        }
    }

    @Test
    fun `insert converts to entity`() = runTest {
        val captured = slot<DiaperEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42
        val id = repository.insert(
            DiaperChange(
                timestamp = Instant.ofEpochMilli(10),
                type = DiaperType.DIRTY,
                createdAt = Instant.ofEpochMilli(5),
            ),
        )
        assertEquals(42, id)
        assertEquals("DIRTY", captured.captured.type)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        repository.deleteById(9)
        coVerify { dao.deleteById(9) }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.data.repository.DiaperRepositoryImplTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(diaper): add DiaperRepository and impl`

---

### Task 6: Database migration to v13 + DAO accessor

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`

- [ ] **Step 1: Register the entity, bump version, add the DAO accessor.** In the `@Database` annotation add `DiaperEntity::class` to `entities`, change `version = 12` to `version = 13`, and add to the abstract class:

```kotlin
abstract fun diaperDao(): DiaperDao
```

Add the import `import com.babytracker.data.local.entity.DiaperEntity` and `import com.babytracker.data.local.dao.DiaperDao`.

- [ ] **Step 2: Add the migration** (place after `MIGRATION_11_12`; additive `CREATE TABLE`, no active-row trigger because a diaper change has no in-progress state)

```kotlin
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS diaper_changes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                type TEXT NOT NULL,
                notes TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_diaper_changes_timestamp ON diaper_changes(timestamp)",
        )
    }
}
```

- [ ] **Step 3: Regenerate + commit the exported schema.** Run `./gradlew :app:compileDebugKotlin` (or `build`) so Room writes `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/13.json`. Commit this generated file alongside the migration — `MigrationTestHelper` and CI read it. Missing it fails the build.
- [ ] **Step 4: Commit** `feat(diaper): add diaper_changes table migration v13`

---

### Task 7: Hilt wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 1:** In `DatabaseModule`, add `MIGRATION_12_13` to the `.addMigrations(...)` list (with its import), and add the DAO provider:

```kotlin
@Provides
fun provideDiaperDao(database: BabyTrackerDatabase): DiaperDao = database.diaperDao()
```

(add `import com.babytracker.data.local.dao.DiaperDao` and `import com.babytracker.data.local.MIGRATION_12_13`)

- [ ] **Step 2:** In `RepositoryModule`, add the binding:

```kotlin
@Binds
@Singleton
abstract fun bindDiaperRepository(impl: DiaperRepositoryImpl): DiaperRepository
```

(add imports for `DiaperRepositoryImpl` and `DiaperRepository`)

- [ ] **Step 3: Commit** `feat(diaper): wire diaper DAO and repository into Hilt`

---

### Task 8: DAO + migration instrumentation tests

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/data/local/dao/DiaperDaoTest.kt`
- Create: `app/src/androidTest/java/com/babytracker/data/local/DiaperMigrationTest.kt`

Follow the existing DAO/migration test patterns in `app/src/androidTest/java/com/babytracker/data/local/` (Room `inMemoryDatabaseBuilder` for DAO tests; `MigrationTestHelper` with the exported schema under `app/schemas/` for the migration test).

- [ ] **Step 1: DAO test** — insert three rows, assert `observeAll()` returns them newest-first, `observeLatest()` returns the newest, `getBetween` filters by range, `update` mutates type, `deleteById` removes and returns 1.

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.DiaperEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaperDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: DiaperDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.diaperDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertsAndObservesNewestFirst() = runTest {
        dao.insert(DiaperEntity(timestamp = 100, type = "WET", createdAt = 100))
        dao.insert(DiaperEntity(timestamp = 300, type = "DIRTY", createdAt = 300))
        dao.insert(DiaperEntity(timestamp = 200, type = "BOTH", createdAt = 200))

        val all = dao.observeAll().first()
        assertEquals(listOf(300L, 200L, 100L), all.map { it.timestamp })
        assertEquals(300L, dao.observeLatest().first()!!.timestamp)
        assertEquals(1, dao.getBetween(150, 250).size)
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = dao.insert(DiaperEntity(timestamp = 100, type = "WET", createdAt = 100))
        dao.update(DiaperEntity(id = id, timestamp = 100, type = "BOTH", createdAt = 100))
        assertEquals("BOTH", dao.getById(id)!!.type)
        assertEquals(1, dao.deleteById(id))
        assertEquals(0, dao.observeAll().first().size)
    }
}
```

- [ ] **Step 2: Migration test** — build the DB at v12 with `MigrationTestHelper`, run `MIGRATION_12_13`, assert the `diaper_changes` table is queryable. Mirror the closest existing migration test (e.g. the one covering `MIGRATION_11_12`).
- [ ] **Step 3: Run** the instrumentation tests on an emulator: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.dao.DiaperDaoTest"` (and the migration test). Expect PASS. If no emulator is available, note it and rely on `./gradlew test` for the unit layer.
- [ ] **Step 4: Commit** `test(diaper): add DiaperDao and migration tests`

---

## Acceptance Criteria

- `./gradlew build` succeeds (Room schema for v13 is exported under `app/schemas/`).
- `./gradlew test` passes, including the new unit tests.
- DAO + migration instrumentation tests pass on an emulator.
- `diaper_changes` table exists at schema v13 with a `timestamp` index; fresh installs and v12→v13 upgrades both produce the table.
- No active-row trigger is added (diaper changes have no in-progress state).
- `DiaperRepository` is injectable wherever a constructor requests it.

## Self-Review Notes

- Spec coverage: domain model, type enum, entity+mapping, DAO (incl. `getAllOnce` for plan 6), repository, DI, migration v13 — all present.
- Type consistency: `DiaperType.name` is persisted as the `type` column; `toDiaperTypeSafe()` reads it back. `DiaperChange.createdAt` is required (no default) so callers must set it — use cases in plan 2 stamp it.
