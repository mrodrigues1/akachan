# Vaccine History Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-200

**Goal:** Add the vaccine history screen — an **Upcoming/Scheduled** group (overdue flagged, each row offering "Mark as given") and an **Administered** group (daily-grouped, most recent first) — with tap-to-edit and delete-with-undo.

**Architecture:** Mirrors `DiaperHistoryScreen` + `DiaperHistoryViewModel` (daily grouping, `pendingDelete` state machine, snackbar undo). Vaccine adds the two-section split and the mark-as-given action wired to `MarkVaccineAdministeredUseCase`. Undo re-creates the record via `AddVaccineRecordUseCase` (re-arms its reminder), so the ViewModel needs no repository access.

**Tech Stack:** Jetpack Compose, Material 3, Hilt, Coroutines/Flow; JUnit 5 + MockK + Turbine (ViewModel); Compose UI Test.

**Dependencies:** Plan 1 (data), Plan 2 (`ObserveVaccineRecordsUseCase`, `MarkVaccineAdministeredUseCase`, `DeleteVaccineRecordUseCase`, `AddVaccineRecordUseCase`, `isOverdue`), Plan 3 (`vaccineColors()`), Plan 4 (`VaccineScreen`/sheet for edit, `Routes.VACCINE`, strings).

**Suggested implementation branch:** `feat/vaccine-history-screen`

**Project convention:** Implement first, then tests. Commit after each task. Strings in both locales. Pre-commit hook runs ktlint/detekt.

---

### Task 1: History strings (en + pt-BR)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1:** Add to `values/strings.xml`:

```xml
<!-- Vaccine history -->
<string name="vaccine_history_title">Vaccine history</string>
<string name="vaccine_history_upcoming">Upcoming</string>
<string name="vaccine_history_administered">Administered</string>
<string name="vaccine_history_empty">No vaccines yet</string>
<string name="vaccine_mark_given">Mark as given</string>
<string name="vaccine_overdue_badge">Overdue</string>
<string name="vaccine_deleted">Vaccine deleted</string>
<string name="vaccine_undo">Undo</string>
<string name="vaccine_scheduled_for">Scheduled for %1$s</string>
```

- [ ] **Step 2:** Mirror in `values-pt-rBR/strings.xml` (e.g. "Histórico de vacinas", "Próximas", "Aplicadas", "Marcar como aplicada", "Atrasada", "Vacina excluída", "Desfazer", "Agendada para %1$s").
- [ ] **Step 3: Commit** `feat(vaccine): add vaccine history strings (en + pt-BR)`

---

### Task 2: `VaccineHistoryViewModel`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineHistoryViewModelTest.kt`

- [ ] **Step 1: Implement** — observe all records, split into upcoming (SCHEDULED, soonest first — overdue sorts first naturally) and administered (ADMINISTERED, grouped by local date desc). `markGiven` calls the use case; `requestDelete`/`confirmDelete`/`undoDelete` follow the diaper `pendingDelete` pattern, with undo re-creating via `AddVaccineRecordUseCase`.

```kotlin
package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class VaccineHistoryUiState(
    val upcoming: List<VaccineRecord> = emptyList(),
    val administeredByDate: List<Pair<LocalDate, List<VaccineRecord>>> = emptyList(),
    val now: Instant = Instant.EPOCH,
)

@HiltViewModel
class VaccineHistoryViewModel @Inject constructor(
    observeRecords: ObserveVaccineRecordsUseCase,
    private val markGivenUseCase: MarkVaccineAdministeredUseCase,
    private val deleteUseCase: DeleteVaccineRecordUseCase,
    private val addUseCase: AddVaccineRecordUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) : ViewModel() {

    val uiState: StateFlow<VaccineHistoryUiState> =
        observeRecords()
            .combine(MutableStateFlow(Unit)) { records, _ -> records }
            .let { flow ->
                kotlinx.coroutines.flow.flow {
                    flow.collect { records ->
                        val current = now()
                        val upcoming = records
                            .filter { it.status == VaccineStatus.SCHEDULED }
                            .sortedBy { it.scheduledDate ?: it.createdAt }
                        val administered = records
                            .filter { it.status == VaccineStatus.ADMINISTERED }
                            .sortedByDescending { it.administeredDate ?: it.createdAt }
                            .groupBy { (it.administeredDate ?: it.createdAt).atZone(zone).toLocalDate() }
                            .toList()
                            .sortedByDescending { it.first }
                        emit(VaccineHistoryUiState(upcoming, administered, current))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaccineHistoryUiState())

    private val _pendingDelete = MutableStateFlow<VaccineRecord?>(null)
    val pendingDelete: StateFlow<VaccineRecord?> = _pendingDelete

    fun markGiven(id: Long) = viewModelScope.launch { markGivenUseCase(id) }

    fun requestDelete(record: VaccineRecord) = viewModelScope.launch {
        _pendingDelete.value = record
        deleteUseCase(record.id)
    }

    fun undoDelete() {
        val record = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            val date = record.administeredDate ?: record.scheduledDate ?: now()
            addUseCase(record.name, record.doseLabel, record.status, date, record.notes)
        }
    }

    fun clearPendingDelete() { _pendingDelete.value = null }
}
```

