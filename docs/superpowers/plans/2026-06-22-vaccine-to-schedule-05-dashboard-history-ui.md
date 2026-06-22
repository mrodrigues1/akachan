# Vaccine To-Schedule — Plan 05: Dashboard & History UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Invoke the `ui-designer` skill for the Compose row/section tasks to finalize the tint token and dot treatment.

**Goal:** Surface to-schedule doses in a dedicated, visually-distinct "To schedule" section on the dashboard and history screens, with a one-tap **Schedule** action (and Mark-given), never feeding the hero.

**Architecture:** `VaccineDashboardViewModel` splits records into to-schedule / scheduled / given, adds a mark-scheduled commit+undo path (reusing the existing immediate-commit + pending-holder pattern). A new `ToScheduleRow` (tinted background + ring dot) renders the section. The history screen mirrors the section and Schedule action.

**Tech Stack:** Jetpack Compose, Material 3, Hilt, Coroutines/Flow, JUnit 5, MockK, Turbine, Compose UI Test.

## Global Constraints

- To-schedule doses **never drive the hero** (`nextVaccine`/`mostOverdue` stay scheduled-only).
- To-schedule section sorted by target date ascending, then name (stable same-day order).
- Distinct row background = `VaccinePalette.container` (the indigo container tone), distinct from plain scheduled rows; a ring (outlined) status dot reinforces it. `ui-designer` may refine the exact tone.
- Mark-scheduled and direct mark-given **commit immediately** (survive the screen leaving composition); the pending holder only opens the undo window + prevents a one-frame flicker.
- Undo of mark-scheduled reuses `UndoMarkVaccineAdministeredUseCase` (generic write-the-record-back-then-reschedule) — no new use case.
- New strings in **both** locales.
- Depends on Plan 01 (`isPastTarget`) + Plan 02 (`MarkVaccineScheduledUseCase`) + Plan 04 (shared sheet handles to-schedule).

---

### Task 1: `VaccineDashboardViewModel` — section + mark-scheduled

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineDashboardViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineDashboardViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: `MarkVaccineScheduledUseCase` (Plan 02); `UndoMarkVaccineAdministeredUseCase` (existing, reused for undo); `VaccineStatus.TO_SCHEDULE`.
- Produces:
  - `VaccineDashboardUiState.toSchedule: List<VaccineRecord>` (sorted, to-schedule only, never in hero/schedule).
  - `VaccineDashboardUiState.lastMarkedScheduled: VaccineRecord?` (undo-window holder).
  - `markScheduled(record)`, `undoMarkScheduled()`, `onMarkScheduledConsumed()`.

- [ ] **Step 1: Write the failing tests**

Add to `VaccineDashboardViewModelTest.kt` (reuse the class's `observeRecords` flow source + fixed `now`/`zone`; the constructor now needs a `MarkVaccineScheduledUseCase` mock — add it to the test's VM construction):

```kotlin
@Test
fun `to-schedule doses populate the toSchedule section and never the hero`() = runTest {
    val ts = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = fixedNow.plus(20, ChronoUnit.DAYS), createdAt = fixedNow,
    )
    recordsFlow.value = listOf(ts)

    viewModel.uiState.test {
        val state = expectMostRecentItem()
        assertEquals(listOf(1L), state.toSchedule.map { it.id })
        assertNull(state.nextVaccine)
        assertNull(state.mostOverdue)
        assertTrue(state.schedule.isEmpty())
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `markScheduled commits via the use case and opens the undo window`() = runTest {
    val ts = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = fixedNow.plus(20, ChronoUnit.DAYS), createdAt = fixedNow,
    )
    viewModel.markScheduled(ts)
    advanceUntilIdle()
    coVerify { markScheduledUseCase(1L) }
    assertEquals(ts, viewModel.uiState.value.lastMarkedScheduled)
}

@Test
fun `undoMarkScheduled writes the original record back`() = runTest {
    val ts = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = fixedNow.plus(20, ChronoUnit.DAYS), createdAt = fixedNow,
    )
    viewModel.markScheduled(ts)
    advanceUntilIdle()
    viewModel.undoMarkScheduled()
    advanceUntilIdle()
    coVerify { undoMarkGivenUseCase(ts) }
    assertNull(viewModel.uiState.value.lastMarkedScheduled)
}
```

In the test's setup, add `private val markScheduledUseCase = mockk<MarkVaccineScheduledUseCase>(relaxed = true)` and pass it to the `VaccineDashboardViewModel(...)` constructor (new parameter — see Step 3). Use whatever the file already names its records `MutableStateFlow` (shown here as `recordsFlow`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineDashboardViewModelTest"`
Expected: FAIL — `toSchedule`/`markScheduled`/constructor param unresolved.

