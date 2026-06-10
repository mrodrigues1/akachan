# Bottle Feed Logging UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **UI CRAFT GATE:** Every task that builds or changes a composable MUST begin by invoking the `impeccable` skill (craft mode) to design the component against the Akachan "Baby" palette, Material 3, rounded shapes, and one-handed usability — then implement under that guidance.

**LINEAR_ISSUE:** AKA-111

**Goal:** Provide the UI to log and edit a bottle/formula feed — a reusable bottle-feed sheet (add + edit), its ViewModel, a dedicated route, and a Home quick-action entry point.

**Architecture:** `BottleFeedViewModel` holds form state in a single `BottleFeedUiState` and exposes change handlers plus `onSave`. The reusable `BottleFeedSheet` composable renders feed-type selection (breast milk / formula), volume input (always entered in ml; label reflects the stored unit), a date/time row reused in style from `AddBagSheet`, a milk-bag picker shown only for breast-milk feeds, and a notes field. `BottleFeedScreen` hosts the sheet behind a route so logging works standalone; plan 05 reuses the same sheet for in-history edits. Save routes to `LogBottleFeedUseCase` (add) or `EditBottleFeedUseCase` (edit) from plan 03.

**Tech Stack:** Compose Material 3 (`ModalBottomSheet`, `SegmentedButton`, `OutlinedTextField`, `DatePickerDialog`/`TimePicker`), Hilt, Coroutines/Flow, JUnit 5 + MockK + Turbine (VM), Compose UI test.

**Dependencies:** Plan 01 (`BottleFeed`, `FeedType`), Plan 02 (`VolumeUnit`, `formatVolume`), Plan 03 (`LogBottleFeedUseCase`, `EditBottleFeedUseCase`). Existing `GetInventoryUseCase` (active bags). Plan 05 reuses `BottleFeedSheet`.

**Suggested implementation branch:** `feat/bottle-feed-logging-ui`

---

## File Structure

- Create `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt` — VM + `BottleFeedUiState`.
- Create `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt` — reusable add/edit sheet composable + `BottleFeedFormState`.
- Create `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedScreen.kt` — route host showing the sheet.
- Modify `app/src/main/java/com/babytracker/navigation/Routes.kt` — add `BOTTLE_FEED`.
- Modify `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` — register route + Home callback.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — `onNavigateToBottleFeed` param + quick-action.
- Modify `app/src/main/res/values/strings.xml` — labels.
- Test `app/src/test/java/com/babytracker/ui/bottlefeed/BottleFeedViewModelTest.kt`.
- Test `app/src/androidTest/java/com/babytracker/ui/bottlefeed/BottleFeedSheetTest.kt`.

---

## Task 1: Add the route

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`

- [ ] **Step 1: Add the constant**

```kotlin
const val BOTTLE_FEED = "bottle_feed"
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/navigation/Routes.kt
git commit -m "feat(navigation): add bottle feed route"
```

---

## Task 2: BottleFeedViewModel (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/bottlefeed/BottleFeedViewModelTest.kt`

