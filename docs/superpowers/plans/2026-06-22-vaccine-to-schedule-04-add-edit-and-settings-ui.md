# Vaccine To-Schedule — Plan 04: Add/Edit Sheet & Settings UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. For the Compose screen tasks, also invoke the `ui-designer` skill to finalize spacing/tokens.

**Goal:** Let parents create/edit a to-schedule dose from the add/edit sheet (3-mode toggle + "target date") and configure the to-schedule reminder lead in vaccine settings.

**Architecture:** `VaccineViewModel` gains TO_SCHEDULE handling in its mode/save logic; `VaccineSheet` adds a third segment and a target-date label. `VaccineSettingsViewModel`/`Screen` add a second lead-days selector bound to the new setting from Plan 03.

**Tech Stack:** Jetpack Compose, Material 3, Hilt, JUnit 5, MockK, Turbine, Compose UI Test.

## Global Constraints

- Mode toggle order: **To schedule · Scheduled · Administered** (lifecycle order). Default add mode stays **Administered**.
- A to-schedule (and scheduled) date defaults to **tomorrow** when the current value is not already future.
- New strings in **both** `values/strings.xml` and `values-pt-rBR/strings.xml`. Reuse `R.plurals.vaccine_reminder_lead_option` for lead labels (avoids a new plural / the pt `many` gotcha).
- Domain-accent tokens come from `vaccineColors()` (`VaccinePalette`), never `MaterialTheme.colorScheme`.
- Depends on Plan 01 (enum) + Plan 02 (add/edit use cases) + Plan 03 (`getToScheduleLeadDays`/`setToScheduleLeadDays`).

---

### Task 1: `VaccineViewModel` — TO_SCHEDULE mode + save

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`; `AddVaccineRecordUseCase`, `EditVaccineRecordUseCase`.
- Produces: switching to TO_SCHEDULE defaults the date to tomorrow when not future; save persists the date as `scheduledDate`; a non-future TO_SCHEDULE save surfaces the future-date error on the DATE field.

- [ ] **Step 1: Write the failing tests**

Add to `VaccineViewModelTest.kt` (reuse the class's `viewModel`, `addVaccine`, `editVaccine`, fixed `now` setup):

```kotlin
@Test
fun `switching to TO_SCHEDULE defaults a non-future date to tomorrow`() = runTest {
    viewModel.onDateChange(fixedNow.minus(2, ChronoUnit.DAYS))
    viewModel.onModeChange(VaccineStatus.TO_SCHEDULE)
    val state = viewModel.uiState.value
    assertEquals(VaccineStatus.TO_SCHEDULE, state.status)
    assertTrue(state.date.isAfter(fixedNow))
}

@Test
fun `saving a TO_SCHEDULE record passes the date as the target to the add use case`() = runTest {
    val future = fixedNow.plus(5, ChronoUnit.DAYS)
    coEvery { addVaccine(any(), any(), any(), any(), any()) } returns 1L
    viewModel.onNameChange("MMR")
    viewModel.onModeChange(VaccineStatus.TO_SCHEDULE)
    viewModel.onDateChange(future)
    viewModel.onSave()
    advanceUntilIdle()
    coVerify { addVaccine("MMR", any(), VaccineStatus.TO_SCHEDULE, future, any()) }
}

@Test
fun `a non-future TO_SCHEDULE save shows the future-date error on the date field`() = runTest {
    coEvery { addVaccine(any(), any(), any(), any(), any()) } throws IllegalArgumentException("future")
    viewModel.onNameChange("MMR")
    viewModel.onModeChange(VaccineStatus.TO_SCHEDULE)
    viewModel.onSave()
    advanceUntilIdle()
    val state = viewModel.uiState.value
    assertEquals(VaccineField.DATE, state.errorField)
    assertNotNull(state.validationError)
}
```

Imports as needed: `java.time.temporal.ChronoUnit`, `io.mockk.coEvery`/`coVerify`, `kotlinx.coroutines.test.advanceUntilIdle`, `org.junit.jupiter.api.Assertions.*`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineViewModelTest"`
Expected: FAIL — TO_SCHEDULE not defaulted/saved as a target; error maps to past-date copy.

- [ ] **Step 3: Write minimal implementation**

In `VaccineViewModel.kt`:

`onModeChange` — broaden the future-default condition to cover both pending states:

```kotlin
fun onModeChange(status: VaccineStatus) = _uiState.update {
    // Scheduled and to-schedule dates must be in the future, so when switching to either and the
    // current date is not already future, default to tomorrow.
    val nextDate = if (status != VaccineStatus.ADMINISTERED && !it.date.isAfter(now())) {
        now().plus(1, ChronoUnit.DAYS)
    } else {
        it.date
    }
    it.copy(
        status = status,
        date = nextDate,
        isFutureAdministered = isFutureAdministered(status, nextDate),
        validationError = null,
        errorField = null,
    )
}
```

`onSave` — in the edit-record builder, treat TO_SCHEDULE like SCHEDULED for the date column:

