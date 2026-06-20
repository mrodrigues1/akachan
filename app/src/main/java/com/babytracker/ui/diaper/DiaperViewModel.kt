package com.babytracker.ui.diaper

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Hard ceiling on note length so a runaway paste can't reach the DB or blow out the sheet. */
const val DIAPER_NOTES_MAX = 280

data class DiaperUiState(
    val type: DiaperType = DiaperType.WET,
    val timestamp: Instant = Instant.EPOCH,
    val notes: String = "",
    val isSaving: Boolean = false,
    // Field-correct errors: timeError sits under the time row, saveError above the action.
    val timeError: String? = null,
    val saveError: String? = null,
    val editingId: Long? = null,
    val editingCreatedAt: Instant? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class DiaperViewModel @Inject constructor(
    private val logDiaperChange: LogDiaperChangeUseCase,
    private val editDiaperChange: EditDiaperChangeUseCase,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaperUiState(timestamp = now()))
    val uiState: StateFlow<DiaperUiState> = _uiState.asStateFlow()

    fun onTypeChange(type: DiaperType) = _uiState.update { it.copy(type = type) }
    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp, timeError = null) }
    fun onNotesChange(text: String) =
        _uiState.update { it.copy(notes = text.take(DIAPER_NOTES_MAX), saveError = null) }

    fun loadForEdit(id: Long, timestamp: Instant, type: DiaperType, notes: String?, createdAt: Instant) =
        _uiState.update {
            it.copy(
                editingId = id,
                editingCreatedAt = createdAt,
                isEditing = true,
                timestamp = timestamp,
                type = type,
                notes = notes.orEmpty().take(DIAPER_NOTES_MAX),
                saved = false,
                timeError = null,
                saveError = null,
            )
        }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        // Validate the time locally so the error lands on the time field, localized, before any write.
        if (state.timestamp.isAfter(now())) {
            _uiState.update { it.copy(timeError = appContext.getString(R.string.diaper_time_future_error)) }
            return
        }
        // Set the guard synchronously so a second rapid tap is rejected before its coroutine launches.
        _uiState.update { it.copy(isSaving = true, timeError = null, saveError = null) }
        viewModelScope.launch {
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    logDiaperChange(state.type, state.timestamp, state.notes)
                } else {
                    editDiaperChange(
                        DiaperChange(
                            id = editingId,
                            timestamp = state.timestamp,
                            type = state.type,
                            notes = state.notes,
                            createdAt = state.editingCreatedAt ?: now(),
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                // Never surface a raw exception message: map every failure to one calm, localized line.
                _uiState.update {
                    it.copy(isSaving = false, saveError = appContext.getString(R.string.diaper_save_error))
                }
            }
        }
    }
}
