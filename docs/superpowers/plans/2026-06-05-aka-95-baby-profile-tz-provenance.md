# AKA-95: BabyProfileEntity + DataStore→Room Bootstrap + TZ Provenance + Stable-Enum Hardening

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE: AKA-95**

**Goal:** Add the singleton `babies` Room table, a bootstrap use case that copies DataStore profile data into it on startup, per-record timezone provenance on `sleep_records`, safe SleepType enum parsing, and lift the `MEDIUM` confidence cap in the predictor for records with qualified timezone provenance.

**Architecture:** Schema-only `MIGRATION_4_5` creates `babies` and adds `timezone_id TEXT` to `sleep_records`; zero DataStore reads in the migration. A fire-and-forget `BootstrapBabyProfileUseCase` copies DataStore profile to the singleton Room row on startup. New sleep records capture `ZoneId.systemDefault().id`; qualified provenance is computed from the fraction of recent intervals with non-null `timezoneId`, enabling `Confidence.HIGH` in the predictor.

**Tech Stack:** Room 2.8.4, DataStore 1.1.1, Hilt 2.59, JUnit 5, MockK, Turbine, Kotlin `java.time`.

**Branch:** `feat/aka-95-baby-profile-entity`

---

## File Map

### New files
| File | Responsibility |
|------|---------------|
| `data/local/entity/BabyProfileEntity.kt` | Room entity for `babies` singleton table + `toDomain`/`toEntity` |
| `data/local/dao/BabyProfileDao.kt` | `getProfile()` + `upsertProfile()` |
| `domain/model/BabyProfile.kt` | Framework-free domain model for baby profile data |
| `domain/repository/BabyProfileRepository.kt` | Repository interface |
| `data/repository/BabyProfileRepositoryImpl.kt` | Repository impl wrapping `BabyProfileDao` |
| `domain/usecase/baby/BootstrapBabyProfileUseCase.kt` | Copies DataStore→Room; idempotent |
| `src/test/.../BootstrapBabyProfileUseCaseTest.kt` | Unit tests for bootstrap |

### Modified files
| File | Change |
|------|--------|
| `domain/model/SleepRecord.kt` | + `timezoneId: String? = null` |
| `data/local/entity/SleepEntity.kt` | + `timezone_id` column, `String.toSleepTypeSafe()`, update conversions |
| `domain/sleep/feature/SleepInterval.kt` | + `timezoneId: String? = null`, update `from(record)` |
| `domain/usecase/sleep/StartSleepRecordUseCase.kt` | Capture `ZoneId.systemDefault().id` |
| `data/local/BabyTrackerDatabase.kt` | version 5, add `BabyProfileEntity`, `MIGRATION_4_5` |
| `di/DatabaseModule.kt` | + `MIGRATION_4_5`, `provideBabyProfileDao()` |
| `di/RepositoryModule.kt` | + `bindBabyProfileRepository()` |
| `domain/sleep/feature/EvidenceQuality.kt` | + `hasQualifiedTimezoneProvenance: Boolean = false` |
| `domain/sleep/feature/SleepFeatureExtractor.kt` | Compute provenance in `computeQuality()` |
| `domain/model/SleepPredictionTuning.kt` | + `HIGH_CONFIDENCE_QUALITY_C_THRESHOLD`, `MIN_QUALIFIED_TZ_PROVENANCE_RATE`, update `ALGORITHM_VERSION` |
| `domain/sleep/eval/SleepWindowPredictor.kt` | Lift `MEDIUM` cap — allow `HIGH` with qualified provenance |
| `BabyTrackerApp.kt` | Inject + launch `BootstrapBabyProfileUseCase` |
| `androidTest/.../MigrationTest.kt` | + v4→v5 migration tests |

---

## Task 1: `timezoneId` on SleepRecord, SleepEntity, SleepInterval; safe SleepType parse; capture zone on start

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepRecord.kt`
- Modify: `app/src/main/java/com/babytracker/data/local/entity/SleepEntity.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt`
- Modify: `app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt`

- [ ] **Step 1.1: Add `timezoneId` to `SleepRecord`**

```kotlin
// domain/model/SleepRecord.kt
data class SleepRecord(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val sleepType: SleepType,
    val notes: String? = null,
    val timezoneId: String? = null,
) {
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null
}
```

Default `null` so every existing construction site still compiles without changes.

- [ ] **Step 1.2: Add safe SleepType parse + `timezone_id` column + update conversions in `SleepEntity.kt`**

```kotlin
// data/local/entity/SleepEntity.kt
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Instant

@Entity(tableName = "sleep_records")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String? = null,
)

fun String.toSleepTypeSafe(): SleepType = when (this) {
    "NAP", "Nap" -> SleepType.NAP
    "NIGHT_SLEEP", "Night Sleep" -> SleepType.NIGHT_SLEEP
    else -> SleepType.NAP
}

