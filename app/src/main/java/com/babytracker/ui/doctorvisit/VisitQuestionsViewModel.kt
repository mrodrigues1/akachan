package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import com.babytracker.domain.usecase.doctorvisit.UpdateVisitQuestionAnswerUseCase
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
    val answerDraft: String = "",
    val lastDeleted: VisitQuestion? = null,
)

@HiltViewModel
class VisitQuestionsViewModel @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val addQuestion: AddVisitQuestionUseCase,
    private val toggleAnswered: ToggleVisitQuestionAnsweredUseCase,
    private val updateAnswer: UpdateVisitQuestionAnswerUseCase,
) : ViewModel() {

    private val localState = MutableStateFlow(VisitQuestionsUiState())

    val uiState: StateFlow<VisitQuestionsUiState> =
        combine(repository.observeInboxQuestions(), localState) { questions, local ->
            local.copy(questions = questions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitQuestionsUiState())

    fun onDraftChange(text: String) = localState.update { it.copy(draft = text) }

    fun onAdd() {
        val text = localState.value.draft.trim()
        if (text.isEmpty()) return
        // Clear synchronously so a rapid double-tap / double-Done can't enqueue the same question
        // twice before the suspending write completes (mirrors the dashboard capture row).
        localState.update { it.copy(draft = "") }
        viewModelScope.launch { addQuestion(text) }
    }

    fun onToggleAnswered(id: Long) {
        viewModelScope.launch { toggleAnswered(id) }
    }

    fun onExpand(question: VisitQuestion?) {
        // Collapsing (or switching rows) persists the outgoing answer first, so an edit isn't lost
        // when the user taps to collapse without blurring the field.
        persistPendingAnswer()
        localState.update { it.copy(expandedQuestion = question, answerDraft = question?.answer.orEmpty()) }
    }

    fun onAnswerDraftChange(text: String) = localState.update { it.copy(answerDraft = text) }

    /** Persist the expanded question's answer (blur / IME Done). A non-blank answer auto-checks it. */
    fun onSaveAnswer() = persistPendingAnswer()

    private fun persistPendingAnswer() {
        val question = localState.value.expandedQuestion ?: return
        val answer = localState.value.answerDraft
        if (answer == question.answer.orEmpty()) return
        viewModelScope.launch { updateAnswer(question.id, answer) }
    }

    fun onDelete(question: VisitQuestion) {
        viewModelScope.launch {
            repository.deleteQuestionById(question.id)
            localState.update { it.copy(lastDeleted = question) }
        }
    }

    fun onUndoDelete() {
        val deleted = localState.value.lastDeleted ?: return
        viewModelScope.launch {
            // Reinsert the whole question (id reset for autogen) so the answer, answered flag and
            // original createdAt survive an undo — re-adding just the text would discard them.
            repository.insertQuestion(deleted.copy(id = 0))
            localState.update { it.copy(lastDeleted = null) }
        }
    }

    fun onUndoConsumed() = localState.update { it.copy(lastDeleted = null) }
}
