# Diaper Home Tile + Quick-Log Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-161

**Goal:** Add a Diaper home tile that shows today's count + time since the last change, and a bottom-sheet quick-log (Wet / Dirty / Both, editable time, optional notes) reached by tapping the tile.

**Architecture:** Mirrors the `BottleFeed` flow exactly: the tile navigates to a dedicated `Routes.DIAPER` screen that hosts a `ModalBottomSheet` and pops back on save (`saved` flag → `LaunchedEffect`). The home summary (`TodayDiaperSummary`) is threaded through `HomeViewModel`/`HomeUiState` from `ObserveTodayDiaperSummaryUseCase`. The new `HomeTile.DIAPER` enum value auto-appends to existing users' saved tile order via `HomeTile.reconcile()` — no DataStore migration.

**Tech Stack:** Jetpack Compose + Material 3, Hilt, Compose Navigation, JUnit 5 + MockK + Turbine (VM), Compose UI Test (sheet).

**Dependencies:** Plan 1 (data) and Plan 2 (`LogDiaperChangeUseCase`, `EditDiaperChangeUseCase`, `ObserveTodayDiaperSummaryUseCase`, `TodayDiaperSummary`).

**Suggested implementation branch:** `feat/diaper-home-tile`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt. The `loadForEdit` entry point added to `DiaperViewModel` here is consumed by plan 4 (history edit).

---

### Task 1: `DiaperViewModel` + `DiaperUiState`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/diaper/DiaperViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/diaper/DiaperViewModelTest.kt`

- [ ] **Step 1: Implement** (mirrors `BottleFeedViewModel`; `editingCreatedAt` preserves the original `created_at` on edit)

```kotlin
package com.babytracker.ui.diaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DiaperUiState(
    val type: DiaperType = DiaperType.WET,
    val timestamp: Instant = Instant.EPOCH,
    val notes: String = "",
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val editingId: Long? = null,
    val editingCreatedAt: Instant? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class DiaperViewModel @Inject constructor(
    private val logDiaperChange: LogDiaperChangeUseCase,
    private val editDiaperChange: EditDiaperChangeUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaperUiState(timestamp = now()))
    val uiState: StateFlow<DiaperUiState> = _uiState.asStateFlow()

    fun onTypeChange(type: DiaperType) = _uiState.update { it.copy(type = type) }
    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp) }
    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text, validationError = null) }

    fun loadForEdit(id: Long, timestamp: Instant, type: DiaperType, notes: String?, createdAt: Instant) =
        _uiState.update {
            it.copy(
                editingId = id,
                editingCreatedAt = createdAt,
                isEditing = true,
                timestamp = timestamp,
                type = type,
                notes = notes.orEmpty(),
                saved = false,
                validationError = null,
            )
        }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        // Set the guard synchronously so a second rapid tap is rejected before its coroutine launches.
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    logDiaperChange(state.type, state.timestamp, state.notes)
                } else {
                    editDiaperChange(
                        DiaperChange(
                            id = editingId,
                            timestamp = state.timestamp,
                            type = state.type,
                            notes = state.notes,
                            createdAt = state.editingCreatedAt ?: now(),
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, validationError = error.message ?: "Could not save") }
            }
        }
    }
}
```

- [ ] **Step 2: Test** (`UnconfinedTestDispatcher` so the launched coroutine runs eagerly; mirror existing `*ViewModelTest` setup with `Dispatchers.setMain`)

```kotlin
package com.babytracker.ui.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperViewModelTest {
    private val log = mockk<LogDiaperChangeUseCase>()
    private val edit = mockk<EditDiaperChangeUseCase>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(50_000)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DiaperViewModel(log, edit) { fixedNow }

    @Test
    fun `save logs a new change and flags saved`() = runTest {
        coEvery { log(any(), any(), any()) } returns 1
        val vm = viewModel()
        vm.onTypeChange(DiaperType.DIRTY)
        vm.onSave()
        coVerify { log(DiaperType.DIRTY, any(), any()) }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `save edits when editingId is set, preserving createdAt`() = runTest {
        val created = Instant.ofEpochMilli(10_000)
        val captured = slot<DiaperChange>()
        coEvery { edit(capture(captured)) } returns Unit
        val vm = viewModel()
        vm.loadForEdit(id = 9, timestamp = Instant.ofEpochMilli(20_000), type = DiaperType.BOTH, notes = "x", createdAt = created)
        vm.onSave()
        assertEquals(9, captured.captured.id)
        assertEquals(created, captured.captured.createdAt)
    }

    @Test
    fun `failure surfaces validation error and clears saving`() = runTest {
        coEvery { log(any(), any(), any()) } throws IllegalArgumentException("future")
        val vm = viewModel()
        vm.onSave()
        assertEquals("future", vm.uiState.value.validationError)
        assertTrue(!vm.uiState.value.isSaving)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.diaper.DiaperViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DiaperViewModel`

