# Task 7 — PumpingHistoryScreen + ViewModel + EditPumpingSessionSheet

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Browse past pumping sessions, tap to edit, swipe/long-press to delete. Mirrors `BreastfeedingHistoryScreen` + `EditBreastfeedingSessionSheet`.

**Depends on:** Task 4 (pumping use cases), Task 6 (route registered).

## Files

- Create: `app/src/main/java/com/babytracker/ui/pumping/PumpingHistoryViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/pumping/PumpingHistoryScreen.kt`
- Create: `app/src/main/java/com/babytracker/ui/pumping/EditPumpingSessionSheet.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`
- Test: `app/src/test/java/com/babytracker/ui/pumping/PumpingHistoryViewModelTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/pumping/PumpingHistoryScreenTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/pumping/EditPumpingSessionSheetTest.kt`

## Implementation

### Step 1: `PumpingHistoryViewModel`

```kotlin
package com.babytracker.ui.pumping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.usecase.pumping.DeletePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.GetPumpingHistoryUseCase
import com.babytracker.domain.usecase.pumping.UpdatePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.validatePumpingEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EditPumpingSheetState(
    val original: PumpingSession,
    val editedStart: Instant,
    val editedEnd: Instant?,
    val editedBreast: PumpingBreast,
    val editedVolumeMl: String,
    val editedNotes: String,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val deleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
)

data class PumpingHistoryUiState(
    val sessions: List<PumpingSession> = emptyList(),
    val editSheet: EditPumpingSheetState? = null,
    val error: String? = null,
)

@HiltViewModel
class PumpingHistoryViewModel @Inject constructor(
    getHistory: GetPumpingHistoryUseCase,
    private val updateSession: UpdatePumpingSessionUseCase,
    private val deleteSession: DeletePumpingSessionUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpingHistoryUiState())
    val uiState: StateFlow<PumpingHistoryUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<PumpingSession>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            sessions.collect { list ->
                _uiState.value = _uiState.value.copy(sessions = list)
            }
        }
    }

    fun onEditClicked(session: PumpingSession) {
        _uiState.value = _uiState.value.copy(
            editSheet = EditPumpingSheetState(
                original = session,
                editedStart = session.startTime,
                editedEnd = session.endTime,
                editedBreast = session.breast,
                editedVolumeMl = session.volumeMl?.toString().orEmpty(),
                editedNotes = session.notes.orEmpty(),
            )
        )
    }

    fun onEditFieldChange(transform: (EditPumpingSheetState) -> EditPumpingSheetState) {
        val current = _uiState.value.editSheet ?: return
        val updated = transform(current)
        val volume = updated.editedVolumeMl.toIntOrNull()
        val error = validatePumpingEdit(
            startTime = updated.editedStart,
            endTime = updated.editedEnd,
            volumeMl = volume,
            pausedDurationMs = updated.original.pausedDurationMs,
            now = now(),
        )
        _uiState.value = _uiState.value.copy(editSheet = updated.copy(validationError = error))
    }

    fun onEditDismiss() {
        _uiState.value = _uiState.value.copy(editSheet = null)
    }

    fun onEditSave() {
        val sheet = _uiState.value.editSheet ?: return
        if (sheet.validationError != null) return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                updateSession(
                    original = sheet.original,
                    startTime = sheet.editedStart,
                    endTime = sheet.editedEnd,
                    breast = sheet.editedBreast,
                    volumeMl = sheet.editedVolumeMl.toIntOrNull(),
                    notes = sheet.editedNotes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(editSheet = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    editSheet = sheet.copy(isSaving = false, validationError = "Could not save")
                )
            }
        }
    }

    fun onDeleteRequested() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(deleteConfirm = true))
    }

    fun onDeleteCancelled() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(deleteConfirm = false))
    }

    fun onDeleteConfirmed() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(isDeleting = true))
        viewModelScope.launch {
            runCatching { deleteSession(sheet.original) }
                .onSuccess { _uiState.value = _uiState.value.copy(editSheet = null) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        editSheet = sheet.copy(isDeleting = false, deleteConfirm = false),
                        error = "Could not delete session",
                    )
                }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
```

### Step 2: `PumpingHistoryScreen`

`LazyColumn` of `HistoryCard` rows (reuse the component already in `ui/component/HistoryCard.kt`). Card content per row:

- `title` = `${session.breast.displayName()} · ${session.volumeMl ?: "—"} mL`
- `subtitle` = `${formatTime12h(session.startTime)} · ${session.duration?.formatDuration() ?: "in progress"}`
- `trailingIcon` = `Icons.Default.Edit`, `onClick` opens `EditPumpingSessionSheet` via `viewModel.onEditClicked(session)`.

