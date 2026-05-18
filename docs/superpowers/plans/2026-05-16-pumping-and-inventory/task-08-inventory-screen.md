# Task 8 — InventoryScreen + ViewModel + AddBagSheet

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Browse active milk bags FIFO-ordered with a summary header, mark used, delete, and add bags manually.

**Depends on:** Task 5 (inventory use cases), Task 6 (route constant `INVENTORY`).

## Files

- Create: `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`
- Create: `app/src/main/java/com/babytracker/ui/inventory/AddBagSheet.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`
- Test: `app/src/test/java/com/babytracker/ui/inventory/InventoryViewModelTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/inventory/InventoryScreenTest.kt`
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/inventory/AddBagSheetTest.kt`

## Implementation

### Step 1: `InventoryViewModel`

```kotlin
package com.babytracker.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.inventory.DeleteMilkBagUseCase
import com.babytracker.domain.usecase.inventory.GetInventorySummaryUseCase
import com.babytracker.domain.usecase.inventory.GetInventoryUseCase
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class AddBagSheetState(
    val collectionDate: Instant,
    val volumeMl: String = "",
    val notes: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class InventoryUiState(
    val summary: InventorySummary = InventorySummary.Empty,
    val bags: List<MilkBag> = emptyList(),
    val addSheet: AddBagSheetState? = null,
    val error: String? = null,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    getInventory: GetInventoryUseCase,
    getSummary: GetInventorySummaryUseCase,
    private val addBag: AddMilkBagUseCase,
    private val markUsed: MarkBagUsedUseCase,
    private val deleteBag: DeleteMilkBagUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getInventory(), getSummary()) { bags, summary -> bags to summary }
                .collect { (bags, summary) ->
                    _uiState.value = _uiState.value.copy(bags = bags, summary = summary)
                }
        }
    }

    fun onAddBagClicked() {
        _uiState.value = _uiState.value.copy(addSheet = AddBagSheetState(collectionDate = now()))
    }

    fun onAddBagFieldChange(transform: (AddBagSheetState) -> AddBagSheetState) {
        val current = _uiState.value.addSheet ?: return
        _uiState.value = _uiState.value.copy(addSheet = transform(current))
    }

    fun onAddBagDismiss() {
        _uiState.value = _uiState.value.copy(addSheet = null)
    }

    fun onAddBagConfirm() {
        val sheet = _uiState.value.addSheet ?: return
        val volume = sheet.volumeMl.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.value = _uiState.value.copy(
                addSheet = sheet.copy(validationError = "Volume must be greater than 0")
            )
            return
        }
        _uiState.value = _uiState.value.copy(addSheet = sheet.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                addBag(
                    collectionDate = sheet.collectionDate,
                    volumeMl = volume,
                    sourceSessionId = null,
                    notes = sheet.notes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(addSheet = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    addSheet = sheet.copy(isSaving = false, validationError = "Could not save")
                )
            }
        }
    }

    fun onMarkUsed(bag: MilkBag) {
        viewModelScope.launch {
            runCatching { markUsed(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not mark used") }
        }
    }

    fun onDelete(bag: MilkBag) {
        viewModelScope.launch {
            runCatching { deleteBag(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not delete bag") }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
```

### Step 2: `InventoryScreen`

Composition:

- **TopAppBar** with back navigation.
- **Summary header card** (`MaterialTheme.colorScheme.surfaceVariant`, large shape): three columns — Total mL, Bags, Oldest bag age. Pull values from `state.summary`. Use `Duration.between(state.summary.oldestBagDate, Instant.now()).formatElapsedAgo()` for the age, hidden when `oldestBagDate == null`.
- **`FloatingActionButton`** "Add bag" — calls `viewModel.onAddBagClicked()`.
- **`LazyColumn`** of rows:

  ```kotlin
  items(state.bags, key = { it.id }) { bag ->
      MilkBagRow(
          bag = bag,
          onMarkUsed = { viewModel.onMarkUsed(bag) },
          onDelete = { viewModel.onDelete(bag) },
      )
  }
  ```

  `MilkBagRow` is a private composable that renders a `Card` containing the date, volume, and "Used X ago" age, with a trailing IconButton "Mark used" and an overflow menu with `DropdownMenuItem("Delete")`. Tertiary container colour highlights the bag closest to expiry (use the oldest bag — `bag.id == state.bags.first().id`).
- **Empty state** when `state.bags.isEmpty()`: centered text "No bags in your stash yet" + the 🧊 emoji.

Render `AddBagSheet` at the bottom of the screen when `state.addSheet != null`.

```kotlin
state.addSheet?.let { sheet ->
    AddBagSheet(
        state = sheet,
        onFieldChange = viewModel::onAddBagFieldChange,
        onConfirm = viewModel::onAddBagConfirm,
        onDismiss = viewModel::onAddBagDismiss,
    )
}
```

### Step 3: `AddBagSheet`

Mirrors `AddBagPromptSheet` (task 6) with two differences:

1. The user picks `collectionDate`. Add a date+time picker pair using the `EditDatePicker`/`EditTimePicker` pattern from `EditBreastfeedingSessionSheet`.
2. No `sourceSessionId`.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBagSheet(
    state: AddBagSheetState,
    onFieldChange: ((AddBagSheetState) -> AddBagSheetState) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Add milk bag", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            CollectionDateRow(
                date = state.collectionDate,
                onChange = { newDate -> onFieldChange { it.copy(collectionDate = newDate) } },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.volumeMl,
                onValueChange = { input ->
                    onFieldChange {
                        it.copy(
                            volumeMl = input.filter { c -> c.isDigit() },
                            validationError = null,
                        )
                    }
                },
                label = { Text("Volume (mL)") },
                singleLine = true,
                isError = state.validationError != null,
                supportingText = { state.validationError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = { value -> onFieldChange { it.copy(notes = value) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
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
                    Text("Save bag")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
```

`CollectionDateRow` is a small private composable showing the formatted date + time labels as buttons that open `EditDatePicker`/`EditTimePicker` (copy these helpers from `EditBreastfeedingSessionSheet`).

### Step 4: AppNavGraph

```kotlin
composable(Routes.INVENTORY) {
    InventoryScreen(onNavigateBack = { navController.popBackStack() })
}
```

## Tests

### `InventoryViewModelTest`

- Combined flow updates `bags` + `summary` when both repositories emit.
- `onAddBagClicked` opens the sheet with `collectionDate = now()`.
- `onAddBagConfirm` rejects empty/non-positive volume — surface `validationError`.
- `onAddBagConfirm` calls `addBag` with parsed values; clears sheet on success.
- `onMarkUsed` calls `markUsed(bag)`.
- `onDelete` calls `deleteBag(bag)`.

### `InventoryScreenTest`

- Summary card shows totals from the state.
- Empty state copy appears when `bags.isEmpty()`.
- Tap the trailing "Mark used" button → `onMarkUsed` invoked on the right bag (verify on a fake VM).
- FAB opens `AddBagSheet`.
- Tapping the overflow → Delete invokes `onDelete`.

### `AddBagSheetTest`

- Default volume empty; entering "0" + Save surfaces validation error.
- Valid volume + Save fires `onConfirm`.
- Cancel button fires `onDismiss`.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.ui.inventory.InventoryViewModelTest"
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.ui.inventory.InventoryScreenTest,\
com.babytracker.ui.inventory.AddBagSheetTest
```

Expected: all green. Manually: open `INVENTORY` route, add a bag, mark used, delete.

## Commit

```
feat(inventory): add InventoryScreen with summary, bag list, and add sheet

InventoryViewModel combines GetInventory + GetInventorySummary into a
single UiState. Users can FIFO-browse active bags, mark them used, or
delete them. AddBagSheet allows manual entry of a bag with no source
session link.
```
