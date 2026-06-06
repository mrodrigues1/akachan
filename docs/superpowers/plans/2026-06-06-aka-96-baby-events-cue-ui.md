# AKA-96: baby_events Table + BabyEventType Enum + Home Quick-Tap Cue Row

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE: AKA-96**

**Goal:** Add the `baby_events` Room table and `BabyEventType` enum for parental cue logging, wire the event flow into `PredictSleepWindowUseCase` so cue inserts auto-trigger prediction recomputation, lower confidence during active SICK/TEETHING/TRAVEL disruptions, and add the Home quick-tap cue row below the sleep prediction card.

**Architecture:** `MIGRATION_5_6` creates `baby_events` schema-only. `BabyEventDao` returns a `Flow<List<BabyEventEntity>>` so Room emits on every insert — the existing `combine` in `PredictSleepWindowUseCase` gains a fourth source and recomputes automatically. Disruption detection is a pure function of the event list: any SICK/TEETHING/TRAVEL event within `DISRUPTION_LOOKBACK_HOURS` sets `SleepFeatures.hasActiveDisruption = true`, which lowers the computed `Confidence` by one level in `SleepWindowPredictor.buildWindow()`. Cues never shift candidate generation (confirmation-loop risk). The `CueQuickTapRow` composable is added directly below `SleepPredictionCard` in `HomeScreen.kt`; taps call `HomeViewModel.onCueTapped()` which fire-and-forgets `LogBabyEventUseCase`.

**Tech Stack:** Room 2.8.4, Hilt 2.59, Kotlin Coroutines/Flow 1.9.0, Jetpack Compose BOM 2026.03.00, Material 3, JUnit 5, MockK 1.13.13, Turbine 1.2.0.

**Branch:** `feat/aka-96-baby-events-cue-ui`

---

## File Map

### New files

| File | Responsibility |
|------|---------------|
| `domain/model/BabyEventType.kt` | Enum `SLEEPY_CUE, HUNGER_CUE, FUSSY, SICK, TEETHING, TRAVEL`; `isDisruption` computed property |
| `domain/model/BabyEvent.kt` | Framework-free domain model |
| `data/local/entity/BabyEventEntity.kt` | Room entity for `baby_events`; `toDomain()`/`toEntity()` extensions |
| `data/local/dao/BabyEventDao.kt` | `insertEvent()` + `getAllEvents(): Flow<List<BabyEventEntity>>` |
| `domain/repository/BabyEventRepository.kt` | Repository interface |
| `data/repository/BabyEventRepositoryImpl.kt` | Repository impl wrapping `BabyEventDao` |
| `domain/usecase/baby/LogBabyEventUseCase.kt` | Single-responsibility cue-logging use case |
| `src/test/.../usecase/baby/LogBabyEventUseCaseTest.kt` | Unit tests for logging |

### Modified files

| File | Change |
|------|--------|
| `data/local/BabyTrackerDatabase.kt` | version 6, add `BabyEventEntity`, `MIGRATION_5_6` |
| `di/DatabaseModule.kt` | Add `MIGRATION_5_6`, `provideBabyEventDao()` |
| `di/RepositoryModule.kt` | Add `bindBabyEventRepository()` |
| `domain/model/SleepPredictionTuning.kt` | Add `DISRUPTION_LOOKBACK_HOURS`, update `ALGORITHM_VERSION` |
| `domain/sleep/feature/SleepFeatures.kt` | Add `hasActiveDisruption: Boolean = false` |
| `domain/usecase/sleep/PredictSleepWindowUseCase.kt` | Add `BabyEventRepository` dep; include event Flow in combine; compute disruption |
| `domain/sleep/eval/SleepWindowPredictor.kt` | Lower confidence by one level when `hasActiveDisruption` |
| `ui/home/HomeViewModel.kt` | Inject `LogBabyEventUseCase`; add `onCueTapped(BabyEventType)` |
| `ui/home/HomeScreen.kt` | Add `CueQuickTapRow` composable + `val emoji`/`val label` extension properties; wire below `SleepPredictionCard` |
| `src/test/.../eval/SleepWindowPredictorTest.kt` | Add disruption confidence-lowering tests |

---

## Task 1: BabyEventType enum + BabyEvent domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/BabyEventType.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/BabyEvent.kt`

- [ ] **Step 1.1: Create `BabyEventType.kt`**

