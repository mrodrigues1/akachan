package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.model.isUpcoming
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitsUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Drives the Doctor Visits landing screen: the next appointment, the running list of questions to
 * ask, and a short strip of recent visits. Composes the existing observe/mutate use cases rather
 * than introducing new persistence; the redesign is presentation-only.
 *
 * The [draft] is held as its own flow so inline question capture stays responsive while the visit
 * and inbox flows recombine in the background.
 */
data class DoctorVisitDashboardUiState(
    val isLoading: Boolean = true,
    val nextVisit: DoctorVisit? = null,
    val recentVisits: List<DoctorVisit> = emptyList(),
    val questions: List<VisitQuestion> = emptyList(),
    val openQuestionCount: Int = 0,
    val draft: String = "",
) {
    /** True on a clean install: nothing scheduled, nothing recorded, nothing being jotted yet. */
    val isFirstRun: Boolean
        get() = nextVisit == null && recentVisits.isEmpty() && openQuestionCount == 0 && draft.isBlank()
}

@HiltViewModel
class DoctorVisitDashboardViewModel @Inject constructor(
    observeVisits: ObserveDoctorVisitsUseCase,
    observeInbox: ObserveInboxQuestionsUseCase,
    private val addQuestion: AddVisitQuestionUseCase,
    private val toggleAnswered: ToggleVisitQuestionAnsweredUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val draft = MutableStateFlow("")

    val uiState: StateFlow<DoctorVisitDashboardUiState> =
        combine(
            observeVisits(),
            observeInbox(),
            draft,
        ) { visits, inbox, draftText ->
            val instant = now()
            val upcoming = visits.filter { it.isUpcoming(instant) }.minByOrNull { it.date }
            val recent = visits
                .filterNot { it.isUpcoming(instant) }
                .sortedByDescending { it.date }
                .take(RECENT_LIMIT)
            val unanswered = inbox.filterNot { it.answered }
            DoctorVisitDashboardUiState(
                isLoading = false,
                nextVisit = upcoming,
                recentVisits = recent,
                questions = unanswered.take(QUESTION_PREVIEW_LIMIT),
                openQuestionCount = unanswered.size,
                draft = draftText,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DoctorVisitDashboardUiState(),
        )

    fun onDraftChange(text: String) {
        draft.value = text
    }

    fun onAddQuestion() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        // Clear synchronously so the field empties the instant the parent taps add, and a quick
        // double-tap can't enqueue the same question twice before the write completes.
        draft.value = ""
        viewModelScope.launch { addQuestion(text) }
    }

    fun onToggleAnswered(id: Long) {
        viewModelScope.launch { toggleAnswered(id) }
    }

    private companion object {
        const val RECENT_LIMIT = 3
        const val QUESTION_PREVIEW_LIMIT = 4
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