- [ ] **Step 3: Write minimal implementation**

In `VaccineDashboardViewModel.kt`:

Add to `VaccineDashboardUiState`:

```kotlin
/** To-schedule doses (known but unbooked), sorted by target date asc then name. Never in the hero. */
val toSchedule: List<VaccineRecord> = emptyList(),
/** The dose inside the mark-scheduled undo window, held so the screen can offer an undo snackbar. */
val lastMarkedScheduled: VaccineRecord? = null,
```

Update `isFirstRun`:

```kotlin
val isFirstRun: Boolean get() = schedule.isEmpty() && toSchedule.isEmpty() && givenCount == 0
```

Add the constructor parameter (after `markGivenUseCase`):

```kotlin
private val markScheduledUseCase: MarkVaccineScheduledUseCase,
```

Add a pending holder next to the others:

```kotlin
// The record inside the mark-scheduled undo window. The flip already committed (commits immediately
// so it survives the screen leaving composition); this holds the original to-schedule record so the
// screen can show the undo snackbar and the lists don't flicker while Room re-emits.
private val pendingMarkScheduled = MutableStateFlow<VaccineRecord?>(null)
```

Replace the `combine(...)` call with the 4-flow version and the new sectioning:

```kotlin
combine(
    observeRecords(), pendingMarkGiven, pendingDelete, pendingMarkScheduled,
) { records, pendingMark, pendingDel, pendingSch ->
    val instant = now()
    val today = instant.atZone(zone).toLocalDate()
    val visible = records.filterNot { it.id == pendingDel?.id }

    // To-schedule: hide the one being scheduled (optimistic) and any being marked given.
    val toSchedule = visible
        .filter {
            it.status == VaccineStatus.TO_SCHEDULE && it.scheduledDate != null &&
                it.id != pendingSch?.id && it.id != pendingMark?.id
        }
        .sortedWith(compareBy({ it.scheduledDate }, { it.name }))

    // Scheduled excludes the mark-given one; folds in the just-scheduled one optimistically so it
    // doesn't vanish for a frame while Room re-emits it as SCHEDULED.
    val scheduledBase = visible.filter {
        it.status == VaccineStatus.SCHEDULED && it.scheduledDate != null && it.id != pendingMark?.id
    }
    val optimisticScheduled = pendingSch
        ?.takeIf { p -> scheduledBase.none { it.id == p.id } }
        ?.copy(status = VaccineStatus.SCHEDULED)
    val scheduled = listOfNotNull(optimisticScheduled) + scheduledBase

    val overdue = scheduled.filter { it.isOverdue(instant, zone) }.sortedBy { it.scheduledDate }
    val future = scheduled
        .filterNot { it.isOverdue(instant, zone) }
        .sortedWith(compareBy({ it.scheduledDate }, { it.name }))

    val administered = visible.filter { it.status == VaccineStatus.ADMINISTERED }
    val optimisticPending = pendingMark
        ?.takeIf { p -> administered.none { it.id == p.id } }
        ?.copy(status = VaccineStatus.ADMINISTERED, administeredDate = instant)
    val given = (listOfNotNull(optimisticPending) + administered)
        .sortedByDescending { it.administeredDate ?: it.createdAt }

    val mostOverdue = overdue.firstOrNull()
    val next = future.firstOrNull()
    val nextDay = next?.scheduledDate?.atZone(zone)?.toLocalDate()
    val nextVaccines = if (nextDay == null) {
        emptyList()
    } else {
        future.filter { it.scheduledDate!!.atZone(zone).toLocalDate() == nextDay }
    }
    VaccineDashboardUiState(
        isLoading = false,
        nextVaccine = next,
        nextVaccines = nextVaccines,
        nextInDays = next?.scheduledDate?.let { daysBetween(today, it) },
        mostOverdue = mostOverdue,
        mostOverdueDays = mostOverdue?.scheduledDate?.let { -daysBetween(today, it) },
        overdueCount = overdue.size,
        schedule = overdue + future,
        toSchedule = toSchedule,
        recentlyGiven = given.take(RECENT_LIMIT),
        givenCount = given.size,
        lastMarkedGiven = pendingMark,
        lastMarkedScheduled = pendingSch,
        lastDeleted = pendingDel,
        now = instant,
    )
}
```

Add the action functions (next to `markGiven`/`undoMarkGiven`):

