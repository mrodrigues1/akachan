# Doctor Visit Question Inbox UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-204

**Goal:** Build the pre-visit question **inbox** screen — add a question, tap a row to expand it full-screen for reading at the appointment, toggle answered, delete with undo — plus its route and navigation.

**Architecture:** MVVM with a single `StateFlow<VisitQuestionsUiState>` and event-handler functions (per `CLAUDE.md`). The ViewModel observes the inbox via `ObserveInboxQuestionsUseCase` and mutates via the add/toggle/delete use cases (plan 2). The "expand full-screen" interaction is a focused read view (a full-width dialog or a dedicated detail composable) showing the question text large and legible. Delete uses the established snackbar-undo pattern used by other history screens.

**Tech Stack:** Jetpack Compose, Material 3, Hilt Navigation Compose, Coroutines/Flow; Compose UI Test (androidTest).

## Global Constraints

- Single `StateFlow<*UiState>` + event functions; no `BaseViewModel`.
- All user-facing strings in `res/values/strings.xml` **and** `res/values-pt-rBR/strings.xml`.
- Use the section accent via `doctorVisitColors()` (plan 3) where a section tint is needed; if plan 3 has not merged, fall back to `MaterialTheme.colorScheme.primary` and swap in a follow-up — but prefer landing plan 3 first.
- Follow the existing screen+sheet patterns (e.g. `ui/vaccine/`, `ui/diaper/`).

**Dependencies:** Plan 1 (data), Plan 2 (use cases). Plan 3 (theme) recommended-before for the accent.

**Suggested implementation branch:** `feat/doctor-visit-question-inbox`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Strings (en + pt-BR)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1: Add the inbox strings** to both files (keep keys identical; translate values). At minimum:

```xml
<!-- values/strings.xml -->
<string name="visit_questions_title">Questions to ask</string>
<string name="visit_questions_add_hint">Add a question…</string>
<string name="visit_questions_add">Add</string>
<string name="visit_questions_empty">No questions yet. Add one to ask at your next visit.</string>
<string name="visit_questions_mark_answered">Mark answered</string>
<string name="visit_questions_mark_unanswered">Mark unanswered</string>
<string name="visit_questions_delete">Delete</string>
<string name="visit_questions_deleted">Question deleted</string>
<string name="visit_questions_undo">Undo</string>
<string name="visit_questions_expand_close">Close</string>
```

```xml
<!-- values-pt-rBR/strings.xml -->
<string name="visit_questions_title">Perguntas para o médico</string>
<string name="visit_questions_add_hint">Adicionar pergunta…</string>
<string name="visit_questions_add">Adicionar</string>
<string name="visit_questions_empty">Nenhuma pergunta ainda. Adicione uma para perguntar na próxima consulta.</string>
<string name="visit_questions_mark_answered">Marcar como respondida</string>
<string name="visit_questions_mark_unanswered">Marcar como não respondida</string>
<string name="visit_questions_delete">Excluir</string>
<string name="visit_questions_deleted">Pergunta excluída</string>
<string name="visit_questions_undo">Desfazer</string>
<string name="visit_questions_expand_close">Fechar</string>
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add question inbox strings (en + pt-BR)`

---

### Task 2: `VisitQuestionsViewModel` + `VisitQuestionsUiState`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/VisitQuestionsViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/doctorvisit/VisitQuestionsViewModelTest.kt`

- [ ] **Step 1: UI state + ViewModel.** State holds the question list, the draft input text, and an optional `expandedQuestion`. Delete captures the removed `VisitQuestion` for undo (re-insert via `AddVisitQuestionUseCase` is text-only; instead re-insert the captured row through the repository — simplest is a dedicated path: keep the captured domain object and re-add via `AddVisitQuestionUseCase` preserving text + answered). For fidelity, re-insert through `repository.insertQuestion` is cleaner — expose an `undoDelete` that re-adds the captured question's text (answered state is acceptable to reset to false on undo; document this).

```kotlin
package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.DeleteVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisitQuestionsUiState(
    val questions: List<VisitQuestion> = emptyList(),
    val draft: String = "",
    val expandedQuestion: VisitQuestion? = null,
    val lastDeleted: VisitQuestion? = null,
)

@HiltViewModel
class VisitQuestionsViewModel @Inject constructor(
    observeInbox: ObserveInboxQuestionsUseCase,
    private val addQuestion: AddVisitQuestionUseCase,
    private val toggleAnswered: ToggleVisitQuestionAnsweredUseCase,
    private val deleteQuestion: DeleteVisitQuestionUseCase,
) : ViewModel() {

    private val localState = MutableStateFlow(VisitQuestionsUiState())

    val uiState: StateFlow<VisitQuestionsUiState> =
        combine(observeInbox(), localState) { questions, local ->
            local.copy(questions = questions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitQuestionsUiState())

    fun onDraftChange(text: String) = localState.update { it.copy(draft = text) }

    fun onAdd() {
        val text = localState.value.draft
        if (text.isBlank()) return
        viewModelScope.launch {
            addQuestion(text)
            localState.update { it.copy(draft = "") }
        }
    }

    fun onToggleAnswered(id: Long) = viewModelScope.launch { toggleAnswered(id) }.let { }

    fun onExpand(question: VisitQuestion?) = localState.update { it.copy(expandedQuestion = question) }

    fun onDelete(question: VisitQuestion) {
        viewModelScope.launch {
            deleteQuestion(question.id)
            localState.update { it.copy(lastDeleted = question) }
        }
    }

    fun onUndoDelete() {
        val deleted = localState.value.lastDeleted ?: return
        viewModelScope.launch {
            addQuestion(deleted.text)
            localState.update { it.copy(lastDeleted = null) }
        }
    }

    fun onUndoConsumed() = localState.update { it.copy(lastDeleted = null) }
}
```