```kotlin
// domain/model/BabyEventType.kt
package com.babytracker.domain.model

enum class BabyEventType {
    SLEEPY_CUE,
    HUNGER_CUE,
    FUSSY,
    SICK,
    TEETHING,
    TRAVEL;

    val isDisruption: Boolean
        get() = this == SICK || this == TEETHING || this == TRAVEL
}
```

Enum names are stable — they are persisted to Room as `BabyEventType.name`. The UI `emoji` and `label` presentation properties are defined in `HomeScreen.kt` as extension properties (not here), to keep the domain free of UI concerns.

- [ ] **Step 1.2: Create `BabyEvent.kt`**

```kotlin
// domain/model/BabyEvent.kt
package com.babytracker.domain.model

data class BabyEvent(
    val id: Long = 0,
    val timestampMs: Long,
    val type: BabyEventType,
    val intensity: Int? = null,
    val notes: String? = null,
    val createdAtMs: Long,
)
```

- [ ] **Step 1.3: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/BabyEventType.kt \
        app/src/main/java/com/babytracker/domain/model/BabyEvent.kt
git commit -m "feat(db): add BabyEventType enum and BabyEvent domain model [AKA-96]"
```

---

## Task 2: BabyEventEntity + BabyEventDao

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/BabyEventEntity.kt`
- Create: `app/src/main/java/com/babytracker/data/local/dao/BabyEventDao.kt`

- [ ] **Step 2.1: Create `BabyEventEntity.kt`**

```kotlin
// data/local/entity/BabyEventEntity.kt
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType

@Entity(
    tableName = "baby_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["event_type"]),
    ],
)
data class BabyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "intensity") val intensity: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun BabyEventEntity.toDomain(): BabyEvent = BabyEvent(
    id = id,
    timestampMs = timestamp,
    type = BabyEventType.entries.firstOrNull { it.name == eventType } ?: BabyEventType.FUSSY,
    intensity = intensity,
    notes = notes,
    createdAtMs = createdAt,
)

fun BabyEvent.toEntity(): BabyEventEntity = BabyEventEntity(
    id = id,
    timestamp = timestampMs,
    eventType = type.name,
    intensity = intensity,
    notes = notes,
    createdAt = createdAtMs,
)
```

The safe fallback in `toDomain()` (`?: BabyEventType.FUSSY`) prevents crashes from unknown future enum values in legacy DB rows. Using `BabyEventType.entries` (Kotlin 1.9+) rather than deprecated `values()`.

- [ ] **Step 2.2: Create `BabyEventDao.kt`**

```kotlin
// data/local/dao/BabyEventDao.kt
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.BabyEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BabyEventEntity)

    @Query("SELECT * FROM baby_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<BabyEventEntity>>
}
```

`getAllEvents()` returns a hot `Flow` backed by Room — any `insertEvent()` call causes Room to re-emit. `PredictSleepWindowUseCase` subscribes to this Flow so prediction recomputes on every cue tap with zero explicit notification.

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/entity/BabyEventEntity.kt \
        app/src/main/java/com/babytracker/data/local/dao/BabyEventDao.kt
git commit -m "feat(db): add BabyEventEntity and BabyEventDao for baby_events table [AKA-96]"
```

---

## Task 3: Room v6 migration + BabyTrackerDatabase + DatabaseModule

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

- [ ] **Step 3.1: Add `MIGRATION_5_6` in `BabyTrackerDatabase.kt`**

Add at the bottom of `BabyTrackerDatabase.kt`, after `MIGRATION_4_5`:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS baby_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                intensity INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_baby_events_timestamp ON baby_events(timestamp)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_baby_events_event_type ON baby_events(event_type)"
        )
    }
}
```

- [ ] **Step 3.2: Update `@Database` annotation (add entity, bump version)**

Replace the existing `@Database` annotation block:

```kotlin
@Database(
    entities = [
        BreastfeedingEntity::class,
        SleepEntity::class,
        PumpingEntity::class,
        MilkBagEntity::class,
        BabyProfileEntity::class,
        BabyEventEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
```

Add the abstract DAO accessor to the `BabyTrackerDatabase` class body:

```kotlin
abstract fun babyEventDao(): BabyEventDao
```

Add the import at the top of the file:

```kotlin
import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.entity.BabyEventEntity
```

