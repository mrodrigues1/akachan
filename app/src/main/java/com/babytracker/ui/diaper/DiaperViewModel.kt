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

data class DiaperUiState(
    val type: DiaperType = DiaperType.WET,
    val timestamp: Instant = Instant.EPOCH,
    val notes: String = "",
    val isSaving: Boolean = false,
    val validationError: String? = null,
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
    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp) }
    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text, validationError = null) }

    fun loadForEdit(id: Long, timestamp: Instant, type: DiaperType, notes: String?, createdAt: Instant) =
        _uiState.update {
            it.copy(
                editingId = id,
                editingCreatedAt = createdAt,
                isEditing = true,
                timestamp = timestamp,
                type = type,
                notes = notes.orEmpty(),
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
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        validationError = error.message ?: appContext.getString(R.string.diaper_save_error),
                    )
                }
            }
        }
    }
}
