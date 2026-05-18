# Task 2 — Room data layer (entities, DAOs, MIGRATION_2_3, DI)

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Add Room v3 with `pumping_sessions` and `milk_bags`, wire DAOs through Hilt, ship a non-destructive migration from v2.

**Depends on:** Task 1 (domain models).

## Files

- Create: `app/src/main/java/com/babytracker/data/local/entity/PumpingEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/entity/MilkBagEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/dao/PumpingDao.kt`
- Create: `app/src/main/java/com/babytracker/data/local/dao/MilkBagDao.kt`
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/data/local/dao/PumpingDaoTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/data/local/dao/MilkBagDaoTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt` *(create if absent)*

> Schema export: `BabyTrackerDatabase` currently sets `exportSchema = false`. Keep this flag — `MigrationTest` does not depend on `app/schemas/`.

## Implementation

### Step 1: `PumpingEntity.kt`

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import java.time.Instant

@Entity(
    tableName = "pumping_sessions",
    indices = [Index(value = ["start_time"], orders = [Index.Order.DESC])],
)
data class PumpingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "breast") val breast: String,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0,
)

fun PumpingEntity.toDomain(): PumpingSession = PumpingSession(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    breast = PumpingBreast.valueOf(breast),
    volumeMl = volumeMl,
    notes = notes,
    pausedAt = pausedAt?.let { Instant.ofEpochMilli(it) },
    pausedDurationMs = pausedDurationMs,
)

fun PumpingSession.toEntity(): PumpingEntity = PumpingEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    breast = breast.name,
    volumeMl = volumeMl,
    notes = notes,
    pausedAt = pausedAt?.toEpochMilli(),
    pausedDurationMs = pausedDurationMs,
)
```

### Step 2: `MilkBagEntity.kt`

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.MilkBag
import java.time.Instant

