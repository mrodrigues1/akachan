# Task 6 — PumpingScreen + ViewModel + AddBagPromptSheet + nav route

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Ship the screen users land on to log a pumping session — Timer mode (default) and Manual entry mode — plus the post-stop `AddBagPromptSheet`. Wire it onto the nav graph.

**Depends on:** Task 4 (pumping use cases), Task 5 (inventory + sync use cases).

## Files

- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/com/babytracker/ui/pumping/PumpingViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/pumping/PumpingScreen.kt`
- Create: `app/src/main/java/com/babytracker/ui/pumping/AddBagPromptSheet.kt`
- Test: `app/src/test/java/com/babytracker/ui/pumping/PumpingViewModelTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/pumping/PumpingScreenTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/pumping/AddBagPromptSheetTest.kt`

## Implementation

### Step 1: Routes.kt

Add the three new constants now (the latter two are referenced by tasks 7 and 8 but kept here as a single edit):

```kotlin
const val PUMPING = "pumping"
const val PUMPING_HISTORY = "pumping/history"
const val INVENTORY = "inventory"
```

### Step 2: PumpingViewModel

Single `StateFlow<PumpingUiState>`. The state covers both modes and the bag prompt.

```kotlin
package com.babytracker.ui.pumping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.pumping.PausePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.ResumePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.SavePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StartPumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StopPumpingSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class PumpingMode { TIMER, MANUAL }

data class BagPromptState(
    val sessionId: Long?,
    val collectionDate: Instant,
    val volumeMl: Int,
    val notes: String = "",
    val volumeError: String? = null,
    val isSaving: Boolean = false,
)

