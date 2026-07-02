# Doctor Visit Question Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scope visible questions to their visit, add inline question capture to the visit form, and make question-management actions visibly button-like.

**Architecture:** Keep the existing Room ownership model and repository flows. Add draft/add behavior to `DoctorVisitViewModel`, reuse the dashboard capture composable, and choose the displayed list in Compose based on add/edit mode.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, StateFlow, coroutines, MockK, JUnit 5, Compose UI Test.

---

### Task 1: Add question capture behavior

**Files:**
- Modify: `app/src/test/java/com/babytracker/ui/doctorvisit/DoctorVisitViewModelTest.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitViewModel.kt`

- [ ] **Step 1: Write the failing ViewModel test**

Add an `AddVisitQuestionUseCase` mock to the fixture, pass it to `DoctorVisitViewModel`, and add:

```kotlin
@Test
fun `adding a question clears the draft and selects the inserted question`() = runTest {
    every { repository.observeInboxQuestions() } returns flowOf(emptyList())
    every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
    coEvery { addQuestion("Question text") } returns 42
    val vm = vm()
    vm.onQuestionDraftChange("  Question text  ")
    vm.onAddQuestion()
    advanceUntilIdle()
    assertEquals("", vm.uiState.value.questionDraft)
    assertEquals(setOf(42L), vm.uiState.value.selectedQuestionIds)
}
```

Add a second test that submits whitespace, verifies `addQuestion` is never called, and keeps `selectedQuestionIds` empty.

- [ ] **Step 2: Verify RED**

Run `.\gradlew.bat testDebugUnitTest --tests "com.babytracker.ui.doctorvisit.DoctorVisitViewModelTest"`.

Expected: FAIL because the draft state, handlers, and constructor dependency do not exist.

- [ ] **Step 3: Implement the minimum behavior**

Add `questionDraft: String = ""` to `DoctorVisitUiState`, inject `AddVisitQuestionUseCase`, and add:

```kotlin
fun onQuestionDraftChange(value: String) = local.update { it.copy(questionDraft = value) }

fun onAddQuestion() {
    val text = local.value.questionDraft.trim()
    if (text.isEmpty()) return
    local.update { it.copy(questionDraft = "") }
    viewModelScope.launch {
        val id = addQuestion(text)
        local.update { it.copy(selectedQuestionIds = it.selectedQuestionIds + id) }
    }
}
```

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2. Expected: all class tests pass. Commit with `feat(doctorvisit): add inline question capture state`.

### Task 2: Scope questions and wire inline capture

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/ui/doctorvisit/DoctorVisitSheetTest.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitDashboardScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitScreen.kt`

- [ ] **Step 1: Write failing Compose coverage**

Add a `setSheet` helper and test these distinct states:

```kotlin
DoctorVisitUiState(inboxQuestions = listOf(question(1, "Inbox question")))
```

Add mode must display `Inbox question`.

```kotlin
DoctorVisitUiState(
    editingId = 7,
    attachedQuestions = listOf(question(1, "Attached question", 7)),
    inboxQuestions = listOf(
        question(2, "Unrelated inbox question"),
        question(3, "New question"),
    ),
    selectedQuestionIds = setOf(1, 3),
)
```

Edit mode must display `Attached question` and `New question`, and `assertDoesNotExist()` for `Unrelated inbox question`. A third test clicks the `Add question` content description and verifies the new `onAddQuestion` callback.

- [ ] **Step 2: Verify RED**

Start an emulator if needed with `android emulator start Small_Phone`, then run `.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.ui.doctorvisit.DoctorVisitSheetTest`.

Expected: FAIL because edit mode still displays unrelated inbox questions and the capture callback does not exist.

- [ ] **Step 3: Reuse the existing capture row**

Change `QuestionCaptureRow` in `DoctorVisitDashboardScreen.kt` from `private` to `internal`. Pass `onQuestionDraftChange` and `onAddQuestion` through `DoctorVisitScreen` to `DoctorVisitSheet`, and render:

```kotlin
QuestionCaptureRow(
    draft = state.questionDraft,
    colors = colors,
    onDraftChange = onQuestionDraftChange,
    onAddQuestion = onAddQuestion,
)
```

- [ ] **Step 4: Implement add/edit list selection**

```kotlin
val questions = remember(
    state.isEditing,
    state.attachedQuestions,
    state.inboxQuestions,
    state.selectedQuestionIds,
) {
    if (state.isEditing) {
        state.attachedQuestions + state.inboxQuestions.filter { it.id in state.selectedQuestionIds }
    } else {
        state.inboxQuestions
    }
}
```

- [ ] **Step 5: Verify GREEN and commit**

Run the focused ViewModel and Compose commands. Expected: both pass. Commit with `fix(doctorvisit): scope questions to each visit`.

### Task 3: Strengthen management affordances

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/ui/doctorvisit/DoctorVisitSheetTest.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitDashboardScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/doctorvisit/DoctorVisitSheet.kt`

- [ ] **Step 1: Cover both management actions**

Extend Compose coverage to find `Manage questions` and `Manage all`, assert button/click semantics, and invoke their callbacks. Keep labels localized through existing resources.

- [ ] **Step 2: Replace text links**

Use Material 3 `OutlinedButton`, Doctor Visit Slate content color, `MaterialTheme.shapes.extraLarge`, and at least a 48dp touch target. Add no strings, colors, or components.

- [ ] **Step 3: Compile and commit**

Run `.\gradlew.bat assembleDebugAndroidTest`. Expected: BUILD SUCCESSFUL. Commit with `fix(doctorvisit): clarify question management actions`.

### Task 4: Full verification and review

**Files:**
- Modify: `AI_TASK_PROGRESS.md` (ignored local resumability file)

- [ ] **Step 1: Run full unit tests**

Run `.\gradlew.bat test`. Expected: BUILD SUCCESSFUL with no failed tests.

- [ ] **Step 2: Run production build**

Run `.\gradlew.bat build`. Expected: BUILD SUCCESSFUL. The pre-commit hook already runs ktlint and detekt; do not invoke them separately.

- [ ] **Step 3: Review against the approved spec**

Run `git diff origin/main...HEAD --check`, `git diff --stat origin/main...HEAD`, and `git status --short --branch`. Expected: no whitespace errors or unintended files.

- [ ] **Step 4: Mark local task progress complete**

Check every item in `AI_TASK_PROGRESS.md` only after verification succeeds.