fun SleepEntity.toDomain(): SleepRecord = SleepRecord(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    sleepType = sleepType.toSleepTypeSafe(),
    notes = notes,
    timezoneId = timezoneId,
)

fun SleepRecord.toEntity(): SleepEntity = SleepEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType.name,
    notes = notes,
    timezoneId = timezoneId,
)
```

- [ ] **Step 1.3: Add `timezoneId` to `SleepInterval`**

```kotlin
// domain/sleep/feature/SleepInterval.kt
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Duration

data class SleepInterval(
    val startMillis: Long,
    val endMillis: Long?,
    val sleepType: SleepType,
    val timezoneId: String? = null,
) {
    val isCompleted: Boolean
        get() = endMillis != null

    val durationMillis: Long?
        get() = endMillis?.let { it - startMillis }

    companion object {
        private val maxNapMillis = Duration.ofHours(SleepPredictionTuning.MAX_NAP_DURATION_HOURS).toMillis()
        private val maxNightSleepMillis =
            Duration.ofHours(SleepPredictionTuning.MAX_NIGHT_SLEEP_DURATION_HOURS).toMillis()

        fun from(record: SleepRecord): SleepInterval? =
            from(record.startTime.toEpochMilli(), record.endTime?.toEpochMilli(), record.sleepType, record.timezoneId)

        fun from(
            startMillis: Long,
            endMillis: Long?,
            sleepType: SleepType,
            timezoneId: String? = null,
        ): SleepInterval? {
            if (endMillis != null) {
                if (endMillis <= startMillis) return null
                if (sleepType == SleepType.NAP && endMillis - startMillis > maxNapMillis) return null
                if (sleepType == SleepType.NIGHT_SLEEP && endMillis - startMillis > maxNightSleepMillis) return null
            }
            return SleepInterval(startMillis, endMillis, sleepType, timezoneId)
        }
    }
}
```

Default `null` on both the data class and `from()` — all existing call sites compile unchanged.

- [ ] **Step 1.4: Capture device zone in `StartSleepRecordUseCase`**

```kotlin
// domain/usecase/sleep/StartSleepRecordUseCase.kt
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class StartSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sleepType: SleepType): SleepRecord {
        val record = SleepRecord(
            startTime = Instant.now(),
            sleepType = sleepType,
            timezoneId = ZoneId.systemDefault().id,
        )
        val id = repository.insertRecord(record)
        return record.copy(id = id)
    }
}
```

- [ ] **Step 1.5: Run unit tests**

```bash
./gradlew test --tests "*.SleepIntervalTest" --tests "*.SleepFeatureExtractorTest" --tests "*.SleepWindowPredictorTest"
```

Expected: all pass (default `null` added; no existing call site breaks).

- [ ] **Step 1.6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/SleepRecord.kt \
        app/src/main/java/com/babytracker/data/local/entity/SleepEntity.kt \
        app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt \
        app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt
git commit -m "feat(db): add timezone_id to SleepRecord/SleepEntity/SleepInterval; safe SleepType parse [AKA-95]"
```

---

## Task 2: BabyProfileEntity + BabyProfileDao

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/BabyProfileEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/dao/BabyProfileDao.kt`

- [ ] **Step 2.1: Create `BabyProfileEntity.kt`**

```kotlin
// data/local/entity/BabyProfileEntity.kt
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BabyProfile
import java.time.LocalDate

const val SINGLETON_ID = 1L

@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long? = null,      // epoch ms; null = DataStore not yet read
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,                // epoch ms; null = not set
    @ColumnInfo(name = "due_date_user_provided") val dueDateUserProvided: Boolean = false,
    @ColumnInfo(name = "home_timezone_id") val homeTimezoneId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun BabyProfileEntity.toDomain(): BabyProfile = BabyProfile(
    dateOfBirth = dateOfBirth?.let { LocalDate.ofEpochDay(it / MILLIS_PER_DAY) },
    dueDate = dueDate?.let { LocalDate.ofEpochDay(it / MILLIS_PER_DAY) },
    dueDateUserProvided = dueDateUserProvided,
    homeTimezoneId = homeTimezoneId,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

fun BabyProfile.toEntity(): BabyProfileEntity = BabyProfileEntity(
    id = SINGLETON_ID,
    dateOfBirth = dateOfBirth?.toEpochDay()?.times(MILLIS_PER_DAY),
    dueDate = dueDate?.toEpochDay()?.times(MILLIS_PER_DAY),
    dueDateUserProvided = dueDateUserProvided,
    homeTimezoneId = homeTimezoneId,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
)

private const val MILLIS_PER_DAY = 86_400_000L
```

- [ ] **Step 2.2: Create `BabyProfileDao.kt`**

```kotlin
// data/local/dao/BabyProfileDao.kt
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.BabyProfileEntity
import com.babytracker.data.local.entity.SINGLETON_ID

@Dao
interface BabyProfileDao {
    @Query("SELECT * FROM babies WHERE id = $SINGLETON_ID LIMIT 1")
    suspend fun getProfile(): BabyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: BabyProfileEntity)
}
```

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/entity/BabyProfileEntity.kt \
        app/src/main/java/com/babytracker/data/local/dao/BabyProfileDao.kt
git commit -m "feat(db): add BabyProfileEntity and BabyProfileDao for singleton profile table [AKA-95]"
```

