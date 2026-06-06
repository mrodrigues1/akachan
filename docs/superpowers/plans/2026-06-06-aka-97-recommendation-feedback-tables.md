# AKA-97: Sleep Recommendation + Feedback Telemetry Tables

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE: AKA-97**

**Goal:** Add `sleep_recommendations` and `sleep_recommendation_feedback` Room tables with full lifecycle and outcome tracking, wire lifecycle transitions into the coordinator and receiver, and create feedback records when a sleep is logged — telemetry only, no automatic bias correction.

**Architecture:** Two new entities with a UNIQUE constraint on `(anchor_sleep_id, recommendation_type, algorithm_version)` provide dedup identity — `OnConflictStrategy.IGNORE` on insert + a fallback SELECT returns the stable row ID across Flow recomputations. The coordinator gains three use-case injections and tracks active-recommendation state in memory; it writes GENERATED on first persist, SCHEDULED on alarm-set, SUPERSEDED on anchor-change or disable. The receiver writes FIRED. A second subscription in `start()` watches `observeLatestRecord()` for completed sleeps and creates ACTED_IN_WINDOW/ACTED_OUTSIDE feedback. No automatic bias correction is added.

**Tech Stack:** Room 2.8.4, Hilt 2.59, Kotlin Coroutines/Flow 1.9.0, JUnit 5, MockK 1.13.13, Turbine 1.2.0.

**Branch:** `feat/aka-97-recommendation-feedback-tables`

---

## File Map

### New files

| File | Responsibility |
|------|----------------|
| `domain/model/RecommendationLifecycle.kt` | Enum: `GENERATED, DISPLAYED, SCHEDULED, FIRED, SUPERSEDED` |
| `domain/model/RecommendationOutcome.kt` | Enum: `ACTED_IN_WINDOW, ACTED_OUTSIDE, NO_SLEEP, DISMISSED, QUIET_HOURS_SUPPRESSED, SUPERSEDED` |
| `data/local/entity/SleepRecommendationEntity.kt` | Room entity for `sleep_recommendations`; unique on `(anchor_sleep_id, recommendation_type, algorithm_version)` |
| `data/local/entity/SleepRecommendationFeedbackEntity.kt` | Room entity for `sleep_recommendation_feedback` |
| `data/local/dao/SleepRecommendationDao.kt` | `insertRecommendation` (IGNORE), `getByAnchorTypeVersion`, `updateLifecycle`, `insertFeedback` |
| `domain/repository/SleepRecommendationRepository.kt` | Repository interface |
| `data/repository/SleepRecommendationRepositoryImpl.kt` | Repository impl |
| `domain/usecase/sleep/PersistSleepRecommendationUseCase.kt` | Insert-or-fetch-existing pattern; returns stable row ID |
| `domain/usecase/sleep/UpdateRecommendationLifecycleUseCase.kt` | Single lifecycle transition by ID |
| `domain/usecase/sleep/CreateSleepRecommendationFeedbackUseCase.kt` | Insert feedback record; computes error_minutes from sleepStart vs bestEstimate |
| `src/test/.../usecase/sleep/PersistSleepRecommendationUseCaseTest.kt` | Unit tests for dedup logic |
| `src/test/.../usecase/sleep/UpdateRecommendationLifecycleUseCaseTest.kt` | Unit tests for lifecycle update |
| `src/test/.../usecase/sleep/CreateSleepRecommendationFeedbackUseCaseTest.kt` | Unit tests for outcome computation |

### Modified files

| File | Change |
|------|--------|
| `data/local/BabyTrackerDatabase.kt` | Version 7, add `SleepRecommendationEntity`, `SleepRecommendationFeedbackEntity`, `MIGRATION_6_7`, DAO accessor |
| `di/DatabaseModule.kt` | Add `MIGRATION_6_7`, provide `SleepRecommendationDao` |
| `di/RepositoryModule.kt` | Bind `SleepRecommendationRepository` |
| `manager/PredictiveSleepNotificationCoordinator.kt` | Inject 4 new deps (`SleepRepository`, `SleepRecommendationRepository`, 3 use cases); track active + scheduled-window state; lifecycle transitions; sleep-completion feedback; restart recovery from Room |
| `receiver/PredictiveSleepReceiver.kt` | Inject `UpdateRecommendationLifecycleUseCase`; write `FIRED` lifecycle |
| `src/test/.../manager/PredictiveSleepNotificationCoordinatorTest.kt` | Add mocks for new deps; test lifecycle writes + feedback |

---

## Task 1: Domain enums — RecommendationLifecycle + RecommendationOutcome

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/RecommendationLifecycle.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/RecommendationOutcome.kt`

- [ ] **Step 1.1: Create `RecommendationLifecycle.kt`**

```kotlin
package com.babytracker.domain.model

enum class RecommendationLifecycle { GENERATED, DISPLAYED, SCHEDULED, FIRED, SUPERSEDED }
```

- [ ] **Step 1.2: Create `RecommendationOutcome.kt`**

```kotlin
package com.babytracker.domain.model

enum class RecommendationOutcome {
    ACTED_IN_WINDOW,
    ACTED_OUTSIDE,
    NO_SLEEP,
    DISMISSED,
    QUIET_HOURS_SUPPRESSED,
    SUPERSEDED,
}
```

`NO_SLEEP` and `DISMISSED` are stubbed for future wiring. The enum values are stable identifiers — they will be persisted as strings via `.name`.

- [ ] **Step 1.3: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/RecommendationLifecycle.kt \
        app/src/main/java/com/babytracker/domain/model/RecommendationOutcome.kt
git commit -m "feat(db): add RecommendationLifecycle and RecommendationOutcome enums [AKA-97]"
```

---

## Task 2: Room entities + DAO

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/SleepRecommendationEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/entity/SleepRecommendationFeedbackEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/dao/SleepRecommendationDao.kt`

- [ ] **Step 2.1: Create `SleepRecommendationEntity.kt`**

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.UniqueConstraint

@Entity(
    tableName = "sleep_recommendations",
    indices = [
        Index("generated_at"),
        Index("anchor_sleep_id"),
        Index("recommendation_type"),
    ],
    uniqueConstraints = [
        UniqueConstraint(columns = ["anchor_sleep_id", "recommendation_type", "algorithm_version"]),
    ],
)
data class SleepRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "anchor_sleep_id") val anchorSleepId: Long,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "recommendation_type") val recommendationType: String,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    @ColumnInfo(name = "best_estimate") val bestEstimate: Long,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "lifecycle") val lifecycle: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String,
)
```

The `UniqueConstraint` on `(anchor_sleep_id, recommendation_type, algorithm_version)` is the identity key. Combined with `OnConflictStrategy.IGNORE` on insert, multiple Flow recomputations for the same wake anchor produce exactly one row.

- [ ] **Step 2.2: Create `SleepRecommendationFeedbackEntity.kt`**

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_recommendation_feedback",
    indices = [
        Index("recommendation_id"),
        Index("actual_sleep_record_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(columns = ["recommendation_id", "outcome"]),
    ],
)
data class SleepRecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: Long,
    @ColumnInfo(name = "actual_sleep_record_id") val actualSleepRecordId: Long? = null,
    @ColumnInfo(name = "error_minutes") val errorMinutes: Int? = null,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