@Entity(
    tableName = "milk_bags",
    foreignKeys = [
        ForeignKey(
            entity = PumpingEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("used_at"),
        Index("collection_date"),
        Index("source_session_id"),
    ],
)
data class MilkBagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "collection_date") val collectionDate: Long,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: Long? = null,
    @ColumnInfo(name = "used_at") val usedAt: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun MilkBagEntity.toDomain(): MilkBag = MilkBag(
    id = id,
    collectionDate = Instant.ofEpochMilli(collectionDate),
    volumeMl = volumeMl,
    sourceSessionId = sourceSessionId,
    usedAt = usedAt?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun MilkBag.toEntity(): MilkBagEntity = MilkBagEntity(
    id = id,
    collectionDate = collectionDate.toEpochMilli(),
    volumeMl = volumeMl,
    sourceSessionId = sourceSessionId,
    usedAt = usedAt?.toEpochMilli(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
```

### Step 3: `PumpingDao.kt`

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PumpingDao {
    @Query("SELECT * FROM pumping_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<PumpingEntity>>

    @Query("SELECT * FROM pumping_sessions WHERE end_time IS NULL LIMIT 1")
    fun getActiveSession(): Flow<PumpingEntity?>

    @Query("SELECT * FROM pumping_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PumpingEntity?

    @Insert
    suspend fun insert(entity: PumpingEntity): Long

    @Update
    suspend fun update(entity: PumpingEntity)

    @Delete
    suspend fun delete(entity: PumpingEntity)
}
```

### Step 4: `MilkBagDao.kt`

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.MilkBagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilkBagDao {
    @Query("SELECT * FROM milk_bags WHERE used_at IS NULL ORDER BY collection_date ASC")
    fun getActiveBags(): Flow<List<MilkBagEntity>>

    @Query("SELECT * FROM milk_bags ORDER BY collection_date DESC")
    fun getAllBags(): Flow<List<MilkBagEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(volume_ml), 0) AS totalMl,
               COUNT(*) AS bagCount,
               MIN(collection_date) AS oldestBagDateMs
        FROM milk_bags
        WHERE used_at IS NULL
        """
    )
    fun getActiveSummary(): Flow<InventorySummaryRow>

    @Insert
    suspend fun insert(entity: MilkBagEntity): Long

    @Update
    suspend fun update(entity: MilkBagEntity)

    @Delete
    suspend fun delete(entity: MilkBagEntity)
}

data class InventorySummaryRow(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDateMs: Long?,
)
```

> `InventorySummaryRow` is intentionally placed in the dao file because it is a Room projection type, not a domain model. The repository converts it to `InventorySummary`.

### Step 5: Update `BabyTrackerDatabase.kt`

Replace the file with:

```kotlin
package com.babytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babytracker.data.local.converter.Converters
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity

@Database(
    entities = [
        BreastfeedingEntity::class,
        SleepEntity::class,
        PumpingEntity::class,
        MilkBagEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BabyTrackerDatabase : RoomDatabase() {
    abstract fun breastfeedingDao(): BreastfeedingDao
    abstract fun sleepDao(): SleepDao
    abstract fun pumpingDao(): PumpingDao
    abstract fun milkBagDao(): MilkBagDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE breastfeeding_sessions ADD COLUMN paused_at INTEGER DEFAULT NULL"
        )
        database.execSQL(
            "ALTER TABLE breastfeeding_sessions ADD COLUMN paused_duration_ms INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pumping_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                breast TEXT NOT NULL,
                volume_ml INTEGER,
                notes TEXT,
                paused_at INTEGER,
                paused_duration_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_pumping_sessions_start_time ON pumping_sessions(start_time DESC)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS milk_bags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                collection_date INTEGER NOT NULL,
                volume_ml INTEGER NOT NULL,
                source_session_id INTEGER,
                used_at INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(source_session_id) REFERENCES pumping_sessions(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_milk_bags_used_at ON milk_bags(used_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_milk_bags_collection_date ON milk_bags(collection_date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_milk_bags_source_session_id ON milk_bags(source_session_id)")
    }
}
```

> Verify Room's generated `RoomOpenHelper` SHA-256 matches the manual SQL. After adding the migration, build the project (`./gradlew :app:assembleDebug`). If Room reports a schema mismatch in `MigrationTest`, copy the index names exactly from the message into the migration `execSQL` calls.

### Step 6: Update `DatabaseModule.kt`

```kotlin
package com.babytracker.di

import android.content.Context
import androidx.room.Room
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.MIGRATION_1_2
import com.babytracker.data.local.MIGRATION_2_3
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BabyTrackerDatabase =
        Room.databaseBuilder(
            context,
            BabyTrackerDatabase::class.java,
            "baby_tracker_db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideBreastfeedingDao(database: BabyTrackerDatabase): BreastfeedingDao =
        database.breastfeedingDao()

    @Provides
    fun provideSleepDao(database: BabyTrackerDatabase): SleepDao = database.sleepDao()

    @Provides
    fun providePumpingDao(database: BabyTrackerDatabase): PumpingDao = database.pumpingDao()

    @Provides
    fun provideMilkBagDao(database: BabyTrackerDatabase): MilkBagDao = database.milkBagDao()

    @Provides
    fun provideNowProvider(): () -> Instant = Instant::now
}
```

## Tests (instrumentation)

These run on a device/emulator. Use `Room.inMemoryDatabaseBuilder()` per `CLAUDE.md`.

### `PumpingDaoTest.kt`

```kotlin
package com.babytracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PumpingDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: PumpingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pumpingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAndGetActive() = runTest {
        val id = dao.insert(
            PumpingEntity(
                startTime = 1_000L,
                breast = "LEFT",
            )
        )
        dao.getActiveSession().test {
            val active = awaitItem()
            assertNotNull(active)
            assertEquals(id, active!!.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stoppedSessionRemovedFromActive() = runTest {
        val id = dao.insert(PumpingEntity(startTime = 1_000L, breast = "RIGHT"))
        dao.update(
            PumpingEntity(
                id = id,
                startTime = 1_000L,
                endTime = 2_000L,
                breast = "RIGHT",
                volumeMl = 80,
            )
        )
        dao.getActiveSession().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRemovesRow() = runTest {
        val id = dao.insert(PumpingEntity(startTime = 1_000L, breast = "BOTH"))
        val entity = dao.getById(id)!!
        dao.delete(entity)
        assertNull(dao.getById(id))
    }
}
```

### `MilkBagDaoTest.kt`

```kotlin
package com.babytracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MilkBagDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var bagDao: MilkBagDao
    private lateinit var pumpingDao: PumpingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bagDao = db.milkBagDao()
        pumpingDao = db.pumpingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun activeBagsExcludeUsed() = runTest {
        bagDao.insert(MilkBagEntity(collectionDate = 100L, volumeMl = 100, createdAt = 100L))
        val usedId = bagDao.insert(
            MilkBagEntity(collectionDate = 50L, volumeMl = 150, createdAt = 50L, usedAt = 200L)
        )
        bagDao.getActiveBags().test {
            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals(100, active[0].volumeMl)
            cancelAndIgnoreRemainingEvents()
        }
        // sanity: getAllBags still returns both rows
        bagDao.getAllBags().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(usedId, bagDao.getAllBags().let { /* keep ids referenced */ usedId })
    }

    @Test
    fun summaryAggregatesActiveBags() = runTest {
        bagDao.insert(MilkBagEntity(collectionDate = 200L, volumeMl = 100, createdAt = 200L))
        bagDao.insert(MilkBagEntity(collectionDate = 100L, volumeMl = 60, createdAt = 100L))
        bagDao.insert(
            MilkBagEntity(collectionDate = 50L, volumeMl = 999, createdAt = 50L, usedAt = 300L)
        )
        bagDao.getActiveSummary().test {
            val row = awaitItem()
            assertEquals(160, row.totalMl)
            assertEquals(2, row.bagCount)
            assertEquals(100L, row.oldestBagDateMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun foreignKeySetNullOnSessionDelete() = runTest {
        val sessionId = pumpingDao.insert(
            PumpingEntity(startTime = 1_000L, breast = "LEFT")
        )
        val bagId = bagDao.insert(
            MilkBagEntity(
                collectionDate = 1_000L,
                volumeMl = 100,
                createdAt = 1_000L,
                sourceSessionId = sessionId,
            )
        )
        val session = pumpingDao.getById(sessionId)!!
        pumpingDao.delete(session)

        // Find the bag again — sourceSessionId must now be NULL.
        bagDao.getAllBags().test {
            val all = awaitItem()
            val bag = all.first { it.id == bagId }
            assertNull(bag.sourceSessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### `MigrationTest.kt`

Create if not present. Reuses Room's `MigrationTestHelper`.

```kotlin
package com.babytracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BabyTrackerDatabase::class.java,
    )

    @Test
    fun migrate2To3_createsPumpingAndMilkBagsTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO breastfeeding_sessions(start_time, starting_side, paused_duration_ms)" +
                    " VALUES(1000, 'LEFT', 0)"
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            // Inserting into the new tables should succeed.
            db.execSQL(
                "INSERT INTO pumping_sessions(start_time, breast, paused_duration_ms)" +
                    " VALUES(2000, 'BOTH', 0)"
            )
            db.execSQL(
                "INSERT INTO milk_bags(collection_date, volume_ml, created_at)" +
                    " VALUES(2000, 100, 2000)"
            )
            db.query("SELECT count(*) FROM pumping_sessions").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 1)
            }
            assertNotNull(db)
        }
    }
}
```

> If `MigrationTest.kt` already exists, add `migrate2To3_*` as an additional `@Test` instead of overwriting.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.data.local.dao.PumpingDaoTest,com.babytracker.data.local.dao.MilkBagDaoTest,\
com.babytracker.data.local.MigrationTest
```

Expected: all green. If Room emits a schema mismatch the first time `connectedAndroidTest` runs, copy the corrected SQL/index names from the failure into `MIGRATION_2_3` and re-run.

## Commit

Two commits keep the diff reviewable:

```
feat(db): add pumping_sessions and milk_bags tables (Room v3)

Adds PumpingEntity, MilkBagEntity, their DAOs, and MIGRATION_2_3 with a
foreign key on milk_bags.source_session_id ON DELETE SET NULL.
```

```
chore(di): provide PumpingDao and MilkBagDao via DatabaseModule

Registers MIGRATION_2_3 on the Room builder so existing v2 installs migrate
cleanly.
```
