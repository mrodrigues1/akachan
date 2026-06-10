# Bottle Feed Domain + Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-108

**Goal:** Add the persistence foundation for point-in-time bottle/formula feeds: domain model, Room entity/DAO, a new table via migration 7→8, and a repository that mirrors the milk-stash (`InventoryRepository`) shape.

**Architecture:** Bottle feeds are point-in-time events (single timestamp + volume + feed type), not timed sessions. They live in a new `bottle_feeds` Room table. Conversion is done with top-level extension functions (`toDomain`/`toEntity`) — no Mapper classes. The repository interface/impl mirror `InventoryRepository`/`InventoryRepositoryImpl` so later use cases (edit/delete) reuse the same `updateDetails(...)` / `delete(...)` contract.

**Tech Stack:** Kotlin, Room 2.8.4 (KSP), Hilt, Coroutines/Flow, JUnit 5 + MockK + Turbine (unit), AndroidJUnit4 + in-memory Room (instrumentation).

**Dependencies:** None. This is the foundation plan; 03, 04, 05, 06, 07 depend on it.

**Suggested implementation branch:** `feat/bottle-feed-data`

---

## File Structure

- Create `app/src/main/java/com/babytracker/domain/model/FeedType.kt` — enum `BREAST_MILK`, `FORMULA`.
- Create `app/src/main/java/com/babytracker/domain/model/BottleFeed.kt` — pure Kotlin data class.
- Create `app/src/main/java/com/babytracker/data/local/entity/BottleFeedEntity.kt` — Room entity + `toDomain`/`toEntity`.
- Create `app/src/main/java/com/babytracker/data/local/dao/BottleFeedDao.kt` — DAO.
- Create `app/src/main/java/com/babytracker/domain/repository/BottleFeedRepository.kt` — interface.
- Create `app/src/main/java/com/babytracker/data/repository/BottleFeedRepositoryImpl.kt` — impl.
- Modify `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt` — register entity, bump version to 8, add `MIGRATION_7_8`, add `bottleFeedDao()`.
- Modify `app/src/main/java/com/babytracker/di/DatabaseModule.kt` — provide `BottleFeedDao`, register `MIGRATION_7_8`.
- Modify `app/src/main/java/com/babytracker/di/RepositoryModule.kt` — bind `BottleFeedRepository`.
- Generated: `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/8.json` (Room emits on build).
- Test `app/src/test/java/com/babytracker/data/local/entity/BottleFeedEntityTest.kt`.
- Test `app/src/test/java/com/babytracker/data/repository/BottleFeedRepositoryImplTest.kt`.
- Test `app/src/androidTest/java/com/babytracker/data/local/dao/BottleFeedDaoTest.kt`.
- Test `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt` (add a 7→8 case to the existing file).

---

## Task 1: FeedType enum

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/FeedType.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.babytracker.domain.model