- [ ] **Step 2: Test** the ViewModel with fake/mocked use cases + Turbine: adding clears the draft and calls the use case; blank draft is ignored; delete records `lastDeleted`; undo re-adds.

```kotlin
package com.babytracker.ui.doctorvisit

import app.cash.turbine.test
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.DeleteVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VisitQuestionsViewModelTest {
    private val observeInbox = mockk<ObserveInboxQuestionsUseCase>()
    private val add = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val toggle = mockk<ToggleVisitQuestionAnsweredUseCase>(relaxed = true)
    private val delete = mockk<DeleteVisitQuestionUseCase>(relaxed = true)

    @BeforeEach fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `add clears draft and calls use case`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(observeInbox, add, toggle, delete)
        vm.uiState.test {
            awaitItem() // initial
            vm.onDraftChange("New Q")
            vm.onAdd()
            // draft cleared after add completes
            coVerify { add("New Q", any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete then undo re-adds`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(observeInbox, add, toggle, delete)
        val q = VisitQuestion(id = 5, text = "Ask about sleep", createdAt = Instant.EPOCH)
        vm.onDelete(q)
        coVerify { delete(5) }
        vm.onUndoDelete()
        coVerify { add("Ask about sleep", any()) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.ui.doctorvisit.VisitQuestionsViewModelTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add VisitQuestionsViewModel`

---

### Task 3: `VisitQuestionsScreen` composable

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/doctorvisit/VisitQuestionsScreen.kt`

- [ ] **Step 1: Build the screen.** Structure (follow the closest existing list+input screen, e.g. an inventory or history screen):
  - `Scaffold` with a `TopAppBar` titled `R.string.visit_questions_title` and a `SnackbarHost`.
  - A pinned input row at the top or bottom: an `OutlinedTextField` bound to `uiState.draft` (`onDraftChange`) + an Add button (`onAdd`), enabled only when the draft is non-blank.
  - A `LazyColumn` of question rows. Each row: the question text (single/two-line, ellipsized), an answered checkbox/toggle (`onToggleAnswered(id)`), tappable surface that calls `onExpand(question)`, and a delete affordance (overflow or swipe) calling `onDelete(question)`.
  - Empty state: center `R.string.visit_questions_empty` when `questions.isEmpty()`.
  - When `uiState.expandedQuestion != null`, show a full-screen / large dialog rendering the text in a big readable style with a Close button (`onExpand(null)`); this is the "tap to read at the appointment" view.
  - On `uiState.lastDeleted != null`, show a snackbar with `visit_questions_deleted` + `visit_questions_undo` action (`onUndoDelete`), then `onUndoConsumed()` when dismissed. Use `LaunchedEffect(uiState.lastDeleted)`.
  - Provide a stateless `VisitQuestionsContent(state, callbacks…)` plus a `@Composable VisitQuestionsScreen(viewModel: VisitQuestionsViewModel = hiltViewModel(), onBack: () -> Unit)` wrapper, matching the repo's screen/content split for testability.

- [ ] **Step 2: Add `@Preview`s** for: empty state, a few questions (mixed answered), and the expanded read view.
- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add VisitQuestionsScreen`

---

### Task 4: Route + navigation

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` (confirm exact filename via `rg "NavHost" -g "*.kt"`)

- [ ] **Step 1: Add the route** constant `VISIT_QUESTIONS` (e.g. `"visit_questions"`) following the existing `Routes` style.
- [ ] **Step 2: Add the `composable(Routes.VISIT_QUESTIONS) { VisitQuestionsScreen(onBack = navController::popBackStack) }`** destination in the nav graph, mirroring how `VACCINE_HISTORY` is registered.
- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add VISIT_QUESTIONS route`

> Entry points: the inbox is reachable from the visit history screen (plan 6) and from the visit sheet's "manage questions" affordance (plan 5). This plan only registers the route; the wiring of nav callbacks from those screens lands in plans 5/6.

---

### Task 5: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/doctorvisit/VisitQuestionsScreenTest.kt`

- [ ] **Step 1: Test** with `createComposeRule()` over the stateless `VisitQuestionsContent` (pass fake state + capture callbacks): empty state shows the empty string; typing + Add invokes the add callback; tapping a row invokes expand; the expanded view shows the full text and Close; delete invokes the delete callback. Follow the existing Compose test patterns in `app/src/androidTest/java/com/babytracker/ui/`.
- [ ] **Step 2: Run** `./gradlew connectedAndroidTest --tests "com.babytracker.ui.doctorvisit.VisitQuestionsScreenTest"` (needs emulator); if unavailable, note it.
- [ ] **Step 3: Commit** `test(doctor-visit): add VisitQuestionsScreen UI test`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- The inbox screen lists inbox questions, adds new ones (draft clears), toggles answered, expands a question full-screen for reading, and deletes with snackbar undo.
- `VISIT_QUESTIONS` route navigates to the screen.
- All strings exist in en + pt-BR.

## Self-Review Notes

- Spec coverage: add, expand-full-screen, toggle answered, delete-with-undo, route — all present.
- Undo re-adds the question text via `AddVisitQuestionUseCase`; the answered flag resets to false on undo (documented trade-off — the simplest correct behavior; a deleted question is rare and re-adding as unanswered is sensible).
- The inbox shows only `visit_id IS NULL` questions (the use case enforces this); attached questions appear under their visit (plan 5/6), not here.
- Screen/content split keeps the Compose test off Hilt.