```kotlin
/** Flip [record] to scheduled now (one tap). Commits immediately; opens the undo window. */
fun markScheduled(record: VaccineRecord) {
    pendingMarkScheduled.value = record
    viewModelScope.launch { runCatching { markScheduledUseCase(record.id) } }
}

/** Snackbar "Undo": revert the committed flip by writing the original to-schedule record back. */
fun undoMarkScheduled() {
    val record = pendingMarkScheduled.value ?: return
    pendingMarkScheduled.value = null
    viewModelScope.launch { runCatching { undoMarkGivenUseCase(record) } }
}

/** Snackbar dismissed / timed out: the flip already happened, so just close the undo window. */
fun onMarkScheduledConsumed() {
    pendingMarkScheduled.value = null
}
```

(Add the import `com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCase`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineDashboardViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineDashboardViewModel.kt app/src/test/java/com/babytracker/ui/vaccine/VaccineDashboardViewModelTest.kt
git commit -m "feat(vaccine): section to-schedule doses and add mark-scheduled to dashboard VM"
```

---

### Task 2: Shared `ToScheduleRow` + ring dot

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineShared.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

**Interfaces:**
- Consumes: `VaccinePalette`, `VaccineRecord.isPastTarget`, `nameWithDose()`, `toVaccineDateLabel()`.
- Produces: `internal fun ToScheduleRow(record, colors, isPastTarget, onSchedule, onMarkGiven, onDelete, onEdit)` and `internal fun StatusRingDot(color)`, reusable by both screens.

- [ ] **Step 1: Add the strings**

`values/strings.xml`:

```xml
<string name="vaccine_to_schedule_section_title">To schedule</string>
<string name="vaccine_action_schedule">Schedule</string>
<string name="vaccine_schedule_content_description">Schedule %1$s</string>
<string name="vaccine_to_schedule_target">Book by %1$s</string>
<string name="vaccine_to_schedule_past_target">Was due %1$s</string>
<string name="vaccine_marked_scheduled">Marked as scheduled</string>
```

`values-pt-rBR/strings.xml`:

```xml
<string name="vaccine_to_schedule_section_title">A agendar</string>
<string name="vaccine_action_schedule">Agendar</string>
<string name="vaccine_schedule_content_description">Agendar %1$s</string>
<string name="vaccine_to_schedule_target">Marcar até %1$s</string>
<string name="vaccine_to_schedule_past_target">Estava prevista para %1$s</string>
<string name="vaccine_marked_scheduled">Marcada como agendada</string>
```

- [ ] **Step 2: Add the shared composables**

In `VaccineShared.kt`, add (alongside `StatusDot`):

```kotlin
/** A 10dp hollow ring dot used for to-schedule rows, distinct from the filled/low-alpha StatusDot. */
@Composable
internal fun StatusRingDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .border(width = 1.5.dp, color = color, shape = CircleShape),
    )
}

/**
 * A to-schedule row: a tinted (container-colored) rounded background marks it visually distinct from
 * plain scheduled rows, a ring dot reinforces the state, and the trailing controls let the parent
 * Schedule it (one tap), mark it given directly, or delete it. Tapping the body edits.
 */