Add import at top:
```kotlin
import androidx.room.UniqueConstraint
```

`error_minutes` is `null` when no sleep occurred (NO_SLEEP, DISMISSED, QUIET_HOURS_SUPPRESSED, SUPERSEDED outcomes). It is `signed`: negative means sleep started before the window's best estimate (early), positive means after (late).

The `UNIQUE(recommendation_id, outcome)` constraint ensures one feedback row per outcome type per recommendation. `OnConflictStrategy.IGNORE` on feedback insert means repeated calls for the same outcome are no-ops — safe across Flow re-emissions and process restarts.

- [ ] **Step 2.3: Create `SleepRecommendationDao.kt`**

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity

@Dao
interface SleepRecommendationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecommendation(entity: SleepRecommendationEntity): Long

    @Query(
        """
        SELECT * FROM sleep_recommendations
        WHERE anchor_sleep_id = :anchorId
          AND recommendation_type = :type
          AND algorithm_version = :version
        LIMIT 1
        """
    )
    suspend fun getByAnchorTypeVersion(
        anchorId: Long,
        type: String,
        version: String,
    ): SleepRecommendationEntity?

    @Query("UPDATE sleep_recommendations SET lifecycle = :lifecycle WHERE id = :id")
    suspend fun updateLifecycle(id: Long, lifecycle: String)

    @Query(
        """
        SELECT * FROM sleep_recommendations
        WHERE lifecycle = 'SCHEDULED'
        ORDER BY generated_at DESC
        LIMIT 1
        """
    )
    suspend fun getLatestScheduled(): SleepRecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedback(entity: SleepRecommendationFeedbackEntity): Long
}
```

`insertRecommendation` returns the new rowid on success, or `-1L` when the UNIQUE constraint fires (row already existed). The repository uses this return value to decide whether to run a follow-up `getByAnchorTypeVersion` to recover the stable ID.

`insertFeedback` uses `OnConflictStrategy.IGNORE` together with the `UNIQUE(recommendation_id, outcome)` constraint — repeated calls for the same outcome are no-ops, so feedback is idempotent across restarts and re-emissions.

`getLatestScheduled()` is used on coordinator startup to reconstruct in-memory scheduled-window state after a process restart.

- [ ] **Step 2.4: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/entity/SleepRecommendationEntity.kt \
        app/src/main/java/com/babytracker/data/local/entity/SleepRecommendationFeedbackEntity.kt \
        app/src/main/java/com/babytracker/data/local/dao/SleepRecommendationDao.kt
git commit -m "feat(db): add SleepRecommendationEntity, SleepRecommendationFeedbackEntity, and SleepRecommendationDao [AKA-97]"
```

---

## Task 3: Room v7 migration + BabyTrackerDatabase + DatabaseModule

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

- [ ] **Step 3.1: Add `MIGRATION_6_7` in `BabyTrackerDatabase.kt`**

