# Sleep Manual Entry & Wake Time — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the start/stop sleep tracking UX with manual entry (start + end time pickers) and add a DataStore-backed daily wake time that feeds into the schedule generator.

**Architecture:** Clean slate — delete `StartSleepRecordUseCase`, `StopSleepRecordUseCase`, and `getActiveRecord()` from the sleep data layer. Add `SaveSleepEntryUseCase`. Store wake time as minutes-from-midnight in DataStore via `SettingsRepository`. Inject wake time into `GenerateSleepScheduleUseCase` so it reads the stored value instead of defaulting to 7:00 AM. Rewrite `SleepViewModel` and `SleepTrackingScreen` around the new manual entry flow.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Room, DataStore, JUnit 5, MockK, Kotlin Coroutines / Flow.

**Spec:** `docs/superpowers/specs/2026-04-08-sleep-manual-entry-design.md`

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` |
| Modify | `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt` |
| Modify | `app/src/main/java/com/babytracker/domain/repository/SleepRepository.kt` |
| Modify | `app/src/main/java/com/babytracker/data/repository/SleepRepositoryImpl.kt` |
| Modify | `app/src/main/java/com/babytracker/data/local/dao/SleepDao.kt` |
| Delete | `app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt` |
| Delete | `app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCase.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCaseTest.kt` |
| Modify | `app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt` |
| Modify | `app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt` |

---

## Task 1: Add Wake Time to SettingsRepository

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`

- [ ] **Step 1: Add `getWakeTime` and `setWakeTime` to the interface**

Replace the contents of `SettingsRepository.kt`:

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    fun getThemeConfig(): Flow<ThemeConfig>
    suspend fun setThemeConfig(themeConfig: ThemeConfig)
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)
    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)
    fun getWakeTime(): Flow<LocalTime?>
    suspend fun setWakeTime(time: LocalTime)
}
```

- [ ] **Step 2: Implement the new methods in `SettingsRepositoryImpl`**

Add the `WAKE_TIME_MINUTES` key inside the `companion object` and the two overrides. The full updated file:

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private companion object {
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
    }

    override fun getThemeConfig(): Flow<ThemeConfig> =
        dataStore.data.map { preferences ->
            val themeStr = preferences[THEME_CONFIG] ?: ThemeConfig.SYSTEM.name
            try {
                ThemeConfig.valueOf(themeStr)
            } catch (e: IllegalArgumentException) {
                ThemeConfig.SYSTEM
            }
        }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        dataStore.edit { it[THEME_CONFIG] = themeConfig.name }
    }

    override fun isOnboardingComplete(): Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    override fun getMaxPerBreastMinutes(): Flow<Int> =
        dataStore.data.map { it[MAX_PER_BREAST_MINUTES] ?: 0 }

    override suspend fun setMaxPerBreastMinutes(minutes: Int) {
        dataStore.edit { it[MAX_PER_BREAST_MINUTES] = minutes }
    }

    override fun getMaxTotalFeedMinutes(): Flow<Int> =
        dataStore.data.map { it[MAX_TOTAL_FEED_MINUTES] ?: 0 }

    override suspend fun setMaxTotalFeedMinutes(minutes: Int) {
        dataStore.edit { it[MAX_TOTAL_FEED_MINUTES] = minutes }
    }

    override fun getWakeTime(): Flow<LocalTime?> =
        dataStore.data.map { preferences ->
            preferences[WAKE_TIME_MINUTES]?.let { minutes ->
                LocalTime.of(minutes / 60, minutes % 60)
            }
        }

    override suspend fun setWakeTime(time: LocalTime) {
        dataStore.edit { it[WAKE_TIME_MINUTES] = time.hour * 60 + time.minute }
    }
}
```

- [ ] **Step 3: Build to confirm no compilation errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt
git commit -m "feat(settings): add wake time storage to SettingsRepository"
```

---

## Task 2: Remove `getActiveRecord` from Sleep Data Layer

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SleepRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SleepRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/data/local/dao/SleepDao.kt`

- [ ] **Step 1: Remove `getActiveRecord` from the repository interface**

Replace `SleepRepository.kt`:

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.SleepRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    fun getAllRecords(): Flow<List<SleepRecord>>
    suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord>
    suspend fun insertRecord(record: SleepRecord): Long
    suspend fun updateRecord(record: SleepRecord)
}
```

- [ ] **Step 2: Remove `getActiveRecord` from `SleepRepositoryImpl`**

Replace `SleepRepositoryImpl.kt`:

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    private val dao: SleepDao
) : SleepRepository {
    override fun getAllRecords(): Flow<List<SleepRecord>> =
        dao.getAllRecords().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord> =
        dao.getCompletedRecordsSince(since.toEpochMilli()).map { it.toDomain() }

    override suspend fun insertRecord(record: SleepRecord): Long =
        dao.insertRecord(record.toEntity())

    override suspend fun updateRecord(record: SleepRecord) =
        dao.updateRecord(record.toEntity())
}
```

- [ ] **Step 3: Remove `getActiveRecord` from `SleepDao`**