- [ ] **Step 3.3: Update `DatabaseModule` — add migration and DAO provider**

Replace the `provideDatabase(...)` method's `.addMigrations(...)` call:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

Add these imports at the top of `DatabaseModule.kt`:

```kotlin
import com.babytracker.data.local.MIGRATION_5_6
import com.babytracker.data.local.dao.BabyEventDao
```

Add the DAO provider after `provideBabyProfileDao`:

```kotlin
@Provides
fun provideBabyEventDao(database: BabyTrackerDatabase): BabyEventDao = database.babyEventDao()
```

- [ ] **Step 3.4: Build check (schema export will update)**

```bash
./gradlew assembleDebug
```

Expected: builds successfully. Room generates `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/6.json`.

- [ ] **Step 3.5: Commit (include generated schema)**

```bash
git add app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt \
        app/src/main/java/com/babytracker/di/DatabaseModule.kt \
        "app/schemas/com.babytracker.data.local.BabyTrackerDatabase/6.json"
git commit -m "feat(db): Room v6 migration — baby_events table with timestamp and event_type indices [AKA-96]"
```

---

## Task 4: BabyEventRepository interface + impl + RepositoryModule wiring

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/BabyEventRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/BabyEventRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 4.1: Create `BabyEventRepository` interface**

```kotlin
// domain/repository/BabyEventRepository.kt
package com.babytracker.domain.repository

import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import kotlinx.coroutines.flow.Flow

interface BabyEventRepository {
    suspend fun logEvent(event: BabyEvent)
    fun getAllEvents(): Flow<List<BabyEvent>>
}
```

- [ ] **Step 4.2: Create `BabyEventRepositoryImpl`**

```kotlin
// data/repository/BabyEventRepositoryImpl.kt
package com.babytracker.data.repository

import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.repository.BabyEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyEventRepositoryImpl @Inject constructor(
    private val dao: BabyEventDao,
) : BabyEventRepository {

    override suspend fun logEvent(event: BabyEvent) = dao.insertEvent(event.toEntity())

    override fun getAllEvents(): Flow<List<BabyEvent>> =
        dao.getAllEvents().map { entities -> entities.map { it.toDomain() } }
}
```

- [ ] **Step 4.3: Bind in `RepositoryModule`**

Add inside the `abstract class RepositoryModule` body:

```kotlin
@Binds
@Singleton
abstract fun bindBabyEventRepository(impl: BabyEventRepositoryImpl): BabyEventRepository
```

Add imports:

```kotlin
import com.babytracker.data.repository.BabyEventRepositoryImpl
import com.babytracker.domain.repository.BabyEventRepository
```

- [ ] **Step 4.4: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds successfully — Hilt can resolve `BabyEventRepository`.

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/BabyEventRepository.kt \
        app/src/main/java/com/babytracker/data/repository/BabyEventRepositoryImpl.kt \
        app/src/main/java/com/babytracker/di/RepositoryModule.kt
git commit -m "feat(repository): BabyEventRepository interface and impl [AKA-96]"
```

---

## Task 5: LogBabyEventUseCase + tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/baby/LogBabyEventUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/baby/LogBabyEventUseCaseTest.kt`

- [ ] **Step 5.1: Write failing tests**

```kotlin
// src/test/.../usecase/baby/LogBabyEventUseCaseTest.kt
package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.repository.BabyEventRepository
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

class LogBabyEventUseCaseTest {

    private lateinit var repository: BabyEventRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: LogBabyEventUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = LogBabyEventUseCase(repository, nowProvider)
    }

    @Test
    fun `invoke logs SICK event to repository with correct type and timestamp`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.SICK)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        val saved = slot.captured
        assertEquals(BabyEventType.SICK, saved.type)
        assertEquals(fixedNow.toEpochMilli(), saved.timestampMs)
        assertEquals(fixedNow.toEpochMilli(), saved.createdAtMs)
        assertNull(saved.intensity)
        assertNull(saved.notes)
    }

    @Test
    fun `invoke logs SLEEPY_CUE event`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.SLEEPY_CUE)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        assertEquals(BabyEventType.SLEEPY_CUE, slot.captured.type)
    }

    @Test
    fun `invoke logs TEETHING event`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.TEETHING)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        assertEquals(BabyEventType.TEETHING, slot.captured.type)
    }

    @Test
    fun `BabyEventType isDisruption true for SICK TEETHING TRAVEL`() {
        assert(BabyEventType.SICK.isDisruption)
        assert(BabyEventType.TEETHING.isDisruption)
        assert(BabyEventType.TRAVEL.isDisruption)
    }

    @Test
    fun `BabyEventType isDisruption false for SLEEPY_CUE HUNGER_CUE FUSSY`() {
        assert(!BabyEventType.SLEEPY_CUE.isDisruption)
        assert(!BabyEventType.HUNGER_CUE.isDisruption)
        assert(!BabyEventType.FUSSY.isDisruption)
    }
}
```