Add the following after `MIGRATION_5_6` at the bottom of `BabyTrackerDatabase.kt`:

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_recommendations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                anchor_sleep_id INTEGER NOT NULL,
                generated_at INTEGER NOT NULL,
                recommendation_type TEXT NOT NULL,
                window_start INTEGER NOT NULL,
                window_end INTEGER NOT NULL,
                best_estimate INTEGER NOT NULL,
                confidence TEXT NOT NULL,
                lifecycle TEXT NOT NULL,
                algorithm_version TEXT NOT NULL,
                UNIQUE(anchor_sleep_id, recommendation_type, algorithm_version)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_generated_at ON sleep_recommendations(generated_at)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_anchor_sleep_id ON sleep_recommendations(anchor_sleep_id)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_recommendation_type ON sleep_recommendations(recommendation_type)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_recommendation_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recommendation_id INTEGER NOT NULL,
                actual_sleep_record_id INTEGER,
                error_minutes INTEGER,
                outcome TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(recommendation_id, outcome)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendation_feedback_recommendation_id ON sleep_recommendation_feedback(recommendation_id)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendation_feedback_actual_sleep_record_id ON sleep_recommendation_feedback(actual_sleep_record_id)"
        )
    }
}
```

- [ ] **Step 3.2: Update `@Database` annotation — version 7, add new entities**

Replace the existing `@Database` annotation in `BabyTrackerDatabase.kt`:

```kotlin
@Database(
    entities = [
        BreastfeedingEntity::class,
        SleepEntity::class,
        PumpingEntity::class,
        MilkBagEntity::class,
        BabyProfileEntity::class,
        BabyEventEntity::class,
        SleepRecommendationEntity::class,
        SleepRecommendationFeedbackEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
```

Add the abstract DAO accessor to the class body (after the existing `babyEventDao()` line):

```kotlin
abstract fun sleepRecommendationDao(): SleepRecommendationDao
```

Add these imports at the top of `BabyTrackerDatabase.kt`:

```kotlin
import com.babytracker.data.local.dao.SleepRecommendationDao
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity
```

- [ ] **Step 3.3: Update `DatabaseModule` — add `MIGRATION_6_7` and provide the DAO**

Replace the `.addMigrations(...)` call in `provideDatabase`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

Add this import at the top of `DatabaseModule.kt`:

```kotlin
import com.babytracker.data.local.MIGRATION_6_7
import com.babytracker.data.local.dao.SleepRecommendationDao
```

Add the DAO provider after `provideBabyEventDao`:

```kotlin
@Provides
fun provideSleepRecommendationDao(database: BabyTrackerDatabase): SleepRecommendationDao =
    database.sleepRecommendationDao()
```

- [ ] **Step 3.4: Build check (Room schema export updates)**

```bash
./gradlew assembleDebug
```

Expected: builds successfully. Room generates `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/7.json`.

- [ ] **Step 3.5: Commit (include generated schema)**

```bash
git add app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt \
        app/src/main/java/com/babytracker/di/DatabaseModule.kt \
        "app/schemas/com.babytracker.data.local.BabyTrackerDatabase/7.json"
git commit -m "feat(db): Room v7 migration — sleep_recommendations and sleep_recommendation_feedback tables [AKA-97]"
```

---

## Task 4: SleepRecommendationRepository interface + impl + DI

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/SleepRecommendationRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/SleepRecommendationRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 4.1: Create the interface**

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome

interface SleepRecommendationRepository {

    /**
     * Inserts the recommendation row. Returns the new row ID on success, or -1 when a row
     * with the same (anchorSleepId, type, algorithmVersion) already exists (IGNORE strategy).
     */
    suspend fun insertRecommendation(
        anchorSleepId: Long,
        generatedAt: Long,
        type: String,
        windowStart: Long,
        windowEnd: Long,
        bestEstimate: Long,
        confidence: String,
        lifecycle: RecommendationLifecycle,
        algorithmVersion: String,
    ): Long

    /** Returns the row ID for an existing record, or null if none exists yet. */
    suspend fun getIdByAnchorTypeVersion(
        anchorSleepId: Long,
        type: String,
        algorithmVersion: String,
    ): Long?

    suspend fun updateLifecycle(id: Long, lifecycle: RecommendationLifecycle)

    suspend fun insertFeedback(
        recommendationId: Long,
        actualSleepRecordId: Long?,
        errorMinutes: Int?,
        outcome: RecommendationOutcome,
        createdAt: Long,
    ): Long

    /** Returns the most recent SCHEDULED recommendation, used to rebuild coordinator state after process restart. */
    suspend fun getLatestScheduledRecommendation(): SleepRecommendationSnapshot?
}

/** Minimal projection of a recommendation row needed for coordinator state reconstruction. */
data class SleepRecommendationSnapshot(
    val id: Long,
    val anchorSleepId: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val bestEstimateMs: Long,
)
```

- [ ] **Step 4.2: Create the implementation**

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.SleepRecommendationDao
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRecommendationRepositoryImpl @Inject constructor(
    private val dao: SleepRecommendationDao,
) : SleepRecommendationRepository {

    override suspend fun insertRecommendation(
        anchorSleepId: Long,
        generatedAt: Long,
        type: String,
        windowStart: Long,
        windowEnd: Long,
        bestEstimate: Long,
        confidence: String,
        lifecycle: RecommendationLifecycle,
        algorithmVersion: String,
    ): Long = dao.insertRecommendation(
        SleepRecommendationEntity(
            anchorSleepId = anchorSleepId,
            generatedAt = generatedAt,
            recommendationType = type,
            windowStart = windowStart,
            windowEnd = windowEnd,
            bestEstimate = bestEstimate,
            confidence = confidence,
            lifecycle = lifecycle.name,
            algorithmVersion = algorithmVersion,
        )
    )

    override suspend fun getIdByAnchorTypeVersion(
        anchorSleepId: Long,
        type: String,
        algorithmVersion: String,
    ): Long? = dao.getByAnchorTypeVersion(anchorSleepId, type, algorithmVersion)?.id

    override suspend fun updateLifecycle(id: Long, lifecycle: RecommendationLifecycle) {
        dao.updateLifecycle(id, lifecycle.name)
    }

    override suspend fun insertFeedback(
        recommendationId: Long,
        actualSleepRecordId: Long?,
        errorMinutes: Int?,
        outcome: RecommendationOutcome,
        createdAt: Long,
    ): Long = dao.insertFeedback(
        SleepRecommendationFeedbackEntity(
            recommendationId = recommendationId,
            actualSleepRecordId = actualSleepRecordId,
            errorMinutes = errorMinutes,
            outcome = outcome.name,
            createdAt = createdAt,
        )
    )

    override suspend fun getLatestScheduledRecommendation(): SleepRecommendationSnapshot? =
        dao.getLatestScheduled()?.let { entity ->
            SleepRecommendationSnapshot(
                id = entity.id,
                anchorSleepId = entity.anchorSleepId,
                windowStartMs = entity.windowStart,
                windowEndMs = entity.windowEnd,
                bestEstimateMs = entity.bestEstimate,
            )
        }
}
```

- [ ] **Step 4.3: Bind in `RepositoryModule`**

Add inside the `abstract class RepositoryModule` body:

```kotlin
@Binds
@Singleton
abstract fun bindSleepRecommendationRepository(
    impl: SleepRecommendationRepositoryImpl,
): SleepRecommendationRepository
```

Add imports at the top:

```kotlin
import com.babytracker.data.repository.SleepRecommendationRepositoryImpl
import com.babytracker.domain.repository.SleepRecommendationRepository
```

- [ ] **Step 4.4: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds successfully — Hilt can resolve `SleepRecommendationRepository`.

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SleepRecommendationRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SleepRecommendationRepositoryImpl.kt \
        app/src/main/java/com/babytracker/di/RepositoryModule.kt
git commit -m "feat(repository): SleepRecommendationRepository interface and impl [AKA-97]"
```

---

## Task 5: PersistSleepRecommendationUseCase + tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/PersistSleepRecommendationUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/sleep/PersistSleepRecommendationUseCaseTest.kt`

- [ ] **Step 5.1: Write failing tests**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PersistSleepRecommendationUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: PersistSleepRecommendationUseCase

    private val window = SleepWindow(
        windowStart = fixedNow.plusSeconds(1800),
        windowEnd = fixedNow.plusSeconds(5400),
        bestEstimate = fixedNow.plusSeconds(3600),
        confidence = Confidence.MEDIUM,
        reasons = emptyList(),
        feedPrompt = null,
        safetyPrompt = "Safe sleep reminder.",
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = PersistSleepRecommendationUseCase(repository, nowProvider)
    }

    @Test
    fun `returns new row ID when insert succeeds`() = runTest {
        coEvery {
            repository.insertRecommendation(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns 42L

        val id = useCase(anchorSleepId = 7L, window = window, recommendationType = "NAP")

        assertEquals(42L, id)
    }

    @Test
    fun `falls back to getIdByAnchorTypeVersion when insert returns -1`() = runTest {
        coEvery {
            repository.insertRecommendation(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns -1L
        coEvery {
            repository.getIdByAnchorTypeVersion(7L, "NAP", SleepPredictionTuning.ALGORITHM_VERSION)
        } returns 99L

        val id = useCase(anchorSleepId = 7L, window = window, recommendationType = "NAP")

        assertEquals(99L, id)
        coVerify { repository.getIdByAnchorTypeVersion(7L, "NAP", SleepPredictionTuning.ALGORITHM_VERSION) }
    }

    @Test
    fun `inserts with GENERATED lifecycle`() = runTest {
        val lifecycleSlot = slot<RecommendationLifecycle>()
        coEvery {
            repository.insertRecommendation(any(), any(), any(), any(), any(), any(), any(), capture(lifecycleSlot), any())
        } returns 1L

        useCase(anchorSleepId = 7L, window = window, recommendationType = "NAP")

        assertEquals(RecommendationLifecycle.GENERATED, lifecycleSlot.captured)
    }

    @Test
    fun `inserts with current ALGORITHM_VERSION`() = runTest {
        val versionSlot = slot<String>()
        coEvery {
            repository.insertRecommendation(any(), any(), any(), any(), any(), any(), any(), any(), capture(versionSlot))
        } returns 1L

        useCase(anchorSleepId = 7L, window = window, recommendationType = "NAP")

        assertEquals(SleepPredictionTuning.ALGORITHM_VERSION, versionSlot.captured)
    }

    @Test
    fun `window epoch millis are passed to repository`() = runTest {
        val windowStartSlot = slot<Long>()
        val bestEstimateSlot = slot<Long>()
        coEvery {
            repository.insertRecommendation(
                any(), any(), any(),
                capture(windowStartSlot), any(),
                capture(bestEstimateSlot),
                any(), any(), any(),
            )
        } returns 1L

        useCase(anchorSleepId = 7L, window = window, recommendationType = "NAP")

        assertEquals(window.windowStart.toEpochMilli(), windowStartSlot.captured)
        assertEquals(window.bestEstimate.toEpochMilli(), bestEstimateSlot.captured)
    }
}
```

- [ ] **Step 5.2: Run tests to see them fail**

```bash
.\gradlew.bat test --tests "*.PersistSleepRecommendationUseCaseTest"
```

Expected: compilation error — `PersistSleepRecommendationUseCase` does not exist.

- [ ] **Step 5.3: Implement `PersistSleepRecommendationUseCase`**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SleepRecommendationRepository
import java.time.Instant
import javax.inject.Inject

class PersistSleepRecommendationUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke(
        anchorSleepId: Long,
        window: SleepWindow,
        recommendationType: String,
    ): Long {
        val insertedId = repository.insertRecommendation(
            anchorSleepId = anchorSleepId,
            generatedAt = nowProvider().toEpochMilli(),
            type = recommendationType,
            windowStart = window.windowStart.toEpochMilli(),
            windowEnd = window.windowEnd.toEpochMilli(),
            bestEstimate = window.bestEstimate.toEpochMilli(),
            confidence = window.confidence.name,
            lifecycle = RecommendationLifecycle.GENERATED,
            algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
        )
        return if (insertedId == -1L) {
            repository.getIdByAnchorTypeVersion(
                anchorSleepId = anchorSleepId,
                type = recommendationType,
                algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
            ) ?: error("Recommendation dedup: insert was ignored but existing record not found for anchor=$anchorSleepId type=$recommendationType")
        } else {
            insertedId
        }
    }
}
```

- [ ] **Step 5.4: Run tests**

```bash
.\gradlew.bat test --tests "*.PersistSleepRecommendationUseCaseTest"
```

Expected: 5 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/PersistSleepRecommendationUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/sleep/PersistSleepRecommendationUseCaseTest.kt
git commit -m "feat(usecase): PersistSleepRecommendationUseCase — insert-or-fetch dedup by anchor+type+version [AKA-97]"
```

---

## Task 6: UpdateRecommendationLifecycleUseCase + tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/UpdateRecommendationLifecycleUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/sleep/UpdateRecommendationLifecycleUseCaseTest.kt`

- [ ] **Step 6.1: Write failing tests**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateRecommendationLifecycleUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private lateinit var useCase: UpdateRecommendationLifecycleUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        coEvery { repository.updateLifecycle(any(), any()) } returns Unit
        useCase = UpdateRecommendationLifecycleUseCase(repository)
    }

    @Test
    fun `delegates to repository with correct id and lifecycle`() = runTest {
        useCase(42L, RecommendationLifecycle.SCHEDULED)
        coVerify(exactly = 1) { repository.updateLifecycle(42L, RecommendationLifecycle.SCHEDULED) }
    }

    @Test
    fun `FIRED lifecycle is delegated correctly`() = runTest {
        useCase(7L, RecommendationLifecycle.FIRED)
        coVerify { repository.updateLifecycle(7L, RecommendationLifecycle.FIRED) }
    }

    @Test
    fun `SUPERSEDED lifecycle is delegated correctly`() = runTest {
        useCase(3L, RecommendationLifecycle.SUPERSEDED)
        coVerify { repository.updateLifecycle(3L, RecommendationLifecycle.SUPERSEDED) }
    }
}
```

- [ ] **Step 6.2: Run tests to see them fail**

```bash
.\gradlew.bat test --tests "*.UpdateRecommendationLifecycleUseCaseTest"
```

Expected: compilation error — `UpdateRecommendationLifecycleUseCase` does not exist.

- [ ] **Step 6.3: Implement `UpdateRecommendationLifecycleUseCase`**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SleepRecommendationRepository
import javax.inject.Inject

class UpdateRecommendationLifecycleUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
) {
    suspend operator fun invoke(id: Long, lifecycle: RecommendationLifecycle) {
        repository.updateLifecycle(id, lifecycle)
    }
}
```

- [ ] **Step 6.4: Run tests**

```bash
.\gradlew.bat test --tests "*.UpdateRecommendationLifecycleUseCaseTest"
```

Expected: 3 tests pass.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/UpdateRecommendationLifecycleUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/sleep/UpdateRecommendationLifecycleUseCaseTest.kt
git commit -m "feat(usecase): UpdateRecommendationLifecycleUseCase — lifecycle transition by ID [AKA-97]"
```

---

## Task 7: CreateSleepRecommendationFeedbackUseCase + tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/CreateSleepRecommendationFeedbackUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/sleep/CreateSleepRecommendationFeedbackUseCaseTest.kt`

- [ ] **Step 7.1: Write failing tests**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CreateSleepRecommendationFeedbackUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: CreateSleepRecommendationFeedbackUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        coEvery { repository.insertFeedback(any(), any(), any(), any(), any()) } returns 1L
        useCase = CreateSleepRecommendationFeedbackUseCase(repository, nowProvider)
    }

    @Test
    fun `ACTED_IN_WINDOW — sleep start inside window computes positive error when sleep is late`() = runTest {
        val bestEstimate = Instant.parse("2026-06-06T10:00:00Z")
        val sleepStart = bestEstimate.plusSeconds(600) // 10 min late

        useCase(
            recommendationId = 1L,
            outcome = RecommendationOutcome.ACTED_IN_WINDOW,
            actualSleepRecordId = 10L,
            sleepStartTime = sleepStart,
            windowBestEstimate = bestEstimate,
        )

        val errorMinutesSlot = slot<Int?>()
        coVerify {
            repository.insertFeedback(1L, 10L, capture(errorMinutesSlot), RecommendationOutcome.ACTED_IN_WINDOW, fixedNow.toEpochMilli())
        }
        assertEquals(10, errorMinutesSlot.captured)
    }

    @Test
    fun `ACTED_IN_WINDOW — sleep start before best estimate gives negative error`() = runTest {
        val bestEstimate = Instant.parse("2026-06-06T10:00:00Z")
        val sleepStart = bestEstimate.minusSeconds(600) // 10 min early

        useCase(
            recommendationId = 2L,
            outcome = RecommendationOutcome.ACTED_IN_WINDOW,
            actualSleepRecordId = 20L,
            sleepStartTime = sleepStart,
            windowBestEstimate = bestEstimate,
        )

        val errorMinutesSlot = slot<Int?>()
        coVerify {
            repository.insertFeedback(2L, 20L, capture(errorMinutesSlot), RecommendationOutcome.ACTED_IN_WINDOW, any())
        }
        assertEquals(-10, errorMinutesSlot.captured)
    }

    @Test
    fun `QUIET_HOURS_SUPPRESSED — null sleep params produce null error_minutes`() = runTest {
        useCase(
            recommendationId = 3L,
            outcome = RecommendationOutcome.QUIET_HOURS_SUPPRESSED,
        )

        val errorMinutesSlot = slot<Int?>()
        coVerify {
            repository.insertFeedback(3L, null, capture(errorMinutesSlot), RecommendationOutcome.QUIET_HOURS_SUPPRESSED, any())
        }
        assertNull(errorMinutesSlot.captured)
    }

    @Test
    fun `SUPERSEDED — null sleep params produce null error_minutes`() = runTest {
        useCase(
            recommendationId = 4L,
            outcome = RecommendationOutcome.SUPERSEDED,
        )

        coVerify { repository.insertFeedback(4L, null, null, RecommendationOutcome.SUPERSEDED, any()) }
    }

    @Test
    fun `uses nowProvider for createdAt`() = runTest {
        val createdAtSlot = slot<Long>()
        coEvery { repository.insertFeedback(any(), any(), any(), any(), capture(createdAtSlot)) } returns 1L

        useCase(recommendationId = 5L, outcome = RecommendationOutcome.SUPERSEDED)

        assertEquals(fixedNow.toEpochMilli(), createdAtSlot.captured)
    }
}
```

- [ ] **Step 7.2: Run tests to see them fail**

```bash
.\gradlew.bat test --tests "*.CreateSleepRecommendationFeedbackUseCaseTest"
```

Expected: compilation error — `CreateSleepRecommendationFeedbackUseCase` does not exist.

- [ ] **Step 7.3: Implement `CreateSleepRecommendationFeedbackUseCase`**

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class CreateSleepRecommendationFeedbackUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke(
        recommendationId: Long,
        outcome: RecommendationOutcome,
        actualSleepRecordId: Long? = null,
        sleepStartTime: Instant? = null,
        windowBestEstimate: Instant? = null,
    ): Long {
        val errorMinutes = if (sleepStartTime != null && windowBestEstimate != null) {
            Duration.between(windowBestEstimate, sleepStartTime).toMinutes().toInt()
        } else {
            null
        }
        return repository.insertFeedback(
            recommendationId = recommendationId,
            actualSleepRecordId = actualSleepRecordId,
            errorMinutes = errorMinutes,
            outcome = outcome,
            createdAt = nowProvider().toEpochMilli(),
        )
    }
}
```

- [ ] **Step 7.4: Run tests**

```bash
.\gradlew.bat test --tests "*.CreateSleepRecommendationFeedbackUseCaseTest"
```

Expected: 5 tests pass.

- [ ] **Step 7.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/CreateSleepRecommendationFeedbackUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/sleep/CreateSleepRecommendationFeedbackUseCaseTest.kt
git commit -m "feat(usecase): CreateSleepRecommendationFeedbackUseCase — feedback record with computed error_minutes [AKA-97]"
```

---

## Task 8: Coordinator integration — lifecycle tracking + sleep-completion feedback

**Files:**
- Modify: `app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt`
- Modify: `app/src/test/java/com/babytracker/manager/PredictiveSleepNotificationCoordinatorTest.kt`

The coordinator gains four new injected dependencies: `SleepRepository`, `PersistSleepRecommendationUseCase`, `UpdateRecommendationLifecycleUseCase`, and `CreateSleepRecommendationFeedbackUseCase`. It tracks in-memory state for the active recommendation (`activeRecommendationId`, `activeAnchorId`, `activeWindowStart`, `activeWindowEnd`, `activeBestEstimate`, `lastSeenCompletedSleepId`) — state is not persisted across restarts, which is acceptable for Phase 4 telemetry.

`reconcile` becomes `suspend` to call the use cases. A second coroutine in `start()` watches `sleepRepository.observeLatestRecord()` to create feedback when a sleep record is completed.

`deriveRecommendationType(bestEstimate: Instant): String` returns `"NAP"` when the window's best estimate is before 18:00 local time, `"NIGHT_SLEEP"` otherwise. This is a heuristic — Phase 3 threading of the actual predicted `SleepType` is deferred.

- [ ] **Step 8.1: Write coordinator lifecycle tests**

Add the following test class (or add to the existing `PredictiveSleepNotificationCoordinatorTest.kt`). The existing test helpers will need their `buildCoordinator` function updated in Step 8.3. First, add these test methods:

```kotlin
@Test
fun `persists recommendation when Window state received and enabled`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 1L
    val bestEstimate = Instant.now().plusSeconds(3600)

    val coordinator = buildCoordinator(
        stateFlow = MutableStateFlow(windowState(bestEstimate)),
        enabledFlow = MutableStateFlow(true),
        leadFlow = MutableStateFlow(15),
        persistRecommendation = persistRecommendation,
    )
    coordinator.start()
    advanceTimeBy(300)

    coVerify(atLeast = 1) { persistRecommendation(any(), any(), any()) }
}

