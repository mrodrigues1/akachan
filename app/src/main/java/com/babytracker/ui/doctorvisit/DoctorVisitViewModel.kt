package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.AttachSnapshotToVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.EditDoctorVisitUseCase
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import com.babytracker.ui.settings.ShareArtifact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DoctorVisitUiState(
    val editingId: Long? = null,
    val date: Instant = Instant.now(),
    val providerName: String = "",
    val notes: String = "",
    val questionDraft: String = "",
    val inboxQuestions: List<VisitQuestion> = emptyList(),
    // Questions already attached to the visit being edited (empty on the add path).
    val attachedQuestions: List<VisitQuestion> = emptyList(),
    val selectedQuestionIds: Set<Long> = emptySet(),
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Instant? = null,
    // Original row timestamp, preserved across an edit so updateVisit() never rewrites created_at.
    // Unused on the add path (the use case stamps a fresh createdAt there).
    val createdAt: Instant = Instant.now(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val pendingSnapshotShare: ShareArtifact? = null,
    val snapshotError: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoctorVisitViewModel @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val addQuestion: AddVisitQuestionUseCase,
    private val addVisit: AddDoctorVisitUseCase,
    private val editVisit: EditDoctorVisitUseCase,
    private val attachSnapshot: AttachSnapshotToVisitUseCase,
    private val generatePdfReport: GeneratePdfReportUseCase,
    private val fileWriter: BackupFileWriter,
) : ViewModel() {

    private val local = MutableStateFlow(DoctorVisitUiState())

    // Live attached-questions stream for the visit being edited (empty list on the add path).
    private val attachedFlow =
        local.map { it.editingId }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeQuestionsForVisit(id)
        }

    val uiState: StateFlow<DoctorVisitUiState> =
        combine(repository.observeInboxQuestions(), attachedFlow, local) { inbox, attached, state ->
            state.copy(inboxQuestions = inbox, attachedQuestions = attached)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoctorVisitUiState())

    fun onDateChange(date: Instant) = local.update { it.copy(date = date) }
    fun onProviderChange(v: String) = local.update { it.copy(providerName = v) }
    fun onNotesChange(v: String) = local.update { it.copy(notes = v) }
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

    fun onToggleQuestion(id: Long) = local.update {
        val selected = it.selectedQuestionIds
        it.copy(selectedQuestionIds = if (id in selected) selected - id else selected + id)
    }

    fun startEdit(visit: DoctorVisit) {
        local.update {
            it.copy(
                editingId = visit.id,
                date = visit.date,
                providerName = visit.providerName.orEmpty(),
                notes = visit.notes.orEmpty(),
                snapshotLabel = visit.snapshotLabel,
                snapshotCreatedAt = visit.snapshotCreatedAt,
                createdAt = visit.createdAt, // preserve the original timestamp for the update
            )
        }
        // Seed the selection from the questions currently attached to this visit (one-shot),
        // so the edit form starts with all existing attachments selected; the user can deselect.
        viewModelScope.launch {
            val attachedIds = repository.observeQuestionsForVisit(visit.id).first().map { it.id }.toSet()
            local.update { it.copy(selectedQuestionIds = attachedIds) }
        }
    }

    /** Entry point for the edit route: fetch the visit by id, then seed the edit form. */
    fun loadForEdit(visitId: Long) {
        viewModelScope.launch {
            val visit = repository.getVisitById(visitId) ?: return@launch
            startEdit(visit)
        }
    }

    fun onSave() {
        val s = local.value
        if (s.isSaving) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val editingId = s.editingId
            if (editingId == null) {
                addVisit(s.date, s.providerName, s.notes, s.selectedQuestionIds.toList())
            } else {
                editVisit(
                    DoctorVisit(
                        id = editingId,
                        date = s.date,
                        providerName = s.providerName,
                        notes = s.notes,
                        snapshotLabel = s.snapshotLabel,
                        snapshotCreatedAt = s.snapshotCreatedAt,
                        createdAt = s.createdAt, // preserved original — never Instant.now()
                    ),
                    s.selectedQuestionIds.toList(),
                )
            }
            local.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun onSavedConsumed() = local.update { it.copy(saved = false) }

    /** Attach a lightweight snapshot reference (label + timestamp) to the saved visit being edited. */
    fun onAttachSnapshot(label: String) {
        val id = local.value.editingId ?: return
        viewModelScope.launch {
            val now = Instant.now()
            attachSnapshot(id, label, now)
            local.update { it.copy(snapshotLabel = label, snapshotCreatedAt = now) }
        }
    }

    /** Re-generate a fresh export on demand and surface it as a shareable artifact. */
    fun onViewSnapshot() {
        viewModelScope.launch {
            runCatching {
                val bytes = generatePdfReport(DateRange.allTime())
                fileWriter.writeCacheBytes("visit-snapshot-${Instant.now().toEpochMilli()}.pdf", bytes)
            }.onSuccess { uri ->
                local.update { it.copy(pendingSnapshotShare = ShareArtifact(uri, "application/pdf")) }
            }.onFailure {
                local.update { it.copy(snapshotError = true) }
            }
        }
    }

    fun onSnapshotShareConsumed() = local.update { it.copy(pendingSnapshotShare = null) }
    fun onSnapshotErrorConsumed() = local.update { it.copy(snapshotError = false) }
}