`BottleFeedUiState` fields: `feedType: FeedType = BREAST_MILK`, `volumeText: String = ""`, `timestamp: Instant`, `activeBags: List<MilkBag> = emptyList()`, `selectedBagId: Long? = null`, `notes: String = ""`, `volumeUnit: VolumeUnit = ML`, `isSaving: Boolean = false`, `validationError: String? = null`, `editingId: Long? = null`, `saved: Boolean = false`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.ui.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.EditBottleFeedUseCase
import com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCase
import com.babytracker.domain.usecase.inventory.GetInventoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BottleFeedViewModelTest {

    private val log = mockk<LogBottleFeedUseCase>(relaxed = true)
    private val edit = mockk<EditBottleFeedUseCase>(relaxed = true)
    private val getInventory = mockk<GetInventoryUseCase>()
    private val settings = mockk<SettingsRepository>()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        stubFlows()
    }

    private fun stubFlows() {
        // GetInventoryUseCase.invoke() and SettingsRepository.getVolumeUnit() are non-suspend Flow returns.
        every { getInventory() } returns flowOf(emptyList())
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BottleFeedViewModel(log, edit, getInventory, settings) { Instant.ofEpochMilli(10_000) }

    @Test
    fun `blank volume sets validation error and does not save`() = runTest {
        val viewModel = vm()
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter a volume", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { log(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `valid add calls LogBottleFeedUseCase and marks saved`() = runTest {
        val viewModel = vm()
        viewModel.onVolumeChange("120")
        viewModel.onTypeChange(FeedType.FORMULA)
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { log(Instant.ofEpochMilli(10_000), 120, FeedType.FORMULA, null, null) }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `loadForEdit populates state and save calls EditBottleFeedUseCase`() = runTest {
        val viewModel = vm()
        viewModel.loadForEdit(
            id = 3,
            timestamp = Instant.ofEpochMilli(8_000),
            volumeMl = 90,
            type = FeedType.BREAST_MILK,
            linkedMilkBagId = 7,
            notes = "am",
        )
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { edit(3, Instant.ofEpochMilli(8_000), 90, FeedType.BREAST_MILK, 7, "am") }
    }
}
```

> The test references `viewModel.onVolumeChange`, `onTypeChange`, `onSave`, `loadForEdit`, `onBagSelect`, `onNotesChange`, `onTimeChange`. Define them all in Step 3. Match how other VM tests in `app/src/test/java/com/babytracker/ui/pumping/PumpingViewModelTest.kt` set the main dispatcher.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.ui.bottlefeed.BottleFeedViewModelTest"`
Expected: FAIL — `BottleFeedViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.babytracker.ui.bottlefeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.EditBottleFeedUseCase
import com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCase
import com.babytracker.domain.usecase.inventory.GetInventoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BottleFeedUiState(
    val feedType: FeedType = FeedType.BREAST_MILK,
    val volumeText: String = "",
    val timestamp: Instant = Instant.EPOCH,
    val activeBags: List<MilkBag> = emptyList(),
    val selectedBagId: Long? = null,
    val notes: String = "",
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val editingId: Long? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class BottleFeedViewModel @Inject constructor(
    private val logBottleFeed: LogBottleFeedUseCase,
    private val editBottleFeed: EditBottleFeedUseCase,
    private val getInventory: GetInventoryUseCase,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BottleFeedUiState(timestamp = now()))
    val uiState: StateFlow<BottleFeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getInventory().collect { bags -> _uiState.update { it.copy(activeBags = bags) } }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit -> _uiState.update { it.copy(volumeUnit = unit) } }
        }
    }

    fun onTypeChange(type: FeedType) = _uiState.update {
        // Clearing the bag when switching away from breast milk keeps state consistent.
        it.copy(feedType = type, selectedBagId = if (type == FeedType.BREAST_MILK) it.selectedBagId else null)
    }

    fun onVolumeChange(text: String) = _uiState.update {
        it.copy(volumeText = text.filter { c -> c.isDigit() }, validationError = null)
    }

    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp) }

    fun onBagSelect(bagId: Long?) = _uiState.update { it.copy(selectedBagId = bagId) }

    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text) }

    fun loadForEdit(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ) = _uiState.update {
        it.copy(
            editingId = id,
            timestamp = timestamp,
            volumeText = volumeMl.toString(),
            feedType = type,
            selectedBagId = linkedMilkBagId,
            notes = notes.orEmpty(),
        )
    }

    fun onSave() {
        val state = _uiState.value
        val volume = state.volumeText.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.update { it.copy(validationError = "Enter a volume") }
            return
        }
        val notes = state.notes.trim().ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    val linkedBag = state.activeBags.firstOrNull { it.id == state.selectedBagId }
                        .takeIf { state.feedType == FeedType.BREAST_MILK }
                    logBottleFeed(state.timestamp, volume, state.feedType, linkedBag, notes)
                } else {
                    val linkedBagId = state.selectedBagId.takeIf { state.feedType == FeedType.BREAST_MILK }
                    editBottleFeed(editingId, state.timestamp, volume, state.feedType, linkedBagId, notes)
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

> If `GetInventoryUseCase`'s `invoke()` signature differs (e.g. returns `Flow<List<MilkBag>>` of active bags vs all), adapt the `getInventory()` call accordingly — confirm against `app/src/main/java/com/babytracker/domain/usecase/inventory/GetInventoryUseCase.kt`. The bag picker needs **active** bags only.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt app/src/test/java/com/babytracker/ui/bottlefeed/BottleFeedViewModelTest.kt
git commit -m "feat(breastfeeding): add BottleFeedViewModel for add and edit"
```

---

## Task 3: BottleFeedSheet composable (reusable add/edit)

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: REQUIRED — invoke `impeccable` (craft mode)** to design the sheet. It must reuse the visual structure of `AddBagSheet` (title, field cells for date/time, rounded `OutlinedTextField`s, full-width primary button + text Cancel) and add: a feed-type `SingleChoiceSegmentedButtonRow` (Breast milk / Formula) at the top, and — only when Breast milk is selected — a bag picker listing active bags as selectable chips/rows labelled with `formatVolume(bag.volumeMl, unit)` + collection date.

- [ ] **Step 2: Implement the composable** (signature mirrors `AddBagSheet` — stateless, driven by the VM)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleFeedSheet(
    state: BottleFeedUiState,
    onTypeChange: (FeedType) -> Unit,
    onVolumeChange: (String) -> Unit,
    onTimeChange: (Instant) -> Unit,
    onBagSelect: (Long?) -> Unit,
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(
                    if (state.editingId == null) R.string.bottle_feed_add_title
                    else R.string.bottle_feed_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            FeedTypeSelector(selected = state.feedType, onSelect = onTypeChange, enabled = !state.isSaving)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.volumeText,
                onValueChange = onVolumeChange,
                label = { Text(stringResource(R.string.bottle_feed_volume_label)) }, // "Volume (mL)"
                singleLine = true,
                isError = state.validationError != null,
                supportingText = { state.validationError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Date + time row — reuse the FieldCell/date+time pattern from AddBagSheet.
            FeedTimeRow(timestamp = state.timestamp, onChange = onTimeChange, enabled = !state.isSaving)

            if (state.feedType == FeedType.BREAST_MILK && state.activeBags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BagPicker(
                    bags = state.activeBags,
                    selectedBagId = state.selectedBagId,
                    unit = state.volumeUnit,
                    onSelect = onBagSelect,
                    enabled = !state.isSaving,
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.bottle_feed_notes_label)) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.bottle_feed_save))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

Implement the private helpers `FeedTypeSelector`, `FeedTimeRow` (copy the date/time cell pattern from `AddBagSheet.CollectionDateRow` — extract a shared helper if practical), and `BagPicker` under the impeccable guidance from Step 1.

- [ ] **Step 3: Add strings** to `strings.xml`

```xml
<string name="bottle_feed_add_title">Log bottle feed</string>
<string name="bottle_feed_edit_title">Edit bottle feed</string>
<string name="bottle_feed_volume_label">Volume (mL)</string>
<string name="bottle_feed_notes_label">Notes (optional)</string>
<string name="bottle_feed_save">Save feed</string>
<string name="bottle_feed_type_breast_milk">Breast milk</string>
<string name="bottle_feed_type_formula">Formula</string>
```
(Reuse an existing `cancel` string if present; otherwise add `<string name="cancel">Cancel</string>`.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat(breastfeeding): add reusable BottleFeedSheet"
```

---

## Task 4: BottleFeedScreen route host + Home entry

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedScreen.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: REQUIRED — invoke `impeccable` (craft mode)** for the Home quick-action button so it matches the existing pumping/inventory quick-action styling.

- [ ] **Step 2: Create `BottleFeedScreen`** — collects `uiState`, shows `BottleFeedSheet` always-expanded, and pops back when `saved` flips true:

```kotlin
@Composable
fun BottleFeedScreen(
    onNavigateBack: () -> Unit,
    viewModel: BottleFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    BottleFeedSheet(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onVolumeChange = viewModel::onVolumeChange,
        onTimeChange = viewModel::onTimeChange,
        onBagSelect = viewModel::onBagSelect,
        onNotesChange = viewModel::onNotesChange,
        onConfirm = viewModel::onSave,
        onDismiss = onNavigateBack,
    )
}
```

- [ ] **Step 3: Register the route** in `AppNavGraph.kt` (near the `Routes.PUMPING` composable):

```kotlin
composable(Routes.BOTTLE_FEED) {
    BottleFeedScreen(onNavigateBack = { navController.popBackStack() })
}
```

And in the `composable(Routes.HOME)` block add:
```kotlin
onNavigateToBottleFeed = { navController.navigate(Routes.BOTTLE_FEED) },
```

- [ ] **Step 4: Add the Home param + quick-action** in `HomeScreen.kt` — add `onNavigateToBottleFeed: () -> Unit = {}` to the screen signature and a quick-action button (styled like the pumping/inventory entries) that calls it.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedScreen.kt app/src/main/java/com/babytracker/navigation/AppNavGraph.kt app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
git commit -m "feat(breastfeeding): add bottle feed screen and home entry"
```

---

## Task 5: Sheet UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/bottlefeed/BottleFeedSheetTest.kt`

- [ ] **Step 1: Write tests** using `createComposeRule()`:
  - Renders both feed-type options and the volume field.
  - Selecting "Breast milk" with non-empty `activeBags` shows the bag picker; selecting "Formula" hides it.
  - Tapping save invokes `onConfirm`.

- [ ] **Step 2: Run (device/CI)**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.bottlefeed.BottleFeedSheetTest"`
Expected: PASS or defer to CI.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/bottlefeed/BottleFeedSheetTest.kt
git commit -m "test(breastfeeding): cover BottleFeedSheet interactions"
```

---

## Acceptance Criteria

- A Home quick-action opens the bottle-feed logging UI (route `bottle_feed`).
- The sheet lets the user pick feed type, enter volume (ml), set date/time, optionally pick an active stash bag (breast milk only), and add notes.
- Saving a new feed calls `LogBottleFeedUseCase`; the bag picker only appears and links for breast-milk feeds.
- `loadForEdit(...)` pre-populates the sheet and saving calls `EditBottleFeedUseCase` (this entry path is exercised by plan 05).
- Blank/zero volume shows an inline validation error and does not save.
- Every composable was built under `impeccable` craft guidance; matches Baby palette + Material 3 + rounded shapes.
- `./gradlew test` passes; sheet UI test passes on device/CI.

## Self-Review Notes

- The sheet is intentionally stateless (driven by `BottleFeedUiState`) so plan 05 can reuse it for edits by calling `loadForEdit` on the same VM.
- Bag picker only renders for breast-milk feeds with active bags; switching type clears any selected bag to avoid linking a bag to a formula feed.
- Volume input stays ml; `formatVolume`/unit is used only for the bag picker labels and read-only displays (plan 02 owns the unit).
- `GetInventoryUseCase` active-bag assumption flagged for the implementer to confirm.