> Note: simplify the `uiState` flow to match the codebase idiom — if `DiaperHistoryViewModel` uses a plain `.map { ... }.stateIn(...)`, use that instead of the `combine`/nested-flow shown here. The shape (upcoming list + administered-by-date) is what matters.

- [ ] **Step 2: Test** (Turbine): given a mix of scheduled (one overdue, one future) + administered (two dates) records, assert `upcoming` is sorted earliest-first and `administeredByDate` groups by day desc; `markGiven` invokes the use case; `requestDelete` then `undoDelete` re-adds with the captured fields.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineHistoryViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add VaccineHistoryViewModel`

---

### Task 3: `VaccineHistoryScreen`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryScreen.kt`

- [ ] **Step 1: Implement** (mirror `DiaperHistoryScreen` list/scaffold/snackbar structure):
  - Top app bar titled `vaccine_history_title`.
  - **Upcoming** section header (`vaccine_history_upcoming`); each row shows name, dose, scheduled date (`vaccine_scheduled_for`), an **Overdue** badge (warning amber — import `WarningAmber`/`OnWarningContainerAmber` by name) when `record.isOverdue(state.now)`, and a **"Mark as given"** button → `markGiven(id)`. Tap a row to edit (navigate to `VaccineScreen` in edit mode).
  - **Administered** section header (`vaccine_history_administered`); daily-grouped rows (date header + entries), tinted with `vaccineColors()`. Tap to edit.
  - Empty state `vaccine_history_empty` when both groups are empty.
  - Delete via long-press/swipe → `requestDelete(record)`; show a snackbar (`vaccine_deleted` + `vaccine_undo`) wired to `undoDelete()`; dismiss → `clearPendingDelete()`.
- [ ] **Step 2: Commit** `feat(vaccine): add VaccineHistoryScreen`

---

### Task 4: History route

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: the nav graph (`AppNavGraph.kt`).

- [ ] **Step 1:** Add `const val VACCINE_HISTORY = "vaccine/history"` to `Routes`.
- [ ] **Step 2:** Add the `composable(Routes.VACCINE_HISTORY) { VaccineHistoryScreen(...) }` destination; provide a way to reach it (e.g. an overflow/"history" affordance on the vaccine sheet or Home tile long-press, matching how Diaper history is reached).
- [ ] **Step 3: Commit** `feat(vaccine): add vaccine history navigation route`

---

### Task 5: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineHistoryScreenTest.kt`

- [ ] **Step 1:** With `createComposeRule()`: render the screen with a fake state containing one overdue scheduled + one administered record; assert the Overdue badge shows, the "Mark as given" button is present on the scheduled row and fires its callback, and the administered row appears under its date header.
- [ ] **Step 2: Run** on an emulator; if none, note it.
- [ ] **Step 3: Commit** `test(vaccine): add VaccineHistoryScreen UI test`

---

## Acceptance Criteria

- Upcoming and Administered groups render correctly; overdue scheduled vaccines are flagged.
- "Mark as given" flips a scheduled vaccine to administered and cancels its reminder (via the use case).
- Rows are editable (reuse the plan-4 sheet) and deletable with working snackbar undo (undo re-creates the record and re-arms any reminder).
- All strings resolve in both locales.
- `./gradlew test` passes; the Compose UI test passes on an emulator.

## Self-Review Notes

- Undo re-creates via `AddVaccineRecordUseCase` rather than re-inserting a raw row, so the ViewModel stays repository-free and the reminder is re-armed automatically. The new row gets a fresh id/`createdAt`, which is invisible to the user.
- The upcoming list sorts by scheduled date ascending, so overdue items (earliest) naturally sort to the top; the `isOverdue(now)` badge is computed in the UI from `state.now`.
- The exact `uiState` flow builder should follow whatever idiom `DiaperHistoryViewModel` uses (`.map{}.stateIn`); the nested-flow snippet is illustrative of the output shape, not prescriptive.
- Mark-as-given and edit both reuse plan-2 use cases — no new domain logic here.
