# Vaccine Add/Edit UI + Home Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-197

**Goal:** Add the vaccine quick-add/edit bottom sheet, its ViewModel, the Home tile, the navigation route, and the localized strings — so a parent can log an administered vaccine or schedule a future one, and see the next upcoming vaccine on the Home screen.

**Architecture:** Mirrors the Diaper UI slice (`DiaperViewModel` + `DiaperUiState`, `DiaperSheet`, `DiaperScreen`, `DiaperHomeCard`, Home wiring), with the vaccine-specific additions: an **"Already given / Schedule for later"** mode toggle that drives `status` and which date the picker edits, a **name field with suggestion chips** from a localized `<string-array>`, and an optional dose-label field. The tile uses `vaccineColors()` (plan 3) and `ObserveVaccineSummaryUseCase` (plan 2).

**Tech Stack:** Jetpack Compose, Material 3, Hilt, Coroutines/Flow; JUnit 5 + MockK (ViewModel unit tests); Compose UI Test (`createComposeRule`).

**Dependencies:** Plan 1 (data), Plan 2 (`AddVaccineRecordUseCase`, `EditVaccineRecordUseCase`, `ObserveVaccineSummaryUseCase`, `VaccineSummary`), Plan 3 (`vaccineColors()`).

**Suggested implementation branch:** `feat/vaccine-add-edit-ui`

**Project convention:** Implement first, then tests. Commit after each task. Strings go in **both** `values/strings.xml` and `values-pt-rBR/strings.xml`. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Strings + suggestion `<string-array>` (en + pt-BR)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1:** Add the vaccine UI strings to `values/strings.xml`. Keep keys grouped under a comment. Suggested set (extend as the sheet needs):

```xml
<!-- Vaccine -->
<string name="vaccine_tile_label">Vaccines</string>
<string name="vaccine_add_title">Add vaccine</string>
<string name="vaccine_edit_title">Edit vaccine</string>
<string name="vaccine_name_label">Vaccine name</string>
<string name="vaccine_dose_label">Dose (optional)</string>
<string name="vaccine_notes_label">Notes (optional)</string>
<string name="vaccine_mode_administered">Already given</string>
<string name="vaccine_mode_scheduled">Schedule for later</string>
<string name="vaccine_date_administered_label">Date given</string>
<string name="vaccine_date_scheduled_label">Scheduled date</string>
<string name="vaccine_save">Save</string>
<string name="vaccine_save_error">Could not save vaccine</string>
<string name="vaccine_tile_next">Next: %1$s</string>
<string name="vaccine_tile_in_days">in %1$d days</string>
<string name="vaccine_tile_overdue">Overdue</string>
<string name="vaccine_tile_last_given">Last: %1$s</string>
<string name="vaccine_tile_none">No upcoming vaccines</string>
<string-array name="vaccine_suggestions">
    <item>BCG</item>
    <item>Hepatitis B</item>
    <item>DTaP</item>
    <item>Hib</item>
    <item>Polio (IPV)</item>
    <item>Rotavirus</item>
    <item>Pneumococcal (PCV)</item>
    <item>MMR</item>
    <item>Varicella</item>
    <item>Hepatitis A</item>
    <item>Meningococcal</item>
    <item>Influenza</item>
</string-array>
```

- [ ] **Step 2:** Add the same keys with Portuguese (pt-BR) text to `values-pt-rBR/strings.xml`. Translate labels (e.g. `vaccine_tile_label` → "Vacinas", `vaccine_mode_administered` → "Já aplicada", `vaccine_mode_scheduled` → "Agendar", `vaccine_tile_overdue` → "Atrasada", `vaccine_tile_none` → "Nenhuma vacina agendada"). Keep vaccine proper names (BCG, MMR, DTaP, Hib, IPV, PCV) but localize the common-noun ones (e.g. "Hepatite B", "Poliomielite (VIP)", "Pneumocócica (PCV)", "Influenza/Gripe"). Keep both `<string-array name="vaccine_suggestions">` arrays the **same length and order**.

- [ ] **Step 3: Commit** `feat(vaccine): add vaccine UI strings and suggestion list (en + pt-BR)`

---

### Task 2: `HomeTile.VACCINE`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`

- [ ] **Step 1:** Add `VACCINE` to the enum and to `DEFAULT_ORDER` (place it after `DIAPER`, grouping the point-in-time trackers). `reconcile()` auto-appends it for existing users — no DataStore migration needed.
- [ ] **Step 2:** If a feature-visibility registry exists (the Feature Selection project — search `rg -l "HomeTile\." app/src/main/java/com/babytracker/domain`), add `VACCINE` to whatever enumerates toggleable tiles so it can be shown/hidden like the others. If none, skip.
- [ ] **Step 3:** Update `HomeTile` tests if they assert the full enum/order set (e.g. `HomeViewModelTileOrderTest`).
- [ ] **Step 4: Commit** `feat(vaccine): add VACCINE home tile to the tile set`

