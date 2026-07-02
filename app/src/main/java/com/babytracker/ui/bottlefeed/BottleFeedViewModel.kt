package com.babytracker.ui.bottlefeed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.EditBottleFeedUseCase
import com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BottleFeedUiState(
    val feedType: FeedType = FeedType.BREAST_MILK,
    val volumeText: String = "",
    val timestamp: Instant = Instant.EPOCH,
    val activeBags: List<MilkBag> = emptyList(),
    val selectedBagId: Long? = null,
    val notes: String = "",
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val editingId: Long? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class BottleFeedViewModel @Inject constructor(
    private val logBottleFeed: LogBottleFeedUseCase,
    private val editBottleFeed: EditBottleFeedUseCase,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BottleFeedUiState(timestamp = now()))
    val uiState: StateFlow<BottleFeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            inventoryRepository.getActiveBags().collect { bags -> _uiState.update { it.copy(activeBags = bags) } }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit -> _uiState.update { it.copy(volumeUnit = unit) } }
        }
    }

    fun onTypeChange(type: FeedType) = _uiState.update {
        // Clearing the bag when switching away from breast milk keeps state consistent.
        it.copy(feedType = type, selectedBagId = if (type == FeedType.BREAST_MILK) it.selectedBagId else null)
    }

    fun onVolumeChange(text: String) = _uiState.update {
        it.copy(volumeText = text.filter { c -> c.isDigit() }, validationError = null)
    }

    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp) }

    fun onBagSelect(bagId: Long?) = _uiState.update { state ->
        // Selecting a bag prefills the volume with that bag's amount; deselecting leaves the volume untouched.
        val bagVolume = state.activeBags.firstOrNull { it.id == bagId }?.volumeMl
        state.copy(
            selectedBagId = bagId,
            volumeText = bagVolume?.toString() ?: state.volumeText,
            validationError = if (bagVolume != null) null else state.validationError,
        )
    }

    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text) }

    fun loadForEdit(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ) = _uiState.update {
        it.copy(
            editingId = id,
            isEditing = true,
            timestamp = timestamp,
            volumeText = volumeMl.toString(),
            feedType = type,
            selectedBagId = linkedMilkBagId,
            notes = notes.orEmpty(),
            saved = false,
            validationError = null,
        )
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val input = state.parseBottleFeedInput()
        if (input == null) {
            _uiState.update { it.copy(validationError = appContext.getString(R.string.error_volume_positive)) }
            return
        }
        // Set the guard synchronously so a second rapid tap is rejected before its coroutine launches.
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val editingId = state.editingId
                if (editingId == null) {
                    val linkedBag = state.activeBags.firstOrNull { it.id == state.selectedBagId }
                        .takeIf { state.feedType == FeedType.BREAST_MILK }
                    logBottleFeed(state.timestamp, input.volumeMl, state.feedType, linkedBag, input.notes)
                } else {
                    val linkedBagId = state.selectedBagId.takeIf { state.feedType == FeedType.BREAST_MILK }
                    editBottleFeed(editingId, state.timestamp, input.volumeMl, state.feedType, linkedBagId, input.notes)
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update {
                    it.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save))
                }
            }
        }
    }
}