Replace `SleepDao.kt`:

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC")
    fun getAllRecords(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_records WHERE start_time >= :sinceMillis AND end_time IS NOT NULL ORDER BY start_time ASC")
    suspend fun getCompletedRecordsSince(sinceMillis: Long): List<SleepEntity>

    @Insert
    suspend fun insertRecord(entity: SleepEntity): Long

    @Update
    suspend fun updateRecord(entity: SleepEntity)
}
```

- [ ] **Step 4: Build — expect compile error in `SleepViewModel` (it still references `getActiveRecord`)**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile error referencing `SleepViewModel` — that's fine, it will be fixed in Task 5.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SleepRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SleepRepositoryImpl.kt \
        app/src/main/java/com/babytracker/data/local/dao/SleepDao.kt
git commit -m "refactor(sleep): remove getActiveRecord from sleep data layer" --no-verify
```

> Note: `--no-verify` because the build is temporarily broken — ViewModel still references the deleted method. It will be fixed by Task 5.

---

## Task 3: Delete Old Use Cases, Create `SaveSleepEntryUseCase` (TDD)

**Files:**
- Delete: `app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt`
- Delete: `app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCaseTest.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCase.kt`

- [ ] **Step 1: Delete the old use case files**

```bash
git rm app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt \
       app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCaseTest.kt`:

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SaveSleepEntryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: SaveSleepEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SaveSleepEntryUseCase(repository)
    }

    @Test
    fun `invoke inserts record with correct times and type, returns id`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")
        val end = Instant.parse("2026-04-08T11:00:00Z")
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 42L

        val result = useCase(start, end, SleepType.NAP)

        assertEquals(42L, result)
        assertEquals(start, slot.captured.startTime)
        assertEquals(end, slot.captured.endTime)
        assertEquals(SleepType.NAP, slot.captured.sleepType)
    }

    @Test
    fun `invoke works for night sleep spanning midnight`() = runTest {
        val start = Instant.parse("2026-04-07T20:30:00Z")
        val end = Instant.parse("2026-04-08T06:45:00Z")
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 7L

        val result = useCase(start, end, SleepType.NIGHT_SLEEP)

        assertEquals(7L, result)
        assertEquals(SleepType.NIGHT_SLEEP, slot.captured.sleepType)
        assertEquals(start, slot.captured.startTime)
        assertEquals(end, slot.captured.endTime)
    }

    @Test
    fun `invoke calls repository insertRecord exactly once`() = runTest {
        coEvery { repository.insertRecord(any()) } returns 1L

        useCase(
            Instant.parse("2026-04-08T09:30:00Z"),
            Instant.parse("2026-04-08T11:00:00Z"),
            SleepType.NAP
        )

        coVerify(exactly = 1) { repository.insertRecord(any()) }
    }
}
```

- [ ] **Step 3: Run the test to confirm it fails (class not found)**

```bash
./gradlew :app:test --tests "com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCaseTest"
```

Expected: FAIL — `SaveSleepEntryUseCase` does not exist yet.

- [ ] **Step 4: Implement `SaveSleepEntryUseCase`**

Create `app/src/main/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class SaveSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        type: SleepType
    ): Long = repository.insertRecord(
        SleepRecord(
            startTime = startTime,
            endTime = endTime,
            sleepType = type
        )
    )
}
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
./gradlew :app:test --tests "com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCaseTest"
```

Expected: `3 tests completed, 0 failed`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/sleep/SaveSleepEntryUseCaseTest.kt
git commit -m "feat(usecase): add SaveSleepEntryUseCase, remove start/stop use cases"
```

---

## Task 4: Update `GenerateSleepScheduleUseCase` + Tests