- [ ] **Step 5.2: Run tests to see them fail**

```bash
./gradlew test --tests "*.LogBabyEventUseCaseTest"
```

Expected: compilation error — `LogBabyEventUseCase` doesn't exist yet.

- [ ] **Step 5.3: Implement `LogBabyEventUseCase`**

```kotlin
// domain/usecase/baby/LogBabyEventUseCase.kt
package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.repository.BabyEventRepository
import java.time.Instant
import javax.inject.Inject

class LogBabyEventUseCase @Inject constructor(
    private val repository: BabyEventRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke(type: BabyEventType) {
        val nowMs = nowProvider().toEpochMilli()
        repository.logEvent(
            BabyEvent(
                timestampMs = nowMs,
                type = type,
                createdAtMs = nowMs,
            )
        )
    }
}
```

- [ ] **Step 5.4: Run tests**

```bash
./gradlew test --tests "*.LogBabyEventUseCaseTest"
```

Expected: 5 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/baby/LogBabyEventUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/baby/LogBabyEventUseCaseTest.kt
git commit -m "feat(usecase): LogBabyEventUseCase — logs cue/disruption events to baby_events [AKA-96]"
```

---

## Task 6: SleepFeatures.hasActiveDisruption + PredictSleepWindowUseCase integration + SleepWindowPredictor confidence lowering

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt`
- Modify: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

- [ ] **Step 6.1: Write failing tests in `SleepWindowPredictorTest`**

Add the following tests to `SleepWindowPredictorTest`. The `features()` helper will gain a `hasActiveDisruption` parameter in step 6.3; for now just add the test methods (they will fail to compile until then):

```kotlin
@Test
fun `active disruption lowers HIGH confidence to MEDIUM`() {
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = true)
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
        ageInWeeks,
        baseNow,
    )

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.MEDIUM, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `active disruption lowers MEDIUM confidence to LOW`() {
    val quality = sufficientQuality(completedCount = 7) // qualityC = 7/14 = 0.5 → MEDIUM
        .copy(hasQualifiedTimezoneProvenance = false)
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
        ageInWeeks,
        baseNow,
    )

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `active disruption keeps LOW confidence at LOW`() {
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.MIN_COMPLETED_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = false)
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
        ageInWeeks,
        baseNow,
    )

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `no disruption leaves confidence unchanged`() {
    val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        .copy(hasQualifiedTimezoneProvenance = true)
    val metrics = sufficientMetrics(
        lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
        medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
    )
    val result = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = false),
        ageInWeeks,
        baseNow,
    )

    assertInstanceOf(SleepPredictionState.Window::class.java, result)
    assertEquals(Confidence.HIGH, (result as SleepPredictionState.Window).window.confidence)
}

@Test
fun `cue does not shift window best-estimate time — disruption is annotation only`() {
    val quality = sufficientQuality(completedCount = 10)
    val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
    val medianMillis = Duration.ofMinutes(90).toMillis()
    val metrics = sufficientMetrics(
        lastWakeMillis = lastWakeMillis,
        medianIntervalMillis = medianMillis,
    )
    val withoutDisruption = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = false),
        ageInWeeks,
        baseNow,
    ) as SleepPredictionState.Window
    val withDisruption = SleepWindowPredictor.predict(
        features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
        ageInWeeks,
        baseNow,
    ) as SleepPredictionState.Window

    // bestEstimate and window bounds must not change — only confidence changes.
    assertEquals(withoutDisruption.window.bestEstimate, withDisruption.window.bestEstimate)
    assertEquals(withoutDisruption.window.windowStart, withDisruption.window.windowStart)
    assertEquals(withoutDisruption.window.windowEnd, withDisruption.window.windowEnd)
}
```