---

### Task 3: `VaccineUiState` + `VaccineViewModel`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/vaccine/VaccineViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineViewModelTest.kt`

- [ ] **Step 1: Implement** (mirrors `DiaperViewModel`; the mode toggle sets `status`, and the single `date` field is interpreted per status on save)

```kotlin
package com.babytracker.ui.vaccine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class VaccineUiState(
    val name: String = "",
    val doseLabel: String = "",
    val status: VaccineStatus = VaccineStatus.ADMINISTERED,
    val date: Instant = Instant.EPOCH,
    val notes: String = "",
    val suggestions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val editingId: Long? = null,
    val editingCreatedAt: Instant? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class VaccineViewModel @Inject constructor(
    private val addVaccine: AddVaccineRecordUseCase,
    private val editVaccine: EditVaccineRecordUseCase,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaccineUiState(
            date = now(),
            suggestions = appContext.resources.getStringArray(R.array.vaccine_suggestions).toList(),
        ),
    )
    val uiState: StateFlow<VaccineUiState> = _uiState.asStateFlow()

    fun onNameChange(text: String) = _uiState.update { it.copy(name = text, validationError = null) }
    fun onDoseChange(text: String) = _uiState.update { it.copy(doseLabel = text) }
    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text) }
    fun onDateChange(date: Instant) = _uiState.update { it.copy(date = date, validationError = null) }

    fun onModeChange(status: VaccineStatus) = _uiState.update {
        // Scheduled vaccines must be in the future (AddVaccineRecordUseCase enforces it), so when
        // switching to "schedule" and the current date is not already future, default to tomorrow.
        val nextDate = if (status == VaccineStatus.SCHEDULED && !it.date.isAfter(now())) {
            now().plus(1, java.time.temporal.ChronoUnit.DAYS)
        } else {
            it.date
        }
        it.copy(status = status, date = nextDate, validationError = null)
    }

    fun loadForEdit(record: VaccineRecord) = _uiState.update {
        it.copy(
            editingId = record.id,
            editingCreatedAt = record.createdAt,
            isEditing = true,
            name = record.name,
            doseLabel = record.doseLabel.orEmpty(),
            status = record.status,
            date = record.administeredDate ?: record.scheduledDate ?: now(),
            notes = record.notes.orEmpty(),
            saved = false,
            validationError = null,
        )
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    addVaccine(state.name, state.doseLabel, state.status, state.date, state.notes)
                } else {
                    editVaccine(
                        VaccineRecord(
                            id = editingId,
                            name = state.name,
                            doseLabel = state.doseLabel,
                            status = state.status,
                            scheduledDate = if (state.status == VaccineStatus.SCHEDULED) state.date else null,
                            administeredDate = if (state.status == VaccineStatus.ADMINISTERED) state.date else null,
                            notes = state.notes,
                            createdAt = state.editingCreatedAt ?: now(),
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        validationError = error.message ?: appContext.getString(R.string.vaccine_save_error),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Test** (MockK the use cases; cover: add administered, add scheduled, edit builds the right date field per status, validation failure surfaces `validationError`, double-tap guard). Use Robolectric or a fake `Context` for `getStringArray`/`getString` — follow how `DiaperViewModelTest` provides the context.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add VaccineViewModel and UiState`

---

### Task 4: `VaccineSheet` + `VaccineScreen`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/vaccine/VaccineSheet.kt`
- Create: `app/src/main/java/com/babytracker/ui/vaccine/VaccineScreen.kt`

- [ ] **Step 1: `VaccineSheet`** — a stateless content composable taking `VaccineUiState` + callbacks (mirror `DiaperSheet`'s structure and the app's existing date/time picker component). Layout, top to bottom:
  - Title (`vaccine_add_title` / `vaccine_edit_title`).
  - **Name** `OutlinedTextField` (`vaccine_name_label`) with a horizontally-scrolling row of suggestion **chips** (`state.suggestions`); tapping a chip sets the name. Tint selected/leading elements with `vaccineColors().accent`.
  - **Dose** `OutlinedTextField` (`vaccine_dose_label`), optional.
  - **Mode toggle** — a `SegmentedButton`/two-option selector: "Already given" (`VaccineStatus.ADMINISTERED`) vs "Schedule for later" (`VaccineStatus.SCHEDULED`) → `onModeChange`.
  - **Date** picker labeled `vaccine_date_administered_label` or `vaccine_date_scheduled_label` per mode. Reuse the existing date picker used by other sheets. For administered, cap at "now"; for scheduled, allow future.
  - **Notes** `OutlinedTextField`, optional.
  - **Save** button (disabled while `isSaving`); show `validationError` inline.
