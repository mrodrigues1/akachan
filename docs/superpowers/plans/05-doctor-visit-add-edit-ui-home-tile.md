# Doctor Visit Add/Edit UI + Home Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-205

**Goal:** Build the add/edit visit bottom sheet (date, provider name, notes, attach inbox questions, snapshot attach/view), wire it behind a `DOCTOR_VISIT` route, and add the Home-screen `DoctorVisitHomeCard` tile.

**Architecture:** A bottom sheet (`DoctorVisitSheet`) driven by `DoctorVisitViewModel` exposing one `StateFlow<DoctorVisitUiState>`. The sheet is reused for add and edit. The Home tile (`DoctorVisitHomeCard`) is a compact card (like `DIAPER`/`VACCINE`) fed by `ObserveDoctorVisitSummaryUseCase`, added to the `HomeTile` enum so it participates in reorder. The snapshot control attaches a lightweight reference (`AttachSnapshotToVisitUseCase`) and "View" re-generates an export on demand (`GenerateVisitSnapshotUseCase`).

**Tech Stack:** Jetpack Compose, Material 3, Hilt Navigation Compose, Coroutines/Flow; Compose UI Test.

## Global Constraints

- Single `StateFlow<*UiState>` + event functions; no `BaseViewModel`.
- Strings in `res/values/strings.xml` **and** `res/values-pt-rBR/strings.xml`.
- Section tint via `doctorVisitColors()` (plan 3).
- `HomeTile.DOCTOR_VISIT` is auto-reconciled into existing users' saved order — no DataStore migration.
- Date stored as `Instant`; use the app's existing date-picker pattern (search `rg "DatePicker" -g "*.kt"`).

**Dependencies:** Plan 1 (data), Plan 2 (use cases), Plan 3 (theme). Plan 4 (question inbox) for the attach list source + the "manage questions" entry point.

**Suggested implementation branch:** `feat/doctor-visit-add-edit-ui`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Add `DOCTOR_VISIT` to the `HomeTile` enum

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/HomeTileTest.kt` (if one exists; otherwise extend the reconcile test that covers `VACCINE`)

- [ ] **Step 1: Add the enum constant** `DOCTOR_VISIT` and insert it into `DEFAULT_ORDER` in a sensible slot — directly after `VACCINE` (medical grouping):

```kotlin
// in enum body, after VACCINE:
DOCTOR_VISIT,

// in DEFAULT_ORDER, after VACCINE:
DOCTOR_VISIT,
```

- [ ] **Step 2: Test** — assert `reconcile(listOf())` contains `DOCTOR_VISIT`, and that an older saved order without it gets it appended (mirror the existing `VACCINE` reconcile assertion).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.model.HomeTileTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DOCTOR_VISIT home tile to enum`

---

### Task 2: Strings (en + pt-BR)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1: Add the visit + tile + snapshot strings** to both files. At minimum:

```xml
<!-- values/strings.xml -->
<string name="doctor_visit_tile_label">Doctor visits</string>
<string name="doctor_visit_add_title">Log a visit</string>
<string name="doctor_visit_edit_title">Edit visit</string>
<string name="doctor_visit_date">Date</string>
<string name="doctor_visit_provider_hint">Doctor or clinic (optional)</string>
<string name="doctor_visit_notes_hint">Notes</string>
<string name="doctor_visit_attach_questions">Attach questions to ask</string>
<string name="doctor_visit_manage_questions">Manage questions</string>
<string name="doctor_visit_snapshot_attach">Attach data snapshot</string>
<string name="doctor_visit_snapshot_view">View snapshot</string>
<string name="doctor_visit_snapshot_attached">Snapshot attached</string>
<string name="doctor_visit_snapshot_failed">Couldn\'t generate snapshot</string>
<string name="doctor_visit_save">Save</string>
<string name="doctor_visit_tile_next_in_days">Next visit in %1$d days</string>
<string name="doctor_visit_tile_today">Visit today</string>
<string name="doctor_visit_tile_last">Last visit %1$s</string>
<string name="doctor_visit_tile_open_questions">%1$d questions to ask</string>
<string name="doctor_visit_tile_empty">No visits logged</string>
```

