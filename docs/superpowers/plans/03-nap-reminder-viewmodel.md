# Nap Reminder ViewModel Integration — Implementation Plan

LINEAR_ISSUE: AKA-32

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `NapReminderScheduler` into `SleepViewModel` so a nap reminder alarm is scheduled when a NAP session stops (if enabled) and cancelled whenever any new sleep session starts.

**Architecture:** `SleepViewModel` and `SettingsViewModel` both receive `NapReminderScheduler` as a constructor-injected dependency. In `SleepViewModel`: `onStopRecord()` reads `getNapReminderEnabled()` and `getNapReminderDelayMinutes()` from `SettingsRepository` at call time (`.first()`) and conditionally calls `schedule()`; `onStartRecord()` calls `cancel()` unconditionally before starting the record. In `SettingsViewModel`: `onNapReminderToggleChanged(false)` calls `cancel()` immediately so a pending alarm is cleared as soon as the user disables the feature (defense-in-depth: the receiver also checks the flag at fire time, but this provides instant cancellation). No new state, no new composable — this plan is pure wiring.

**Tech Stack:** Kotlin, Hilt `@HiltViewModel`, Kotlin Coroutines, JUnit 5, MockK, Turbine

**Suggested branch:** `feat/nap-reminder-viewmodel`

**Dependencies:** Requires Plan 01 (AKA-30) and Plan 02 (AKA-31) merged — `SettingsRepository.getNapReminderEnabled()` / `getNapReminderDelayMinutes()` and `NapReminderScheduler` interface must both exist in the codebase before starting this plan.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `ui/sleep/SleepViewModel.kt` | Modify | Inject `NapReminderScheduler`; cancel on start; schedule on NAP stop when enabled |
| `src/test/.../SleepViewModelTest.kt` | Modify | Add mock + 4 new tests; stub `getNapReminderEnabled` / `getNapReminderDelayMinutes` in `setUp()` |
| `ui/settings/SettingsViewModel.kt` | Modify | Inject `NapReminderScheduler`; call `cancel()` when toggle is turned off |
| `src/test/.../SettingsViewModelTest.kt` | Modify | Add mock + 1 new test for cancel-on-disable |

---

### Task 1: SleepViewModel integration (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/sleep/SleepViewModelTest.kt`

The test file already has a `createViewModel()` helper and a `setUp()` that stubs all existing dependencies. Changes here must not break any of the 34 existing tests.

- [ ] **Step 1: Update SleepViewModelTest — setUp, createViewModel, and 4 new tests**

Make these changes to `SleepViewModelTest.kt`:

**a) Add import at top (with existing imports):**
```kotlin
import com.babytracker.manager.NapReminderScheduler
import io.mockk.verify
```

**b) Add field declaration with existing `lateinit var` declarations:**
```kotlin
private lateinit var napReminderScheduler: NapReminderScheduler
```

**c) In `setUp()`, add these 5 lines after the existing mock initializations (after `syncToFirestore = mockk()`):**
```kotlin
napReminderScheduler = mockk()
every { napReminderScheduler.cancel() } returns Unit
every { napReminderScheduler.schedule(any(), any()) } returns Unit
every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
every { settingsRepository.getNapReminderDelayMinutes() } returns flowOf(60)
```

The `getNapReminderEnabled` default of `false` keeps all existing `onStopRecord` tests passing unchanged — no reminder is scheduled, which matches their expectations.

**d) Update `createViewModel()` to pass the new dependency:**
```kotlin
private fun createViewModel() = SleepViewModel(
    saveSleepEntry,
    updateSleepEntry,
    deleteSleepEntry,
    getSleepHistory,
    generateSchedule,
    getBabyProfile,
    settingsRepository,
    startRecord,
    stopRecord,
    sleepNotificationScheduler,
    syncToFirestore,
    napReminderScheduler,
)
```

**e) Add 4 new test methods at the end of the class:**