@Composable
internal fun ToScheduleRow(
    record: VaccineRecord,
    colors: VaccinePalette,
    isPastTarget: Boolean,
    onSchedule: () -> Unit,
    onMarkGiven: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(colors.container)
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        StatusRingDot(color = colors.onContainer)
        Spacer(Modifier.size(12.dp))
        val date = record.scheduledDate?.toVaccineDateLabel().orEmpty()
        val subtitle = if (isPastTarget) {
            stringResource(R.string.vaccine_to_schedule_past_target, date)
        } else {
            stringResource(R.string.vaccine_to_schedule_target, date)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onContainer,
                maxLines = 1,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onContainer)
        }
        TextButton(
            onClick = onSchedule,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.onContainer),
            modifier = Modifier.semantics {
                // semantics import already present in file scope via the call site; see note below
            },
        ) { Text(stringResource(R.string.vaccine_action_schedule)) }
        androidx.compose.material3.IconButton(onClick = onMarkGiven) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.vaccine_mark_given_content_description, record.name),
                tint = colors.onContainer,
            )
        }
        androidx.compose.material3.IconButton(onClick = onDelete) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.vaccine_delete_content_description, record.name),
                tint = colors.onContainer,
            )
        }
    }
}
```

Add the required imports to `VaccineShared.kt`: `androidx.compose.foundation.border`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.heightIn`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.outlined.CheckCircle`, `androidx.compose.material.icons.outlined.Delete`, `androidx.compose.material3.ButtonDefaults`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`. (Fully-qualify or import — the `ui-designer` pass will tidy imports; prefer top-level imports over the inline `androidx.…` qualifiers shown for brevity.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineShared.kt app/src/main/res/values/strings.xml app/src/main/res/values-pt-rBR/strings.xml
git commit -m "feat(vaccine): add shared to-schedule row and ring dot"
```

---

### Task 3: Dashboard screen — render the To-schedule section + snackbar

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineDashboardScreen.kt`
- Test: `app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineDashboardScreenTest.kt` (add case)

**Interfaces:**
- Consumes: `VaccineDashboardUiState.toSchedule`, `lastMarkedScheduled`; `markScheduled`/`undoMarkScheduled`/`onMarkScheduledConsumed`; `ToScheduleRow`.

- [ ] **Step 1: Write the failing test**

Add to `VaccineDashboardScreenTest.kt` (render `VaccineDashboardContent` with a state containing a to-schedule record + the new `onMarkScheduled` callback — see Step 3 for the new content signature):

```kotlin
@Test
fun toScheduleSection_isRendered() {
    val record = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-07-10T00:00:00Z"), createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )
    composeRule.setContent {
        VaccineDashboardContent(
            state = VaccineDashboardUiState(isLoading = false, toSchedule = listOf(record), now = Instant.parse("2026-06-22T00:00:00Z")),
            snackbarHostState = remember { SnackbarHostState() },
            onAddVaccine = {}, onEditRecord = {}, onMarkGiven = {}, onMarkScheduled = {},
            onDeleteRecord = {}, onRetry = {}, onNavigateToHistory = {}, onNavigateToSettings = {}, onNavigateBack = {},
        )
    }
    composeRule.onNodeWithText(
        composeRule.activity.getString(R.string.vaccine_to_schedule_section_title),
    ).assertIsDisplayed()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (emulator): `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineDashboardScreenTest"`
Expected: FAIL — `onMarkScheduled` param + section don't exist.

- [ ] **Step 3: Write minimal implementation**

In `VaccineDashboardScreen.kt`:

1. `VaccineDashboardScreen` — add the mark-scheduled snackbar (mirror the mark-given one) and pass the new callback:

```kotlin
val markedScheduledMessage = stringResource(R.string.vaccine_marked_scheduled)
LaunchedEffect(state.lastMarkedScheduled) {
    state.lastMarkedScheduled ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
        message = markedScheduledMessage,
        actionLabel = undoLabel,
        duration = SnackbarDuration.Long,
    )
    when (result) {
        SnackbarResult.ActionPerformed -> dashboardViewModel.undoMarkScheduled()
        SnackbarResult.Dismissed -> dashboardViewModel.onMarkScheduledConsumed()
    }
}
```

In the `VaccineDashboardContent(...)` call, add `onMarkScheduled = dashboardViewModel::markScheduled,`.

2. `VaccineDashboardContent` — add `onMarkScheduled: (VaccineRecord) -> Unit,` to its parameters and forward it to `DashboardBody(... onMarkScheduled = onMarkScheduled, ...)`.

3. `DashboardBody` — add `onMarkScheduled: (VaccineRecord) -> Unit,` to its parameters and, in the `LazyColumn`, insert the To-schedule section between the hero and the schedule section (after the `item(key = "hero") { ... }` block):

```kotlin
if (state.toSchedule.isNotEmpty()) {
    item(key = "to_schedule_header") {
        Spacer(Modifier.height(24.dp))
        SectionLabel(
            text = stringResource(R.string.vaccine_to_schedule_section_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() },
        )
    }
    items(state.toSchedule, key = { "ts_${it.id}" }) { record ->
        ToScheduleRow(
            record = record,
            colors = colors,
            isPastTarget = record.isPastTarget(state.now, ZoneId.systemDefault()),
            onSchedule = { onMarkScheduled(record) },
            onMarkGiven = { onMarkGiven(record) },
            onDelete = { onRequestDelete(record) },
            onEdit = { onEditRecord(record) },
        )
    }
}
```

Add imports: `com.babytracker.domain.model.isPastTarget`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineDashboardScreenTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineDashboardScreen.kt app/src/androidTest/java/com/babytracker/ui/vaccine/VaccineDashboardScreenTest.kt
git commit -m "feat(vaccine): render to-schedule section with schedule action on dashboard"
```

---

### Task 4: History — to-schedule list + Schedule action

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/vaccine/VaccineHistoryViewModelTest.kt` (add case)

**Interfaces:**
- Consumes: `MarkVaccineScheduledUseCase`; `VaccineStatus.TO_SCHEDULE`; `ToScheduleRow`.
- Produces: `VaccineHistoryUiState.toSchedule: List<VaccineRecord>`; `VaccineHistoryViewModel.markScheduled(id: Long)`.

- [ ] **Step 1: Write the failing test**

Add to `VaccineHistoryViewModelTest.kt` (constructor gains a `MarkVaccineScheduledUseCase` mock):

```kotlin
@Test
fun `to-schedule records populate the toSchedule list sorted by target date`() = runTest {
    val later = VaccineRecord(id = 1, name = "B", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-08-01T00:00:00Z"), createdAt = Instant.parse("2026-06-01T00:00:00Z"))
    val sooner = VaccineRecord(id = 2, name = "A", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-07-01T00:00:00Z"), createdAt = Instant.parse("2026-06-01T00:00:00Z"))
    recordsFlow.value = listOf(later, sooner)

    viewModel.uiState.test {
        val state = expectMostRecentItem()
        assertEquals(listOf(2L, 1L), state.toSchedule.map { it.id })
        cancelAndConsumeRemainingEvents()
    }
}

@Test
fun `markScheduled delegates to the use case`() = runTest {
    viewModel.markScheduled(5L)
    advanceUntilIdle()
    coVerify { markScheduledUseCase(5L) }
}
```

Add `private val markScheduledUseCase = mockk<MarkVaccineScheduledUseCase>(relaxed = true)` to setup and pass it to the VM constructor.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineHistoryViewModelTest"`
Expected: FAIL — `toSchedule`/`markScheduled`/constructor param unresolved.

- [ ] **Step 3: Write minimal implementation**

In `VaccineHistoryViewModel.kt`:

Add to `VaccineHistoryUiState`:

```kotlin
val toSchedule: List<VaccineRecord> = emptyList(),
```

and include it in `isEmpty`:

```kotlin
val isEmpty: Boolean get() = upcoming.isEmpty() && toSchedule.isEmpty() && administeredByDate.isEmpty()
```

Add the constructor parameter:

```kotlin
private val markScheduledUseCase: MarkVaccineScheduledUseCase,
```

In the `combine` block, derive the list and pass it:

```kotlin
val toSchedule = visible
    .filter { it.status == VaccineStatus.TO_SCHEDULE && it.scheduledDate != null }
    .sortedWith(compareBy({ it.scheduledDate }, { it.name }))
```

```kotlin
VaccineHistoryUiState(
    isLoading = false,
    upcoming = upcoming,
    toSchedule = toSchedule,
    administeredByDate = administeredByDate,
    now = now(),
)
```

Add the action:

```kotlin
fun markScheduled(id: Long) = viewModelScope.launch { runCatching { markScheduledUseCase(id) } }
```

(Add import `com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCase`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.ui.vaccine.VaccineHistoryViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Render the section on the history screen**

In `VaccineHistoryScreen.kt`, add a "To schedule" section above the "Upcoming" section, mirroring how the existing upcoming section is rendered. For each `state.toSchedule` record render a `ToScheduleRow`, wiring `onSchedule = { viewModel.markScheduled(record.id) }`, `onMarkGiven = { viewModel.markGiven(record.id) }`, `onDelete = { /* the screen's existing delete-request path */ }`, `onEdit = { /* existing edit path */ }`. Use the screen's existing section-header composable + `R.string.vaccine_to_schedule_section_title`. (Match the file's existing list/section structure — open it and follow the upcoming-section pattern exactly; do not invent a new layout.)

- [ ] **Step 6: Build + run the history screen test (if present)**

Run: `./gradlew :app:compileDebugKotlin` then, if an instrumented history screen test exists, `./gradlew connectedAndroidTest --tests "com.babytracker.ui.vaccine.VaccineHistoryScreenTest"`
Expected: BUILD SUCCESSFUL / PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryViewModel.kt app/src/main/java/com/babytracker/ui/vaccine/VaccineHistoryScreen.kt app/src/test/java/com/babytracker/ui/vaccine/VaccineHistoryViewModelTest.kt
git commit -m "feat(vaccine): show to-schedule section with schedule action in history"
```

---

## Self-Review

- **Spec coverage:** dashboard sectioning + never-hero + mark-scheduled commit/undo (Task 1), distinct row (Task 2), dashboard render + snackbar (Task 3), history parity (Task 4).
- **Placeholder scan:** Task 4 Step 5 intentionally defers to the existing screen structure (the file's layout must be followed rather than duplicated) — open `VaccineHistoryScreen.kt` and mirror the upcoming section. All other steps carry concrete code.
- **Types:** `onMarkScheduled: (VaccineRecord) -> Unit` threads Screen→Content→Body; `markScheduled(record)` (dashboard) vs `markScheduled(id)` (history) match their call sites; undo reuses `undoMarkGivenUseCase(record)`.