```xml
<!-- values-pt-rBR/strings.xml -->
<string name="doctor_visit_tile_label">Consultas médicas</string>
<string name="doctor_visit_add_title">Registrar consulta</string>
<string name="doctor_visit_edit_title">Editar consulta</string>
<string name="doctor_visit_date">Data</string>
<string name="doctor_visit_provider_hint">Médico ou clínica (opcional)</string>
<string name="doctor_visit_notes_hint">Anotações</string>
<string name="doctor_visit_attach_questions">Anexar perguntas</string>
<string name="doctor_visit_manage_questions">Gerenciar perguntas</string>
<string name="doctor_visit_snapshot_attach">Anexar cópia dos dados</string>
<string name="doctor_visit_snapshot_view">Ver cópia</string>
<string name="doctor_visit_snapshot_attached">Cópia anexada</string>
<string name="doctor_visit_snapshot_failed">Não foi possível gerar a cópia</string>
<string name="doctor_visit_save">Salvar</string>
<string name="doctor_visit_tile_next_in_days">Próxima consulta em %1$d dias</string>
<string name="doctor_visit_tile_today">Consulta hoje</string>
<string name="doctor_visit_tile_last">Última consulta %1$s</string>
<string name="doctor_visit_tile_open_questions">%1$d perguntas para fazer</string>
<string name="doctor_visit_tile_empty">Nenhuma consulta registrada</string>
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add visit + tile strings (en + pt-BR)`

---

### Task 3: `DoctorVisitViewModel` + `DoctorVisitUiState`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/doctorvisit/DoctorVisitViewModelTest.kt`

- [ ] **Step 1: UI state + ViewModel.** State: `date: Instant`, `providerName: String`, `notes: String`, `inboxQuestions: List<VisitQuestion>` (observed), `selectedQuestionIds: Set<Long>`, `snapshotLabel: String?`, `isSaving`, `saved`, `editingId: Long?`. Loads inbox via `ObserveInboxQuestionsUseCase`. `onSave` branches on `editingId`: null → `AddDoctorVisitUseCase(date, provider, notes, selectedQuestionIds.toList())`; non-null → `EditDoctorVisitUseCase(visit, selectedQuestionIds.toList())`. `onAttachSnapshot` requires an existing visit id (attach only available in edit, or after first save) — call `AttachSnapshotToVisitUseCase(visitId, label)`. `onViewSnapshot` calls `GenerateVisitSnapshotUseCase()` and emits a one-shot result/error event.

```kotlin
package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.AttachSnapshotToVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.EditDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.GenerateVisitSnapshotUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveVisitQuestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DoctorVisitUiState(
    val editingId: Long? = null,
    val date: Instant = Instant.now(),
    val providerName: String = "",
    val notes: String = "",
    val inboxQuestions: List<VisitQuestion> = emptyList(),
    // Questions already attached to the visit being edited (empty on the add path). Shown in the
    // attach section alongside the inbox so the user can see + deselect existing attachments.
    val attachedQuestions: List<VisitQuestion> = emptyList(),
    val selectedQuestionIds: Set<Long> = emptySet(),
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Instant? = null,
    // Original row timestamp, preserved across an edit so updateVisit() never rewrites created_at.
    // Unused on the add path (the use case stamps a fresh createdAt there).
    val createdAt: Instant = Instant.now(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoctorVisitViewModel @Inject constructor(
    observeInbox: ObserveInboxQuestionsUseCase,
    private val observeVisitQuestions: ObserveVisitQuestionsUseCase,
    private val repository: DoctorVisitRepository,
    private val addVisit: AddDoctorVisitUseCase,
    private val editVisit: EditDoctorVisitUseCase,
    private val attachSnapshot: AttachSnapshotToVisitUseCase,
    private val generateSnapshot: GenerateVisitSnapshotUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(DoctorVisitUiState())

    // Live attached-questions stream for the visit being edited (empty list on the add path).
    private val attachedFlow =
        local.map { it.editingId }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else observeVisitQuestions(id)
        }

    val uiState: StateFlow<DoctorVisitUiState> =
        combine(observeInbox(), attachedFlow, local) { inbox, attached, state ->
            state.copy(inboxQuestions = inbox, attachedQuestions = attached)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoctorVisitUiState())

    fun onDateChange(date: Instant) = local.update { it.copy(date = date) }
    fun onProviderChange(v: String) = local.update { it.copy(providerName = v) }
    fun onNotesChange(v: String) = local.update { it.copy(notes = v) }
    fun onToggleQuestion(id: Long) = local.update {
        it.copy(selectedQuestionIds = if (id in it.selectedQuestionIds) it.selectedQuestionIds - id else it.selectedQuestionIds + id)
    }

    fun startEdit(visit: DoctorVisit) {
        local.update {
            it.copy(
                editingId = visit.id,
                date = visit.date,
                providerName = visit.providerName.orEmpty(),
                notes = visit.notes.orEmpty(),
                snapshotLabel = visit.snapshotLabel,
                snapshotCreatedAt = visit.snapshotCreatedAt,
                createdAt = visit.createdAt, // preserve the original timestamp for the update
            )
        }
        // Seed the selection from the questions currently attached to this visit (one-shot),
        // so the edit form starts with all existing attachments selected; the user can deselect.
        viewModelScope.launch {
            val attachedIds = observeVisitQuestions(visit.id).first().map { it.id }.toSet()
            local.update { it.copy(selectedQuestionIds = attachedIds) }
        }
    }

    /** Entry point for the edit route: fetch the visit by id, then seed the edit form. */
    fun loadForEdit(visitId: Long) {
        viewModelScope.launch {
            val visit = repository.getVisitById(visitId) ?: return@launch
            startEdit(visit)
        }
    }

    fun onSave() {
        val s = local.value
        if (s.isSaving) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val editingId = s.editingId
            if (editingId == null) {
                addVisit(s.date, s.providerName, s.notes, s.selectedQuestionIds.toList())
            } else {
                editVisit(
                    DoctorVisit(
                        id = editingId,
                        date = s.date,
                        providerName = s.providerName,
                        notes = s.notes,
                        snapshotLabel = s.snapshotLabel,
                        snapshotCreatedAt = s.snapshotCreatedAt,
                        createdAt = s.createdAt, // preserved original — never Instant.now()
                    ),
                    s.selectedQuestionIds.toList(),
                )
            }
            local.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun onSavedConsumed() = local.update { it.copy(saved = false) }
}
```

