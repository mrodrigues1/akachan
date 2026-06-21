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