```kotlin
scheduledDate = if (state.status != VaccineStatus.ADMINISTERED) state.date else null,
administeredDate = if (state.status == VaccineStatus.ADMINISTERED) state.date else null,
```

`curatedSaveError` — route both pending states to the future-date error:

```kotlin
private fun curatedSaveError(error: Throwable, state: VaccineUiState): Pair<Int, VaccineField?> = when {
    error !is IllegalArgumentException -> R.string.vaccine_save_error to null
    state.name.isBlank() -> R.string.vaccine_name_required to VaccineField.NAME
    state.status != VaccineStatus.ADMINISTERED -> R.string.vaccine_scheduled_future_error to VaccineField.DATE
    else -> R.string.vaccine_administered_past_error to VaccineField.DATE
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineViewModel.kt app/src/test/java/com/babytracker/ui/vaccine/VaccineViewModelTest.kt
git commit -m "feat(vaccine): handle to-schedule mode in the add/edit view model"
```

---

### Task 2: `VaccineSheet` — third segment + target-date label

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`
- Test: `app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineSheetTest.kt` (add case)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`; `VaccineUiState`.
- Produces: a 3-segment mode toggle; the date label reads "target date" in to-schedule mode.

- [ ] **Step 1: Add the strings**

`values/strings.xml` (after `vaccine_mode_scheduled`, line ~1110):

```xml
<string name="vaccine_mode_to_schedule">To schedule</string>
<string name="vaccine_date_to_schedule_label">Target date</string>
```

`values-pt-rBR/strings.xml`:

```xml
<string name="vaccine_mode_to_schedule">A agendar</string>
<string name="vaccine_date_to_schedule_label">Data prevista</string>
```

- [ ] **Step 2: Write the failing test**

Add to `VaccineSheetTest.kt` (mirror the existing sheet tests; render `VaccineSheet` with a `VaccineUiState`):

```kotlin
@Test
fun toScheduleSegment_isShown() {
    composeRule.setContent {
        VaccineSheet(
            state = VaccineUiState(),
            onNameChange = {}, onDoseChange = {}, onModeChange = {},
            onDateChange = {}, onNotesChange = {}, onConfirm = {}, onDismiss = {},
        )
    }
    composeRule.onNodeWithText(
        composeRule.activity.getString(R.string.vaccine_mode_to_schedule),
    ).assertIsDisplayed()
}
```

(If the test class already has a helper to mount the sheet, use it instead. Use the same `composeRule`/activity accessor pattern as the existing tests in the file.)

- [ ] **Step 3: Run test to verify it fails**

Run (emulator required): `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineSheetTest"`
Expected: FAIL — no to-schedule segment.

- [ ] **Step 4: Write minimal implementation**

In `VaccineSheet.kt`, replace the `modes` list (lines ~150-153) with the 3-way, lifecycle-ordered list:

```kotlin
val modes = listOf(
    VaccineStatus.TO_SCHEDULE to R.string.vaccine_mode_to_schedule,
    VaccineStatus.SCHEDULED to R.string.vaccine_mode_scheduled,
    VaccineStatus.ADMINISTERED to R.string.vaccine_mode_administered,
)
```

Replace the `DateTimeFieldRow` label expression (lines ~172-178) so to-schedule shows its own label:

```kotlin
label = stringResource(
    when (state.status) {
        VaccineStatus.TO_SCHEDULE -> R.string.vaccine_date_to_schedule_label
        VaccineStatus.SCHEDULED -> R.string.vaccine_date_scheduled_label
        VaccineStatus.ADMINISTERED -> R.string.vaccine_date_administered_label
    },
),
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineSheetTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineSheet.kt app/src/main/res/values/strings.xml app/src/main/res/values-pt-rBR/strings.xml app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineSheetTest.kt
git commit -m "feat(vaccine): add to-schedule mode to the add/edit sheet"
```

---

### Task 3: `VaccineSettingsViewModel` — to-schedule lead state

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineSettingsViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineSettingsViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: `VaccineSettingsRepository.getToScheduleLeadDays`/`setToScheduleLeadDays` (Plan 03).
- Produces: `VaccineSettingsUiState.toScheduleLeadDays: Int`; `VaccineSettingsUiState.TO_SCHEDULE_LEAD_DAYS_OPTIONS`; `onToScheduleLeadDaysChange(days: Int)`.

- [ ] **Step 1: Write the failing tests**

Add to `VaccineSettingsViewModelTest.kt` (mock `settings.getToScheduleLeadDays()` in existing setup to `flowOf(14)` so the combine has all flows):

```kotlin
@Test
fun `uiState exposes the to-schedule lead from settings`() = runTest {
    every { settings.getToScheduleLeadDays() } returns flowOf(30)
    viewModel.uiState.test {
        val loaded = expectMostRecentItem() // skip the initial loading emission
        assertEquals(30, loaded.toScheduleLeadDays)
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `onToScheduleLeadDaysChange persists and reschedules`() = runTest {
    viewModel.onToScheduleLeadDaysChange(7)
    advanceUntilIdle()
    coVerify { settings.setToScheduleLeadDays(7) }
    coVerify { reminderScheduler.rescheduleAll() }
}
```