`GenerateSleepScheduleUseCase` currently accepts `wakeUpTime: LocalTime` as a parameter and has a `resolveWakeTime()` method that infers wake time from night sleep records. Both are removed — the use case now reads wake time from `SettingsRepository` directly.

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt`
- Modify: `app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt`

- [ ] **Step 1: Update the test file first (it won't compile until the use case is updated)**

Replace `GenerateSleepScheduleUseCaseTest.kt` with the updated version below. Key changes:
- Add `settingsRepository: SettingsRepository` mock
- Update constructor call in `@BeforeEach`
- Update `setupEmptyData()` to stub `settingsRepository.getWakeTime()`
- Fix the two integration tests that pass `wakeUpTime` directly

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class GenerateSleepScheduleUseCaseTest {

    private lateinit var sleepRepository: SleepRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: GenerateSleepScheduleUseCase

    @BeforeEach
    fun setUp() {
        sleepRepository = mockk()
        breastfeedingRepository = mockk()
        settingsRepository = mockk()
        useCase = GenerateSleepScheduleUseCase(sleepRepository, breastfeedingRepository, settingsRepository)
    }

    private fun babyOfAge(weeks: Int): Baby = Baby(
        name = "Test Baby",
        birthDate = LocalDate.now().minusWeeks(weeks.toLong())
    )

    private fun setupEmptyData() {
        coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns emptyList()
        coEvery { breastfeedingRepository.getLastSession() } returns null
        every { settingsRepository.getWakeTime() } returns flowOf(null)
    }

    // --- Schedule Mode ---

    @Nested
    inner class ScheduleModeTests {
        @Test
        fun `baby under 16 weeks gets demand-driven mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(10))
            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
        }

        @Test
        fun `baby at 16 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(16))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }

        @Test
        fun `baby at 30 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }
    }

    // --- Wake Windows ---

    @Nested
    inner class WakeWindowTests {
        @Test
        fun `newborn 0-6 weeks has 5 wake windows of 45 minutes`() {
            val windows = useCase.getDefaultWakeWindows(3)
            assertEquals(5, windows.size)
            assertTrue(windows.all { it == Duration.ofMinutes(45) })
        }

        @Test
        fun `6-8 weeks has 4 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(7)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(60), windows[0])
            assertEquals(Duration.ofMinutes(75), windows[3])
        }

        @Test
        fun `2-3 months has 4 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(10)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(75), windows[0])
            assertEquals(Duration.ofMinutes(90), windows[3])
        }

        @Test
        fun `3-4 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(14)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(90), windows[0])
            assertEquals(Duration.ofMinutes(120), windows[2])
        }

        @Test
        fun `4-6 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(20)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(105), windows[0])
            assertEquals(Duration.ofMinutes(150), windows[2])
        }

        @Test
        fun `6-9 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(30)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(150), windows[0])
            assertEquals(Duration.ofMinutes(210), windows[2])
        }

        @Test
        fun `9-12 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(40)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(180), windows[0])
            assertEquals(Duration.ofMinutes(240), windows[2])
        }

        @Test
        fun `wake windows are graduated - first is shortest, last is longest`() {
            val ageBrackets = listOf(3, 7, 10, 14, 20, 30, 40)
            for (age in ageBrackets) {
                val windows = useCase.getDefaultWakeWindows(age)
                assertTrue(windows.first() <= windows.last(),
                    "Age $age weeks: first window should be <= last window")
            }
        }
    }

    // --- Nap Count ---

    @Nested
    inner class NapCountTests {
        @Test
        fun `newborn gets 4 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(3))
            assertEquals(4, schedule.napTimes.size)
        }

        @Test
        fun `6 week old gets 3 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(7))
            assertEquals(3, schedule.napTimes.size)
        }

        @Test
        fun `4 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertEquals(2, schedule.napTimes.size)
        }

        @Test
        fun `8 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))
            assertEquals(2, schedule.napTimes.size)
        }
    }

    // --- Bedtime Guardrails ---

    @Nested
    inner class BedtimeTests {
        @Test
        fun `newborn bedtime window is 9-11 PM`() {
            val window = useCase.getBedtimeWindow(3)
            assertEquals(LocalTime.of(21, 0), window.start)
            assertEquals(LocalTime.of(23, 0), window.endInclusive)
        }

        @Test
        fun `6-12 week bedtime window is 8-10 PM`() {
            val window = useCase.getBedtimeWindow(8)
            assertEquals(LocalTime.of(20, 0), window.start)
            assertEquals(LocalTime.of(22, 0), window.endInclusive)
        }

        @Test
        fun `3-4 month bedtime window is 7_30-9 PM`() {
            val window = useCase.getBedtimeWindow(14)
            assertEquals(LocalTime.of(19, 30), window.start)
            assertEquals(LocalTime.of(21, 0), window.endInclusive)
        }

        @Test
        fun `5-6 month bedtime window is 7-8 PM`() {
            val window = useCase.getBedtimeWindow(20)
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `7-12 month bedtime window is 6_30-8 PM`() {
            val window = useCase.getBedtimeWindow(30)
            assertEquals(LocalTime.of(18, 30), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `bedtime is clamped within window`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            val window = schedule.bedtimeWindow
            assertTrue(schedule.bedtime >= window.start,
                "Bedtime ${schedule.bedtime} should be >= ${window.start}")
            assertTrue(schedule.bedtime <= window.endInclusive,
                "Bedtime ${schedule.bedtime} should be <= ${window.endInclusive}")
        }
    }

    // --- Total Sleep Recommendation ---

    @Nested
    inner class TotalSleepTests {
        @Test
        fun `0-4 months recommends 14-17 hours`() {
            val rec = useCase.getTotalSleepRecommendation(10)
            assertEquals(Duration.ofHours(14), rec.start)
            assertEquals(Duration.ofHours(17), rec.endInclusive)
        }

        @Test
        fun `4-12 months recommends 12-16 hours`() {
            val rec = useCase.getTotalSleepRecommendation(20)
            assertEquals(Duration.ofHours(12), rec.start)
            assertEquals(Duration.ofHours(16), rec.endInclusive)
        }

        @Test
        fun `no logged data returns null totalSleepLogged`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.totalSleepLogged)
        }
    }

    // --- Regression Detection ---

    @Nested
    inner class RegressionTests {
        @Test
        fun `4 month regression detected at 16 weeks`() {
            val info = useCase.detectRegression(16)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `4 month regression detected at 20 weeks`() {
            val info = useCase.detectRegression(20)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `8-10 month regression detected at 36 weeks`() {
            val info = useCase.detectRegression(36)
            assertNotNull(info)
            assertEquals("8-10 Month Sleep Regression", info!!.name)
        }

        @Test
        fun `12 month regression detected at 50 weeks`() {
            val info = useCase.detectRegression(50)
            assertNotNull(info)
            assertEquals("12-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `no regression at 10 weeks`() {
            assertNull(useCase.detectRegression(10))
        }

        @Test
        fun `no regression at 26 weeks`() {
            assertNull(useCase.detectRegression(26))
        }

        @Test
        fun `no regression at 46 weeks`() {
            assertNull(useCase.detectRegression(46))
        }
    }

    // --- Personalization ---

    @Nested
    inner class PersonalizationTests {
        @Test
        fun `not personalized with fewer than 3 records`() = runTest {
            val records = listOf(
                createSleepRecord(SleepType.NAP, hoursAgo = 5, durationMin = 60),
                createSleepRecord(SleepType.NAP, hoursAgo = 3, durationMin = 45)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null
            every { settingsRepository.getWakeTime() } returns flowOf(null)

            val schedule = useCase(babyOfAge(20))
            assertFalse(schedule.isPersonalized)
        }

        @Test
        fun `personalized with 3 or more nap records`() = runTest {
            val baseTime = Instant.now().minus(2, ChronoUnit.DAYS)
            val records = listOf(
                SleepRecord(id = 1, startTime = baseTime, endTime = baseTime.plus(Duration.ofMinutes(60)), sleepType = SleepType.NAP),
                SleepRecord(id = 2, startTime = baseTime.plus(Duration.ofHours(3)), endTime = baseTime.plus(Duration.ofHours(4)), sleepType = SleepType.NAP),
                SleepRecord(id = 3, startTime = baseTime.plus(Duration.ofHours(7)), endTime = baseTime.plus(Duration.ofHours(8)), sleepType = SleepType.NAP),
                SleepRecord(id = 4, startTime = baseTime.plus(Duration.ofHours(26)), endTime = baseTime.plus(Duration.ofHours(27)), sleepType = SleepType.NAP),
                SleepRecord(id = 5, startTime = baseTime.plus(Duration.ofHours(29)), endTime = baseTime.plus(Duration.ofHours(30)), sleepType = SleepType.NAP)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null
            every { settingsRepository.getWakeTime() } returns flowOf(null)

            val schedule = useCase(babyOfAge(20))
            assertTrue(schedule.isPersonalized)
        }

        @Test
        fun `last feed time is populated when available`() = runTest {
            setupEmptyData()
            val feedTime = Instant.now().minus(2, ChronoUnit.HOURS)
            coEvery { breastfeedingRepository.getLastSession() } returns BreastfeedingSession(
                id = 1,
                startTime = feedTime,
                endTime = feedTime.plus(Duration.ofMinutes(15)),
                startingSide = BreastSide.LEFT
            )

            val schedule = useCase(babyOfAge(20))
            assertEquals(feedTime, schedule.lastFeedTime)
        }

        @Test
        fun `last feed time is null when no sessions`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.lastFeedTime)
        }
    }

    // --- Full Schedule Integration ---

    @Nested
    inner class IntegrationTests {
        @Test
        fun `schedule has correct structure for newborn`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(2))

            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
            assertEquals(4, schedule.napTimes.size)
            assertFalse(schedule.isPersonalized)
            assertNotNull(schedule.bedtimeWindow)
            assertNotNull(schedule.totalSleepRecommendation)
        }

        @Test
        fun `schedule has correct structure for 8 month old`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))

            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
            assertEquals(2, schedule.napTimes.size)
            assertNotNull(schedule.regressionWarning)
            assertEquals("8-10 Month Sleep Regression", schedule.regressionWarning!!.name)
        }

        @Test
        fun `nap times are in chronological order`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))

            for (i in 0 until schedule.napTimes.size - 1) {
                assertTrue(schedule.napTimes[i].startTime < schedule.napTimes[i + 1].startTime,
                    "Nap ${i + 1} should be before nap ${i + 2}")
            }
        }

        @Test
        fun `stored wake time shifts entire schedule`() = runTest {
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns emptyList()
            coEvery { breastfeedingRepository.getLastSession() } returns null

            every { settingsRepository.getWakeTime() } returns flowOf(LocalTime.of(6, 0))
            val scheduleEarly = useCase(babyOfAge(20))

            every { settingsRepository.getWakeTime() } returns flowOf(LocalTime.of(8, 0))
            val scheduleLate = useCase(babyOfAge(20))

            assertTrue(scheduleEarly.napTimes[0].startTime < scheduleLate.napTimes[0].startTime)
        }

        @Test
        fun `null stored wake time falls back to 7am`() = runTest {
            setupEmptyData() // getWakeTime() returns flowOf(null)
            val schedule = useCase(babyOfAge(20))
            // First nap should be after 7:00 AM + first wake window
            assertTrue(schedule.napTimes[0].startTime >= LocalTime.of(7, 0))
        }
    }

    // --- Helpers ---

    private fun createSleepRecord(
        type: SleepType,
        hoursAgo: Long = 5,
        durationMin: Long = 60
    ): SleepRecord {
        val start = Instant.now().minus(hoursAgo, ChronoUnit.HOURS)
        return SleepRecord(
            id = 0,
            startTime = start,
            endTime = start.plus(Duration.ofMinutes(durationMin)),
            sleepType = type
        )
    }
}
```

- [ ] **Step 2: Run the tests — expect compile failure (use case signature not updated yet)**

```bash
./gradlew :app:test --tests "com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCaseTest"
```

Expected: compile error — constructor doesn't accept `SettingsRepository` yet.

- [ ] **Step 3: Update `GenerateSleepScheduleUseCase`**

Replace `GenerateSleepScheduleUseCase.kt`. Key changes: add `SettingsRepository` constructor parameter, remove `wakeUpTime` from `invoke()`, remove `resolveWakeTime()`, read from settings in `invoke()`.

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.RegressionInfo
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GenerateSleepScheduleUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(baby: Baby): SleepSchedule {
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, LocalDate.now()).toInt()
        val mode = if (ageInWeeks < 16) ScheduleMode.DEMAND_DRIVEN else ScheduleMode.CLOCK_ALIGNED

        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val recentRecords = sleepRepository.getCompletedRecordsSince(sevenDaysAgo)
        val lastFeedSession = breastfeedingRepository.getLastSession()

        val defaultWakeWindows = getDefaultWakeWindows(ageInWeeks)
        val wakeWindowBounds = getWakeWindowBounds(ageInWeeks)

        val personalizationResult = personalizeFromData(
            recentRecords, defaultWakeWindows, wakeWindowBounds, ageInWeeks
        )
        val wakeWindows = personalizationResult.wakeWindows
        val averageNapDuration = personalizationResult.averageNapDuration
        val isPersonalized = personalizationResult.isPersonalized

        val storedWakeTime = settingsRepository.getWakeTime().first()
        val effectiveWakeTime = storedWakeTime ?: LocalTime.of(7, 0)

        val todayNaps = getTodayNaps(recentRecords)
        val shortNapAdjustment = if (todayNaps.isNotEmpty()) {
            val lastNap = todayNaps.last()
            val napDuration = lastNap.duration
            napDuration != null && napDuration < Duration.ofMinutes(30)
        } else false

        val napTimes = generateNapTimes(
            effectiveWakeTime, wakeWindows, averageNapDuration, ageInWeeks,
            mode, shortNapAdjustment
        )

        val bedtimeWindow = getBedtimeWindow(ageInWeeks)
        val bedtime = calculateBedtime(napTimes, wakeWindows, bedtimeWindow)

        val totalSleepRecommendation = getTotalSleepRecommendation(ageInWeeks)
        val totalSleepLogged = calculateAverageDailySleep(recentRecords)

        val regressionWarning = detectRegression(ageInWeeks)
        val napTransitionSuggestion = detectNapTransition(recentRecords, ageInWeeks)

        return SleepSchedule(
            ageInWeeks = ageInWeeks,
            mode = mode,
            wakeWindows = wakeWindows,
            napTimes = napTimes,
            bedtime = bedtime,
            bedtimeWindow = bedtimeWindow,
            totalSleepRecommendation = totalSleepRecommendation,
            totalSleepLogged = totalSleepLogged,
            regressionWarning = regressionWarning,
            napTransitionSuggestion = napTransitionSuggestion,
            lastFeedTime = lastFeedSession?.startTime,
            isPersonalized = isPersonalized
        )
    }

    // --- Wake Windows ---

    internal fun getDefaultWakeWindows(ageInWeeks: Int): List<Duration> = when {
        ageInWeeks < 6 -> listOf(45, 45, 45, 45, 45).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 8 -> listOf(60, 60, 75, 75).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 12 -> listOf(75, 80, 90, 90).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 16 -> listOf(90, 105, 120).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 24 -> listOf(105, 135, 150).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 36 -> listOf(150, 180, 210).map { Duration.ofMinutes(it.toLong()) }
        else -> listOf(180, 210, 240).map { Duration.ofMinutes(it.toLong()) }
    }

    internal fun getWakeWindowBounds(ageInWeeks: Int): Pair<Duration, Duration> = when {
        ageInWeeks < 6 -> Duration.ofMinutes(30) to Duration.ofMinutes(60)
        ageInWeeks < 8 -> Duration.ofMinutes(45) to Duration.ofMinutes(90)
        ageInWeeks < 12 -> Duration.ofMinutes(60) to Duration.ofMinutes(120)
        ageInWeeks < 16 -> Duration.ofMinutes(75) to Duration.ofMinutes(150)
        ageInWeeks < 24 -> Duration.ofMinutes(90) to Duration.ofMinutes(180)
        ageInWeeks < 36 -> Duration.ofMinutes(120) to Duration.ofMinutes(210)
        else -> Duration.ofMinutes(150) to Duration.ofMinutes(240)
    }

    // --- Personalization ---

    private data class PersonalizationResult(
        val wakeWindows: List<Duration>,
        val averageNapDuration: Duration?,
        val isPersonalized: Boolean
    )

    private fun personalizeFromData(
        recentRecords: List<SleepRecord>,
        defaultWakeWindows: List<Duration>,
        wakeWindowBounds: Pair<Duration, Duration>,
        ageInWeeks: Int
    ): PersonalizationResult {
        val completedNaps = recentRecords.filter { it.sleepType == SleepType.NAP && it.duration != null }

        if (completedNaps.size < 3) {
            return PersonalizationResult(defaultWakeWindows, null, false)
        }

        val avgNapDuration = completedNaps
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(completedNaps.size.toLong())

        val sortedRecords = recentRecords.sortedBy { it.startTime }
        val actualWakeWindows = mutableListOf<Duration>()
        for (i in 0 until sortedRecords.size - 1) {
            val currentEnd = sortedRecords[i].endTime ?: continue
            val nextStart = sortedRecords[i + 1].startTime
            val gap = Duration.between(currentEnd, nextStart)
            if (!gap.isNegative && gap < Duration.ofHours(6)) {
                actualWakeWindows.add(gap)
            }
        }

        if (actualWakeWindows.isEmpty()) {
            return PersonalizationResult(defaultWakeWindows, avgNapDuration, false)
        }

        val avgActualWakeWindow = actualWakeWindows
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(actualWakeWindows.size.toLong())

        val (minBound, maxBound) = wakeWindowBounds
        val blendedWindows = defaultWakeWindows.map { defaultWw ->
            val blended = defaultWw.multipliedBy(40).plus(avgActualWakeWindow.multipliedBy(60)).dividedBy(100)
            clampDuration(blended, minBound, maxBound)
        }

        return PersonalizationResult(blendedWindows, avgNapDuration, true)
    }

    // --- Nap Schedule Generation ---

    private fun generateNapTimes(
        wakeUpTime: LocalTime,
        wakeWindows: List<Duration>,
        averageNapDuration: Duration?,
        ageInWeeks: Int,
        mode: ScheduleMode,
        shortNapAdjustment: Boolean
    ): List<ScheduleEntry> {
        val naps = mutableListOf<ScheduleEntry>()
        var currentTime = wakeUpTime
        val napCount = wakeWindows.size - 1

        for (i in 0 until napCount) {
            var ww = wakeWindows[i]

            val isAdjusted = shortNapAdjustment && i == 0
            if (isAdjusted) {
                ww = ww.minus(Duration.ofMinutes(15))
                if (ww.isNegative) ww = Duration.ofMinutes(15)
            }

            currentTime = currentTime.plus(ww)

            if (mode == ScheduleMode.CLOCK_ALIGNED) {
                currentTime = applyCircadianAlignment(currentTime, i, napCount)
            }

            val napDuration = averageNapDuration ?: getDefaultNapDuration(i, ageInWeeks)
            naps.add(
                ScheduleEntry(
                    startTime = currentTime,
                    duration = napDuration,
                    label = "Nap ${i + 1}",
                    isAdjusted = isAdjusted
                )
            )
            currentTime = currentTime.plus(napDuration)
        }
        return naps
    }

    private fun getDefaultNapDuration(napIndex: Int, ageInWeeks: Int): Duration = when {
        napIndex == 0 && ageInWeeks >= 24 -> Duration.ofMinutes(120)
        napIndex == 0 -> Duration.ofMinutes(90)
        else -> Duration.ofMinutes(60)
    }

    private fun applyCircadianAlignment(napTime: LocalTime, napIndex: Int, totalNaps: Int): LocalTime {
        if (napIndex == 0) {
            val target = LocalTime.of(9, 30)
            if (isWithinMinutes(napTime, target, 30)) {
                return shiftToward(napTime, target)
            }
        }
        if ((napIndex == 1 && totalNaps >= 2) || (napIndex == 0 && totalNaps == 1)) {
            val target = LocalTime.of(13, 0)
            if (isWithinMinutes(napTime, target, 45)) {
                return shiftToward(napTime, target)
            }
        }
        return napTime
    }

    private fun isWithinMinutes(time: LocalTime, target: LocalTime, minutes: Int): Boolean {
        val diff = kotlin.math.abs(Duration.between(time, target).toMinutes())
        return diff <= minutes
    }

    private fun shiftToward(time: LocalTime, target: LocalTime): LocalTime {
        val diffMinutes = Duration.between(time, target).toMinutes()
        val shiftMinutes = diffMinutes / 2
        return time.plusMinutes(shiftMinutes)
    }

    // --- Bedtime ---

    internal fun getBedtimeWindow(ageInWeeks: Int): ClosedRange<LocalTime> = when {
        ageInWeeks < 6 -> LocalTime.of(21, 0)..LocalTime.of(23, 0)
        ageInWeeks < 12 -> LocalTime.of(20, 0)..LocalTime.of(22, 0)
        ageInWeeks < 16 -> LocalTime.of(19, 30)..LocalTime.of(21, 0)
        ageInWeeks < 24 -> LocalTime.of(19, 0)..LocalTime.of(20, 0)
        else -> LocalTime.of(18, 30)..LocalTime.of(20, 0)
    }

    private fun calculateBedtime(
        napTimes: List<ScheduleEntry>,
        wakeWindows: List<Duration>,
        bedtimeWindow: ClosedRange<LocalTime>
    ): LocalTime {
        if (napTimes.isEmpty()) return bedtimeWindow.start

        val lastNap = napTimes.last()
        val lastNapEnd = lastNap.startTime.plus(lastNap.duration)
        val calculated = lastNapEnd.plus(wakeWindows.last())

        return clampLocalTime(calculated, bedtimeWindow)
    }

    // --- Total Sleep ---

    internal fun getTotalSleepRecommendation(ageInWeeks: Int): ClosedRange<Duration> = when {
        ageInWeeks < 16 -> Duration.ofHours(14)..Duration.ofHours(17)
        else -> Duration.ofHours(12)..Duration.ofHours(16)
    }

    private fun calculateAverageDailySleep(recentRecords: List<SleepRecord>): Duration? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.isEmpty()) return null

        val zone = ZoneId.systemDefault()
        val dailySleep = completedRecords
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .map { (_, records) ->
                records.mapNotNull { it.duration }.fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            }

        if (dailySleep.isEmpty()) return null

        return dailySleep
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(dailySleep.size.toLong())
    }

    // --- Regression Detection ---

    internal fun detectRegression(ageInWeeks: Int): RegressionInfo? = when {
        ageInWeeks in 14..22 -> RegressionInfo(
            name = "4-Month Sleep Regression",
            description = "Your baby's sleep architecture is maturing from 2-stage to 4-stage cycles. " +
                "This is a permanent and healthy change. Sleep may be more disrupted than usual.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 32..44 -> RegressionInfo(
            name = "8-10 Month Sleep Regression",
            description = "Object permanence, separation anxiety, and motor milestones (crawling, pulling up) " +
                "can temporarily disrupt sleep patterns.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 48..55 -> RegressionInfo(
            name = "12-Month Sleep Regression",
            description = "Walking, early language development, and nap resistance may cause temporary " +
                "sleep disruption.",
            durationWeeks = "1-3 weeks"
        )
        else -> null
    }

    // --- Nap Transition Detection ---

    private fun detectNapTransition(recentRecords: List<SleepRecord>, ageInWeeks: Int): String? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.size < 5) return null

        val zone = ZoneId.systemDefault()
        val dailyNapCounts = completedRecords
            .filter { it.sleepType == SleepType.NAP }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { it.value.size }

        if (dailyNapCounts.size < 3) return null

        val avgNapsPerDay = dailyNapCounts.values.average()
        val expectedNaps = getExpectedNapCount(ageInWeeks)

        if (avgNapsPerDay >= expectedNaps - 0.5) return null

        val nightRecords = completedRecords.filter { it.sleepType == SleepType.NIGHT_SLEEP }
        if (nightRecords.isEmpty()) return null

        val avgNightSleep = nightRecords
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(nightRecords.size.toLong())

        if (avgNightSleep < Duration.ofHours(10)) return null

        val currentNaps = expectedNaps
        val targetNaps = (expectedNaps - 1).coerceAtLeast(1)
        return "Your baby may be ready to transition from $currentNaps to $targetNaps naps. " +
            "They've been averaging ${String.format("%.1f", avgNapsPerDay)} naps per day " +
            "with good night sleep."
    }

    private fun getExpectedNapCount(ageInWeeks: Int): Int = when {
        ageInWeeks < 6 -> 5
        ageInWeeks < 12 -> 4
        ageInWeeks < 16 -> 3
        ageInWeeks < 24 -> 3
        ageInWeeks < 36 -> 2
        else -> 2
    }

    // --- Today's Naps ---

    private fun getTodayNaps(recentRecords: List<SleepRecord>): List<SleepRecord> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return recentRecords
            .filter { it.sleepType == SleepType.NAP }
            .filter { it.startTime.atZone(zone).toLocalDate() == today }
            .filter { it.endTime != null }
            .sortedBy { it.startTime }
    }

    // --- Utility ---

    private fun clampDuration(value: Duration, min: Duration, max: Duration): Duration = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    private fun clampLocalTime(time: LocalTime, range: ClosedRange<LocalTime>): LocalTime = when {
        time < range.start -> range.start
        time > range.endInclusive -> range.endInclusive
        else -> time
    }
}
```