@Test
fun `writes SCHEDULED lifecycle when alarm is set`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 42L
    val updateLifecycle = mockk<UpdateRecommendationLifecycleUseCase>(relaxed = true)
    val bestEstimate = Instant.now().plusSeconds(3600)

    val coordinator = buildCoordinator(
        stateFlow = MutableStateFlow(windowState(bestEstimate)),
        enabledFlow = MutableStateFlow(true),
        leadFlow = MutableStateFlow(15),
        persistRecommendation = persistRecommendation,
        updateLifecycle = updateLifecycle,
    )
    coordinator.start()
    advanceTimeBy(300)

    coVerify { updateLifecycle(42L, RecommendationLifecycle.SCHEDULED) }
}

@Test
fun `writes SUPERSEDED lifecycle when feature is disabled`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 55L
    val updateLifecycle = mockk<UpdateRecommendationLifecycleUseCase>(relaxed = true)
    val bestEstimate = Instant.now().plusSeconds(3600)
    val enabled = MutableStateFlow(true)

    val coordinator = buildCoordinator(
        stateFlow = MutableStateFlow(windowState(bestEstimate)),
        enabledFlow = enabled,
        leadFlow = MutableStateFlow(15),
        persistRecommendation = persistRecommendation,
        updateLifecycle = updateLifecycle,
    )
    coordinator.start()
    advanceTimeBy(300)
    enabled.value = false
    advanceTimeBy(300)

    coVerify { updateLifecycle(55L, RecommendationLifecycle.SUPERSEDED) }
}