- [ ] **Step 6.2: Run tests to verify they fail**

```bash
./gradlew test --tests "*.SleepWindowPredictorTest"
```

Expected: compile error — `.copy(hasActiveDisruption = ...)` doesn't exist on `SleepFeatures` yet.

- [ ] **Step 6.3: Add `hasActiveDisruption` to `SleepFeatures`**

```kotlin
// domain/sleep/feature/SleepFeatures.kt
package com.babytracker.domain.sleep.feature

data class SleepFeatures(
    val validIntervals: List<SleepInterval>,
    val feedIntervals: List<BreastfeedInterval>,
    val metrics: SleepMetrics,
    val quality: EvidenceQuality,
    val currentMinuteOfDay: Int? = null,
    val hasActiveDisruption: Boolean = false,
)
```

Default `false` keeps all existing construction sites — `SleepFeatureExtractor`, eval harness fixtures, and tests — compile-clean without changes.

- [ ] **Step 6.4: Add tuning constant and update `ALGORITHM_VERSION` in `SleepPredictionTuning`**

Add after `ALGORITHM_VERSION`:

```kotlin
const val DISRUPTION_LOOKBACK_HOURS = 48L
```

Update `ALGORITHM_VERSION`:

```kotlin
const val ALGORITHM_VERSION = "sleep-pred-phase3-cue-disruption-1"
```

Find and update any existing test asserting the old version string. Search with:

```bash
grep -r "sleep-pred-phase3-tz-provenance-1" app/src/test/
```

Change each occurrence to `"sleep-pred-phase3-cue-disruption-1"`.

- [ ] **Step 6.5: Lower confidence in `SleepWindowPredictor.buildWindow()`**

In `SleepWindowPredictor.kt`, replace the `val confidence = when { ... }` block inside `buildWindow()`:

```kotlin
val rawConfidence = when {
    qualityC >= SleepPredictionTuning.HIGH_CONFIDENCE_QUALITY_C_THRESHOLD &&
        quality.hasQualifiedTimezoneProvenance -> Confidence.HIGH
    qualityC >= 0.5f -> Confidence.MEDIUM
    else -> Confidence.LOW
}
val confidence = if (features.hasActiveDisruption) rawConfidence.loweredByOne() else rawConfidence
```

Add the private extension function at the bottom of `SleepWindowPredictor.kt`, before the `private const val MINUTES_PER_DAY`:

```kotlin
private fun Confidence.loweredByOne(): Confidence = when (this) {
    Confidence.HIGH -> Confidence.MEDIUM
    Confidence.MEDIUM -> Confidence.LOW
    Confidence.LOW -> Confidence.LOW
}
```

- [ ] **Step 6.6: Run predictor tests**

```bash
./gradlew test --tests "*.SleepWindowPredictorTest"
```

Expected: all tests pass, including the 5 new disruption tests.

- [ ] **Step 6.7: Integrate `BabyEventRepository` into `PredictSleepWindowUseCase`**

Replace the full `PredictSleepWindowUseCase.kt`:

```kotlin
// domain/usecase/sleep/PredictSleepWindowUseCase.kt
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.BabyEventRepository
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.eval.SleepDebtFactor
import com.babytracker.domain.sleep.eval.SleepWindowPredictor
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PredictSleepWindowUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val babyRepository: BabyRepository,
    private val babyEventRepository: BabyEventRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    operator fun invoke(): Flow<SleepPredictionState> = flow {
        emitAll(
            combine(
                sleepRepository.getAllRecords(),
                breastfeedingRepository.getAllSessions(),
                babyRepository.getBabyProfile(),
                babyEventRepository.getAllEvents(),
            ) { sleepRecords, feedSessions, baby, recentEvents ->
                predict(sleepRecords, feedSessions, baby, recentEvents)
            }
        )
    }.catch { e ->
        emit(SleepPredictionState.Unavailable(e.message ?: "prediction error"))
    }

    private fun predict(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby?,
        recentEvents: List<BabyEvent>,
    ): SleepPredictionState {
        val now = Instant.now(clock)
        val maxOpenSleepAgeMillis = Duration.ofHours(SleepPredictionTuning.MAX_OPEN_SLEEP_AGE_HOURS).toMillis()
        val hasActiveSleep = sleepRecords.any { record ->
            val startMillis = record.startTime.toEpochMilli()
            val nowMillis = now.toEpochMilli()
            record.endTime == null && startMillis <= nowMillis && (nowMillis - startMillis) <= maxOpenSleepAgeMillis
        }
        if (hasActiveSleep) return SleepPredictionState.CurrentlySleeping
        baby ?: return SleepPredictionState.Unavailable("no baby profile")
        return predictForBaby(baby, sleepRecords, feedSessions, recentEvents, now)
    }

    private fun predictForBaby(
        baby: Baby,
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        recentEvents: List<BabyEvent>,
        now: Instant,
    ): SleepPredictionState {
        val today = now.atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()
        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) return SleepPredictionState.CueLed

        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val disruptionLookbackStart = now.minus(Duration.ofHours(SleepPredictionTuning.DISRUPTION_LOOKBACK_HOURS))
        val hasActiveDisruption = recentEvents.any { event ->
            event.type.isDisruption && Instant.ofEpochMilli(event.timestampMs) >= disruptionLookbackStart
        }

        val features = SleepFeatureExtractor(clock, zoneId)
            .extract(sleepRecords.filter { it.startTime >= lookbackStart }, feedSessions)
            .copy(hasActiveDisruption = hasActiveDisruption)

        return SleepWindowPredictor.predict(
            features,
            ageInWeeks,
            now,
            sleepDebtFactorProvider = SleepDebtFactor::adjustment,
        )
    }
}
```

- [ ] **Step 6.8: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds successfully. Hilt will inject `BabyEventRepository` into `PredictSleepWindowUseCase` — the binding is in `RepositoryModule` from Task 4.

- [ ] **Step 6.9: Run full test suite**

```bash
./gradlew test
```

Expected: all tests pass. If any test constructs `PredictSleepWindowUseCase` directly (not via Hilt), it will need `babyEventRepository = mockk()` added to its setup.

- [ ] **Step 6.10: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt \
        app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt \
        app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt \
        app/src/main/java/com/babytracker/domain/usecase/sleep/PredictSleepWindowUseCase.kt \
        app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
git commit -m "feat(sleep): integrate cue disruption into prediction — lowers confidence, no window shift [AKA-96]"
```

---

## Task 7: HomeViewModel — add LogBabyEventUseCase + onCueTapped handler

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

- [ ] **Step 7.1: Inject `LogBabyEventUseCase` and add `onCueTapped`**

In `HomeViewModel.kt`, add the import:

```kotlin
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
```

Add the constructor parameter after `predictSleepWindow`:

```kotlin
private val logBabyEvent: LogBabyEventUseCase,
```

Add the event handler function (after the `init` block):

```kotlin
fun onCueTapped(type: BabyEventType) {
    viewModelScope.launch { runCatching { logBabyEvent(type) } }
}
```

The full updated constructor signature:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    getBabyProfile: GetBabyProfileUseCase,
    getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    getSleepHistory: GetSleepHistoryUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
    settingsRepository: SettingsRepository,
    pumpingRepository: PumpingRepository,
    inventoryRepository: InventoryRepository,
    predictNextFeed: PredictNextFeedUseCase,
    predictSleepWindow: PredictSleepWindowUseCase,
    private val logBabyEvent: LogBabyEventUseCase,
) : ViewModel() {
```

`runCatching` swallows any DB error silently — the cue row is best-effort; a failed tap must not crash the UI.

- [ ] **Step 7.2: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds successfully.

- [ ] **Step 7.3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt
git commit -m "feat(ui): inject LogBabyEventUseCase into HomeViewModel; add onCueTapped handler [AKA-96]"
```

---

## Task 8: CueQuickTapRow composable + HomeScreen wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

- [ ] **Step 8.1: Add extension properties for emoji and label**

Add these at the top of `HomeScreen.kt`, after the existing imports block (before `private val EaseOutQuart`):

```kotlin
private val BabyEventType.emoji: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "😪"
        BabyEventType.HUNGER_CUE -> "😋"
        BabyEventType.FUSSY -> "😣"
        BabyEventType.SICK -> "🤒"
        BabyEventType.TEETHING -> "🦷"
        BabyEventType.TRAVEL -> "✈️"
    }