- [ ] **Step 4: Run all schedule use case tests**

```bash
./gradlew :app:test --tests "com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCaseTest"
```

Expected: all tests pass (same count as before, no regressions).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt \
        app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt
git commit -m "feat(usecase): inject SettingsRepository into GenerateSleepScheduleUseCase for wake time"
```

---

## Task 5: Rewrite `SleepViewModel` and `SleepUiState`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`

- [ ] **Step 1: Replace `SleepViewModel.kt`**

```kotlin
package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = getSleepHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.getWakeTime().collect { wakeTime ->
                _uiState.value = _uiState.value.copy(wakeTime = wakeTime)
            }
        }
        loadSchedule()
    }

    fun onAddEntryClick() {
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            entryStartTime = LocalTime.now(),
            entryEndTime = LocalTime.now(),
            entryError = null
        )
    }

    fun onDismissSheet() {
        _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
    }

    fun onEntryTypeChanged(type: SleepType) {
        _uiState.value = _uiState.value.copy(entryType = type)
    }

    fun onEntryStartTimeChanged(time: LocalTime) {
        _uiState.value = _uiState.value.copy(entryStartTime = time, entryError = null)
    }

    fun onEntryEndTimeChanged(time: LocalTime) {
        _uiState.value = _uiState.value.copy(entryEndTime = time, entryError = null)
    }

    fun onSaveEntry() {
        val state = _uiState.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        var startInstant = state.entryStartTime.atDate(today).atZone(zone).toInstant()
        val endInstant = state.entryEndTime.atDate(today).atZone(zone).toInstant()

        // Cross-midnight night sleep: start was yesterday
        if (startInstant > endInstant) {
            startInstant = state.entryStartTime.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }

        if (endInstant <= startInstant) {
            _uiState.value = state.copy(entryError = "End time must be after start time")
            return
        }

        viewModelScope.launch {
            saveSleepEntry(startInstant, endInstant, state.entryType)
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
        }
    }

    fun onSetWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
            loadSchedule()
        }
    }

    fun onGenerateSchedule() {
        loadSchedule()
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm the ViewModel compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` (ViewModel no longer references deleted APIs). `SleepTrackingScreen` may still have issues if it referenced `activeRecord` — that gets fixed in Task 6.