The existing `setup()` must stub `every { settings.getToScheduleLeadDays() } returns flowOf(14)` so the 4-way `combine` in every other test has all flows and doesn't hang. If the test file doesn't use Turbine, replace the first test body with: call `advanceUntilIdle()`, then `assertEquals(30, viewModel.uiState.value.toScheduleLeadDays)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineSettingsViewModelTest"`
Expected: FAIL — `toScheduleLeadDays`/`onToScheduleLeadDaysChange` unresolved.

- [ ] **Step 3: Write minimal implementation**

In `VaccineSettingsViewModel.kt`:

Extend `VaccineSettingsUiState`:

```kotlin
val toScheduleLeadDays: Int = DEFAULT_TO_SCHEDULE_LEAD_DAYS,
```

and its companion:

```kotlin
const val DEFAULT_TO_SCHEDULE_LEAD_DAYS = 14
val TO_SCHEDULE_LEAD_DAYS_OPTIONS = listOf(7, 14, 30)
```

Extend the `combine` to a 4th flow:

```kotlin
combine(
    settings.getReminderEnabled(),
    settings.getReminderLeadDays(),
    settings.getToScheduleLeadDays(),
    permissionGranted,
) { enabled, leadDays, toScheduleLeadDays, granted ->
    VaccineSettingsUiState(
        isLoading = false,
        reminderEnabled = enabled,
        leadDays = leadDays,
        toScheduleLeadDays = toScheduleLeadDays,
        showPermissionWarning = enabled && !granted,
    )
}
```

Add the handler:

```kotlin
fun onToScheduleLeadDaysChange(days: Int) {
    viewModelScope.launch {
        settings.setToScheduleLeadDays(days)
        reminderScheduler.rescheduleAll()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineSettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineSettingsViewModel.kt app/src/test/java/com/babytracker/ui/vaccine/VaccineSettingsViewModelTest.kt
git commit -m "feat(vaccine): expose to-schedule lead in settings view model"
```

---

### Task 4: `VaccineSettingsScreen` — second lead selector

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineSettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

**Interfaces:**
- Consumes: `VaccineSettingsUiState.toScheduleLeadDays`, `TO_SCHEDULE_LEAD_DAYS_OPTIONS`, `onToScheduleLeadDaysChange`.

- [ ] **Step 1: Add the strings**

`values/strings.xml` (after `vaccine_reminder_lead_hint`, line ~1197):

```xml
<string name="vaccine_to_schedule_lead_label">Remind me to book</string>
<string name="vaccine_to_schedule_lead_hint">We\'ll nudge you this many days before the target date to book the appointment.</string>
```

`values-pt-rBR/strings.xml`:

```xml
<string name="vaccine_to_schedule_lead_label">Lembrar de agendar</string>
<string name="vaccine_to_schedule_lead_hint">Vamos te lembrar esta quantidade de dias antes da data prevista para marcar a consulta.</string>
```

- [ ] **Step 2: Add the selector to the screen**

In `VaccineSettingsScreen.kt`, after the existing lead-hint `Text` (line ~225, before the closing `}` of the inner `Column`), add a second labelled segmented row bound to the to-schedule lead:

```kotlin
Spacer(Modifier.height(16.dp))
Text(
    text = stringResource(R.string.vaccine_to_schedule_lead_label),
    style = MaterialTheme.typography.titleSmall,
    color = if (state.reminderEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
)
val toScheduleOptions = VaccineSettingsUiState.TO_SCHEDULE_LEAD_DAYS_OPTIONS
SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    toScheduleOptions.forEachIndexed { index, days ->
        SegmentedButton(
            selected = state.toScheduleLeadDays == days,
            onClick = { viewModel.onToScheduleLeadDaysChange(days) },
            enabled = state.reminderEnabled,
            shape = SegmentedButtonDefaults.itemShape(index = index, count = toScheduleOptions.size),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = vaccine.container,
                activeContentColor = vaccine.onContainer,
            ),
            label = {
                Text(pluralStringResource(R.plurals.vaccine_reminder_lead_option, days, days))
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
Spacer(Modifier.height(8.dp))
Text(
    text = stringResource(R.string.vaccine_to_schedule_lead_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp),
)
```

- [ ] **Step 3: Build to verify the screen compiles + resources resolve**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineSettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-pt-rBR/strings.xml
git commit -m "feat(vaccine): add to-schedule reminder lead selector to settings"
```

---

## Self-Review

- **Spec coverage:** 3-mode sheet + target-date label (Tasks 1-2), settings lead selector (Tasks 3-4). Default add mode stays Administered (UiState default unchanged).
- **Placeholder scan:** the one intentionally-garbled Turbine line in Task 3 Step 1 is flagged with a fix instruction — replace with the file's real Turbine usage or value-assertion.
- **Types:** `onToScheduleLeadDaysChange(Int)` and `toScheduleLeadDays`/`TO_SCHEDULE_LEAD_DAYS_OPTIONS` consistent between VM (Task 3) and screen (Task 4).
- **i18n:** every new string added to both locales; lead labels reuse the existing plural.