---

### Task 2: String resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add** (place near the existing `bottle_feed_*` strings; reuse the existing `cancel` string)

```xml
<string name="diaper_add_title">Log diaper change</string>
<string name="diaper_edit_title">Edit diaper change</string>
<string name="diaper_time_label">Time</string>
<string name="diaper_notes_label">Notes (optional)</string>
<string name="diaper_save">Save</string>
```

- [ ] **Step 2: Commit** `feat(diaper): add diaper sheet string resources`

---

### Task 3: `DiaperSheet`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/diaper/DiaperSheet.kt`

- [ ] **Step 1: Implement** (mirrors `BottleFeedSheet`, minus volume/bag picker; reuses `com.babytracker.ui.common.DateTimeFieldRow`)

```kotlin
package com.babytracker.ui.diaper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.DiaperType
import com.babytracker.ui.common.DateTimeFieldRow
import java.time.Instant

const val DIAPER_SAVE_TAG = "DiaperSaveButton"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperSheet(
    state: DiaperUiState,
    onTypeChange: (DiaperType) -> Unit,
    onTimeChange: (Instant) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(
                    if (!state.isEditing) R.string.diaper_add_title else R.string.diaper_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            DiaperTypeSelector(selected = state.type, onSelect = onTypeChange, enabled = !state.isSaving)
            Spacer(Modifier.height(12.dp))

            DateTimeFieldRow(
                label = stringResource(R.string.diaper_time_label),
                timestamp = state.timestamp,
                onChange = onTimeChange,
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.diaper_notes_label)) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            state.validationError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().testTag(DIAPER_SAVE_TAG),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.diaper_save), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaperTypeSelector(
    selected: DiaperType,
    onSelect: (DiaperType) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DiaperType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DiaperType.entries.size),
                // Keep the chosen type legible even while the form is disabled mid-save.
                enabled = enabled || selected == type,
                label = { Text("${type.emoji} ${type.label}") },
            )
        }
    }
}
```

- [ ] **Step 2: Commit** `feat(diaper): add DiaperSheet quick-log UI`

---

### Task 4: `DiaperScreen` route host

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/diaper/DiaperScreen.kt`

- [ ] **Step 1: Implement** (mirrors `BottleFeedScreen`)

```kotlin
package com.babytracker.ui.diaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiaperScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiaperViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    DiaperSheet(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onTimeChange = viewModel::onTimeChange,
        onNotesChange = viewModel::onNotesChange,
        onConfirm = viewModel::onSave,
        onDismiss = onNavigateBack,
    )
}
```

- [ ] **Step 2: Commit** `feat(diaper): add DiaperScreen route host`

---

### Task 5: Register the route

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1:** In `Routes.kt` add: `const val DIAPER = "diaper"`
- [ ] **Step 2:** In `AppNavGraph.kt`, add the import `import com.babytracker.ui.diaper.DiaperScreen`; pass `onNavigateToDiaper = { navController.navigate(Routes.DIAPER) }` to `HomeScreen(...)`; and register the destination (inside `insightsGraph` or the main `NavHost`):

```kotlin
composable(Routes.DIAPER) {
    DiaperScreen(onNavigateBack = { navController.popBackStack() })
}
```

- [ ] **Step 3: Commit** `feat(diaper): register diaper route`

---

### Task 6: Home tile registration

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`

- [ ] **Step 1:** Add `DIAPER` to the `enum class HomeTile` body (after `BOTTLE_FEED`) and to `DEFAULT_ORDER` in the same position. `reconcile()` and `serialize()`/`deserialize()` need no other change — existing saved orders auto-append `DIAPER`.
- [ ] **Step 2: Test** — extend the existing `HomeTile` test (search `app/src/test` for `HomeTileTest`): assert `deserialize(null)` contains `DIAPER`, and that `reconcile(listOf("SLEEP","BREASTFEEDING"))` appends `DIAPER`.
- [ ] **Step 3: Run** `./gradlew test --tests "*HomeTileTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DIAPER home tile to the tile set`

---

### Task 7: Thread `TodayDiaperSummary` through Home state

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

- [ ] **Step 1:** Add the field to `HomeUiState`:

```kotlin
val todayDiaperSummary: TodayDiaperSummary = TodayDiaperSummary(),
```

(add `import com.babytracker.domain.model.TodayDiaperSummary` and `import com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCase`)

- [ ] **Step 2:** Inject the use case into the constructor:

```kotlin
observeTodayDiaperSummary: ObserveTodayDiaperSummaryUseCase,
```

- [ ] **Step 3:** Add it as a fourth flow to the final `uiState` combine and copy it into state:

