# Doctor Visit History Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-206

**Goal:** Build the visit history screen — an **Upcoming** group and a **Past** group, each row showing date / provider / notes preview / attached-question count / snapshot badge, with tap-to-edit (reusing the sheet from plan 5) and delete-with-undo — plus its route.

**Architecture:** `DoctorVisitHistoryViewModel` exposes one `StateFlow<DoctorVisitHistoryUiState>` derived from `ObserveDoctorVisitsUseCase`, partitioning visits into upcoming (`date > now`) and past (`date <= now`). Edit re-opens the `DoctorVisitSheet` (plan 5) seeded via `startEdit`. Delete uses `DeleteDoctorVisitUseCase` with snackbar undo (re-insert + re-attach via the add/edit use cases).

**Tech Stack:** Jetpack Compose, Material 3, Hilt Navigation Compose, Coroutines/Flow; Compose UI Test.

## Global Constraints

- Single `StateFlow<*UiState>` + event functions; no `BaseViewModel`.
- Strings in `res/values/strings.xml` **and** `res/values-pt-rBR/strings.xml`.
- Section tint via `doctorVisitColors()` (plan 3).
- Date formatting via `util/DateTimeExt.kt` extension functions (search for the one used by other history screens).

**Dependencies:** Plan 1 (data), Plan 2 (use cases), Plan 3 (theme), Plan 5 (the sheet + ViewModel reused for edit).

**Suggested implementation branch:** `feat/doctor-visit-history`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Strings (en + pt-BR)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1: Add the history strings** to both files:

```xml
<!-- values/strings.xml -->
<string name="doctor_visit_history_title">Visit history</string>
<string name="doctor_visit_history_upcoming">Upcoming</string>
<string name="doctor_visit_history_past">Past</string>
<string name="doctor_visit_history_empty">No visits yet</string>
<string name="doctor_visit_history_question_count">%1$d questions</string>
<string name="doctor_visit_history_has_snapshot">Snapshot</string>
<string name="doctor_visit_history_deleted">Visit deleted</string>
<string name="doctor_visit_history_undo">Undo</string>
<string name="doctor_visit_history_no_provider">Visit</string>
```

```xml
<!-- values-pt-rBR/strings.xml -->
<string name="doctor_visit_history_title">Histórico de consultas</string>
<string name="doctor_visit_history_upcoming">Próximas</string>
<string name="doctor_visit_history_past">Anteriores</string>
<string name="doctor_visit_history_empty">Nenhuma consulta ainda</string>
<string name="doctor_visit_history_question_count">%1$d perguntas</string>
<string name="doctor_visit_history_has_snapshot">Cópia</string>
<string name="doctor_visit_history_deleted">Consulta excluída</string>
<string name="doctor_visit_history_undo">Desfazer</string>
<string name="doctor_visit_history_no_provider">Consulta</string>
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add history strings (en + pt-BR)`

---

### Task 2: `DoctorVisitHistoryViewModel` + UI state

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitHistoryViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/doctorvisit/DoctorVisitHistoryViewModelTest.kt`

- [ ] **Step 1: ViewModel.** Partition visits via the injected "now" (match plan 2's source). Each row needs its attached-question count — derive it by observing questions; the simplest correct approach is a DAO-level join, but to avoid schema churn, observe per-visit counts by combining `observeAllVisits()` with a single `getAllQuestionsOnce()` snapshot refreshed alongside the visits flow. Cleaner: add an `observeAttachedQuestionCounts(): Flow<Map<Long, Int>>` — but that's new DAO surface. **Decision:** count attached questions by combining the visits flow with `repository.observeInboxQuestions()` is wrong (inbox only). Instead, expose a lightweight `@Query("SELECT visit_id AS visitId, COUNT(*) AS count FROM visit_questions WHERE visit_id IS NOT NULL GROUP BY visit_id")` returning `Flow<List<VisitQuestionCount>>` — add this to the DAO/repository in **this** task (small, cohesive with the screen that needs it).

```kotlin
// Add to DoctorVisitDao (plan 1 file):
// data/local/dao/VisitQuestionCount.kt
package com.babytracker.data.local.dao