@Test
fun `creates QUIET_HOURS_SUPPRESSED feedback when trigger falls in quiet hours`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 77L
    val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
    // bestEstimate is 1 hour from now; lead is 60 min → triggerAt = now; set quiet hours to cover now
    val now = Instant.now()
    val bestEstimate = now.plusSeconds(3600)
    // quiet hours 0:00–23:59 (full day) — any trigger is in quiet hours
    val coordinator = buildCoordinator(
        stateFlow = MutableStateFlow(windowState(bestEstimate)),
        enabledFlow = MutableStateFlow(true),
        leadFlow = MutableStateFlow(60),
        quietStartFlow = MutableStateFlow(0),
        quietEndFlow = MutableStateFlow(1439),
        persistRecommendation = persistRecommendation,
        createFeedback = createFeedback,
    )
    coordinator.start()
    advanceTimeBy(300)

    coVerify { createFeedback(77L, RecommendationOutcome.QUIET_HOURS_SUPPRESSED) }
}
```

@Test
fun `quiet-hours suppressed feedback is created only once per recommendation across repeated reconciles`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 88L
    val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
    val bestEstimate = Instant.now().plusSeconds(3600)
    val stateFlow = MutableStateFlow(windowState(bestEstimate))

    val coordinator = buildCoordinator(
        stateFlow = stateFlow,
        enabledFlow = MutableStateFlow(true),
        leadFlow = MutableStateFlow(60),
        quietStartFlow = MutableStateFlow(0),
        quietEndFlow = MutableStateFlow(1439),
        persistRecommendation = persistRecommendation,
        createFeedback = createFeedback,
    )
    coordinator.start()
    advanceTimeBy(300)
    // Trigger a second reconcile with the same anchor
    stateFlow.value = windowState(bestEstimate.plusSeconds(1))
    advanceTimeBy(300)

    // Despite two reconciles, QUIET_HOURS_SUPPRESSED feedback must be created exactly once.
    coVerify(exactly = 1) { createFeedback(88L, RecommendationOutcome.QUIET_HOURS_SUPPRESSED) }
}

@Test
fun `sleep completion does not create feedback when recommendation was suppressed by quiet hours`() = runTest {
    val persistRecommendation = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
    coEvery { persistRecommendation(any(), any(), any()) } returns 99L
    val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
    val bestEstimate = Instant.now().plusSeconds(3600)
    val completedRecord = mockk<com.babytracker.domain.model.SleepRecord> {
        every { id } returns 5L
        every { isInProgress } returns false
        every { startTime } returns bestEstimate
    }
    val sleepFlow = MutableStateFlow<com.babytracker.domain.model.SleepRecord?>(completedRecord)

    val coordinator = buildCoordinator(
        stateFlow = MutableStateFlow(windowState(bestEstimate)),
        enabledFlow = MutableStateFlow(true),
        leadFlow = MutableStateFlow(60),
        quietStartFlow = MutableStateFlow(0),
        quietEndFlow = MutableStateFlow(1439),
        sleepRepository = mockk(relaxed = true) {
            coEvery { getLatestRecord() } returns mockk { every { id } returns 1L }
            every { observeLatestRecord() } returns sleepFlow
        },
        persistRecommendation = persistRecommendation,
        createFeedback = createFeedback,
    )
    coordinator.start()
    advanceTimeBy(300)

    // QUIET_HOURS_SUPPRESSED feedback is expected, but ACTED_* feedback must NOT be created.
    coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_IN_WINDOW, any(), any(), any()) }
    coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_OUTSIDE, any(), any(), any()) }
}

- [ ] **Step 8.2: Run new tests to see them fail**

```bash
.\gradlew.bat test --tests "*.PredictiveSleepNotificationCoordinatorTest"
```

Expected: compilation errors — new constructor parameters and methods do not exist yet.

- [ ] **Step 8.3: Rewrite `PredictiveSleepNotificationCoordinator.kt`**

Replace the full file:

```kotlin
package com.babytracker.manager

