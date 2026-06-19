package com.babytracker.ui.vaccine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class VaccineUiState(
    val name: String = "",
    val doseLabel: String = "",
    val status: VaccineStatus = VaccineStatus.ADMINISTERED,
    val date: Instant = Instant.EPOCH,
    val notes: String = "",
    val suggestions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val editingId: Long? = null,
    val editingCreatedAt: Instant? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class VaccineViewModel @Inject constructor(
    private val addVaccine: AddVaccineRecordUseCase,
    private val editVaccine: EditVaccineRecordUseCase,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaccineUiState(
            date = now(),
            suggestions = appContext.resources.getStringArray(R.array.vaccine_suggestions).toList(),
        ),
    )
    val uiState: StateFlow<VaccineUiState> = _uiState.asStateFlow()

    fun onNameChange(text: String) = _uiState.update { it.copy(name = text, validationError = null) }
    fun onDoseChange(text: String) = _uiState.update { it.copy(doseLabel = text) }
    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text) }
    fun onDateChange(date: Instant) = _uiState.update { it.copy(date = date, validationError = null) }

    fun onModeChange(status: VaccineStatus) = _uiState.update {
        // Scheduled vaccines must be in the future (AddVaccineRecordUseCase enforces it), so when
        // switching to "schedule" and the current date is not already future, default to tomorrow.
        val nextDate = if (status == VaccineStatus.SCHEDULED && !it.date.isAfter(now())) {
            now().plus(1, ChronoUnit.DAYS)
        } else {
            it.date
        }
        it.copy(status = status, date = nextDate, validationError = null)
    }

    fun loadForEdit(record: VaccineRecord) = _uiState.update {
        it.copy(
            editingId = record.id,
            editingCreatedAt = record.createdAt,
            isEditing = true,
            name = record.name,
            doseLabel = record.doseLabel.orEmpty(),
            status = record.status,
            date = record.administeredDate ?: record.scheduledDate ?: now(),
            notes = record.notes.orEmpty(),
            saved = false,
            validationError = null,
        )
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        // Set the guard synchronously so a second rapid tap is rejected before its coroutine launches.
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    addVaccine(state.name, state.doseLabel, state.status, state.date, state.notes)
                } else {
                    editVaccine(
                        VaccineRecord(
                            id = editingId,
                            name = state.name,
                            doseLabel = state.doseLabel,
                            status = state.status,
                            scheduledDate = if (state.status == VaccineStatus.SCHEDULED) state.date else null,
                            administeredDate = if (state.status == VaccineStatus.ADMINISTERED) state.date else null,
                            notes = state.notes,
                            createdAt = state.editingCreatedAt ?: now(),
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        validationError = error.message ?: appContext.getString(R.string.vaccine_save_error),
                    )
                }
            }
        }
    }
}
