# Pause/Resume Breastfeeding Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pause/resume toggle button above the stop button in the active breastfeeding session screen, freezing the timer and cancelling notifications on pause, and restoring them (adjusted for paused duration) on resume.

**Architecture:** Pause state is persisted in Room (`paused_at`, `paused_duration_ms` columns) so a paused session survives app kills. The ViewModel cancels/reschedules `AlarmManager` alarms on pause/resume using the computable remaining time from the known trigger formula. The timer component receives a `frozenElapsedSeconds` hint so a cold-loaded paused session displays the correct frozen value immediately.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Room 2.6.1 (migration v1→v2), AlarmManager, Hilt, JUnit 5, MockK.

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/domain/model/BreastfeedingSession.kt` |
| Modify | `app/src/main/java/com/babytracker/data/local/entity/BreastfeedingEntity.kt` |
| Modify | `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt` |
| Modify | `app/src/main/java/com/babytracker/di/DatabaseModule.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCase.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCase.kt` |
| Modify | `app/src/main/java/com/babytracker/manager/NotificationScheduler.kt` |
| Modify | `app/src/main/java/com/babytracker/manager/BreastfeedingNotificationManager.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCaseTest.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCaseTest.kt` |
| Modify | `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt` |

---

### Task 1: Extend the `BreastfeedingSession` domain model

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/BreastfeedingSession.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class BreastfeedingSession(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val startingSide: BreastSide,
    val switchTime: Instant? = null,
    val notes: String? = null,
    val pausedAt: Instant? = null,
    val pausedDurationMs: Long = 0
) {
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null

    val isPaused: Boolean
        get() = pausedAt != null
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/BreastfeedingSession.kt
git commit -m "feat(breastfeeding): add pausedAt and pausedDurationMs to domain model"
```

---

### Task 2: Extend the Room entity and add DB migration v1→v2

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/entity/BreastfeedingEntity.kt`
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

- [ ] **Step 1: Update `BreastfeedingEntity` with the two new columns and fix the conversion functions**

Replace the entire file:

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import java.time.Instant

@Entity(tableName = "breastfeeding_sessions")
data class BreastfeedingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "starting_side") val startingSide: String,
    @ColumnInfo(name = "switch_time") val switchTime: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0
)

fun BreastfeedingEntity.toDomain(): BreastfeedingSession = BreastfeedingSession(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    startingSide = BreastSide.valueOf(startingSide),
    switchTime = switchTime?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    pausedAt = pausedAt?.let { Instant.ofEpochMilli(it) },
    pausedDurationMs = pausedDurationMs
)

fun BreastfeedingSession.toEntity(): BreastfeedingEntity = BreastfeedingEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    startingSide = startingSide.name,
    switchTime = switchTime?.toEpochMilli(),
    notes = notes,
    pausedAt = pausedAt?.toEpochMilli(),
    pausedDurationMs = pausedDurationMs
)
```

- [ ] **Step 2: Update `BabyTrackerDatabase` to version 2 and declare the migration**

Replace the entire file:

```kotlin
package com.babytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babytracker.data.local.converter.Converters
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.SleepEntity

@Database(
    entities = [BreastfeedingEntity::class, SleepEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BabyTrackerDatabase : RoomDatabase() {
    abstract fun breastfeedingDao(): BreastfeedingDao
    abstract fun sleepDao(): SleepDao
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
```

- [ ] **Step 3: Wire the migration into `DatabaseModule`**

Replace the `provideDatabase` function in `DatabaseModule.kt`:

```kotlin
@Provides
@Singleton
fun provideDatabase(@ApplicationContext context: Context): BabyTrackerDatabase =
    Room.databaseBuilder(
        context,
        BabyTrackerDatabase::class.java,
        "baby_tracker_db"
    )
        .addMigrations(MIGRATION_1_2)
        .build()
```

(Add `import com.babytracker.data.local.MIGRATION_1_2` at the top of the file.)

- [ ] **Step 4: Build to confirm Room generates correctly**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL with no Room schema errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/entity/BreastfeedingEntity.kt \
        app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt \
        app/src/main/java/com/babytracker/di/DatabaseModule.kt
git commit -m "feat(db): add paused_at and paused_duration_ms columns, migrate db to v2"
```

---

### Task 3: `PauseBreastfeedingSessionUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PauseBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: PauseBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = PauseBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun `invoke sets pausedAt to now on a running session`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertNotNull(slot.captured.pausedAt)
        coVerify(exactly = 1) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke does nothing when session is already paused`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = Instant.now().minusSeconds(60)
        )

        useCase(session)

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke preserves existing pausedDurationMs when pausing`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            startingSide = BreastSide.LEFT,
            pausedDurationMs = 30_000L
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        org.junit.jupiter.api.Assertions.assertEquals(30_000L, slot.captured.pausedDurationMs)
    }
}
```

- [ ] **Step 2: Run the tests — expect compilation failure (class not found)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCaseTest"
```