If you want a swipe-to-delete affordance, wrap each row in `SwipeToDismissBox` with a confirmation step. The simpler approach: long-press triggers `onEditClicked` and the edit sheet contains the Delete button (same as breastfeeding). Stick with that to match the existing pattern.

Render the edit sheet at the bottom of the screen:

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
state.editSheet?.let { sheet ->
    EditPumpingSessionSheet(
        state = sheet,
        onFieldChange = viewModel::onEditFieldChange,
        onDismiss = viewModel::onEditDismiss,
        onSave = viewModel::onEditSave,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
    )
}
```

Empty state: when `state.sessions.isEmpty()`, render a centered `Text("No pumping sessions yet")` with an inviting illustration emoji (`"🥛"`).

### Step 3: `EditPumpingSessionSheet`

Mirror `EditBreastfeedingSessionSheet.kt` but with three extra editable fields (`breast` pill row, `volume` numeric input, `notes` text). Reuse the private `EditDatePicker`, `EditTimePicker`, `withDate`, `withTime`, `toDateLabel` helpers — copy them verbatim into the new file (these are file-private so cannot be imported; duplication is fine and mirrors the existing pattern).

Key UI elements (omit obvious imports):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPumpingSessionSheet(
    state: EditPumpingSheetState,
    onFieldChange: ((EditPumpingSheetState) -> EditPumpingSheetState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 24.dp),
        ) {
            // Title + close icon — same as EditBreastfeedingSessionSheet
            Spacer(Modifier.height(16.dp))
            SectionLabel("STARTED")
            FieldRow(
                dateLabel = state.editedStart.toDateLabel(),
                timeLabel = state.editedStart.formatTime12h(),
                onDateClick = { /* open date picker, then onFieldChange { it.copy(editedStart = ...) } */ },
                onTimeClick = { /* ditto */ },
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("ENDED")
            FieldRow(
                dateLabel = state.editedEnd?.toDateLabel() ?: "Set date",
                timeLabel = state.editedEnd?.formatTime12h() ?: "Set time",
                onDateClick = { /* … */ },
                onTimeClick = { /* … */ },
                placeholder = state.editedEnd == null,
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("BREAST")
            BreastPillRow(
                selected = state.editedBreast,
                onSelect = { value -> onFieldChange { it.copy(editedBreast = value) } },
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.editedVolumeMl,
                onValueChange = { input ->
                    onFieldChange { it.copy(editedVolumeMl = input.filter { c -> c.isDigit() }) }
                },
                label = { Text("Volume (mL)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.editedNotes,
                onValueChange = { value -> onFieldChange { it.copy(editedNotes = value) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )

            state.validationError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            // Save / Delete row — reuse the DeleteConfirmRow from the breastfeeding sheet
        }
    }
}
```

`BreastPillRow` is a small composable with three `FilterChip` instances. Place it private to the file unless shared with other screens later.

### Step 4: AppNavGraph

```kotlin
composable(Routes.PUMPING_HISTORY) {
    PumpingHistoryScreen(onNavigateBack = { navController.popBackStack() })
}
```

## Tests

### `PumpingHistoryViewModelTest`

- `sessions` flow emits the repository list.
- `onEditClicked` opens the sheet with prefilled values.
- `onEditFieldChange` re-runs validation: setting `editedEnd` before `editedStart` populates `validationError`.
- `onEditSave` calls `updateSession` then clears the sheet.
- `onDeleteConfirmed` calls `deleteSession` then clears the sheet.

### `PumpingHistoryScreenTest` (Compose)

- Renders one row per session.
- Tap the trailing edit icon → asserts the edit sheet is visible.
- Empty list shows the "No pumping sessions yet" copy.

### `EditPumpingSessionSheetTest`

- Fields prefill from state.
- Changing breast emits `onFieldChange` with the new breast.
- Save button disabled while `validationError != null` (e.g. volume = 0).
- Delete button → confirm row → confirmed callback fires.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.ui.pumping.PumpingHistoryViewModelTest"
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.ui.pumping.PumpingHistoryScreenTest,\
com.babytracker.ui.pumping.EditPumpingSessionSheetTest
```

Expected: all green. Manual: open the history from `PumpingScreen` top-right icon, tap a row, edit volume, save, delete.

## Commit

```
feat(pumping): add PumpingHistoryScreen and edit/delete flow

PumpingHistoryViewModel exposes the full session list and an
EditPumpingSheetState mirroring EditBreastfeedingSessionSheet. The new
EditPumpingSessionSheet adds breast/volume/notes fields on top of the
shared edit pattern.
```