---

## Task 3: Room v5 migration + BabyTrackerDatabase + DatabaseModule

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

- [ ] **Step 3.1: Add `MIGRATION_4_5` and update `BabyTrackerDatabase` to version 5**

Add at the bottom of `BabyTrackerDatabase.kt` (after `MIGRATION_3_4`):

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Create singleton babies table (schema-only; no DataStore read here).
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS babies (
                id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                date_of_birth INTEGER,
                due_date INTEGER,
                due_date_user_provided INTEGER NOT NULL DEFAULT 0,
                home_timezone_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. Add nullable timezone_id to existing sleep_records.
        database.execSQL(
            "ALTER TABLE sleep_records ADD COLUMN timezone_id TEXT DEFAULT NULL"
        )

        // 3. Normalize any legacy sleep_type labels that used the enum's label
        //    property ("Nap", "Night Sleep") instead of the enum name.
        database.execSQL(
            """
            UPDATE sleep_records
            SET sleep_type = CASE
                WHEN sleep_type = 'Nap'         THEN 'NAP'
                WHEN sleep_type = 'Night Sleep' THEN 'NIGHT_SLEEP'
                ELSE sleep_type
            END
            WHERE sleep_type IN ('Nap', 'Night Sleep')
            """.trimIndent()
        )
    }
}
```

Update the `@Database` annotation (add `BabyProfileEntity`, bump version to 5):

```kotlin
@Database(
    entities = [
        BreastfeedingEntity::class,
        SleepEntity::class,
        PumpingEntity::class,
        MilkBagEntity::class,
        BabyProfileEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
```

Add the abstract DAO accessor:

```kotlin
abstract fun babyProfileDao(): BabyProfileDao
```

- [ ] **Step 3.2: Update `DatabaseModule` — add migration and DAO provider**

```kotlin
// di/DatabaseModule.kt  (full updated file)
package com.babytracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.MIGRATION_1_2
import com.babytracker.data.local.MIGRATION_2_3
import com.babytracker.data.local.MIGRATION_3_4
import com.babytracker.data.local.MIGRATION_4_5
import com.babytracker.data.local.installActiveSessionInvariantTriggers
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.BabyProfileDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.installActiveSessionInvariantTriggers()
                }
            })
            .build()

    @Provides fun provideBreastfeedingDao(db: BabyTrackerDatabase): BreastfeedingDao = db.breastfeedingDao()
    @Provides fun provideSleepDao(db: BabyTrackerDatabase): SleepDao = db.sleepDao()
    @Provides fun providePumpingDao(db: BabyTrackerDatabase): PumpingDao = db.pumpingDao()
    @Provides fun provideMilkBagDao(db: BabyTrackerDatabase): MilkBagDao = db.milkBagDao()
    @Provides fun provideBabyProfileDao(db: BabyTrackerDatabase): BabyProfileDao = db.babyProfileDao()
    @Provides fun provideNowProvider(): () -> Instant = Instant::now
}
```

- [ ] **Step 3.3: Build check (schema export will update)**

```bash
./gradlew assembleDebug
```

Expected: builds successfully. Room generates a new schema JSON under `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/5.json`.

- [ ] **Step 3.4: Commit (include generated schema)**

```bash
git add app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt \
        app/src/main/java/com/babytracker/di/DatabaseModule.kt \
        "app/schemas/com.babytracker.data.local.BabyTrackerDatabase/5.json"
git commit -m "feat(db): Room v5 migration — babies table, timezone_id on sleep_records, legacy SleepType normalization [AKA-95]"
```

---

## Task 4: BabyProfile domain model + BabyProfileRepository

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/BabyProfile.kt`
- Create: `app/src/main/java/com/babytracker/domain/repository/BabyProfileRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/BabyProfileRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 4.1: Create `BabyProfile` domain model**

```kotlin
// domain/model/BabyProfile.kt
package com.babytracker.domain.model

import java.time.LocalDate

