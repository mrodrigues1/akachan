# Diaper History Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-162

**Goal:** A diaper history screen that groups changes by day (most recent first), lets the user edit a change (reusing `DiaperSheet`), and delete one with an Undo snackbar. Reachable via a "View history" link in the quick-log sheet.

**Architecture:** `DiaperHistoryViewModel` derives a day-grouped list from `ObserveDiaperChangesUseCase` and exposes a one-shot `deletions` flow for the snackbar. The screen reuses the existing `DiaperSheet` + a `DiaperViewModel` instance for editing (its `loadForEdit` → `onSave` path, added in plan 3). Delete is undoable by re-logging the captured change. Daily grouping + sticky headers mirror `SleepHistoryScreen`.

**Tech Stack:** Jetpack Compose + Material 3, Hilt, Compose Navigation, JUnit 5 + MockK + Turbine, Compose UI Test.

**Dependencies:** Plan 1 (data), Plan 2 (`ObserveDiaperChangesUseCase`, `DeleteDiaperChangeUseCase`, `LogDiaperChangeUseCase`), Plan 3 (`DiaperSheet`, `DiaperViewModel.loadForEdit`, `Routes.DIAPER`, `DiaperScreen`).

**Suggested implementation branch:** `feat/diaper-history`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Register the history route

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`

- [ ] **Step 1:** Add: `const val DIAPER_HISTORY = "diaper/history"`
- [ ] **Step 2: Commit** `feat(diaper): add diaper history route constant`

---

### Task 2: `DiaperHistoryViewModel`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/diaper/DiaperHistoryViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/diaper/DiaperHistoryViewModelTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.ui.diaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.ObserveDiaperChangesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DiaperHistoryViewModel @Inject constructor(
    observeDiaperChanges: ObserveDiaperChangesUseCase,
    private val deleteDiaperChange: DeleteDiaperChangeUseCase,
    private val logDiaperChange: LogDiaperChangeUseCase,
    private val zone: ZoneId,
) : ViewModel() {

    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<DiaperChange>>>> =
        observeDiaperChanges()
            .map { changes ->
                changes
                    .groupBy { it.timestamp.atZone(zone).toLocalDate() }
                    .toSortedMap(reverseOrder())
                    .map { (date, list) -> date to list.sortedByDescending { it.timestamp } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _deletions = MutableSharedFlow<DiaperChange>(extraBufferCapacity = 1)
    val deletions: SharedFlow<DiaperChange> = _deletions.asSharedFlow()

    fun onDelete(change: DiaperChange) {
        viewModelScope.launch {
            runCatching { deleteDiaperChange(change.id) }
                .onSuccess { _deletions.emit(change) }
        }
    }

    fun onUndoDelete(change: DiaperChange) {
        viewModelScope.launch {
            runCatching { logDiaperChange(change.type, change.timestamp, change.notes) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 2: Test** (Turbine on the grouped flow; verify delete emits + delegates, undo re-logs)

```kotlin
package com.babytracker.ui.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.ObserveDiaperChangesUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DiaperHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val observe = mockk<ObserveDiaperChangesUseCase>()
    private val delete = mockk<DeleteDiaperChangeUseCase>()
    private val log = mockk<LogDiaperChangeUseCase>(relaxed = true)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun change(id: Long, at: Instant) =
        DiaperChange(id = id, timestamp = at, type = DiaperType.WET, createdAt = at)

    @Test
    fun `groups by day descending`() = runTest {
        val day16 = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val day15 = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant()
        every { observe() } returns flowOf(listOf(change(2, day16), change(1, day15)))
        val vm = DiaperHistoryViewModel(observe, delete, log, zone)
        vm.historyByDateDesc.test {
            // stateIn emits its initial empty value first; skip past it to the mapped value.
            var groups = awaitItem()
            if (groups.isEmpty()) groups = awaitItem()
            assertEquals(2, groups.size)
            assertEquals(java.time.LocalDate.of(2026, 6, 16), groups.first().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete delegates and emits, undo re-logs`() = runTest {
        every { observe() } returns flowOf(emptyList())
        coEvery { delete(7) } just Runs
        val vm = DiaperHistoryViewModel(observe, delete, log, zone)
        val c = change(7, Instant.ofEpochMilli(1_000))

        vm.deletions.test {
            vm.onDelete(c)
            assertEquals(7, awaitItem().id)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { delete(7) }

        vm.onUndoDelete(c)
        coVerify { log(c.type, c.timestamp, c.notes) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.diaper.DiaperHistoryViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DiaperHistoryViewModel`

---

### Task 3: `DiaperHistoryScreen`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/diaper/DiaperHistoryScreen.kt`

- [ ] **Step 1: Implement** (daily grouping + sticky headers mirror `SleepHistoryScreen`; reuses `DiaperSheet` + a `DiaperViewModel` for editing; delete shows an Undo snackbar). `toRelativeLabel()` is the existing `java.time.LocalDate` extension used by Sleep history.

```kotlin
package com.babytracker.ui.diaper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.DiaperChange
import com.babytracker.util.toRelativeLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    historyViewModel: DiaperHistoryViewModel = hiltViewModel(),
    editViewModel: DiaperViewModel = hiltViewModel(),
) {
    val grouped by historyViewModel.historyByDateDesc.collectAsStateWithLifecycle()
    val editState by editViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(editState.saved) {
        if (editState.saved) showEditSheet = false
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        historyViewModel.deletions.collect { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "Diaper change deleted",
                actionLabel = "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) historyViewModel.onUndoDelete(deleted)
        }
    }

    if (showEditSheet) {
        DiaperSheet(
            state = editState,
            onTypeChange = editViewModel::onTypeChange,
            onTimeChange = editViewModel::onTimeChange,
            onNotesChange = editViewModel::onNotesChange,
            onConfirm = editViewModel::onSave,
            onDismiss = { showEditSheet = false },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Diaper History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (grouped.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🧷", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No diaper changes yet",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            ) {
                grouped.forEach { (date, changes) ->
                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${changes.size} changes".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                    items(changes, key = { it.id }) { change ->
                        DiaperHistoryRow(
                            change = change,
                            onEdit = {
                                editViewModel.loadForEdit(
                                    id = change.id,
                                    timestamp = change.timestamp,
                                    type = change.type,
                                    notes = change.notes,
                                    createdAt = change.createdAt,
                                )
                                showEditSheet = true
                            },
                            onDelete = { historyViewModel.onDelete(change) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaperHistoryRow(
    change: DiaperChange,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = timeFormatter.format(change.timestamp.atZone(ZoneId.systemDefault()).toLocalTime())
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(change.type.emoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${change.type.label} · $time",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                change.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${change.type.label} change at $time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit** `feat(diaper): add DiaperHistoryScreen with edit and delete`

---

### Task 4: History entry point + nav wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/diaper/DiaperSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/diaper/DiaperScreen.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1:** In `DiaperSheet`, add an optional `onNavigateToHistory: (() -> Unit)? = null` parameter, and render a history link just above the Cancel button (only when non-null and not editing):

```kotlin
if (onNavigateToHistory != null && !state.isEditing) {
    TextButton(
        onClick = onNavigateToHistory,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("View diaper history", style = MaterialTheme.typography.labelLarge)
    }
}
```

- [ ] **Step 2:** In `DiaperScreen`, add `onNavigateToHistory: () -> Unit` and pass it to `DiaperSheet(onNavigateToHistory = onNavigateToHistory, ...)`. The history screen continues to call `DiaperSheet` without this argument (defaults to `null`, so no link there).
- [ ] **Step 3:** In `AppNavGraph.kt`:
  - Update the diaper destination: `DiaperScreen(onNavigateBack = { navController.popBackStack() }, onNavigateToHistory = { navController.navigate(Routes.DIAPER_HISTORY) })`.
  - Add the history destination + import `com.babytracker.ui.diaper.DiaperHistoryScreen`:

```kotlin
composable(Routes.DIAPER_HISTORY) {
    DiaperHistoryScreen(onNavigateBack = { navController.popBackStack() })
}
```

- [ ] **Step 4: Build** `./gradlew assembleDebug` — expect SUCCESS.
- [ ] **Step 5: Commit** `feat(diaper): wire diaper history navigation`

---

### Task 5: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/diaper/DiaperHistoryScreenTest.kt`

- [ ] **Step 1:** Render `DiaperHistoryScreen` against a fake `DiaperHistoryViewModel` (or a Hilt test graph seeded with an in-memory DB holding two changes on different days). Assert: two day headers render; tapping a row's delete icon shows the "Undo" snackbar; tapping a row opens the edit sheet (the `diaper_edit_title` string is displayed). Follow the existing history UI test pattern under `app/src/androidTest/java/com/babytracker/ui/`.
- [ ] **Step 2: Run** `./gradlew connectedAndroidTest --tests "com.babytracker.ui.diaper.DiaperHistoryScreenTest"` on an emulator — expect PASS. If no emulator, note it.
- [ ] **Step 3: Commit** `test(diaper): add DiaperHistoryScreen UI test`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- History groups changes by day, newest day first, newest change first within a day; each row shows type emoji, label, time, and notes.
- Tapping a row opens the pre-filled edit sheet; saving updates the row in place.
- Deleting a row removes it and shows an Undo snackbar; Undo restores the change.
- "View diaper history" link appears in the quick-log sheet (not in the edit sheet) and navigates to the history screen.

## Self-Review Notes

- Spec coverage: daily grouping, edit, delete with undo, history entry point — all covered.
- Type consistency: `editViewModel.loadForEdit(id, timestamp, type, notes, createdAt)` matches the plan-3 signature; `DiaperSheet`'s new `onNavigateToHistory` is optional/defaulted so plan-3 callers compile unchanged. `onDelete`/`onUndoDelete(change)` match the VM.
- The edit sheet and quick-log sheet share `DiaperSheet`; the `isEditing` flag (set by `loadForEdit`) hides the history link during edit.
- `tertiary`/`surfaceVariant` are standard M3 tokens.