```kotlin
val uiState: StateFlow<HomeUiState> = combine(
    baseState,
    observeTodayFeedingSummary(),
    settingsRepository.getHomeTileOrder(),
    observeTodayDiaperSummary(),
) { base, todayFeedingSummary, tileOrder, todayDiaperSummary ->
    base.copy(
        todayFeedingSummary = todayFeedingSummary,
        tileOrder = tileOrder,
        todayDiaperSummary = todayDiaperSummary,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    initialValue = HomeUiState(),
)
```

- [ ] **Step 4: Commit** `feat(diaper): expose today diaper summary in Home state`

---

### Task 8: `DiaperHomeCard` + tile wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`

- [ ] **Step 1:** In `HomeScreen.kt` add the card + ago text (place next to `BottleFeedHomeCard`; add `import com.babytracker.domain.model.TodayDiaperSummary`):

```kotlin
@Composable
internal fun DiaperHomeCard(
    summary: TodayDiaperSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countText = if (summary.count == 1) "1 today" else "${summary.count} today"
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = if (summary.hasAny) {
                    "Diapers, $countText. Log a diaper change."
                } else {
                    "Diapers. Log a diaper change."
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "🧷",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Diapers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (summary.hasAny || summary.lastChangeAt != null) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                summary.lastChangeAt?.let { LastDiaperAgoText(it) }
            } else {
                Text(
                    text = "Tap to log",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun LastDiaperAgoText(lastChangeAt: Instant) {
    val now by produceState(initialValue = Instant.now(), key1 = lastChangeAt) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = "Last ${Duration.between(lastChangeAt, now).formatElapsedAgo()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
```

- [ ] **Step 2:** In `HomeTileContent.kt`:
  - Add `onDiaper: () -> Unit` to `HomeTileCallbacks`.
  - In `minTileHeightDp()`, include `DIAPER` in the compact-tile group that returns `0.dp` (the line with `BOTTLE_FEED`, `FEEDING_HISTORY`).
  - In the `when (tile)` of `HomeTileContent`, add:

```kotlin
HomeTile.DIAPER -> DiaperHomeCard(uiState.todayDiaperSummary, callbacks.onDiaper, modifier)
```

- [ ] **Step 3:** In `HomeScreen.kt`, add a `onNavigateToDiaper: () -> Unit = {}` parameter to `HomeScreen`, add it to the `remember(...)` key list, and set `onDiaper = onNavigateToDiaper` in the `HomeTileCallbacks { ... }` builder. (The `AppNavGraph` wiring from Task 5 already passes it.)
- [ ] **Step 4: Build** `./gradlew assembleDebug` — expect SUCCESS (verifies the `when` is exhaustive and all wiring compiles).
- [ ] **Step 5: Commit** `feat(diaper): render diaper home tile with today summary`

---

### Task 9: Compose UI test for the sheet

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/diaper/DiaperSheetTest.kt`

- [ ] **Step 1:** Using `createComposeRule()`, render `DiaperSheet` with a fake state + capturing callbacks. Assert: the three type segments (Wet/Dirty/Both) are shown; tapping "Dirty" fires `onTypeChange(DiaperType.DIRTY)`; tapping the save button (`onNodeWithTag(DIAPER_SAVE_TAG)`) fires `onConfirm`. Follow the existing sheet UI test pattern in `app/src/androidTest/java/com/babytracker/ui/bottlefeed/` if present.
- [ ] **Step 2: Run** `./gradlew connectedAndroidTest --tests "com.babytracker.ui.diaper.DiaperSheetTest"` on an emulator — expect PASS. If no emulator, note it.
- [ ] **Step 3: Commit** `test(diaper): add DiaperSheet UI test`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- Tapping the Diaper home tile opens the bottom sheet; choosing a type + Save logs a change and returns Home; the tile then shows "N today" and "Last … ago".
- The `DIAPER` tile appears for both fresh installs (in `DEFAULT_ORDER`) and existing users (auto-appended by `reconcile`), and is reorderable/hideable like other tiles.
- The sheet's save button is disabled while saving (no double-submit).
- No new DataStore migration is introduced.

## Self-Review Notes

- Spec coverage: home tile summary (last change + count today), bottom-sheet quick-log (3 types, time, notes) — covered. Edit-from-history uses `DiaperViewModel.loadForEdit` (consumed by plan 4).
- Type consistency: `DiaperViewModel.loadForEdit(id, timestamp, type, notes, createdAt)` is the exact signature plan 4 calls; `DIAPER_SAVE_TAG` is referenced by the UI test. `HomeUiState.todayDiaperSummary` is read by `DiaperHomeCard`.
- `combine` with four flows uses the standard kotlinx overload; the lambda arity (4) matches.
- `tertiaryContainer`/`onTertiaryContainer` are standard M3 tokens (not the project's extended warning tokens), so direct `MaterialTheme.colorScheme` access is allowed.
