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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
    /** Whole-day countdown to [nextVisit], computed against the injected clock so the hero's
     *  "Today/Tomorrow" boundary agrees with [isUpcoming] instead of a recomposition-time wall clock. */
    val nextVisitInDays: Int? = null,
    val recentVisits: List<DoctorVisit> = emptyList(),
    val questions: List<VisitQuestion> = emptyList(),
    val openQuestionCount: Int = 0,
    val draft: String = "",
    /** The question most recently checked off, held so the screen can offer an undo snackbar. */
    val lastAnswered: VisitQuestion? = null,
    /** Set when an upstream flow throws, so the screen can offer a retry instead of a dead spinner. */
    val isError: Boolean = false,
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

    // Held outside the combined data flows so it survives the inbox re-emission that drops the
    // checked question from the unanswered list; the screen consumes and clears it.
    private val lastAnswered = MutableStateFlow<VisitQuestion?>(null)

    // Bumped by onRetry so flatMapLatest rebuilds the combined flow after an upstream failure;
    // a plain .catch would emit the error once and leave the flow terminated with no way back.
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DoctorVisitDashboardUiState> =
        retryTrigger.flatMapLatest {
            combine(
                observeVisits(),
                observeInbox(),
                draft,
                lastAnswered,
            ) { visits, inbox, draftText, answered ->
                val instant = now()
                val upcoming = visits.filter { it.isUpcoming(instant) }.minByOrNull { it.date }
                val zone = ZoneId.systemDefault()
                val today = instant.atZone(zone).toLocalDate()
                val daysUntil = upcoming?.let {
                    ChronoUnit.DAYS.between(today, it.date.atZone(zone).toLocalDate()).toInt()
                }
                val recent = visits
                    .filterNot { it.isUpcoming(instant) }
                    .sortedByDescending { it.date }
                    .take(RECENT_LIMIT)
                val unanswered = inbox.filterNot { it.answered }
                DoctorVisitDashboardUiState(
                    isLoading = false,
                    nextVisit = upcoming,
                    nextVisitInDays = daysUntil,
                    recentVisits = recent,
                    questions = unanswered.take(QUESTION_PREVIEW_LIMIT),
                    openQuestionCount = unanswered.size,
                    draft = draftText,
                    lastAnswered = answered,
                )
            }.catch {
                emit(DoctorVisitDashboardUiState(isLoading = false, isError = true))
            }
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
        // Capture the question before the inbox re-emits without it, so undo has something to flip
        // back. Rows are only tappable from the visible preview, so it is always present here.
        lastAnswered.value = uiState.value.questions.firstOrNull { it.id == id }
        viewModelScope.launch { toggleAnswered(id) }
    }

    fun onUndoAnswered() {
        val question = lastAnswered.value ?: return
        lastAnswered.value = null
        viewModelScope.launch { toggleAnswered(question.id) }
    }

    fun onUndoAnsweredConsumed() {
        lastAnswered.value = null
    }

    fun onRetry() {
        retryTrigger.value++
    }

    private companion object {
        const val RECENT_LIMIT = 3
        const val QUESTION_PREVIEW_LIMIT = 4
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
