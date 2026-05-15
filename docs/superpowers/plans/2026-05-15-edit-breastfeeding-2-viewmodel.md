# Edit Past Breastfeeding Session — PR 2: ViewModel Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `EditSheetState` + edit/delete handlers to `BreastfeedingViewModel`. No UI composables yet. Fully testable in isolation.

**Branch:** `feat/breastfeeding-edit-viewmodel` (branch from `feat/breastfeeding-edit-domain` or `main` after PR 1 merges)

**Depends on:** PR 1 (`2026-05-15-edit-breastfeeding-1-domain.md`) — requires `UpdateBreastfeedingSessionUseCase`, `DeleteBreastfeedingSessionUseCase`, `validateBreastfeedingEdit`, `foldPause`.

**Part of:** Edit Past Breastfeeding Session feature — see sibling plans:
- **PR 1:** `2026-05-15-edit-breastfeeding-1-domain.md`
- **PR 3:** `2026-05-15-edit-breastfeeding-3-ui.md`

**Architecture:** `EditSheetState` data class added inside `ui/breastfeeding/`. `BreastfeedingUiState` gains `editSheet: EditSheetState?`. ViewModel gains 7 handlers. The `combine { }` block in `init` preserves `editSheet` across active-session Flow emissions so the open sheet isn't blown away. Save and delete orchestrate notification cancellation and Firestore sync exactly like `onStopSession`.