data class BabyProfile(
    val dateOfBirth: LocalDate?,
    val dueDate: LocalDate?,
    val dueDateUserProvided: Boolean,
    val homeTimezoneId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
```

- [ ] **Step 4.2: Create `BabyProfileRepository` interface**

```kotlin
// domain/repository/BabyProfileRepository.kt
package com.babytracker.domain.repository

import com.babytracker.domain.model.BabyProfile

interface BabyProfileRepository {
    suspend fun getProfile(): BabyProfile?
    suspend fun upsertProfile(profile: BabyProfile)
}
```

- [ ] **Step 4.3: Create `BabyProfileRepositoryImpl`**

```kotlin
// data/repository/BabyProfileRepositoryImpl.kt
package com.babytracker.data.repository

import com.babytracker.data.local.dao.BabyProfileDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyProfileRepositoryImpl @Inject constructor(
    private val dao: BabyProfileDao
) : BabyProfileRepository {
    override suspend fun getProfile(): BabyProfile? = dao.getProfile()?.toDomain()
    override suspend fun upsertProfile(profile: BabyProfile) = dao.upsertProfile(profile.toEntity())
}
```

- [ ] **Step 4.4: Bind in `RepositoryModule`**

Add to `RepositoryModule.kt` inside the `abstract class`:

```kotlin
@Binds
@Singleton
abstract fun bindBabyProfileRepository(impl: BabyProfileRepositoryImpl): BabyProfileRepository
```

Also add the import:
```kotlin
import com.babytracker.data.repository.BabyProfileRepositoryImpl
import com.babytracker.domain.repository.BabyProfileRepository
```

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/BabyProfile.kt \
        app/src/main/java/com/babytracker/domain/repository/BabyProfileRepository.kt \
        app/src/main/java/com/babytracker/data/repository/BabyProfileRepositoryImpl.kt \
        app/src/main/java/com/babytracker/di/RepositoryModule.kt
git commit -m "feat(repository): BabyProfile domain model + BabyProfileRepository interface and impl [AKA-95]"
```

---

## Task 5: BootstrapBabyProfileUseCase + tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/baby/BootstrapBabyProfileUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/baby/BootstrapBabyProfileUseCaseTest.kt`

- [ ] **Step 5.1: Write failing tests**

```kotlin
// src/test/.../usecase/baby/BootstrapBabyProfileUseCaseTest.kt
package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BootstrapBabyProfileUseCaseTest {

    private lateinit var babyRepo: BabyRepository
    private lateinit var profileRepo: BabyProfileRepository
    private val fixedNow = Instant.parse("2026-06-05T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }
    private val testZoneId = "America/New_York"

    private lateinit var useCase: BootstrapBabyProfileUseCase

    @BeforeEach
    fun setup() {
        babyRepo = mockk()
        profileRepo = mockk()
        useCase = BootstrapBabyProfileUseCase(babyRepo, profileRepo, nowProvider, testZoneId)
    }

    @Test
    fun `copies DataStore profile to Room when no profile exists`() = runTest {
        val baby = Baby(
            name = "Lila",
            birthDate = LocalDate.of(2025, 12, 1),
            allergies = emptyList(),
        )
        coEvery { profileRepo.getProfile() } returns null
        coEvery { babyRepo.getBabyProfile() } returns flowOf(baby)
        coEvery { profileRepo.upsertProfile(any()) } returns Unit

        useCase()

        val slot = slot<BabyProfile>()
        coVerify { profileRepo.upsertProfile(capture(slot)) }
        val saved = slot.captured
        assertEquals(baby.birthDate, saved.dateOfBirth)
        assertEquals(testZoneId, saved.homeTimezoneId)
        assertNull(saved.dueDate)
        assertEquals(fixedNow.toEpochMilli(), saved.createdAtEpochMs)
        assertEquals(fixedNow.toEpochMilli(), saved.updatedAtEpochMs)
    }

    @Test
    fun `skips upsert when profile already exists (idempotent)`() = runTest {
        val existingProfile = BabyProfile(
            dateOfBirth = LocalDate.of(2025, 12, 1),
            dueDate = null,
            dueDateUserProvided = false,
            homeTimezoneId = "UTC",
            createdAtEpochMs = 1_000_000L,
            updatedAtEpochMs = 1_000_000L,
        )
        coEvery { profileRepo.getProfile() } returns existingProfile

        useCase()

        coVerify(exactly = 0) { profileRepo.upsertProfile(any()) }
    }

    @Test
    fun `skips upsert when DataStore has no baby profile (onboarding not complete)`() = runTest {
        coEvery { profileRepo.getProfile() } returns null
        coEvery { babyRepo.getBabyProfile() } returns flowOf(null)

        useCase()

        coVerify(exactly = 0) { profileRepo.upsertProfile(any()) }
    }

    @Test
    fun `does not throw when DataStore emits exception`() = runTest {
        coEvery { profileRepo.getProfile() } returns null
        coEvery { babyRepo.getBabyProfile() } returns flowOf(null)

        // Should complete without throwing
        useCase()
    }
}
```

- [ ] **Step 5.2: Run tests to see them fail**

```bash
./gradlew test --tests "*.BootstrapBabyProfileUseCaseTest"
```

Expected: compilation error or `ClassNotFoundException` — use case doesn't exist yet.

- [ ] **Step 5.3: Implement `BootstrapBabyProfileUseCase`**

```kotlin
// domain/usecase/baby/BootstrapBabyProfileUseCase.kt
package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class BootstrapBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
    private val profileRepository: BabyProfileRepository,
    private val nowProvider: () -> Instant,
    private val homeZoneId: String = ZoneId.systemDefault().id,
) {
    suspend operator fun invoke() {
        if (profileRepository.getProfile() != null) return   // idempotent
        val baby = babyRepository.getBabyProfile().first() ?: return   // onboarding not complete
        val nowMs = nowProvider().toEpochMilli()
        profileRepository.upsertProfile(
            BabyProfile(
                dateOfBirth = baby.birthDate,
                dueDate = null,
                dueDateUserProvided = false,
                homeTimezoneId = homeZoneId,
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
            )
        )
    }
}
```

Note: `homeZoneId` is a constructor parameter with a default so the test can inject a fixed string (`"America/New_York"`) while production Hilt injection calls `ZoneId.systemDefault().id` at DI graph construction time. Hilt uses the no-arg `@Inject` constructor, so `ZoneId.systemDefault()` is evaluated at graph build (startup) — which is correct for a "home zone at install time" capture.

- [ ] **Step 5.4: Run tests**

```bash
./gradlew test --tests "*.BootstrapBabyProfileUseCaseTest"
```

Expected: 4 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/baby/BootstrapBabyProfileUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/baby/BootstrapBabyProfileUseCaseTest.kt
git commit -m "feat(usecase): BootstrapBabyProfileUseCase — copies DataStore profile to Room on startup [AKA-95]"
```

---

## Task 6: Wire bootstrap in BabyTrackerApp

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

- [ ] **Step 6.1: Inject and launch `BootstrapBabyProfileUseCase`**

Add the `@Inject` field and fire-and-forget launch in `onCreate`:

```kotlin
// BabyTrackerApp.kt — diff view (add among existing @Inject fields and in onCreate)
@Inject lateinit var bootstrapBabyProfile: BootstrapBabyProfileUseCase
```

In `onCreate()`, after the existing coordinators start, add:

```kotlin
appScope.launch { runCatching { bootstrapBabyProfile() } }
```

`runCatching` ensures any unexpected failure doesn't crash the app at startup; the bootstrap will retry on the next launch.

Full updated `onCreate()` block:

```kotlin
override fun onCreate() {
    super.onCreate()
    NotificationHelper.createBreastfeedingNotificationChannel(this)
    NotificationHelper.createSleepNotificationChannel(this)
    NotificationHelper.createStashExpirationNotificationChannel(this)
    createPredictiveFeedNotificationChannel(this)
    predictiveCoordinator.start()
    createPredictiveSleepNotificationChannel(this)
    predictiveSleepCoordinator.start()
    widgetSyncManager.start()
    reconcileStashExpirationAlarm()
    appScope.launch { runCatching { bootstrapBabyProfile() } }
    if (BuildConfig.DEBUG) {
        appScope.launch { debugDataSeeder.seedIfEmpty() }
    }
}
```

Add import:
```kotlin
import com.babytracker.domain.usecase.baby.BootstrapBabyProfileUseCase
```

- [ ] **Step 6.2: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds successfully.

- [ ] **Step 6.3: Commit**

```bash
git add app/src/main/java/com/babytracker/BabyTrackerApp.kt
git commit -m "feat(db): launch BootstrapBabyProfileUseCase on app startup [AKA-95]"
```

---

## Task 7: `EvidenceQuality.hasQualifiedTimezoneProvenance` + `SleepFeatureExtractor` + tuning constants

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Test: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 7.1: Write failing tests for provenance computation**

Add the following test to `SleepFeatureExtractorTest` (use the existing `sleepRecord()` helper and `extractor`):

```kotlin
@Test
fun `computeQuality sets hasQualifiedTimezoneProvenance false when no records have timezoneId`() {
    val records = (1..10).map { i ->
        SleepRecord(
            startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
            endTime   = nowInstant.minusMillis(hoursMs(i * 3.0)),
            sleepType = SleepType.NAP,
            timezoneId = null,          // no provenance
        )
    }
    val intervals = extractor.buildSleepIntervals(records)
    val metrics   = extractor.computeMetrics(intervals)
    val quality   = extractor.computeQuality(intervals, records.size, metrics)

    assertFalse(quality.hasQualifiedTimezoneProvenance)
}

@Test
fun `computeQuality sets hasQualifiedTimezoneProvenance true when majority of recent intervals have timezoneId`() {
    // 8 out of 10 records have a timezoneId — above the 50% threshold
    val records = (1..10).map { i ->
        SleepRecord(
            startTime  = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
            endTime    = nowInstant.minusMillis(hoursMs(i * 3.0)),
            sleepType  = SleepType.NAP,
            timezoneId = if (i <= 8) "America/New_York" else null,
        )
    }
    val intervals = extractor.buildSleepIntervals(records)
    val metrics   = extractor.computeMetrics(intervals)
    val quality   = extractor.computeQuality(intervals, records.size, metrics)

    assertTrue(quality.hasQualifiedTimezoneProvenance)
}

@Test
fun `computeQuality sets hasQualifiedTimezoneProvenance false when fewer than MIN_COMPLETED_INTERVALS available`() {
    // Only 3 records — below the minimum gating count, so provenance cannot be qualified
    val records = (1..3).map { i ->
        SleepRecord(
            startTime  = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
            endTime    = nowInstant.minusMillis(hoursMs(i * 3.0)),
            sleepType  = SleepType.NAP,
            timezoneId = "UTC",
        )
    }
    val intervals = extractor.buildSleepIntervals(records)
    val metrics   = extractor.computeMetrics(intervals)
    val quality   = extractor.computeQuality(intervals, records.size, metrics)

    assertFalse(quality.hasQualifiedTimezoneProvenance)
}
```

```kotlin
@Test
fun `hasQualifiedTimezoneProvenance true when records have timezoneId from a different zone than current device`() {
    // Records were created in "America/New_York" but extractor runs with UTC zone.
    // Provenance is still qualified — it only checks timezoneId != null, not zone equality.
    val records = (1..10).map { i ->
        SleepRecord(
            startTime  = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
            endTime    = nowInstant.minusMillis(hoursMs(i * 3.0)),
            sleepType  = SleepType.NAP,
            timezoneId = "America/New_York",
        )
    }
    val intervals = extractor.buildSleepIntervals(records)
    val metrics   = extractor.computeMetrics(intervals)
    val quality   = extractor.computeQuality(intervals, records.size, metrics)

    assertTrue(quality.hasQualifiedTimezoneProvenance)
}

@Test
fun `hasQualifiedTimezoneProvenance false for legacy records with null timezoneId after device timezone change`() {
    // Legacy records have null timezoneId (created before Phase 3 shipped).
    // Even after a device timezone change, these records carry no provenance.
    val records = (1..10).map { i ->
        SleepRecord(
            startTime  = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
            endTime    = nowInstant.minusMillis(hoursMs(i * 3.0)),
            sleepType  = SleepType.NAP,
            timezoneId = null,
        )
    }
    val intervals = extractor.buildSleepIntervals(records)
    val metrics   = extractor.computeMetrics(intervals)
    val quality   = extractor.computeQuality(intervals, records.size, metrics)

    assertFalse(quality.hasQualifiedTimezoneProvenance)
}
```

- [ ] **Step 7.2: Run tests to see them fail**

```bash
./gradlew test --tests "*.SleepFeatureExtractorTest"
```

Expected: the three new provenance tests fail — `EvidenceQuality` doesn't have the field yet.

- [ ] **Step 7.3: Add `hasQualifiedTimezoneProvenance` to `EvidenceQuality`**

```kotlin
// domain/sleep/feature/EvidenceQuality.kt
package com.babytracker.domain.sleep.feature

data class EvidenceQuality(
    val lastWakeRecencyMillis: Long?,
    val isFresh: Boolean,
    val completedIntervalCount: Int,
    val localDayCoverage: Int,
    val isLocalDayCoverageSufficient: Boolean,
    val wakeIntervalIqrMillis: Long?,
    val invalidRecordRate: Float,
    val hasSufficientZoneIndependentEvidence: Boolean,
    val hasQualifiedTimezoneProvenance: Boolean = false,
)
```

Default `false` = Phase 0 cap remains in effect for all existing construction sites.

- [ ] **Step 7.4: Add tuning constants to `SleepPredictionTuning`**

Add these two constants and update `ALGORITHM_VERSION`:

```kotlin
const val MIN_QUALIFIED_TZ_PROVENANCE_RATE = 0.5f
const val HIGH_CONFIDENCE_QUALITY_C_THRESHOLD = 0.8f
const val ALGORITHM_VERSION = "sleep-pred-phase3-tz-provenance-1"
```

Also update the existing test that asserts `ALGORITHM_VERSION`:

In `SleepFeatureExtractorTest` find:
```kotlin
assertEquals("sleep-pred-phase2-sleep-debt-1", SleepPredictionTuning.ALGORITHM_VERSION)
```
Change to:
```kotlin
assertEquals("sleep-pred-phase3-tz-provenance-1", SleepPredictionTuning.ALGORITHM_VERSION)
```

- [ ] **Step 7.5: Compute provenance in `SleepFeatureExtractor.computeQuality()`**

In `computeQuality()`, add the provenance computation after the existing `invalidRecordRate` calculation (before the `return EvidenceQuality(...)` statement):

```kotlin
// Qualified timezone provenance: true when ≥MIN_QUALIFIED_TZ_PROVENANCE_RATE of completed
// intervals in the lookback window carry a recorded timezone_id.
// Requires at least MIN_COMPLETED_INTERVALS to avoid granting HIGH confidence on sparse data.
val recentCompleted = possibleIntervals.filter {
    it.isCompleted && it.endMillis!! >= lookbackStartMillis
}
val qualifiedTzCount = recentCompleted.count { it.timezoneId != null }
val hasQualifiedTimezoneProvenance = recentCompleted.size >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
    qualifiedTzCount.toFloat() / recentCompleted.size >= SleepPredictionTuning.MIN_QUALIFIED_TZ_PROVENANCE_RATE
```

Update the `return EvidenceQuality(...)` call to include the new field:

```kotlin
return EvidenceQuality(
    lastWakeRecencyMillis = recencyMillis,
    isFresh = isFresh,
    completedIntervalCount = metrics.completedWakeIntervals.size,
    localDayCoverage = localDayCoverage,
    isLocalDayCoverageSufficient = localDayCoverage >= SleepPredictionTuning.MIN_LOCAL_DAYS,
    wakeIntervalIqrMillis = metrics.wakeIntervalIqrMillis,
    invalidRecordRate = invalidRecordRate,
    hasSufficientZoneIndependentEvidence = hasSufficientZoneIndependentEvidence,
    hasQualifiedTimezoneProvenance = hasQualifiedTimezoneProvenance,
)
```

- [ ] **Step 7.6: Run tests**

```bash
./gradlew test --tests "*.SleepFeatureExtractorTest" --tests "*.SleepWindowPredictorTest"
```

Expected: all pass.

- [ ] **Step 7.7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt \
        app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt \
        app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt \
        app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add hasQualifiedTimezoneProvenance to EvidenceQuality; compute in SleepFeatureExtractor [AKA-95]"
```

---

## Task 8: SleepWindowPredictor — lift MEDIUM cap, allow HIGH with qualified provenance

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

- [ ] **Step 8.1: Write failing tests**

Add to `SleepWindowPredictorTest`:

```kotlin
@Test
fun `confidence is MEDIUM without qualified timezone provenance even when qualityC is high`() {
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    // qualityC = 14/14 = 1.0 ≥ 0.8 but no TZ provenance → stays MEDIUM
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = false)

    val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), 20, baseNow)

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.MEDIUM, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `confidence is HIGH with qualified timezone provenance and high qualityC`() {
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    // qualityC = 14/14 = 1.0 ≥ HIGH threshold AND TZ provenance → HIGH
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = true)

    val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), 20, baseNow)

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.HIGH, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `confidence is LOW when qualityC is low regardless of provenance`() {
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    // qualityC = 5/14 ≈ 0.36 < 0.5 → LOW regardless of provenance
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.MIN_COMPLETED_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = true)

    val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), 20, baseNow)

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
}
```

- [ ] **Step 8.2: Run tests to verify they fail**

```bash
./gradlew test --tests "*.SleepWindowPredictorTest"
```

Expected: the two new HIGH-confidence tests fail — predictor still returns MEDIUM.

- [ ] **Step 8.3: Update `buildWindow()` in `SleepWindowPredictor`**

Replace the single line:
```kotlin
val confidence = if (qualityC >= 0.5f) Confidence.MEDIUM else Confidence.LOW
```

With:
```kotlin
val confidence = when {
    qualityC >= SleepPredictionTuning.HIGH_CONFIDENCE_QUALITY_C_THRESHOLD &&
        quality.hasQualifiedTimezoneProvenance -> Confidence.HIGH
    qualityC >= 0.5f -> Confidence.MEDIUM
    else -> Confidence.LOW
}
```

- [ ] **Step 8.4: Run tests**

```bash
./gradlew test --tests "*.SleepWindowPredictorTest"
```

Expected: all tests pass.

- [ ] **Step 8.5: Run full test suite to confirm no regressions**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 8.6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt \
        app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): lift MEDIUM confidence cap — allow HIGH when timezone provenance is qualified [AKA-95]"