import android.app.NotificationManager
import android.content.Context
import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.usecase.sleep.CreateSleepRecommendationFeedbackUseCase
import com.babytracker.domain.usecase.sleep.PersistSleepRecommendationUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.domain.usecase.sleep.UpdateRecommendationLifecycleUseCase
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveSleepNotificationCoordinator @Inject constructor(
    private val predictSleepWindow: PredictSleepWindowUseCase,
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveSleepScheduler,
    private val sleepRepository: SleepRepository,
    private val sleepRecommendationRepository: SleepRecommendationRepository,
    private val persistRecommendation: PersistSleepRecommendationUseCase,
    private val updateLifecycle: UpdateRecommendationLifecycleUseCase,
    private val createFeedback: CreateSleepRecommendationFeedbackUseCase,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    // In-memory telemetry state — not persisted across process restarts (Phase 4 accepted limitation).
    private var activeRecommendationId: Long? = null
    private var activeAnchorId: Long? = null
    // Only set when the alarm is actually scheduled — prevents spurious ACTED_* feedback
    // for past-trigger or quiet-hours-suppressed recommendations.
    private var scheduledWindowStart: Instant? = null
    private var scheduledWindowEnd: Instant? = null
    private var scheduledBestEstimate: Instant? = null
    // Guards against duplicate QUIET_HOURS_SUPPRESSED feedback rows across repeated reconcile calls.
    private var quietHoursFeedbackCreated: Boolean = false
    private var lastSeenCompletedSleepId: Long? = null

    @OptIn(FlowPreview::class)
    fun start() {
        // Reconstruct scheduled-window state from Room to survive process restarts.
        // Must run before the collect coroutines start so state is ready for the first emission.
        applicationScope.launch {
            val latestAnchorId = sleepRepository.getLatestRecord()?.id
            val scheduled = sleepRecommendationRepository.getLatestScheduledRecommendation()
            if (scheduled != null && latestAnchorId == scheduled.anchorSleepId) {
                activeRecommendationId = scheduled.id
                activeAnchorId = scheduled.anchorSleepId
                scheduledWindowStart = Instant.ofEpochMilli(scheduled.windowStartMs)
                scheduledWindowEnd = Instant.ofEpochMilli(scheduled.windowEndMs)
                scheduledBestEstimate = Instant.ofEpochMilli(scheduled.bestEstimateMs)
            }
        }

        applicationScope.launch {
            combine(
                predictSleepWindow(),
                settingsRepository.getPredictiveSleepEnabled(),
                settingsRepository.getPredictiveSleepLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
            ) { state, enabled, lead, quietStart, quietEnd ->
                ReconcileParams(state, enabled, lead, quietStart, quietEnd)
            }
                .debounce(DEBOUNCE_MS)
                .collect { params ->
                    reconcile(
                        params.state,
                        params.enabled,
                        params.leadMinutes,
                        params.quietStartMinute,
                        params.quietEndMinute,
                    )
                }
        }

        applicationScope.launch {
            sleepRepository.observeLatestRecord().collect { record ->
                val completed = record?.takeIf { !it.isInProgress } ?: return@collect
                if (completed.id == lastSeenCompletedSleepId) return@collect
                lastSeenCompletedSleepId = completed.id

                val recId = activeRecommendationId ?: return@collect
                // scheduledWindowStart is only non-null when an alarm was actually set.
                // Past-trigger and quiet-hours paths leave it null, so no feedback is created.
                val winStart = scheduledWindowStart ?: return@collect
                val winEnd = scheduledWindowEnd ?: return@collect
                val bestEst = scheduledBestEstimate ?: return@collect

                val outcome = if (completed.startTime in winStart..winEnd) {
                    RecommendationOutcome.ACTED_IN_WINDOW
                } else {
                    RecommendationOutcome.ACTED_OUTSIDE
                }
                createFeedback(
                    recommendationId = recId,
                    outcome = outcome,
                    actualSleepRecordId = completed.id,
                    sleepStartTime = completed.startTime,
                    windowBestEstimate = bestEst,
                )
            }
        }
    }

    private suspend fun reconcile(
        state: SleepPredictionState,
        enabled: Boolean,
        leadMinutes: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ) {
        val window = (state as? SleepPredictionState.Window)?.window
        if (!enabled || window == null) {
            supersedeCurrent()
            cancelAll()
            return
        }

        val anchorId = sleepRepository.getLatestRecord()?.id ?: run {
            supersedeCurrent()
            cancelAll()
            return
        }

        if (anchorId != activeAnchorId) {
            supersedeCurrent()
            quietHoursFeedbackCreated = false
        }

        val recommendationType = deriveRecommendationType(window.bestEstimate)
        val recId = persistRecommendation(anchorId, window, recommendationType)

        activeRecommendationId = recId
        activeAnchorId = anchorId

        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            // Past-trigger: alarm would fire immediately; don't set scheduled window state so
            // sleep-completion collector ignores this recommendation.
            cancelAll()
            return
        }
        if (isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, ZoneId.systemDefault())) {
            // Guard: only create QUIET_HOURS_SUPPRESSED once per recommendation lifetime.
            if (!quietHoursFeedbackCreated) {
                createFeedback(recId, RecommendationOutcome.QUIET_HOURS_SUPPRESSED)
                quietHoursFeedbackCreated = true
            }
            cancelAll()
            return
        }

        // Alarm will be scheduled — now record the window state for sleep-completion feedback.
        scheduledWindowStart = window.windowStart
        scheduledWindowEnd = window.windowEnd
        scheduledBestEstimate = window.bestEstimate
        quietHoursFeedbackCreated = false

        scheduler.schedulePredictiveReminderAt(triggerAt, window.bestEstimate)
        updateLifecycle(recId, RecommendationLifecycle.SCHEDULED)
    }

    private suspend fun supersedeCurrent() {
        val id = activeRecommendationId ?: return
        updateLifecycle(id, RecommendationLifecycle.SUPERSEDED)
        createFeedback(id, RecommendationOutcome.SUPERSEDED)
        activeRecommendationId = null
        activeAnchorId = null
        scheduledWindowStart = null
        scheduledWindowEnd = null
        scheduledBestEstimate = null
        quietHoursFeedbackCreated = false
    }

    private fun cancelAll() {
        scheduler.cancelPredictiveReminder()
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
    }

    private fun deriveRecommendationType(bestEstimate: Instant): String =
        if (bestEstimate.atZone(ZoneId.systemDefault()).hour < 18) "NAP" else "NIGHT_SLEEP"

    private data class ReconcileParams(
        val state: SleepPredictionState,
        val enabled: Boolean,
        val leadMinutes: Int,
        val quietStartMinute: Int,
        val quietEndMinute: Int,
    )

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
```

- [ ] **Step 8.4: Update `buildCoordinator` helper in the test file**

In `PredictiveSleepNotificationCoordinatorTest.kt`, find the `buildCoordinator` private helper and update its signature to accept the new dependencies with relaxed-mock defaults:

```kotlin
private fun TestScope.buildCoordinator(
    scheduler: PredictiveSleepScheduler = mockk(relaxed = true),
    stateFlow: MutableStateFlow<SleepPredictionState>,
    enabledFlow: MutableStateFlow<Boolean>,
    leadFlow: MutableStateFlow<Int>,
    quietStartFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    quietEndFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    sleepRepository: SleepRepository = mockk(relaxed = true) {
        coEvery { getLatestRecord() } returns null
        every { observeLatestRecord() } returns MutableStateFlow(null)
    },
    sleepRecommendationRepository: SleepRecommendationRepository = mockk(relaxed = true) {
        coEvery { getLatestScheduledRecommendation() } returns null
    },
    persistRecommendation: PersistSleepRecommendationUseCase = mockk(relaxed = true) {
        coEvery { invoke(any(), any(), any()) } returns 1L
    },
    updateLifecycle: UpdateRecommendationLifecycleUseCase = mockk(relaxed = true),
    createFeedback: CreateSleepRecommendationFeedbackUseCase = mockk(relaxed = true),
): PredictiveSleepNotificationCoordinator {
    val predictUseCase = mockk<PredictSleepWindowUseCase> {
        every { invoke() } returns stateFlow
    }
    val settings = mockk<SettingsRepository> {
        every { getPredictiveSleepEnabled() } returns enabledFlow
        every { getPredictiveSleepLeadMinutes() } returns leadFlow
        every { getQuietHoursStartMinute() } returns quietStartFlow
        every { getQuietHoursEndMinute() } returns quietEndFlow
    }
    val ctx = mockk<Context>(relaxed = true) {
        every { getSystemService(NotificationManager::class.java) } returns mockk(relaxed = true)
    }
    return PredictiveSleepNotificationCoordinator(
        predictSleepWindow = predictUseCase,
        settingsRepository = settings,
        scheduler = scheduler,
        sleepRepository = sleepRepository,
        sleepRecommendationRepository = sleepRecommendationRepository,
        persistRecommendation = persistRecommendation,
        updateLifecycle = updateLifecycle,
        createFeedback = createFeedback,
        context = ctx,
        applicationScope = backgroundScope,
    )
}
```

Add the missing imports at the top of the test file:

```kotlin
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.CreateSleepRecommendationFeedbackUseCase
import com.babytracker.domain.usecase.sleep.PersistSleepRecommendationUseCase
import com.babytracker.domain.usecase.sleep.UpdateRecommendationLifecycleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
```

- [ ] **Step 8.5: Run coordinator tests**

```bash
.\gradlew.bat test --tests "*.PredictiveSleepNotificationCoordinatorTest"
```

Expected: all existing + new tests pass.

- [ ] **Step 8.6: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt \
        app/src/test/java/com/babytracker/manager/PredictiveSleepNotificationCoordinatorTest.kt
git commit -m "feat(sleep): coordinator lifecycle tracking + sleep-completion feedback [AKA-97]"
```