Expected: BUILD FAILED — `PauseBreastfeedingSessionUseCase` does not exist.

- [ ] **Step 3: Implement the use case**

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class PauseBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        if (session.isPaused) return
        repository.updateSession(session.copy(pausedAt = Instant.now()))
    }
}
```

- [ ] **Step 4: Run tests — expect all three to pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCaseTest"
```

Expected: BUILD SUCCESSFUL — 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/breastfeeding/PauseBreastfeedingSessionUseCaseTest.kt
git commit -m "feat(usecase): add PauseBreastfeedingSessionUseCase"
```

---

### Task 4: `ResumeBreastfeedingSessionUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ResumeBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: ResumeBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = ResumeBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun `invoke does nothing when session is not paused`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )

        useCase(session)

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke clears pausedAt on resume`() = runTest {
        val pausedAt = Instant.now().minusSeconds(30)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = pausedAt
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertNull(slot.captured.pausedAt)
    }

    @Test
    fun `invoke accumulates pause duration into pausedDurationMs`() = runTest {
        val pausedAt = Instant.now().minusSeconds(30)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 10_000L // previous pause already accumulated
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        // The new pausedDurationMs must be >= 10_000 + ~30_000 (30 seconds paused)
        assertTrue(slot.captured.pausedDurationMs >= 40_000L)
    }
}
```

- [ ] **Step 2: Run the tests — expect compilation failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCaseTest"
```

Expected: BUILD FAILED — `ResumeBreastfeedingSessionUseCase` does not exist.

- [ ] **Step 3: Implement the use case**

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class ResumeBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        val pausedAt = session.pausedAt ?: return
        val additionalPause = Duration.between(pausedAt, Instant.now()).toMillis()
        repository.updateSession(
            session.copy(
                pausedAt = null,
                pausedDurationMs = session.pausedDurationMs + additionalPause
            )
        )
    }
}
```

- [ ] **Step 4: Run tests — expect all three to pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCaseTest"
```

Expected: BUILD SUCCESSFUL — 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ResumeBreastfeedingSessionUseCaseTest.kt
git commit -m "feat(usecase): add ResumeBreastfeedingSessionUseCase"
```

---

### Task 5: Extend `NotificationScheduler` and `BreastfeedingNotificationManager`

**Files:**
- Modify: `app/src/main/java/com/babytracker/manager/NotificationScheduler.kt`
- Modify: `app/src/main/java/com/babytracker/manager/BreastfeedingNotificationManager.kt`

The existing `schedule*` methods compute `triggerTime = sessionStartTime + maxMinutes`. For resume, we already have the remaining trigger time computed in the ViewModel and need to schedule at an absolute `Instant`. Two new interface methods handle this.

- [ ] **Step 1: Add two methods to the interface**

Replace `NotificationScheduler.kt` entirely:

```kotlin
package com.babytracker.manager

import java.time.Instant

interface NotificationScheduler {
    fun scheduleMaxTotalTimeNotification(sessionStartTime: Instant, maxTotalMinutes: Int)
    fun scheduleMaxPerBreastNotification(sessionStartTime: Instant, maxPerBreastMinutes: Int)
    fun scheduleMaxTotalTimeNotificationAt(triggerTime: Instant)
    fun scheduleMaxPerBreastNotificationAt(triggerTime: Instant)
    fun cancelAllScheduledNotifications()
}
```

- [ ] **Step 2: Implement the two new methods in `BreastfeedingNotificationManager`**

Add these two overrides to the class (after `scheduleMaxPerBreastNotification`):

```kotlin
override fun scheduleMaxTotalTimeNotificationAt(triggerTime: Instant) {
    scheduleAlarm(
        triggerTime = triggerTime,
        requestCode = REQUEST_CODE_MAX_TOTAL,
        notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL
    )
}

override fun scheduleMaxPerBreastNotificationAt(triggerTime: Instant) {
    scheduleAlarm(
        triggerTime = triggerTime,
        requestCode = REQUEST_CODE_MAX_PER_BREAST,
        notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE
    )
}
```

- [ ] **Step 3: Build to confirm no compilation errors from the interface change**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/NotificationScheduler.kt \
        app/src/main/java/com/babytracker/manager/BreastfeedingNotificationManager.kt