- [ ] **Step 2: `VaccineScreen`** — thin wrapper that hosts the sheet/route, collects `uiState`, passes callbacks, and invokes an `onSaved` navigation callback when `state.saved` flips true (mirror `DiaperScreen`).
- [ ] **Step 3: Commit** `feat(vaccine): add VaccineSheet and VaccineScreen`

---

### Task 5: Navigation route

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` (confirm exact filename)

- [ ] **Step 1:** Add `const val VACCINE = "vaccine"` to `Routes` (history route is added in plan 5).
- [ ] **Step 2:** Add the `composable(Routes.VACCINE) { VaccineScreen(...) }` destination to the nav graph, mirroring the `Routes.DIAPER` destination.
- [ ] **Step 3: Commit** `feat(vaccine): add vaccine add/edit navigation route`

---

### Task 6: Home tile (`VaccineHomeCard`) + Home wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

- [ ] **Step 1:** Add a `VaccineHomeCard` composable rendering from `VaccineSummary`:
  - If `overdueCount > 0` → show `vaccine_tile_overdue` (warning amber accent — import `WarningAmber`/`WarningContainerAmber` by name, never via `colorScheme`).
  - Else if `nextUpcoming != null` → show its name + a "in N days" countdown (`vaccine_tile_in_days`, computed from `scheduledDate` and `now`), tinted with `vaccineColors()`.
  - Else if `lastAdministered != null` → show the last administered vaccine (`vaccine_tile_last_given` with its name/date) — the spec's fallback before empty.
  - Else → `vaccine_tile_none` empty state.
  Add the `HomeTile.VACCINE -> VaccineHomeCard(...)` branch in the tile `when(...)`, plus `isFullWidth()`/`minTileHeightDp()` entries (compact, like `DIAPER`).
- [ ] **Step 2:** Add `onVaccine: () -> Unit` to `HomeTileCallbacks`; wire it through `HomeScreen` to navigate to `Routes.VACCINE`.
- [ ] **Step 3:** In `HomeViewModel`, collect `ObserveVaccineSummaryUseCase` into the home state (mirror how the diaper summary is exposed) so `VaccineHomeCard` has data.
- [ ] **Step 4:** Update `HomeViewModel` tests that assert the tile/callback set if needed.
- [ ] **Step 5: Commit** `feat(vaccine): add Home vaccine tile and wiring`

---

### Task 7: Compose UI tests

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineSheetTest.kt`

- [ ] **Step 1:** With `createComposeRule()`: render `VaccineSheet`, type a name, tap a suggestion chip (asserts name fills), toggle to "Schedule for later" (asserts the date label switches to scheduled), tap Save (asserts the save callback fires). Mirror `DiaperSheet`'s UI test.
- [ ] **Step 2: Run** on an emulator: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineSheetTest"`. If no emulator, note it.
- [ ] **Step 3: Commit** `test(vaccine): add VaccineSheet Compose UI test`

---

## Acceptance Criteria

- A parent can open the vaccine sheet from the Home tile, type/pick a name, choose "Already given" or "Schedule for later", set the date, and save.
- Administered saves reject future dates; scheduled saves accept future dates (validation surfaced inline).
- The Home tile shows the next upcoming vaccine with a countdown, an overdue indicator when applicable, the last administered vaccine when there is history but nothing upcoming, or an empty state when there is no vaccine data at all.
- New tile auto-appears for existing users via `reconcile()`; no DataStore migration.
- All strings resolve in both English and pt-BR; the two `vaccine_suggestions` arrays are equal length/order.
- `./gradlew test` passes; the Compose UI test passes on an emulator.

## Self-Review Notes

- The single `date` field + `status` mode mirrors the spec's "Already given / Schedule for later" toggle and feeds `AddVaccineRecordUseCase(name, dose, status, date, notes)` directly — no separate scheduled/administered date inputs to keep in sync.
- Edit reconstructs the correct nullable date field from `status`; `EditVaccineRecordUseCase` re-validates and re-arms the reminder.
- The tile reads `VaccineSummary` (plan 2), not the raw list, so the countdown/overdue logic stays in the domain layer.
- Suggestion list is a localized resource array (not in `domain/`), keeping the domain framework-free.