---

## Task 9: PredictiveSleepReceiver — write FIRED lifecycle on alarm fire

**Files:**
- Modify: `app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt`

The receiver injects `UpdateRecommendationLifecycleUseCase`. On a successful notification show (after the stale/quiet-hours checks pass), it calls `updateLifecycle(recommendationId, FIRED)`. The recommendation ID is passed via the intent extra `EXTRA_RECOMMENDATION_ID`. The coordinator sets this extra in `PredictiveSleepSchedulerImpl.buildPendingIntent()`.

- [ ] **Step 9.1: Add `EXTRA_RECOMMENDATION_ID` to `PredictiveSleepReceiver.Companion`**

In `PredictiveSleepReceiver.kt`, add to the `companion object`:

```kotlin
const val EXTRA_RECOMMENDATION_ID = "recommendation_id"
```

- [ ] **Step 9.2: Update `PredictiveSleepSchedulerImpl.buildPendingIntent` to pass the recommendation ID**

In `PredictiveSleepScheduler.kt`, update `PredictiveSleepScheduler` interface — add `recommendationId` parameter:

```kotlin
interface PredictiveSleepScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, bestEstimate: Instant, recommendationId: Long)
    fun cancelPredictiveReminder()
}
```

Update `PredictiveSleepSchedulerImpl.schedulePredictiveReminderAt`:

```kotlin
override fun schedulePredictiveReminderAt(
    triggerTime: Instant,
    bestEstimate: Instant,
    recommendationId: Long,
) {
    val pi = buildPendingIntent(bestEstimate, recommendationId)
    // ... rest unchanged
}
```

Update `buildPendingIntent` to accept and embed `recommendationId`:

```kotlin
private fun buildPendingIntent(bestEstimate: Instant, recommendationId: Long): PendingIntent {
    val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
        action = PredictiveSleepReceiver.ACTION_FIRE
        putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, bestEstimate.toEpochMilli())
        putExtra(PredictiveSleepReceiver.EXTRA_RECOMMENDATION_ID, recommendationId)
    }
    return PendingIntent.getBroadcast(
        context,
        PredictiveSleepReceiver.REQUEST_CODE_PREDICTIVE_SLEEP,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

Update `cancelPredictiveReminder` — it calls `buildPendingIntent` with `recommendationId = 0L` (only the action + request code matter for cancellation):

```kotlin
override fun cancelPredictiveReminder() {
    alarmManager.cancel(buildPendingIntent(Instant.EPOCH, 0L))
}
```

Update the coordinator's call to `scheduler.schedulePredictiveReminderAt` to pass the recommendation ID:

```kotlin
scheduler.schedulePredictiveReminderAt(triggerAt, window.bestEstimate, recId)
```

(In `PredictiveSleepNotificationCoordinator.kt`, `recId` is already in scope in `reconcile`.)

- [ ] **Step 9.3: Inject `UpdateRecommendationLifecycleUseCase` into `PredictiveSleepReceiver`**

Replace the full `PredictiveSleepReceiver.kt`:

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.UpdateRecommendationLifecycleUseCase
import com.babytracker.util.showPredictiveSleepReminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveSleepReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var updateRecommendationLifecycle: UpdateRecommendationLifecycleUseCase

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val bestEstimateMs = intent.getLongExtra(EXTRA_BEST_ESTIMATE_MS, 0L)
        if (bestEstimateMs <= 0L) {
            Log.w(TAG, "Missing bestEstimate; dropping fire")
            return
        }
        val recommendationId = intent.getLongExtra(EXTRA_RECOMMENDATION_ID, 0L)
        val nowMs = System.currentTimeMillis()
        val staleAfterMs = bestEstimateMs + MAX_STALE_MINUTES * 60_000L
        if (nowMs > staleAfterMs) {
            Log.i(TAG, "Prediction stale by ${(nowMs - bestEstimateMs) / 60_000L}m; dropping fire")
            return
        }
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val enabled = settingsRepository.getPredictiveSleepEnabled().first()
                if (!enabled) {
                    Log.d(TAG, "Feature disabled at fire time; dropping")
                    return@launch
                }
                val quietStart = settingsRepository.getQuietHoursStartMinute().first()
                val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
                if (isInsideQuietHours(nowMs, quietStart, quietEnd)) {
                    Log.d(TAG, "Fire time inside quiet hours; suppressing notification")
                    return@launch
                }
                showPredictiveSleepReminder(context = context, bestEstimateMs = bestEstimateMs)
                if (recommendationId > 0L) {
                    runCatching {
                        updateRecommendationLifecycle(recommendationId, RecommendationLifecycle.FIRED)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_SLEEP_FIRE"
        const val EXTRA_BEST_ESTIMATE_MS = "best_estimate_ms"
        const val EXTRA_RECOMMENDATION_ID = "recommendation_id"
        const val REQUEST_CODE_PREDICTIVE_SLEEP = 1005
        internal const val MAX_STALE_MINUTES = 20L
        private const val TAG = "PredictiveSleepRx"

        fun isInsideQuietHours(nowMs: Long, quietStartMinute: Int, quietEndMinute: Int): Boolean {
            if (quietStartMinute == quietEndMinute) return false
            val minuteOfDay = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .let { it.hour * 60 + it.minute }
            return if (quietStartMinute < quietEndMinute) {
                minuteOfDay in quietStartMinute until quietEndMinute
            } else {
                minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
            }
        }
    }
}
```

- [ ] **Step 9.4: Build check**

```bash
.\gradlew.bat assembleDebug
```

Expected: builds successfully — Hilt injects `UpdateRecommendationLifecycleUseCase` into the receiver.

- [ ] **Step 9.5: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt \
        app/src/main/java/com/babytracker/manager/PredictiveSleepScheduler.kt \
        app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt
git commit -m "feat(sleep): receiver writes FIRED lifecycle; scheduler passes recommendationId in intent [AKA-97]"
```

---

## Task 10: ktlint + detekt + full test suite

- [ ] **Step 10.1: Auto-fix formatting**

```bash
.\gradlew.bat ktlintFormat
```

- [ ] **Step 10.2: Static analysis**

```bash
.\gradlew.bat detekt
```

Fix any violations. Each fix gets its own commit: `fix(detekt): fix <RuleName> violations`.

- [ ] **Step 10.3: Run full test suite**

```bash
.\gradlew.bat test
```

Expected: all tests pass.

- [ ] **Step 10.4: Full build**

```bash
.\gradlew.bat build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10.5: Commit style fixes (if any)**

```bash
git add -p
git commit -m "style: ktlint formatting fixes [AKA-97]"
```

---

## Acceptance Criteria Checklist

- [ ] `sleep_recommendations` table created in `MIGRATION_6_7` with UNIQUE constraint on `(anchor_sleep_id, recommendation_type, algorithm_version)` and all three indices
- [ ] `sleep_recommendation_feedback` table created in `MIGRATION_6_7` with indices on `recommendation_id` and `actual_sleep_record_id`
- [ ] Multiple Flow recomputations for the same wake anchor deduplicate to one `sleep_recommendations` row (`OnConflictStrategy.IGNORE` + fallback SELECT)
- [ ] `PersistSleepRecommendationUseCase` returns the stable row ID whether the row was newly inserted or already existed
- [ ] Recommendation is persisted with `lifecycle = GENERATED` on first insert; not re-inserted on subsequent reconcile calls for the same anchor+type+version
- [ ] `UpdateRecommendationLifecycleUseCase` transitions lifecycle to SCHEDULED when alarm is set
- [ ] `UpdateRecommendationLifecycleUseCase` transitions lifecycle to SUPERSEDED when a new anchor replaces the old one or the feature is disabled
- [ ] `UpdateRecommendationLifecycleUseCase` transitions lifecycle to FIRED when `PredictiveSleepReceiver` fires the notification
- [ ] `EXTRA_RECOMMENDATION_ID` is embedded in the AlarmManager pending intent; receiver reads it and calls `updateRecommendationLifecycle`
- [ ] A new completed sleep record triggers `CreateSleepRecommendationFeedbackUseCase` with `ACTED_IN_WINDOW` if `sleep.startTime ∈ [windowStart, windowEnd]`, otherwise `ACTED_OUTSIDE`
- [ ] `error_minutes` is the signed difference (`sleepStartTime - bestEstimate`) in minutes: negative = early, positive = late
- [ ] `QUIET_HOURS_SUPPRESSED` feedback is created when the trigger falls inside quiet hours
- [ ] `SUPERSEDED` feedback is created when `supersedeCurrent()` is called
- [ ] `QUIET_HOURS_SUPPRESSED` feedback is created **at most once** per recommendation lifetime, even when quiet-hours reconcile fires multiple times for the same anchor+type+version
- [ ] A sleep completion that occurs while the recommendation was **not** scheduled (past-trigger or quiet-hours-suppressed) does **not** generate ACTED_IN_WINDOW or ACTED_OUTSIDE feedback — `scheduledWindowStart` guards this
- [ ] On coordinator restart, `getLatestScheduledRecommendation()` is called; if the stored anchor matches the current latest sleep record, the in-memory scheduled-window state is reconstructed so sleep-completion feedback survives process death
- [ ] Feedback `insertFeedback` uses `OnConflictStrategy.IGNORE`; duplicate calls for the same `(recommendation_id, outcome)` are no-ops (idempotent across restarts and re-emissions)
- [ ] No automatic bias correction logic is added anywhere
- [ ] All unit tests pass; `./gradlew build` succeeds
