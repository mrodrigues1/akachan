package com.babytracker.ui.pumping

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.pumping.DeletePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.GetPumpingHistoryUseCase
import com.babytracker.domain.usecase.pumping.PumpingEditError
import com.babytracker.domain.usecase.pumping.UpdatePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.validatePumpingEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EditPumpingSheetState(
    val original: PumpingSession,
    val editedStart: Instant,
    val editedEnd: Instant?,
    val editedBreast: PumpingBreast,
    val editedVolumeMl: String,
    val editedNotes: String,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val deleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val isDirty: Boolean
        get() = editedStart != original.startTime ||
            editedEnd != original.endTime ||
            editedBreast != original.breast ||
            editedVolumeMl != (original.volumeMl?.toString().orEmpty()) ||
            editedNotes != (original.notes.orEmpty())

    val canSave: Boolean
        get() = isDirty && validationError == null && !isSaving && !isDeleting
}

data class PumpingHistoryUiState(
    val sessions: List<PumpingSession> = emptyList(),
    val editSheet: EditPumpingSheetState? = null,
    val pendingDeleteSession: PumpingSession? = null,
    val error: String? = null,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
class PumpingHistoryViewModel @Inject constructor(
    getHistory: GetPumpingHistoryUseCase,
    private val updateSession: UpdatePumpingSessionUseCase,
    private val deleteSession: DeletePumpingSessionUseCase,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpingHistoryUiState())
    val uiState: StateFlow<PumpingHistoryUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<PumpingSession>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(sessions, settingsRepository.getVolumeUnit()) { list, unit -> list to unit }
                .collect { (list, unit) ->
                    _uiState.value = _uiState.value.copy(sessions = list, volumeUnit = unit)
                }
        }
    }

    fun onEditClicked(session: PumpingSession) {
        _uiState.value = _uiState.value.copy(
            editSheet = EditPumpingSheetState(
                original = session,
                editedStart = session.startTime,
                editedEnd = session.endTime,
                editedBreast = session.breast,
                editedVolumeMl = session.volumeMl?.toString().orEmpty(),
                editedNotes = session.notes.orEmpty(),
            ),
        )
    }

    fun onEditFieldChange(transform: (EditPumpingSheetState) -> EditPumpingSheetState) {
        val current = _uiState.value.editSheet ?: return
        val updated = transform(current)
        val volume = updated.editedVolumeMl.toIntOrNull()
        val error = validatePumpingEdit(
            startTime = updated.editedStart,
            endTime = updated.editedEnd,
            volumeMl = volume,
            pausedDurationMs = updated.original.pausedDurationMs,
            now = now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = updated.copy(validationError = error?.let { appContext.getString(it.messageRes()) }),
        )
    }

    fun onEditDismiss() {
        _uiState.value = _uiState.value.copy(editSheet = null)
    }

    fun onEditSave() {
        val sheet = _uiState.value.editSheet ?: return
        if (!sheet.canSave) return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                updateSession(
                    original = sheet.original,
                    startTime = sheet.editedStart,
                    endTime = sheet.editedEnd,
                    breast = sheet.editedBreast,
                    volumeMl = sheet.editedVolumeMl.toIntOrNull(),
                    notes = sheet.editedNotes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(editSheet = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    editSheet = sheet.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save)),
                )
            }
        }
    }

    fun onDeleteRequested() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(deleteConfirm = true))
    }

    fun onDeleteCancelled() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(deleteConfirm = false))
    }

    fun onDeleteConfirmed() {
        val sheet = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = sheet.copy(isDeleting = true))
        viewModelScope.launch {
            runCatching { deleteSession(sheet.original) }
                .onSuccess { _uiState.value = _uiState.value.copy(editSheet = null) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        editSheet = sheet.copy(isDeleting = false, deleteConfirm = false),
                        error = appContext.getString(R.string.error_pumping_delete),
                    )
                }
        }
    }

    /** Row-level delete (from the 3-dot menu), gated by a confirmation dialog — mirrors breastfeeding history. */
    fun onPendingDeleteSessionChanged(session: PumpingSession?) {
        _uiState.value = _uiState.value.copy(pendingDeleteSession = session)
    }

    fun onConfirmDeleteSession() {
        val session = _uiState.value.pendingDeleteSession ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteSession = null)
        viewModelScope.launch {
            runCatching { deleteSession(session) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = appContext.getString(R.string.error_pumping_delete),
                    )
                }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

@StringRes
internal fun PumpingEditError.messageRes(): Int = when (this) {
    PumpingEditError.START_IN_FUTURE -> R.string.error_start_future
    PumpingEditError.END_BEFORE_START -> R.string.error_end_after_start
    PumpingEditError.END_IN_FUTURE -> R.string.error_end_future
    PumpingEditError.PAUSE_EXCEEDS_SESSION -> R.string.error_paused_exceeds
    PumpingEditError.VOLUME_NOT_POSITIVE -> R.string.error_volume_greater_than_zero
}