**Tech Stack:** Kotlin 2.3.20, Jetpack Compose, Hilt 2.59, Kotlin Coroutines/Flow, JUnit 5, MockK 1.13.13, Turbine 1.2.0.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt` |
| Create | `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingEditSheetViewModelTest.kt` |

---

## Task 1: Edit Sheet State + Handlers on ViewModel

- [ ] **Step 1: Add EditSheetState model and extend BreastfeedingUiState**

Open `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`.

Add these imports (do not remove existing imports):

```kotlin
import com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.foldPause
import com.babytracker.domain.usecase.breastfeeding.validateBreastfeedingEdit
```

Add `EditSheetState` data class near `LastFeedingSummaryState`:

```kotlin
data class EditSheetState(
    val original: BreastfeedingSession,
    val editedStart: Instant,
    val editedEnd: Instant?,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val deleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val isDirty: Boolean
        get() = editedStart != original.startTime || editedEnd != original.endTime

    val canSave: Boolean
        get() = isDirty && validationError == null && !isSaving && !isDeleting
}
```

Update `BreastfeedingUiState` to add `editSheet`:

```kotlin
data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val lastFeedingSummary: LastFeedingSummaryState = LastFeedingSummaryState.Empty,
    val error: String? = null,
    val currentSide: BreastSide? = null,
    val editSheet: EditSheetState? = null,
)
```

- [ ] **Step 2: Inject new use cases + preserve editSheet in combine block**

Update the constructor parameter list (insert `updateSession` and `deleteSession` before `repository`):

```kotlin
@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    getHistory: GetBreastfeedingHistoryUseCase,
    private val pauseSession: PauseBreastfeedingSessionUseCase,
    private val resumeSession: ResumeBreastfeedingSessionUseCase,
    private val updateSession: UpdateBreastfeedingSessionUseCase,
    private val deleteSession: DeleteBreastfeedingSessionUseCase,
    private val repository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncToFirestore: SyncToFirestoreUseCase,
) : ViewModel() {
```

In the `combine { ... }` block in `init`, preserve `editSheet` so the active-session refresh does not blow the sheet state away. Add this field to the emitted `BreastfeedingUiState(...)` constructor call:

```kotlin
                    editSheet = _uiState.value.editSheet,
```

- [ ] **Step 3: Add handler functions**

Append the following handler functions at the bottom of the class, before the closing brace (place above `currentSide()` if that exists):

```kotlin
    fun onEditSessionClick(session: BreastfeedingSession) {
        val state = EditSheetState(
            original = session,
            editedStart = session.startTime,
            editedEnd = session.endTime,
            validationError = null,
        )
        _uiState.value = _uiState.value.copy(editSheet = state)
    }

    fun onEditStartChanged(newStart: Instant) {
        val current = _uiState.value.editSheet ?: return
        val (projectedPausedMs, _) = foldPause(current.original, newStart, current.editedEnd)
        val error = validateBreastfeedingEdit(
            startTime = newStart,
            endTime = current.editedEnd,
            pausedDurationMs = projectedPausedMs,
            now = Instant.now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(editedStart = newStart, validationError = error)
        )
    }

    fun onEditEndChanged(newEnd: Instant?) {
        val current = _uiState.value.editSheet ?: return
        val (projectedPausedMs, _) = foldPause(current.original, current.editedStart, newEnd)
        val error = validateBreastfeedingEdit(
            startTime = current.editedStart,
            endTime = newEnd,
            pausedDurationMs = projectedPausedMs,
            now = Instant.now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(editedEnd = newEnd, validationError = error)
        )
    }

    fun onEditDismiss() {
        _uiState.value = _uiState.value.copy(editSheet = null)
    }

    fun onEditSave() {
        val current = _uiState.value.editSheet ?: return
        if (!current.canSave) return
        val wasInProgress = current.original.endTime == null
        val nowHasEnd = current.editedEnd != null
        _uiState.value = _uiState.value.copy(editSheet = current.copy(isSaving = true))
        viewModelScope.launch {
            val result = runCatching {
                updateSession(current.original, current.editedStart, current.editedEnd)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    editSheet = current.copy(isSaving = false),
                    error = "Could not save changes. Please try again.",
                )
                return@launch
            }
            if (wasInProgress && nowHasEnd) {
                notificationCoordinator.cancelAllSessionNotifications()
            }
            _uiState.value = _uiState.value.copy(editSheet = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onDeleteRequested() {
        val current = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = current.copy(deleteConfirm = true))
    }

    fun onDeleteCancelled() {
        val current = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = current.copy(deleteConfirm = false))
    }

    fun onDeleteConfirmed() {
        val current = _uiState.value.editSheet ?: return
        val wasInProgress = current.original.endTime == null
        _uiState.value = _uiState.value.copy(editSheet = current.copy(isDeleting = true))
        viewModelScope.launch {
            val result = runCatching { deleteSession(current.original) }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    editSheet = current.copy(isDeleting = false, deleteConfirm = false),
                    error = "Could not delete session. Please try again.",
                )
                return@launch
            }
            if (wasInProgress) {
                notificationCoordinator.cancelAllSessionNotifications()
            }
            _uiState.value = _uiState.value.copy(editSheet = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

---

## Task 2: ViewModel Tests

- [ ] **Step 1: Write the failing ViewModel tests**

Create `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingEditSheetViewModelTest.kt`:

```kotlin
package com.babytracker.ui.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BreastfeedingEditSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var startSession: StartBreastfeedingSessionUseCase
    private lateinit var stopSession: StopBreastfeedingSessionUseCase
    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var getHistory: GetBreastfeedingHistoryUseCase
    private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
    private lateinit var updateSession: UpdateBreastfeedingSessionUseCase
    private lateinit var deleteSession: DeleteBreastfeedingSessionUseCase
    private lateinit var repository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val sampleSession = BreastfeedingSession(
        id = 42L,
        startTime = Instant.parse("2026-05-14T22:00:00Z"),
        endTime = Instant.parse("2026-05-14T22:35:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        startSession = mockk()
        stopSession = mockk()
        switchSide = mockk()
        pauseSession = mockk()
        resumeSession = mockk()
        updateSession = mockk()
        deleteSession = mockk()
        repository = mockk()
        settingsRepository = mockk()
        notificationCoordinator = mockk(relaxed = true)
        syncToFirestore = mockk()

        getHistory = mockk()
        every { getHistory() } returns flowOf(emptyList())
        every { repository.getActiveSession() } returns MutableStateFlow(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        coEvery { syncToFirestore(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BreastfeedingViewModel(
        startSession, stopSession, switchSide, getHistory, pauseSession, resumeSession,
        updateSession, deleteSession, repository, settingsRepository,
        notificationCoordinator, syncToFirestore,
    )

    @Test
    fun onEditSessionClickOpensSheetWithOriginalValues() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditSessionClick(sampleSession)

        val sheet = vm.uiState.value.editSheet
        assertNotNull(sheet)
        assertEquals(sampleSession.startTime, sheet!!.editedStart)
        assertEquals(sampleSession.endTime, sheet.editedEnd)
        assertNull(sheet.validationError)
        assertEquals(false, sheet.isDirty)
    }

    @Test
    fun onEditEndChangedToBeforeStartProducesValidationError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onEditEndChanged(sampleSession.startTime.minusSeconds(60))

        val sheet = vm.uiState.value.editSheet!!
        assertEquals("End time must be after start time", sheet.validationError)
        assertEquals(false, sheet.canSave)
    }

    @Test
    fun onEditDismissClearsSheet() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onEditDismiss()

        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onEditSaveCallsUpdateUseCaseAndClosesSheet() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        val newEnd = sampleSession.startTime.plusSeconds(2400)
        vm.onEditEndChanged(newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateSession(sampleSession, sampleSession.startTime, newEnd) }
        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onEditSaveDoesNotCallUseCaseWhenValidationErrorPresent() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onEditEndChanged(sampleSession.startTime.minusSeconds(60))

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any()) }
        assertNotNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onDeleteRequestedShowsConfirm() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onDeleteRequested()

        assertTrue(vm.uiState.value.editSheet!!.deleteConfirm)
    }

    @Test
    fun onDeleteCancelledHidesConfirmKeepsSheet() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteCancelled()

        val sheet = vm.uiState.value.editSheet!!
        assertEquals(false, sheet.deleteConfirm)
    }

    @Test
    fun onDeleteConfirmedCallsDeleteAndClosesSheet() = runTest(testDispatcher) {
        coJustRun { deleteSession(any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteSession(sampleSession) }
        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun savingInProgressEndTriggersNotificationCancel() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val inProgress = sampleSession.copy(endTime = null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(inProgress)
        val newEnd = inProgress.startTime.plusSeconds(1800)
        vm.onEditEndChanged(newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { notificationCoordinator.cancelAllSessionNotifications() }
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.breastfeeding.BreastfeedingEditSheetViewModelTest"`
Expected: 9 tests pass. If anything under `com.babytracker.ui.breastfeeding` regresses, fix before continuing.

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingEditSheetViewModelTest.kt
git commit -m "feat(breastfeeding): add edit-sheet state and handlers to ViewModel"
```

---

## Final: Quality Checks + Open PR

- [ ] **Step 1: ktlintFormat and detekt**

```
./gradlew ktlintFormat
./gradlew detekt
```
Expected: both succeed; fix violations by adjusting code (never `@Suppress`).

- [ ] **Step 2: Push and open PR**

```bash
git push -u origin feat/breastfeeding-edit-viewmodel
```

Open PR targeting `main` (or `feat/breastfeeding-edit-domain` if stacking). Title: `feat(breastfeeding): add edit/delete session state and ViewModel handlers`.