```

---

## Task 9: Migration instrumentation test — v4→v5

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt`

- [ ] **Step 9.1: Add v4→v5 migration test**

Append to `MigrationTest`:

```kotlin
@Test
fun migrate4To5_createsBabiesTable_addsTimezoneId_normalizesLegacySleepType() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration-test-v4-to-v5"
    try {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(V4DatabaseCallback())
                .build()
        )
        val db = helper.writableDatabase

        // Seed v4 rows: one with enum name, one with legacy label
        db.execSQL(
            "INSERT INTO sleep_records(start_time, sleep_type) VALUES(1000, 'NAP')"
        )
        db.execSQL(
            "INSERT INTO sleep_records(start_time, sleep_type) VALUES(2000, 'Nap')"
        )
        db.execSQL(
            "INSERT INTO sleep_records(start_time, sleep_type) VALUES(3000, 'Night Sleep')"
        )

        MIGRATION_4_5.migrate(db)

        // 1. babies table exists and accepts a singleton insert
        db.execSQL(
            "INSERT INTO babies(id, created_at, updated_at) VALUES(1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})"
        )
        val babiesCursor: Cursor = db.query("SELECT count(*) FROM babies")
        babiesCursor.moveToFirst()
        assertEquals(1, babiesCursor.getInt(0))
        babiesCursor.close()

        // 2. timezone_id column exists on sleep_records
        db.execSQL("UPDATE sleep_records SET timezone_id = 'UTC' WHERE id = 1")

        // 3. Legacy labels were normalized
        val typeCursor: Cursor = db.query(
            "SELECT sleep_type FROM sleep_records ORDER BY start_time ASC"
        )
        typeCursor.moveToFirst()
        assertEquals("NAP", typeCursor.getString(0))          // already canonical
        typeCursor.moveToNext()
        assertEquals("NAP", typeCursor.getString(0))          // normalized from 'Nap'
        typeCursor.moveToNext()
        assertEquals("NIGHT_SLEEP", typeCursor.getString(0))  // normalized from 'Night Sleep'
        typeCursor.close()

        db.close()
    } finally {
        context.deleteDatabase(dbName)
    }
}

// V4DatabaseCallback creates a v4 schema (babies table absent, no timezone_id).
private class V4DatabaseCallback : SupportSQLiteOpenHelper.Callback(4) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                sleep_type TEXT NOT NULL,
                notes TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS breastfeeding_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                starting_side TEXT NOT NULL,
                switch_time INTEGER,
                notes TEXT,
                paused_at INTEGER,
                paused_duration_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
```