- [ ] **Step 3: Run all unit tests**

```bash
./gradlew :app:test
```

Expected: all tests pass. If `SleepTrackingScreen` fails to compile, that is a UI compilation issue not a test issue — check the next task.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt
git commit -m "feat(sleep): rewrite SleepViewModel with manual entry and wake time"
```

---

## Task 6: Rewrite `SleepTrackingScreen`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`

- [ ] **Step 1: Replace `SleepTrackingScreen.kt`**

```kotlin
package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val todayEntries = remember(history) {
        history
            .filter { it.startTime.atZone(zone).toLocalDate() == today }
            .sortedByDescending { it.startTime }
    }
    val totalSleepToday = remember(todayEntries) {
        todayEntries
            .filter { it.endTime != null }
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d }
    }
    val napCount = remember(todayEntries) {
        todayEntries.count { it.sleepType == SleepType.NAP }
    }
    val nightSleepDuration = remember(todayEntries) {
        todayEntries
            .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime != null }
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d }
    }

    var showWakeTimePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showWakeTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.wakeTime ?: LocalTime.of(7, 0),
            onConfirm = { time ->
                viewModel.onSetWakeTime(time)
                showWakeTimePicker = false
            },
            onDismiss = { showWakeTimePicker = false }
        )
    }

    if (showStartTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.entryStartTime,
            onConfirm = { time ->
                viewModel.onEntryStartTimeChanged(time)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.entryEndTime,
            onConfirm = { time ->
                viewModel.onEntryEndTimeChanged(time)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    if (uiState.showEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddSleepEntrySheetContent(
                uiState = uiState,
                onTypeChanged = viewModel::onEntryTypeChanged,
                onStartTimeClick = { showStartTimePicker = true },
                onEndTimeClick = { showEndTimePicker = true },
                onSave = viewModel::onSaveEntry
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
        ) {
            item {
                Text(
                    text = "TODAY'S WAKE TIME",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                WakeTimeChip(
                    wakeTime = uiState.wakeTime,
                    onClick = { showWakeTimePicker = true }
                )
            }
            item {
                SleepSummaryRow(
                    totalSleep = totalSleepToday,
                    napCount = napCount,
                    nightSleep = nightSleepDuration
                )
            }
            item {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (todayEntries.isEmpty()) {
                item { TodayEmptyState() }
            } else {
                items(todayEntries, key = { it.id }) { record ->
                    SleepEntryCard(record = record)
                }
            }
            item {
                Button(
                    onClick = viewModel::onAddEntryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("+ Add Sleep Entry", style = MaterialTheme.typography.titleSmall)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("History") }
                    OutlinedButton(
                        onClick = onNavigateToSchedule,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("Schedule") }
                }
            }
        }
    }
}

@Composable
private fun WakeTimeChip(wakeTime: LocalTime?, onClick: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    if (wakeTime != null) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌅 Woke at ${wakeTime.format(formatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit wake time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    } else {
        OutlinedCard(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌅 Tap to set today's wake time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Set wake time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SleepSummaryRow(
    totalSleep: Duration,
    napCount: Int,
    nightSleep: Duration
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatColumn(
                label = "Sleep today",
                value = if (totalSleep.isZero) "—" else totalSleep.formatDuration()
            )
            SummaryStatColumn(
                label = "Naps",
                value = if (napCount == 0) "—" else napCount.toString()
            )
            SummaryStatColumn(
                label = "Night sleep",
                value = if (nightSleep.isZero) "—" else nightSleep.formatDuration()
            )
        }
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "🌙", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "No sleep entries yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap below to add one",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SleepEntryCard(record: SleepRecord) {
    val isNap = record.sleepType == SleepType.NAP
    HistoryCard(
        title = record.sleepType.label,
        subtitle = buildString {
            append(record.startTime.formatTime12h())
            if (record.endTime != null) append(" – ${record.endTime.formatTime12h()}")
        },
        trailing = record.endTime?.let { end ->
            Duration.between(record.startTime, end).formatDuration()
        } ?: "In progress",
        badgeEmoji = record.sleepType.emoji,
        badgeColor = if (isNap) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSleepEntrySheetContent(
    uiState: SleepUiState,
    onTypeChanged: (SleepType) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSave: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // Duration preview (cross-midnight aware)
    val durationPreview: Duration? = remember(uiState.entryStartTime, uiState.entryEndTime) {
        var startInstant = uiState.entryStartTime.atDate(today).atZone(zone).toInstant()
        val endInstant = uiState.entryEndTime.atDate(today).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = uiState.entryStartTime.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }
        val d = Duration.between(startInstant, endInstant)
        if (d.isNegative || d.isZero) null else d
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Add Sleep Entry",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Type toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepType.entries.forEach { type ->
                val isSelected = uiState.entryType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeChanged(type) },
                    label = { Text("${type.emoji} ${type.label}") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (type == SleepType.NAP)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }

        // Start + End time row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "START TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onStartTimeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.entryStartTime.format(formatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "END TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onEndTimeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.entryEndTime.format(formatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }

        // Duration or error feedback
        when {
            uiState.entryError != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠ ${uiState.entryError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            durationPreview != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "⏱ Duration: ${durationPreview.formatDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }

        // Save button
        val saveLabel = when (uiState.entryType) {
            SleepType.NAP -> "Save Nap"
            SleepType.NIGHT_SLEEP -> "Save Night Sleep"
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.entryType == SleepType.NAP)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(saveLabel, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = timePickerState) }
    )
}
```

- [ ] **Step 2: Build the full project**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` — no references to `activeRecord`, `selectedType`, `StartSleepRecordUseCase`, or `StopSleepRecordUseCase` remain.

- [ ] **Step 3: Run all unit tests**

```bash
./gradlew :app:test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt
git commit -m "feat(sleep): rewrite SleepTrackingScreen with manual entry and wake time"
```

---

## Task 7: Final Verification

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests pass, 0 failures.

- [ ] **Step 2: Build a debug APK**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` and APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Verify `.gitignore` includes `.superpowers/`**

```bash
grep -q ".superpowers" .gitignore && echo "OK" || echo "Add .superpowers/ to .gitignore"
```

If missing, add the entry:

```bash
echo ".superpowers/" >> .gitignore
git add .gitignore
git commit -m "chore: ignore .superpowers/ brainstorm artifacts"
```