enum class FeedType {
    BREAST_MILK,
    FORMULA,
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/FeedType.kt
git commit -m "feat(breastfeeding): add FeedType enum for bottle feeds"
```

---

## Task 2: BottleFeed domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/BottleFeed.kt`

- [ ] **Step 1: Create the model**

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class BottleFeed(
    val id: Long = 0,
    val timestamp: Instant,
    val volumeMl: Int,
    val type: FeedType,
    val linkedMilkBagId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
)
```

Note: `volumeMl` is always stored in millilitres (the canonical unit). The ml/oz display preference (plan 02) is a presentation concern only — storage stays ml. `linkedMilkBagId` is non-null only for `BREAST_MILK` feeds linked to a stash bag; the bag-consume side effect lives in the use-case layer (plan 03), not here.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/BottleFeed.kt
git commit -m "feat(breastfeeding): add BottleFeed domain model"
```

---

## Task 3: BottleFeedEntity + conversion extensions (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/BottleFeedEntity.kt`
- Test: `app/src/test/java/com/babytracker/data/local/entity/BottleFeedEntityTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.data.local.entity

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class BottleFeedEntityTest {

    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val domain = BottleFeed(
            id = 7,
            timestamp = Instant.ofEpochMilli(1_000),
            volumeMl = 120,
            type = FeedType.FORMULA,
            linkedMilkBagId = 42,
            notes = "half feed",
            createdAt = Instant.ofEpochMilli(2_000),
        )

        val restored = domain.toEntity().toDomain()

        assertEquals(domain, restored)
    }

    @Test
    fun `toDomain maps null linkedMilkBagId and notes`() {
        val entity = BottleFeedEntity(
            id = 1,
            timestamp = 1_000,
            volumeMl = 90,
            type = "BREAST_MILK",
            linkedMilkBagId = null,
            notes = null,
            createdAt = 2_000,
        )

        val domain = entity.toDomain()

        assertEquals(FeedType.BREAST_MILK, domain.type)
        assertEquals(null, domain.linkedMilkBagId)
        assertEquals(null, domain.notes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.data.local.entity.BottleFeedEntityTest"`
Expected: FAIL — `BottleFeedEntity` unresolved.

- [ ] **Step 3: Write the entity + extensions**

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import java.time.Instant

@Entity(
    tableName = "bottle_feeds",
    foreignKeys = [
        ForeignKey(
            entity = MilkBagEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_milk_bag_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("timestamp"),
        Index("linked_milk_bag_id"),
    ],
)
data class BottleFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "linked_milk_bag_id") val linkedMilkBagId: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun BottleFeedEntity.toDomain(): BottleFeed = BottleFeed(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    volumeMl = volumeMl,
    type = FeedType.valueOf(type),
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun BottleFeed.toEntity(): BottleFeedEntity = BottleFeedEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.data.local.entity.BottleFeedEntityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/entity/BottleFeedEntity.kt app/src/test/java/com/babytracker/data/local/entity/BottleFeedEntityTest.kt
git commit -m "feat(db): add BottleFeedEntity with domain conversions"
```

---

## Task 4: BottleFeedDao

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/dao/BottleFeedDao.kt`

The DAO mirrors `MilkBagDao`: a Flow stream ordered newest-first for history, a one-shot read for backup export, an `updateDetails` partial update used by the edit use case, plus insert/delete.

- [ ] **Step 1: Create the DAO**

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.babytracker.data.local.entity.BottleFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BottleFeedDao {
    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BottleFeedEntity>>

    @Query("SELECT * FROM bottle_feeds WHERE timestamp >= :startMs ORDER BY timestamp DESC")
    fun getSince(startMs: Long): Flow<List<BottleFeedEntity>>

    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<BottleFeedEntity>

    @Insert
    suspend fun insert(entity: BottleFeedEntity): Long

    @Query(
        """
        UPDATE bottle_feeds
        SET timestamp = :timestamp,
            volume_ml = :volumeMl,
            type = :type,
            linked_milk_bag_id = :linkedMilkBagId,
            notes = :notes
        WHERE id = :id
        """
    )
    suspend fun updateDetails(
        id: Long,
        timestamp: Long,
        volumeMl: Int,
        type: String,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Int

    @Delete
    suspend fun delete(entity: BottleFeedEntity)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/dao/BottleFeedDao.kt
git commit -m "feat(db): add BottleFeedDao"
```

---

## Task 5: Register entity, bump DB version, add migration 7→8

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`

- [ ] **Step 1: Add the entity import and register it**

In the `import` block add:
```kotlin
import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.entity.BottleFeedEntity
```

In the `@Database(entities = [ ... ])` list add `BottleFeedEntity::class,` and change `version = 7` to `version = 8`.

Add the abstract accessor inside the class body:
```kotlin
abstract fun bottleFeedDao(): BottleFeedDao
```

- [ ] **Step 2: Add the migration** (place after `MIGRATION_6_7`)

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bottle_feeds (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                volume_ml INTEGER NOT NULL,
                type TEXT NOT NULL,
                linked_milk_bag_id INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(linked_milk_bag_id) REFERENCES milk_bags(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bottle_feeds_timestamp ON bottle_feeds(timestamp)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bottle_feeds_linked_milk_bag_id ON bottle_feeds(linked_milk_bag_id)"
        )
    }
}
```

> The column order, names, `NOT NULL` flags, FK action, and index names must exactly match what Room generates from `BottleFeedEntity`, otherwise the `MigrationTest` schema validation fails. If validation reports a mismatch, align the SQL to the generated `8.json`.

- [ ] **Step 3: Wire the migration into DI** — modify `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

Find where `MIGRATION_6_7` is passed to `.addMigrations(...)` and append `MIGRATION_7_8`. Add a provider for the DAO mirroring the existing `provideMilkBagDao`:

```kotlin
@Provides
fun provideBottleFeedDao(database: BabyTrackerDatabase): BottleFeedDao = database.bottleFeedDao()
```

(Add the `import com.babytracker.data.local.dao.BottleFeedDao` and `import com.babytracker.data.local.MIGRATION_7_8` as needed — match how the file imports the existing migrations/DAOs.)

- [ ] **Step 4: Build to generate schema 8.json**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/8.json` is created.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt app/src/main/java/com/babytracker/di/DatabaseModule.kt app/schemas/com.babytracker.data.local.BabyTrackerDatabase/8.json
git commit -m "feat(db): add bottle_feeds table and migration 7 to 8"
```

---

## Task 6: Migration 7→8 instrumentation test

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt`

- [ ] **Step 1: Add a migration test case** (follow the existing cases in the file for the helper/runner setup)

```kotlin
@Test
fun migrate7To8_createsBottleFeedsTable() {
    helper.createDatabase(TEST_DB, 7).close()

    val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bottle_feeds'").use { cursor ->
        assertTrue(cursor.moveToFirst())
    }
}
```

> `runMigrationsAndValidate(..., validateDroppedTables = true, MIGRATION_7_8)` cross-checks the live schema against `8.json`. Reuse the file's existing `helper`, `TEST_DB` constant, and imports; add `import com.babytracker.data.local.MIGRATION_7_8` if absent.

- [ ] **Step 2: Run the migration test (requires emulator/device)**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.MigrationTest"`
Expected: PASS. If no device is available, note it and rely on CI.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt
git commit -m "test(db): verify bottle_feeds migration 7 to 8"
```

---

## Task 7: BottleFeedRepository interface + impl (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/BottleFeedRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/BottleFeedRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/BottleFeedRepositoryImplTest.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BottleFeedRepository {
    fun getAll(): Flow<List<BottleFeed>>
    fun getSince(start: Instant): Flow<List<BottleFeed>>
    suspend fun insert(feed: BottleFeed): Long
    suspend fun updateDetails(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Boolean
    suspend fun delete(feed: BottleFeed)
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BottleFeedRepositoryImplTest {

    private val dao = mockk<BottleFeedDao>(relaxed = true)
    private val repository = BottleFeedRepositoryImpl(dao)

    @Test
    fun `getAll maps entities to domain`() = runTest {
        coEvery { dao.getAll() } returns flowOf(
            listOf(
                BottleFeedEntity(
                    id = 1, timestamp = 1_000, volumeMl = 100,
                    type = "FORMULA", linkedMilkBagId = null, notes = null, createdAt = 2_000,
                ),
            ),
        )

        val result = repository.getAll().first()

        assertEquals(1, result.size)
        assertEquals(FeedType.FORMULA, result.first().type)
    }

    @Test
    fun `updateDetails returns true when a row is updated`() = runTest {
        coEvery { dao.updateDetails(any(), any(), any(), any(), any(), any()) } returns 1

        val updated = repository.updateDetails(
            id = 5,
            timestamp = Instant.ofEpochMilli(3_000),
            volumeMl = 150,
            type = FeedType.BREAST_MILK,
            linkedMilkBagId = 9,
            notes = "note",
        )

        assertTrue(updated)
        coVerify { dao.updateDetails(5, 3_000, 150, "BREAST_MILK", 9, "note") }
    }

    @Test
    fun `insert delegates to dao and returns id`() = runTest {
        val feed = BottleFeed(
            timestamp = Instant.ofEpochMilli(1_000),
            volumeMl = 90,
            type = FeedType.BREAST_MILK,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        coEvery { dao.insert(any()) } returns 11

        assertEquals(11, repository.insert(feed))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.data.repository.BottleFeedRepositoryImplTest"`
Expected: FAIL — `BottleFeedRepositoryImpl` unresolved.

- [ ] **Step 4: Create the impl**

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BottleFeedRepositoryImpl @Inject constructor(
    private val dao: BottleFeedDao,
) : BottleFeedRepository {

    override fun getAll(): Flow<List<BottleFeed>> =
        dao.getAll().map { rows -> rows.map { it.toDomain() } }

    override fun getSince(start: Instant): Flow<List<BottleFeed>> =
        dao.getSince(start.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun insert(feed: BottleFeed): Long = dao.insert(feed.toEntity())

    override suspend fun updateDetails(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Boolean = dao.updateDetails(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        volumeMl = volumeMl,
        type = type.name,
        linkedMilkBagId = linkedMilkBagId,
        notes = notes,
    ) > 0

    override suspend fun delete(feed: BottleFeed) = dao.delete(feed.toEntity())
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.data.repository.BottleFeedRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 6: Bind in DI** — modify `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

Mirror the existing `InventoryRepository` binding:
```kotlin
@Binds
@Singleton
abstract fun bindBottleFeedRepository(impl: BottleFeedRepositoryImpl): BottleFeedRepository
```
Add the matching imports.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/BottleFeedRepository.kt app/src/main/java/com/babytracker/data/repository/BottleFeedRepositoryImpl.kt app/src/test/java/com/babytracker/data/repository/BottleFeedRepositoryImplTest.kt app/src/main/java/com/babytracker/di/RepositoryModule.kt
git commit -m "feat(repository): add BottleFeedRepository and binding"
```

---

## Task 8: BottleFeedDao instrumentation test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/data/local/dao/BottleFeedDaoTest.kt`

Mirror `MilkBagDaoTest`: in-memory DB, insert two feeds, assert `getAll` returns newest-first, `updateDetails` mutates, `delete` removes.

- [ ] **Step 1: Write the test**

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BottleFeedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottleFeedDaoTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: BottleFeedDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bottleFeedDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAll_returnsNewestFirst() = runTest {
        dao.insert(feed(timestamp = 1_000))
        dao.insert(feed(timestamp = 5_000))

        val all = dao.getAll().first()

        assertEquals(listOf(5_000L, 1_000L), all.map { it.timestamp })
    }

    @Test
    fun updateDetails_mutatesRow() = runTest {
        val id = dao.insert(feed(timestamp = 1_000, volumeMl = 100))

        val changed = dao.updateDetails(id, 2_000, 200, "FORMULA", null, "edited")

        assertEquals(1, changed)
        val row = dao.getAll().first().first()
        assertEquals(200, row.volumeMl)
        assertEquals("FORMULA", row.type)
        assertEquals("edited", row.notes)
    }

    private fun feed(
        timestamp: Long,
        volumeMl: Int = 90,
    ) = BottleFeedEntity(
        timestamp = timestamp,
        volumeMl = volumeMl,
        type = "BREAST_MILK",
        createdAt = timestamp,
    )
}
```

- [ ] **Step 2: Run (requires device/emulator)**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.dao.BottleFeedDaoTest"`
Expected: PASS (or defer to CI if no device).

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/data/local/dao/BottleFeedDaoTest.kt
git commit -m "test(db): add BottleFeedDao instrumentation test"
```

---

## Acceptance Criteria

- `bottle_feeds` table exists at DB version 8 with FK to `milk_bags(id)` (`ON DELETE SET NULL`) and indices on `timestamp` and `linked_milk_bag_id`.
- `MIGRATION_7_8` registered in `DatabaseModule` and validated against generated `8.json`.
- `BottleFeed`/`FeedType` are pure Kotlin (no framework imports); entity conversion via `toDomain`/`toEntity` extensions only — no Mapper class.
- `BottleFeedRepository` exposes `getAll`, `getSince`, `insert`, `updateDetails`, `delete`; impl is `@Singleton` and bound in `RepositoryModule`.
- `./gradlew test` passes (unit). DAO + migration instrumentation tests pass on device/CI.
- No changes to use-case, UI, sharing, or backup layers (those are plans 03–07).

## Self-Review Notes

- Storage unit is always ml; oz is presentation-only (plan 02) — no schema impact.
- Repo contract intentionally matches `InventoryRepository.updateDetails/delete` so plan 03 edit/delete use cases mirror the stash use cases exactly.
- Bag-consume side effect is deliberately excluded here; it belongs in `LogBottleFeedUseCase` (plan 03).