Also add `import com.babytracker.data.local.MIGRATION_4_5` to the imports.

- [ ] **Step 9.2: Run instrumentation tests (requires emulator or device)**

```bash
./gradlew connectedAndroidTest --tests "*.MigrationTest"
```

Expected: new migration test passes.

- [ ] **Step 9.3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt
git commit -m "test(db): add v4→v5 migration test — babies table, timezone_id, legacy SleepType normalization [AKA-95]"
```

---

## Task 10: ktlint + detekt + full test suite

- [ ] **Step 10.1: Fix formatting**

```bash
./gradlew ktlintFormat
```

- [ ] **Step 10.2: Run detekt**

```bash
./gradlew detekt
```

Fix any violations. Each fix should be its own commit: `fix(detekt): fix <RuleName> violations`.

- [ ] **Step 10.3: Run full unit test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 10.4: Commit ktlint fixes (if any)**

```bash
git add -p   # stage only formatting changes
git commit -m "style: ktlint formatting fixes [AKA-95]"
```

---

## Acceptance Criteria Checklist

- [ ] Room v5 migration creates `babies` table schema-only; no DataStore read occurs inside `MIGRATION_4_5`
- [ ] `sleep_records.timezone_id` column exists after migration; NULL for all legacy rows
- [ ] Legacy SleepType labels (`Nap`, `Night Sleep`) are normalized to enum names (`NAP`, `NIGHT_SLEEP`) by the migration
- [ ] New `SleepRecord`s inserted via `StartSleepRecordUseCase` carry `timezoneId = ZoneId.systemDefault().id`
- [ ] `BootstrapBabyProfileUseCase` copies `Baby.birthDate` + device zone to the singleton `babies` row
- [ ] Bootstrap is idempotent: second invocation does not overwrite an existing row
- [ ] Bootstrap completes without crashing when DataStore has no profile (onboarding incomplete)
- [ ] `EvidenceQuality.hasQualifiedTimezoneProvenance = true` when ≥50% of recent completed intervals have a non-null `timezoneId` AND at least `MIN_COMPLETED_INTERVALS` intervals exist
- [ ] `Confidence.HIGH` is reachable when `hasQualifiedTimezoneProvenance = true` and `qualityC ≥ HIGH_CONFIDENCE_QUALITY_C_THRESHOLD (0.8f)`
- [ ] `Confidence.HIGH` is NOT produced without qualified provenance (`MEDIUM` cap still active for unqualified records)
- [ ] All unit tests pass; all instrumentation migration tests pass
- [ ] `ALGORITHM_VERSION = "sleep-pred-phase3-tz-provenance-1"`