data class ManualEntryState(
    val startTime: Instant,
    val endTime: Instant,
    val breast: PumpingBreast = PumpingBreast.LEFT,
    val volumeMl: String = "",
    val notes: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class PumpingUiState(
    val mode: PumpingMode = PumpingMode.TIMER,
    val activeSession: PumpingSession? = null,
    val selectedBreast: PumpingBreast = PumpingBreast.BOTH,
    val manual: ManualEntryState? = null,
    val bagPrompt: BagPromptState? = null,
    val error: String? = null,
)

@HiltViewModel
class PumpingViewModel @Inject constructor(
    private val pumpingRepository: PumpingRepository,
    private val startUseCase: StartPumpingSessionUseCase,
    private val stopUseCase: StopPumpingSessionUseCase,
    private val pauseUseCase: PausePumpingSessionUseCase,
    private val resumeUseCase: ResumePumpingSessionUseCase,
    private val saveManual: SavePumpingSessionUseCase,
    private val addBag: AddMilkBagUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpingUiState())
    val uiState: StateFlow<PumpingUiState> = _uiState.asStateFlow()

    val activeSession: StateFlow<PumpingSession?> = pumpingRepository.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            pumpingRepository.getActiveSession().collect { session ->
                _uiState.value = _uiState.value.copy(activeSession = session)
            }
        }
    }

    fun onModeChange(mode: PumpingMode) {
        val newManual = if (mode == PumpingMode.MANUAL && _uiState.value.manual == null) {
            val nowInstant = now()
            ManualEntryState(startTime = nowInstant.minusSeconds(15 * 60), endTime = nowInstant)
        } else {
            _uiState.value.manual
        }
        _uiState.value = _uiState.value.copy(mode = mode, manual = newManual)
    }

    fun onBreastSelected(breast: PumpingBreast) {
        _uiState.value = _uiState.value.copy(selectedBreast = breast)
    }

    fun onStartTimer() {
        viewModelScope.launch {
            runCatching { startUseCase(_uiState.value.selectedBreast) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not start session.") }
        }
    }

    fun onPause() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { runCatching { pauseUseCase(session) } }
    }

    fun onResume() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { runCatching { resumeUseCase(session) } }
    }

    fun onStopTimer(volumeMl: Int?) {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            runCatching { stopUseCase(session, volumeMl) }
                .onSuccess { stopped ->
                    openBagPrompt(sessionId = stopped.id, volumeMl = volumeMl ?: 0, collectionDate = stopped.endTime!!)
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not stop session.") }
        }
    }

    fun onManualFieldChange(transform: (ManualEntryState) -> ManualEntryState) {
        val current = _uiState.value.manual ?: return
        _uiState.value = _uiState.value.copy(manual = transform(current))
    }

    fun onManualSave() {
        val manual = _uiState.value.manual ?: return
        val volume = manual.volumeMl.toIntOrNull()
        val validationError = when {
            volume == null || volume <= 0 -> "Enter volume in mL"
            !manual.endTime.isAfter(manual.startTime) -> "End must be after start"
            else -> null
        }
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                manual = manual.copy(validationError = validationError)
            )
            return
        }
        _uiState.value = _uiState.value.copy(manual = manual.copy(isSaving = true, validationError = null))
        viewModelScope.launch {
            runCatching {
                saveManual(
                    startTime = manual.startTime,
                    endTime = manual.endTime,
                    breast = manual.breast,
                    volumeMl = volume!!,
                    notes = manual.notes.ifBlank { null },
                )
            }.onSuccess { saved ->
                _uiState.value = _uiState.value.copy(manual = null)
                openBagPrompt(sessionId = saved.id, volumeMl = volume, collectionDate = saved.endTime!!)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    manual = manual.copy(isSaving = false, validationError = "Could not save")
                )
            }
        }
    }

    private fun openBagPrompt(sessionId: Long, volumeMl: Int, collectionDate: Instant) {
        _uiState.value = _uiState.value.copy(
            bagPrompt = BagPromptState(
                sessionId = sessionId,
                collectionDate = collectionDate,
                volumeMl = volumeMl,
            )
        )
    }

    fun onBagPromptFieldChange(transform: (BagPromptState) -> BagPromptState) {
        val current = _uiState.value.bagPrompt ?: return
        _uiState.value = _uiState.value.copy(bagPrompt = transform(current))
    }

    fun onBagPromptSkip() {
        _uiState.value = _uiState.value.copy(bagPrompt = null)
    }

    fun onBagPromptConfirm() {
        val prompt = _uiState.value.bagPrompt ?: return
        if (prompt.volumeMl <= 0) {
            _uiState.value = _uiState.value.copy(
                bagPrompt = prompt.copy(volumeError = "Volume must be greater than 0")
            )
            return
        }
        _uiState.value = _uiState.value.copy(bagPrompt = prompt.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                addBag(
                    collectionDate = prompt.collectionDate,
                    volumeMl = prompt.volumeMl,
                    sourceSessionId = prompt.sessionId,
                    notes = prompt.notes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(bagPrompt = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    bagPrompt = prompt.copy(isSaving = false, volumeError = "Could not save bag")
                )
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
```

### Step 3: PumpingScreen

A Compose screen with a `SegmentedButton`-style mode toggle, a breast pill row, big timer when `activeSession != null`, otherwise the start CTA. Manual mode renders a form. `AddBagPromptSheet` shown via `ModalBottomSheet` when `bagPrompt != null`.

Skeleton (omit obvious imports):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PumpingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Pumping") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ModeSegmentedControl(
                mode = state.mode,
                onModeChange = viewModel::onModeChange,
            )
            Spacer(Modifier.height(16.dp))
            when (state.mode) {
                PumpingMode.TIMER -> TimerModeContent(state, viewModel)
                PumpingMode.MANUAL -> ManualModeContent(state, viewModel)
            }
        }
    }

    state.bagPrompt?.let { prompt ->
        AddBagPromptSheet(
            state = prompt,
            onFieldChange = viewModel::onBagPromptFieldChange,
            onConfirm = viewModel::onBagPromptConfirm,
            onDismiss = viewModel::onBagPromptSkip,
        )
    }
}
```

Render details:

- **ModeSegmentedControl** — two-button `SegmentedButton` toggling `TIMER`/`MANUAL`.
- **TimerModeContent** — breast pill row (`LEFT`/`RIGHT`/`BOTH`), big elapsed clock (compute from `state.activeSession.startTime + pausedDurationMs` via `produceState`), Start/Stop/Pause/Resume FABs. On Stop: show a small "volume mL" input bottom-sheet, then call `viewModel.onStopTimer(volume)`. The use case will trigger the bag prompt on success.
- **ManualModeContent** — form bound to `state.manual`: start time picker, end time picker (reuse the date/time pickers from `EditBreastfeedingSessionSheet.kt`), breast pill row, `OutlinedTextField` for volume (numeric), `OutlinedTextField` for notes, Save button calling `viewModel.onManualSave()`. Disable Save while `isSaving == true`.
- Theme: cards use `MaterialTheme.colorScheme.tertiaryContainer` (green, per spec — Pumping card color).
- Use `TimerDisplay` from `ui/component/TimerDisplay.kt` for the elapsed clock.

### Step 4: AddBagPromptSheet

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBagPromptSheet(
    state: BagPromptState,
    onFieldChange: ((BagPromptState) -> BagPromptState) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Add to stash?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.volumeMl.toString(),
                onValueChange = { input ->
                    val parsed = input.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onFieldChange { it.copy(volumeMl = parsed, volumeError = null) }
                },
                label = { Text("Volume (mL)") },
                singleLine = true,
                isError = state.volumeError != null,
                supportingText = { state.volumeError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = { value -> onFieldChange { it.copy(notes = value) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Add to stash")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Skip") }
        }
    }
}
```

> The sheet does **not** allow editing `collectionDate`. The spec calls for an editable field; if you want to wire it later, mirror `EditBreastfeedingSessionSheet`'s date+time picker pattern. For v1, defaulting to `session.endTime` is sufficient and keeps the surface area small.

### Step 5: AppNavGraph wiring

Add inside the `NavHost { ... }` block:

```kotlin
composable(Routes.PUMPING) {
    PumpingScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToHistory = { navController.navigate(Routes.PUMPING_HISTORY) },
    )
}
```

`HomeScreen` will navigate to `Routes.PUMPING` in task 9 — leave the home wiring alone for now.

## Tests

### `PumpingViewModelTest.kt` (unit)

Uses MockK + Turbine + a fixed-clock `() -> Instant`. Cover:

- `onStartTimer` calls `startUseCase` with `selectedBreast`.
- Mode toggle to `MANUAL` initializes `manual` with the clock.
- `onStopTimer(volume)` calls `stopUseCase` then opens a `bagPrompt` pre-filled with `volume` and `endTime`.
- Bag prompt confirm calls `addBag` with `sourceSessionId`; sets `bagPrompt = null` after success.
- Bag prompt skip clears `bagPrompt` without calling `addBag` (use `coVerify(exactly = 0)`).
- Manual entry validation: rejects `volumeMl <= 0`, rejects `endTime <= startTime`.

### `PumpingScreenTest.kt` (Compose instrumentation)

- Initial state: shows `Start` button enabled when a breast is selected.
- Tap **Start** → `viewModel.onStartTimer()` called.
- When `activeSession != null`, `Pause` swaps to `Resume` after tapping.
- Tap **Stop** → opens a volume entry sheet, entering a value and confirming opens `AddBagPromptSheet` (assert `onNodeWithText("Add to stash?")`).
- Switching to **Manual** shows the manual form; empty volume blocks Save with the inline error.

### `AddBagPromptSheetTest.kt`

- Default render shows the pre-filled volume.
- Tap "Add to stash" with valid volume → emits `onConfirm`.
- Tap "Skip" → emits `onDismiss` without calling `onConfirm`.
- Entering `0` and confirming surfaces the inline `volumeError`.

UI tests follow the existing `*ScreenTest.kt` style under `ui/`: build fake state and a stub VM (or use a fake `PumpingUiState` constructor on a Compose host).

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.ui.pumping.PumpingViewModelTest"
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.ui.pumping.PumpingScreenTest,com.babytracker.ui.pumping.AddBagPromptSheetTest
```

Expected: all green. Build the app on a device and smoke-test: start a timer → stop → bag prompt appears with pre-filled volume → confirm → bag persists.

## Commit (two commits)

```
feat(pumping): add PumpingScreen and PumpingViewModel

Live timer + manual entry modes. Stop opens AddBagPromptSheet so the
user can optionally store the volume as a milk bag.
```

```
feat(navigation): wire pumping route into AppNavGraph

Adds PUMPING, PUMPING_HISTORY, INVENTORY route constants. PUMPING route
hosts PumpingScreen; history and inventory routes land in later tasks.
```