git commit -m "feat(manager): add absolute-time scheduling methods to NotificationScheduler"
```

---

### Task 6: Add pause/resume to `BreastfeedingViewModel`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt`

- [ ] **Step 1: Write the failing ViewModel tests**

Append these tests to `BreastfeedingViewModelTest.kt` (inside the class, before the closing brace). Also update `setUp` and `createViewModel` to handle the two new use-case dependencies.

First, update the class-level fields (add two new mocks):

```kotlin
private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
```

Update `setUp`:

```kotlin
pauseSession = mockk()
resumeSession = mockk()
coJustRun { pauseSession(any()) }
coJustRun { resumeSession(any()) }
```

Update `createViewModel`:

```kotlin
private fun createViewModel() = BreastfeedingViewModel(
    startSession,
    stopSession,
    switchSide,
    getHistory,
    pauseSession,
    resumeSession,
    repository,
    settingsRepository,
    mockk(),
    notificationScheduler
)
```

Add these new tests:

```kotlin
@Test
fun `onPauseSession does nothing when no active session`() = runTest {
    viewModel.onPauseSession()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { pauseSession(any()) }
}

@Test
fun `onPauseSession calls pauseSession use case and cancels notifications`() = runTest {
    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now().minusSeconds(300),
        startingSide = BreastSide.LEFT
    )
    activeSessionFlow.value = session
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onPauseSession()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { pauseSession(session) }
    verify(exactly = 1) { notificationScheduler.cancelAllScheduledNotifications() }
}

@Test
fun `onResumeSession does nothing when no active session`() = runTest {
    viewModel.onResumeSession()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { resumeSession(any()) }
}

@Test
fun `onResumeSession calls resumeSession use case`() = runTest {
    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now().minusSeconds(300),
        startingSide = BreastSide.LEFT,
        pausedAt = Instant.now().minusSeconds(60)
    )
    activeSessionFlow.value = session
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onResumeSession()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { resumeSession(session) }
}
```