```kotlin
@Test
fun `onStopRecord schedules nap reminder when NAP session stops and reminder is enabled`() = runTest {
    val inProgress = SleepRecord(
        id = 1L, startTime = Instant.now().minusSeconds(300), sleepType = SleepType.NAP
    )
    every { getSleepHistory() } returns flowOf(listOf(inProgress))
    every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
    every { settingsRepository.getNapReminderDelayMinutes() } returns flowOf(60)
    coJustRun { stopRecord(any()) }
    viewModel = createViewModel()
    val collectJob = launch { viewModel.activeSleepSession.collect {} }
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onStopRecord()
    testDispatcher.scheduler.advanceUntilIdle()

    verify { napReminderScheduler.schedule(any(), 60) }
    collectJob.cancel()
}

@Test
fun `onStopRecord does not schedule nap reminder when reminder is disabled`() = runTest {
    val inProgress = SleepRecord(
        id = 2L, startTime = Instant.now().minusSeconds(300), sleepType = SleepType.NAP
    )
    every { getSleepHistory() } returns flowOf(listOf(inProgress))
    every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
    coJustRun { stopRecord(any()) }
    viewModel = createViewModel()
    val collectJob = launch { viewModel.activeSleepSession.collect {} }
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onStopRecord()
    testDispatcher.scheduler.advanceUntilIdle()

    verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
    collectJob.cancel()
}

@Test
fun `onStopRecord does not schedule nap reminder when sleep type is NIGHT_SLEEP`() = runTest {
    val inProgress = SleepRecord(
        id = 3L, startTime = Instant.now().minusSeconds(300), sleepType = SleepType.NIGHT_SLEEP
    )
    every { getSleepHistory() } returns flowOf(listOf(inProgress))
    every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
    coJustRun { stopRecord(any()) }
    viewModel = createViewModel()
    val collectJob = launch { viewModel.activeSleepSession.collect {} }
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onStopRecord()
    testDispatcher.scheduler.advanceUntilIdle()

    verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
    collectJob.cancel()
}

@Test
fun `onStartRecord always cancels pending nap reminder regardless of sleep type`() = runTest {
    val record = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
    coEvery { startRecord(SleepType.NIGHT_SLEEP) } returns record
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onStartRecord(SleepType.NIGHT_SLEEP)
    testDispatcher.scheduler.advanceUntilIdle()

    verify { napReminderScheduler.cancel() }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.sleep.SleepViewModelTest" 2>&1 | tail -30
```

Expected: compile error — `SleepViewModel` constructor does not accept `NapReminderScheduler`, `settingsRepository.getNapReminderEnabled()` does not exist. If tests somehow pass, stop — the logic already exists.

- [ ] **Step 3: Add NapReminderScheduler to SleepViewModel constructor**

In `SleepViewModel.kt`, add the import and the new constructor parameter.

Add to imports:
```kotlin
import com.babytracker.manager.NapReminderScheduler
import kotlinx.coroutines.flow.first
```

Update the constructor (add `napReminderScheduler` as the last parameter):
```kotlin
@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val updateSleepEntry: UpdateSleepEntryUseCase,
    private val deleteSleepEntry: DeleteSleepEntryUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val startRecord: StartSleepRecordUseCase,
    private val stopRecord: StopSleepRecordUseCase,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val napReminderScheduler: NapReminderScheduler,
) : ViewModel() {
```

- [ ] **Step 4: Run tests to confirm they still fail (logic not yet added)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.sleep.SleepViewModelTest" 2>&1 | tail -30
```

Expected: test failures, NOT compile errors. The 4 new tests fail because `napReminderScheduler.cancel()` / `.schedule()` are never called. Existing tests may now pass. If compile errors remain, fix them before continuing.

- [ ] **Step 5: Update onStartRecord to cancel pending nap reminder**

Replace the existing `onStartRecord` function:

```kotlin
fun onStartRecord(sleepType: SleepType) {
    viewModelScope.launch {
        napReminderScheduler.cancel()
        val record = startRecord(sleepType)
        sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime)
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }
}
```

- [ ] **Step 6: Update onStopRecord to schedule nap reminder when conditions are met**

Replace the existing `onStopRecord` function:

```kotlin
fun onStopRecord() {
    val session = activeSleepSession.value ?: return
    viewModelScope.launch {
        stopRecord(session.id)
        sleepNotificationScheduler.cancel()
        val enabled = settingsRepository.getNapReminderEnabled().first()
        if (enabled && session.sleepType == SleepType.NAP) {
            val delayMinutes = settingsRepository.getNapReminderDelayMinutes().first()
            napReminderScheduler.schedule(Instant.now(), delayMinutes)
        }
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }
}
```

Note: `Instant` is already imported in `SleepViewModel.kt` (used by `SleepRecord`). If not, add:
```kotlin
import java.time.Instant
```

- [ ] **Step 7: Run all SleepViewModelTest to confirm all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.sleep.SleepViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 38 tests pass (34 existing + 4 new).

- [ ] **Step 8: Run the full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, zero failures across all test classes.

- [ ] **Step 9: Format and lint**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations. Fix any that appear (never use `@Suppress`).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt
git add app/src/test/java/com/babytracker/ui/sleep/SleepViewModelTest.kt
git commit -m "feat(sleep): integrate NapReminderScheduler into SleepViewModel"
```