private val BabyEventType.label: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "Sleepy"
        BabyEventType.HUNGER_CUE -> "Hungry"
        BabyEventType.FUSSY -> "Fussy"
        BabyEventType.SICK -> "Sick"
        BabyEventType.TEETHING -> "Teething"
        BabyEventType.TRAVEL -> "Travel"
    }
```

Add the import:

```kotlin
import com.babytracker.domain.model.BabyEventType
```

- [ ] **Step 8.2: Add `CueQuickTapRow` composable**

Add the private composable at the bottom of `HomeScreen.kt`, after the last composable function:

```kotlin
@Composable
private fun CueQuickTapRow(
    onCueTapped: (BabyEventType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cues = remember { BabyEventType.entries }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cues.forEach { type ->
            SuggestionChip(
                onClick = { onCueTapped(type) },
                label = {
                    Text(
                        text = "${type.emoji} ${type.label}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
```

Add the missing imports:

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SuggestionChip
```

`horizontalScroll` lets the 6 chips scroll on narrow screens without truncation. `SuggestionChip` from Material 3 is the correct chip variant for non-selectable action shortcuts.

- [ ] **Step 8.3: Wire `CueQuickTapRow` below `SleepPredictionCard` in `HomeScreen`**

In the `HomeScreen` composable, find the existing line (around line 424):

```kotlin
SleepPredictionCard(state = uiState.sleepPrediction)
```

Add `CueQuickTapRow` immediately after it:

```kotlin
SleepPredictionCard(state = uiState.sleepPrediction)

CueQuickTapRow(onCueTapped = viewModel::onCueTapped)
```

- [ ] **Step 8.4: Build check**

```bash
./gradlew assembleDebug
```

Expected: builds without error.

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
git commit -m "feat(ui): add Home cue quick-tap row below sleep prediction card [AKA-96]"
```

---

## Task 9: ktlint + detekt + full test suite

- [ ] **Step 9.1: Fix formatting**

```bash
./gradlew ktlintFormat
```

- [ ] **Step 9.2: Run detekt**

```bash
./gradlew detekt
```

Fix any violations. Each fix is its own commit: `fix(detekt): fix <RuleName> violations`.

- [ ] **Step 9.3: Run full unit test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 9.4: Commit ktlint fixes (if any)**

```bash
git add -p   # stage only formatting changes
git commit -m "style: ktlint formatting fixes [AKA-96]"
```

---

## Acceptance Criteria Checklist

- [ ] Room v6 migration creates `baby_events` with `index_baby_events_timestamp` and `index_baby_events_event_type` indices; no other tables are touched
- [ ] `BabyEventType` enum names are stable and match what Room persists (`SLEEPY_CUE`, `HUNGER_CUE`, `FUSSY`, `SICK`, `TEETHING`, `TRAVEL`)
- [ ] `BabyEventType.isDisruption == true` for `SICK`, `TEETHING`, `TRAVEL`; `false` for `SLEEPY_CUE`, `HUNGER_CUE`, `FUSSY`
- [ ] `BabyEventType.emoji` and `.label` are extension properties in the UI layer — not persisted, not in the domain model
- [ ] Unknown enum strings in DB fall back to `FUSSY` in `toDomain()` (no crash)
- [ ] `BabyEventDao.getAllEvents()` returns a `Flow` — Room re-emits on every `insertEvent()` call
- [ ] Tapping a cue chip calls `LogBabyEventUseCase` which inserts a `BabyEventEntity` with the correct `event_type` and `timestamp`
- [ ] Any SICK/TEETHING/TRAVEL event within `DISRUPTION_LOOKBACK_HOURS` (48h) causes the next prediction to have confidence one level lower than it would without the disruption
- [ ] Window `bestEstimate`, `windowStart`, and `windowEnd` are NOT shifted by cue events — only `confidence` changes
- [ ] Inserting any `BabyEventEntity` via the DAO causes `PredictSleepWindowUseCase` to recompute (reactive Flow chain)
- [ ] `ALGORITHM_VERSION == "sleep-pred-phase3-cue-disruption-1"`
- [ ] Home screen shows a horizontally-scrollable cue row with 6 chips (😪 Sleepy, 😋 Hungry, 😣 Fussy, 🤒 Sick, 🦷 Teething, ✈️ Travel) below `SleepPredictionCard`
- [ ] `HomeViewModel.onCueTapped()` uses `runCatching` — a DB error does not crash the UI
- [ ] All unit tests pass; `./gradlew build` succeeds