- [ ] **Step 2: Run the tests — expect failures (new use cases not wired)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.breastfeeding.BreastfeedingViewModelTest"
```

Expected: FAILED (compilation errors until VM is updated, or test failures for new tests).

- [ ] **Step 3: Update `BreastfeedingViewModel.kt`**

Replace the entire file:

```kotlin
package com.babytracker.ui.breastfeeding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NotificationScheduler
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0
)

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    private val getHistory: GetBreastfeedingHistoryUseCase,
    private val pauseSession: PauseBreastfeedingSessionUseCase,
    private val resumeSession: ResumeBreastfeedingSessionUseCase,
    private val repository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context,
    private val notificationScheduler: NotificationScheduler = BreastfeedingNotificationManager(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreastfeedingUiState())
    val uiState: StateFlow<BreastfeedingUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<BreastfeedingSession>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                repository.getActiveSession(),
                settingsRepository.getMaxPerBreastMinutes(),
                settingsRepository.getMaxTotalFeedMinutes()
            ) { session, maxPerBreast, maxTotal ->
                BreastfeedingUiState(
                    activeSession = session,
                    selectedSide = _uiState.value.selectedSide,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onSideSelected(side: BreastSide) {
        _uiState.value = _uiState.value.copy(selectedSide = side)
    }

    fun onStartSession() {
        val side = _uiState.value.selectedSide ?: return
        viewModelScope.launch {
            startSession(side)
            repository.getActiveSession().collect { session ->
                session?.let {
                    scheduleNotifications(it)
                    return@collect
                }
            }
        }
    }

    fun onStopSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            stopSession(session)
            notificationScheduler.cancelAllScheduledNotifications()
            NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
            NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
        }
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            switchSide(session)
        }
    }

    fun onPauseSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            pauseSession(session)
            notificationScheduler.cancelAllScheduledNotifications()
        }
    }

    fun onResumeSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            resumeSession(session)
            rescheduleNotificationsAfterResume(session)
        }
    }

    private fun scheduleNotifications(session: BreastfeedingSession) {
        val maxPerBreastMinutes = _uiState.value.maxPerBreastMinutes
        val maxTotalFeedMinutes = _uiState.value.maxTotalFeedMinutes

        if (maxPerBreastMinutes > 0) {
            notificationScheduler.scheduleMaxPerBreastNotification(
                session.startTime,
                maxPerBreastMinutes
            )
        }

        if (maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                session.startTime,
                maxTotalFeedMinutes
            )
        }
    }

    /**
     * After a resume, reschedule any notification that had not yet fired before the pause.
     *
     * The original trigger time for each notification type is:
     *   startTime + maxMinutes + previousPausedDurationMs
     *
     * If that trigger time is still in the future relative to [session.pausedAt], the
     * notification hadn't fired yet — reschedule it for (now + remaining).
     *
     * [session] here is the state *before* the resume use case cleared pausedAt, so
     * pausedAt is still set and pausedDurationMs reflects only previously accumulated pauses
     * (not the current pause, which gets added in the use case).
     */
    private fun rescheduleNotificationsAfterResume(session: BreastfeedingSession) {
        val pausedAt = session.pausedAt ?: return
        val maxPerBreast = _uiState.value.maxPerBreastMinutes
        val maxTotal = _uiState.value.maxTotalFeedMinutes

        if (maxPerBreast > 0) {
            val adjustedTrigger = session.startTime
                .plusSeconds(maxPerBreast * 60L)
                .plusMillis(session.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    Instant.now().plus(remaining)
                )
            }
        }

        if (maxTotal > 0) {
            val adjustedTrigger = session.startTime
                .plusSeconds(maxTotal * 60L)
                .plusMillis(session.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    Instant.now().plus(remaining)
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run all ViewModel tests — expect all to pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.breastfeeding.BreastfeedingViewModelTest"
```

Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt \
        app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt
git commit -m "feat(breastfeeding): add onPauseSession and onResumeSession to BreastfeedingViewModel"
```

---

### Task 7: Add `frozenElapsedSeconds` to `TimerDisplay`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt`

When `isRunning = false` and a `frozenElapsedSeconds` value is provided, the timer shows that frozen value immediately instead of 0. This is needed when the screen is cold-loaded with a paused session.

- [ ] **Step 1: Update `TimerDisplay` signature and `LaunchedEffect`**

Change the function signature (add `frozenElapsedSeconds: Long? = null` before `modifier`):

```kotlin
@Composable
fun TimerDisplay(
    startTimeMillis: Long,
    isRunning: Boolean,
    maxDurationSeconds: Int = 0,
    ringColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    frozenElapsedSeconds: Long? = null,
    modifier: Modifier = Modifier
)
```

Replace the two `LaunchedEffect` calls (keep only one, but update it):

```kotlin
var elapsedSeconds by remember { mutableLongStateOf(frozenElapsedSeconds ?: 0L) }

LaunchedEffect(isRunning, startTimeMillis, frozenElapsedSeconds) {
    if (isRunning) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
            delay(1000L)
        }
    } else if (frozenElapsedSeconds != null) {
        elapsedSeconds = frozenElapsedSeconds
    }
}
```

- [ ] **Step 2: Build to confirm no regressions**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run existing `TimerDisplayTest`**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.component.TimerDisplayTest"
```

Expected: all existing tests pass (new parameter has a default, so no call sites break).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt
git commit -m "feat(ui): add frozenElapsedSeconds parameter to TimerDisplay for paused sessions"
```

---

### Task 8: Update `BreastfeedingScreen` with pause/resume UI

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`

Changes:
1. Status pill text switches between "● Session in progress" and "⏸ Session paused".
2. `TimerDisplay` receives adjusted `startTimeMillis` (accounting for accumulated paused duration) plus `frozenElapsedSeconds` when paused, and `isRunning = !activeSession.isPaused`.
3. Per-side breakdown uses `effectiveNow` (frozen at `pausedAt` when paused).
4. A Pause / Resume `OutlinedCard` button appears above the Stop button.

- [ ] **Step 1: Replace the active-session branch in `BreastfeedingScreen`**

The entire active-session block (everything inside `if (activeSession != null)`) becomes:

```kotlin
if (activeSession != null) {
    // Status pill
    val statusText = if (activeSession.isPaused) "⏸ Session paused" else "● Session in progress"
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (activeSession.isPaused)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = if (activeSession.isPaused)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Compute frozen elapsed seconds when paused so TimerDisplay shows the
    // correct value on cold-load (otherwise it would start from 0).
    val frozenElapsedSeconds: Long? = if (activeSession.isPaused) {
        val pausedAt = activeSession.pausedAt!!
        (pausedAt.toEpochMilli() - activeSession.startTime.toEpochMilli() - activeSession.pausedDurationMs) / 1000L
    } else {
        null
    }

    // Ring timer — effective start accounts for accumulated paused time so
    // elapsed = (now - effectiveStart) = (now - startTime - pausedDurationMs).
    TimerDisplay(
        startTimeMillis = activeSession.startTime.toEpochMilli() + activeSession.pausedDurationMs,
        isRunning = !activeSession.isPaused,
        frozenElapsedSeconds = frozenElapsedSeconds,
        maxDurationSeconds = if (uiState.maxTotalFeedMinutes > 0) {
            uiState.maxTotalFeedMinutes * 60
        } else {
            0
        }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Per-side breakdown: freeze at pausedAt when session is paused.
    val effectiveNow: Instant = if (activeSession.isPaused) activeSession.pausedAt!! else now

    val firstSideDuration: Duration = activeSession.switchTime
        ?.let { Duration.between(activeSession.startTime, it) }
        ?: Duration.between(activeSession.startTime, effectiveNow)
            .minus(Duration.ofMillis(activeSession.pausedDurationMs))

    val secondSideDuration: Duration = activeSession.switchTime
        ?.let {
            Duration.between(it, effectiveNow)
                .minus(Duration.ofMillis(activeSession.pausedDurationMs))
        }
        ?: Duration.ZERO

    val currentSide: BreastSide = activeSession.switchTime?.let {
        if (activeSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
    } ?: activeSession.startingSide

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(BreastSide.LEFT, BreastSide.RIGHT).forEach { side ->
            val isCurrentSide = side == currentSide
            val duration = if (side == activeSession.startingSide) {
                firstSideDuration
            } else {
                secondSideDuration
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentSide) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCurrentSide) "● ${side.name}" else side.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrentSide) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = duration.formatDuration(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentSide) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Switch side button (only shown if not switched yet and not paused)
    if (activeSession.switchTime == null && !activeSession.isPaused) {
        val nextSide = if (activeSession.startingSide == BreastSide.LEFT) "Right" else "Left"
        OutlinedCard(
            onClick = viewModel::onSwitchSide,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⇄  Switch to $nextSide",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Pause / Resume button (above Stop)
    OutlinedCard(
        onClick = if (activeSession.isPaused) viewModel::onResumeSession else viewModel::onPauseSession,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (activeSession.isPaused) "▶  Resume Session" else "⏸  Pause Session",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Stop button
    Button(
        onClick = viewModel::onStopSession,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text("Stop Session", style = MaterialTheme.typography.titleSmall)
    }
}
```

Also add `import java.time.Duration` and `import java.time.Instant` at the top if not already present (they already are in the original file).

- [ ] **Step 2: Build to confirm no compilation errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all unit tests to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt
git commit -m "feat(breastfeeding): add pause/resume button and frozen timer to BreastfeedingScreen"
```

---

### Task 9: Final full test run and PR

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL — all tests in all modules pass.

- [ ] **Step 2: Build the debug APK**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Create PR**

```bash
gh pr create \
  --title "feat(breastfeeding): add pause/resume session" \
  --body "$(cat <<'EOF'
## Summary
- Adds pause/resume toggle button above the stop button in the active breastfeeding screen
- Persists pause state in Room (DB migration v1→v2: `paused_at`, `paused_duration_ms`)
- Cancels AlarmManager notifications on pause; reschedules only un-fired ones on resume with remaining time preserved
- Timer display freezes correctly on pause (including cold-load of a pre-paused session)
- Per-side breakdown freezes at pause time

## Test plan
- [ ] Start a session — verify timer ticks normally
- [ ] Tap Pause — verify timer freezes, status pill changes to "⏸ Session paused", switch-side button hides
- [ ] Kill and reopen the app while paused — verify session still shows as paused with correct frozen time
- [ ] Tap Resume — verify timer continues from where it paused (not from 0 or from original start)
- [ ] Verify any notifications configured in Settings are rescheduled relative to resume time
- [ ] Tap Pause after a notification has already fired — verify that notification is NOT re-triggered on Resume
- [ ] Tap Stop while paused — verify session ends and all notifications are cancelled

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Spec Coverage Self-Review

| Requirement | Task |
|-------------|------|
| Pause button above stop button | Task 8 |
| Timer stops on pause | Task 7 + 8 (`isRunning = false`, `frozenElapsedSeconds`) |
| All active notifications cancelled on pause | Task 6 (`onPauseSession` calls `cancelAllScheduledNotifications`) |
| Track which notifications were pending at pause | Task 6 (`rescheduleNotificationsAfterResume` derives from timestamps) |
| Resume: timer continues from paused point | Task 4 (`pausedDurationMs` accumulates) + Task 8 (`effectiveStart = startTime + pausedDurationMs`) |
| Resume: notifications rescheduled with adjusted timers | Task 6 (`rescheduleNotificationsAfterResume`) |
| Already-fired notifications not re-triggered on resume | Task 6 (remaining Duration check: skip if `isNegative || isZero`) |
| Button toggles label/icon between pause and resume | Task 8 |
| Pause state survives app kill | Task 2 (persisted in Room) |