---

### Task 2: SettingsViewModel — cancel pending alarm on nap reminder toggle-off (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt`

The test file was updated in Plan 01 to promote `getBabyProfile`, `countRecentValidIntervals`, and `permissionChecker` to class fields. The SettingsViewModel constructor currently has 5 parameters (from Plan 01); this task adds a 6th.

- [ ] **Step 1: Add failing test in SettingsViewModelTest**

Add this import at the top of `SettingsViewModelTest.kt` (with existing imports):

```kotlin
import com.babytracker.manager.NapReminderScheduler
import io.mockk.verify
```

Add field declaration with the existing `lateinit var` declarations:

```kotlin
private lateinit var napReminderScheduler: NapReminderScheduler
```

In `setUp()`, after the last existing mock initialization, add:

```kotlin
napReminderScheduler = mockk()
every { napReminderScheduler.cancel() } returns Unit
```

Update the `SettingsViewModel(...)` constructor call in `setUp()` to pass `napReminderScheduler` as the last argument:

```kotlin
viewModel = SettingsViewModel(
    getBabyProfile,
    settingsRepository,
    mockk(),
    countRecentValidIntervals,
    permissionChecker,
    napReminderScheduler,
)
```

Add this test method at the end of the class:

```kotlin
@Test
fun `onNapReminderToggleChanged false cancels pending nap reminder alarm`() = runTest {
    coJustRun { settingsRepository.setNapReminderEnabled(any()) }

    viewModel.onNapReminderToggleChanged(false)
    testDispatcher.scheduler.advanceUntilIdle()

    verify { napReminderScheduler.cancel() }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelTest" 2>&1 | tail -30
```

Expected: compile error — `SettingsViewModel` does not yet accept `NapReminderScheduler`. If tests somehow pass, stop — cancel is already wired.

- [ ] **Step 3: Add NapReminderScheduler to SettingsViewModel constructor**

Add import to `SettingsViewModel.kt`:

```kotlin
import com.babytracker.manager.NapReminderScheduler
```

Add `napReminderScheduler` as the last constructor parameter:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveBabyProfile: SaveBabyProfileUseCase,
    private val countRecentValidIntervals: CountRecentValidIntervalsUseCase,
    private val notificationPermissionChecker: NotificationPermissionChecker,
    private val napReminderScheduler: NapReminderScheduler,
) : ViewModel() {
```

- [ ] **Step 4: Update onNapReminderToggleChanged to cancel when disabling**

Replace the existing `onNapReminderToggleChanged` function (added in Plan 01):

```kotlin
fun onNapReminderToggleChanged(enabled: Boolean) {
    viewModelScope.launch {
        settingsRepository.setNapReminderEnabled(enabled)
        if (!enabled) napReminderScheduler.cancel()
    }
}
```

- [ ] **Step 5: Run all SettingsViewModelTest to confirm all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass (existing + new).

- [ ] **Step 6: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, zero failures.

- [ ] **Step 7: Format and lint**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations. Fix any that appear (never use `@Suppress`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt
git add app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): cancel pending nap alarm on reminder toggle-off"
```

---

## Acceptance Criteria

- [ ] `SleepViewModel` constructor accepts `NapReminderScheduler` as a Hilt-injected parameter
- [ ] `onStartRecord()` calls `napReminderScheduler.cancel()` before calling `startRecord()` — applies to all sleep types (NAP and NIGHT_SLEEP)
- [ ] `onStopRecord()` reads `getNapReminderEnabled()` after stopping; calls `napReminderScheduler.schedule(Instant.now(), delayMinutes)` only when `enabled == true && session.sleepType == SleepType.NAP`
- [ ] `onStopRecord()` does NOT call `schedule()` when disabled, regardless of sleep type
- [ ] `onStopRecord()` does NOT call `schedule()` when `sleepType == NIGHT_SLEEP`, regardless of enabled state
- [ ] 4 new `SleepViewModelTest` tests pass
- [ ] All 34 pre-existing `SleepViewModelTest` tests still pass
- [ ] `SettingsViewModel` constructor accepts `NapReminderScheduler` as a Hilt-injected parameter
- [ ] `onNapReminderToggleChanged(false)` calls `napReminderScheduler.cancel()` after persisting the setting
- [ ] 1 new `SettingsViewModelTest` test passes (`onNapReminderToggleChanged false cancels pending nap reminder alarm`)
- [ ] `./gradlew ktlintFormat && ./gradlew detekt` clean
- [ ] `./gradlew :app:testDebugUnitTest` — zero failures