data class VisitQuestionCount(val visitId: Long, val count: Int)

// in DoctorVisitDao:
@Query("SELECT visit_id AS visitId, COUNT(*) AS count FROM visit_questions WHERE visit_id IS NOT NULL GROUP BY visit_id")
fun observeAttachedQuestionCounts(): kotlinx.coroutines.flow.Flow<List<VisitQuestionCount>>
```

Add the matching `fun observeAttachedQuestionCounts(): Flow<Map<Long, Int>>` to `DoctorVisitRepository`/Impl (map the list to a `Map`).

```kotlin
package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.DeleteDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitsUseCase
import com.babytracker.manager.DoctorVisitReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DoctorVisitHistoryUiState(
    val upcoming: List<DoctorVisit> = emptyList(),
    val past: List<DoctorVisit> = emptyList(),
    val questionCounts: Map<Long, Int> = emptyMap(),
    val lastDeleted: DoctorVisit? = null,
)

@HiltViewModel
class DoctorVisitHistoryViewModel @Inject constructor(
    observeVisits: ObserveDoctorVisitsUseCase,
    private val repository: DoctorVisitRepository,
    private val deleteVisit: DeleteDoctorVisitUseCase,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private val local = MutableStateFlow(DoctorVisitHistoryUiState())

    val uiState: StateFlow<DoctorVisitHistoryUiState> =
        combine(
            observeVisits(),
            repository.observeAttachedQuestionCounts(),
            local,
        ) { visits, counts, state ->
            val instant = now()
            state.copy(
                upcoming = visits.filter { it.date.isAfter(instant) }.sortedBy { it.date },
                past = visits.filter { !it.date.isAfter(instant) }.sortedByDescending { it.date },
                questionCounts = counts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoctorVisitHistoryUiState())

    fun onDelete(visit: DoctorVisit) {
        viewModelScope.launch {
            deleteVisit(visit.id)
            local.update { it.copy(lastDeleted = visit) }
        }
    }

    /**
     * Undo a delete: re-insert the captured visit (preserving its original date / provider /
     * notes / snapshot ref / createdAt — only the row id changes), then re-arm its reminder if
     * still upcoming. Questions were detached to the inbox by the atomic delete and are NOT
     * re-attached (documented v1 trade-off); the user can re-attach from the edit sheet.
     */
    fun onUndoDelete() {
        val deleted = local.value.lastDeleted ?: return
        viewModelScope.launch {
            val newId = repository.insertVisit(deleted.copy(id = 0)) // 0 → Room autogenerates a new id
            reminderScheduler.schedule(deleted.copy(id = newId))     // re-arm under the real new id
            local.update { it.copy(lastDeleted = null) }
        }
    }

    fun onUndoConsumed() = local.update { it.copy(lastDeleted = null) }
}
```

> **Undo behavior:** undo restores the visit row faithfully (a new id is assigned; all other fields preserved). Its previously-attached questions stay in the inbox (the atomic delete moved them there) — this is the documented v1 trade-off, surfaced in the snackbar copy. The scheduler call is a no-op for past visits (the manager guards on future-dated visits).

- [ ] **Step 2: Test** partition logic across the now boundary, ordering (upcoming asc, past desc), `questionCounts` passthrough, delete records `lastDeleted`, and **`onUndoDelete` re-inserts the captured visit** (verify `repository.insertVisit` called with the captured fields and `id = 0`, and `reminderScheduler.schedule` invoked) then clears `lastDeleted`.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.doctorvisit.DoctorVisitHistoryViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisitHistoryViewModel + attached-count query`

---

### Task 3: `DoctorVisitHistoryScreen`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitHistoryScreen.kt`

- [ ] **Step 1: Build the screen** (follow the closest grouped history screen, e.g. `VaccineHistoryScreen`):
  - `Scaffold` + `TopAppBar` (`doctor_visit_history_title`) + `SnackbarHost` + a FAB or top action to add a visit (navigates to `DOCTOR_VISIT`).
  - `LazyColumn` with two `stickyHeader`/section headers: `doctor_visit_history_upcoming` (only when `upcoming` non-empty) and `doctor_visit_history_past`.
  - Each visit row: formatted date (via `DateTimeExt`), provider name (or `doctor_visit_history_no_provider`), a 1–2 line notes preview, a `questionCounts[visit.id]`-based chip (`doctor_visit_history_question_count`) when > 0, and a `doctor_visit_history_has_snapshot` badge when `visit.hasSnapshot()`. Tint accents with `doctorVisitColors()`.
  - Tap a row → `onEdit(visit)` (host re-opens the sheet from plan 5 via `startEdit`).
  - Delete via overflow/swipe → `onDelete(visit)`; on `uiState.lastDeleted != null` show a snackbar `doctor_visit_history_deleted` whose action calls `onUndoDelete()` (and `onUndoConsumed()` on dismiss), via `LaunchedEffect(uiState.lastDeleted)`.
  - Empty state: `doctor_visit_history_empty` when both groups empty.
  - Stateless `DoctorVisitHistoryContent(state, callbacks…)` + `@Composable DoctorVisitHistoryScreen(viewModel = hiltViewModel(), onAddOrEdit, onBack)` wrapper.
- [ ] **Step 2: Previews** — empty, upcoming+past mixed (one with snapshot + questions).
- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisitHistoryScreen`

---

### Task 4: Route + navigation + edit wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route** `DOCTOR_VISIT_HISTORY` (e.g. `"doctor_visit_history"`).
- [ ] **Step 2: Register the destination.** The history screen's add action navigates to `Routes.DOCTOR_VISIT` (add mode); the edit action navigates to `doctor_visit?visitId={id}`. Plan 5 already defined this optional `visitId` argument and the `viewModel.loadForEdit(id)` seeding path, so this task only needs to build the correct nav route string from the tapped visit's id (e.g. a `Routes.doctorVisit(visitId)` helper) and call `navController.navigate(...)`.
- [ ] **Step 3: Add a History entry point** — the Home tile (plan 5) currently opens the add sheet; also surface history (e.g. a "See all" affordance on the tile or in the sheet). Minimum: register the route and navigate to it from the visit sheet's overflow / a Home long-press. Confirm the chosen entry point with the existing tile pattern.
- [ ] **Step 4: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 5: Commit** `feat(doctor-visit): add DOCTOR_VISIT_HISTORY route + edit wiring`

---

### Task 5: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/doctorvisit/DoctorVisitHistoryScreenTest.kt`

- [ ] **Step 1: Test** the stateless content: upcoming + past headers render with the right rows; a row with `hasSnapshot()` shows the snapshot badge and the question-count chip; tapping a row invokes edit; delete invokes the delete callback; empty state shows when both groups empty.
- [ ] **Step 2: Run** `./gradlew connectedAndroidTest --tests "com.babytracker.ui.doctorvisit.DoctorVisitHistoryScreenTest"` (needs emulator); if unavailable, note it.
- [ ] **Step 3: Commit** `test(doctor-visit): add history screen UI test`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- History shows Upcoming (asc) and Past (desc) groups across the now boundary.
- Rows show date, provider, notes preview, attached-question count, snapshot badge.
- Tap edits via the plan-5 sheet; delete shows undo.
- `DOCTOR_VISIT_HISTORY` route works.
- Strings in en + pt-BR.

## Self-Review Notes

- Spec coverage: upcoming/past groups, row metadata, edit, delete-with-undo, route — all present.
- This plan adds a small `observeAttachedQuestionCounts()` query (DAO + repository) because the row count has no other home; it's cohesive with the screen that consumes it. If plan 1 already shipped, this is an additive change to those files.
- Undo is a real restore: `onUndoDelete` re-inserts the captured visit (new id, all other fields preserved) and re-arms its reminder; questions stay in the inbox (documented v1 trade-off). The ViewModel exposes both `onUndoDelete` (restore) and `onUndoConsumed` (dismiss), and the screen wires the snackbar action to `onUndoDelete`.
- Edit reuses plan 5's sheet via a `visitId` nav argument; the `DOCTOR_VISIT` route is extended to accept it.