> **createdAt invariant:** `EditDoctorVisitUseCase` calls `repository.updateVisit(visit)`, which writes the full entity — so `createdAt` and `snapshotCreatedAt` must be the **original** values carried in the UI state from `startEdit`, never `Instant.now()`. The snippet above does this correctly; the test in Step 2 asserts it.

- [ ] **Step 2: Test** add path (calls `addVisit` with selected ids), edit path (calls `editVisit` preserving the original `createdAt`/`snapshotCreatedAt`), `loadForEdit` seeds `selectedQuestionIds` from the visit's attached questions and surfaces them in `attachedQuestions`, deselecting an attached question removes it from `selectedQuestionIds` (so save detaches it), question toggle set logic, `saved` flag. Mock `repository.getVisitById` + `observeVisitQuestions`.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.doctorvisit.DoctorVisitViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisitViewModel`

---

### Task 4: `DoctorVisitSheet` + `DoctorVisitScreen`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitSheet.kt`
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitScreen.kt`

- [ ] **Step 1: Sheet content** (`ModalBottomSheet` or the repo's sheet pattern — match `VaccineSheet`):
  - Title from `editingId` (`doctor_visit_add_title` / `doctor_visit_edit_title`).
  - Date row using the existing date-picker pattern → `onDateChange`.
  - `OutlinedTextField` provider name (`doctor_visit_provider_hint`) → `onProviderChange`.
  - `OutlinedTextField` notes (multiline, `doctor_visit_notes_hint`) → `onNotesChange`.
  - **Attach questions** section: list the union of `uiState.attachedQuestions` (already attached to this visit — only present when editing) **and** `uiState.inboxQuestions`, de-duplicated by id, as toggleable chips/checkbox rows whose checked state is `id in uiState.selectedQuestionIds` (`onToggleQuestion`). This makes existing attachments visible and deselectable on edit (deselecting one detaches it on save via the reconcile). A "Manage questions" text button navigates to `VISIT_QUESTIONS` (plan 4). If both lists are empty, hide the list and show only the manage button.
  - **Snapshot** control: if `editingId != null` show "Attach data snapshot" (calls VM attach) and, when `snapshotLabel != null`, "View snapshot" (calls VM generate → opens/shares the result; surface `doctor_visit_snapshot_failed` on error). If adding a new visit, show the snapshot control only after the first save (or disable with a hint that snapshots attach to saved visits).
  - Save button (`doctor_visit_save`), disabled while `isSaving`.
  - On `saved == true`, dismiss the sheet and call `onSavedConsumed()`.
- [ ] **Step 2: `DoctorVisitScreen`** — a thin wrapper hosting the sheet (mirror `VaccineScreen`), with params `editVisitId: Long? = null`, `onManageQuestions: () -> Unit`, `onDismiss: () -> Unit`. In a `LaunchedEffect(editVisitId)`, call `viewModel.loadForEdit(editVisitId)` when it is non-null (edit mode).
- [ ] **Step 3: Previews** — add sheet (empty inbox), add sheet (with inbox questions), edit sheet (with snapshot attached).
- [ ] **Step 4: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 5: Commit** `feat(doctor-visit): add DoctorVisitSheet and DoctorVisitScreen`

---

### Task 5: `DoctorVisitHomeCard` + Home wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Home summary in the ViewModel.** Inject `ObserveDoctorVisitSummaryUseCase`, expose its `DoctorVisitSummary` in the Home UI state (mirror how the vaccine/diaper summary is surfaced).
- [ ] **Step 2: `DoctorVisitHomeCard`** in `HomeTileContent.kt` (compact tile, like `VACCINE`):
  - If `summary.nextUpcoming != null`: show `doctor_visit_tile_next_in_days` (or `doctor_visit_tile_today` when the countdown is 0) with the provider name.
  - Else if `summary.lastPast != null`: show `doctor_visit_tile_last` with the formatted date.
  - Else: `doctor_visit_tile_empty`.
  - Always show `doctor_visit_tile_open_questions` when `summary.openQuestionCount > 0`.
  - Tint with `doctorVisitColors()`.
  - Tapping the tile invokes `onDoctorVisit`.
- [ ] **Step 3: Add `onDoctorVisit`** to `HomeTileCallbacks` and render `DoctorVisitHomeCard` in the `when (tile)` switch for `HomeTile.DOCTOR_VISIT`; wire `onDoctorVisit` through `HomeScreen` to navigate to `Routes.DOCTOR_VISIT`.
- [ ] **Step 4: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 5: Commit** `feat(doctor-visit): add Home tile and wiring`

---

### Task 6: Route + navigation

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route** `DOCTOR_VISIT` with an optional edit arg: `"doctor_visit?visitId={visitId}"` (default `-1L` = add mode), following the repo's optional-nav-arg convention.
- [ ] **Step 2: Register the destination.** Read the `visitId` argument; when it is a real id (`>= 0`) call `viewModel.loadForEdit(visitId)` once (e.g. in a `LaunchedEffect(visitId)`), otherwise the form stays in add mode:

```kotlin
composable(
    route = Routes.DOCTOR_VISIT,
    arguments = listOf(navArgument("visitId") { type = NavType.LongType; defaultValue = -1L }),
) { entry ->
    val visitId = entry.arguments?.getLong("visitId") ?: -1L
    DoctorVisitScreen(
        editVisitId = visitId.takeIf { it >= 0 },
        onManageQuestions = { navController.navigate(Routes.VISIT_QUESTIONS) },
        onDismiss = navController::popBackStack,
    )
}
```

`DoctorVisitScreen` calls `viewModel.loadForEdit(editVisitId)` in a `LaunchedEffect(editVisitId)` when non-null.
- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DOCTOR_VISIT route`

---

### Task 7: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/doctorvisit/DoctorVisitSheetTest.kt`

- [ ] **Step 1: Test** the stateless sheet content: entering provider/notes + selecting an inbox question + Save invokes the save callback with the selection; the Home card (test `DoctorVisitHomeCard` separately) renders countdown / last / empty states from a fake `DoctorVisitSummary`. Follow existing Compose test patterns.
- [ ] **Step 2: Run** `./gradlew connectedAndroidTest --tests "com.babytracker.ui.doctorvisit.DoctorVisitSheetTest"` (needs emulator); if unavailable, note it.
- [ ] **Step 3: Commit** `test(doctor-visit): add visit sheet + home card UI tests`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- A new visit can be logged with date + optional provider + notes, attaching selected inbox questions.
- Editing preserves `created_at` (no corruption) and reconciles attached questions.
- Snapshot attach stores a reference; "View snapshot" re-generates an export on demand.
- The Home tile shows countdown / last-visit / empty plus the open-question count, tinted Slate, and navigates to the sheet.
- `DOCTOR_VISIT` route works; "Manage questions" navigates to the inbox.
- Strings in en + pt-BR.

## Self-Review Notes

- Spec coverage: HomeTile enum, home card + wiring, sheet (date/provider/notes/attach-questions/snapshot), screen wrapper, route — all present.
- **Critical correctness item:** the edit path carries the original `createdAt` (and `snapshotCreatedAt`) in the UI state from `startEdit`; the snippet uses `s.createdAt`, never `Instant.now()`, and Step 2's test asserts preservation. This protects import/export identity, which dedupes on `createdAt`.
- Snapshot attach is gated to saved visits (needs a visit id); for a brand-new visit the control activates after first save — documented to avoid attaching to a non-existent row.
- Edit mode loads the visit's already-attached questions (live via `observeVisitQuestions(editingId)`) and seeds `selectedQuestionIds` from them, so existing attachments are visible and deselectable; the atomic `update + reconcile` (plan 2) then detaches anything the user unselected. The edit route seeds via `loadForEdit(visitId)`.
- The Home summary "now" source must match plan 2's `ObserveDoctorVisitSummaryUseCase` injection.
