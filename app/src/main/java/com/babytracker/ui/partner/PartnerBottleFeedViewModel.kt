package com.babytracker.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.usecase.EditPartnerFeedUseCase
import com.babytracker.sharing.usecase.LogPartnerFeedUseCase
import com.babytracker.ui.bottlefeed.BOTTLE_FEED_VOLUME_ERROR
import com.babytracker.ui.bottlefeed.BottleFeedUiState
import com.babytracker.ui.bottlefeed.parseBottleFeedInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class PartnerBottleFeedViewModel @Inject constructor(
    private val logPartnerFeed: LogPartnerFeedUseCase,
    private val editPartnerFeed: EditPartnerFeedUseCase,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BottleFeedUiState(timestamp = now()))
    val uiState: StateFlow<BottleFeedUiState> = _uiState.asStateFlow()

    private var availableBags: List<MilkBagSnapshot> = emptyList()
    private var editingEntry: BottleFeedSnapshot? = null

    init {
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    fun onBagsAvailable(bags: List<MilkBagSnapshot>) {
        availableBags = bags
        _uiState.update { it.copy(activeBags = bags.map { bag -> bag.toPickerBag() }) }
    }

    fun startLogging() {
        editingEntry = null
        _uiState.update {
            BottleFeedUiState(
                timestamp = now(),
                activeBags = it.activeBags,
                volumeUnit = it.volumeUnit,
            )
        }
    }

    fun startEditing(entry: BottleFeedSnapshot) {
        check(entry.author == FeedAuthor.PARTNER.name) {
            "Only partner entries are editable in partner mode"
        }
        check(entry.clientId.isNotEmpty()) { "Entry has no clientId" }
        editingEntry = entry
        _uiState.update {
            it.copy(
                feedType = FeedType.entries.firstOrNull { type -> type.name == entry.type } ?: FeedType.FORMULA,
                volumeText = entry.volumeMl.toString(),
                timestamp = Instant.ofEpochMilli(entry.timestamp),
                selectedBagId = null,
                notes = entry.notes.orEmpty(),
                isEditing = true,
                validationError = null,
                saved = false,
            )
        }
    }

    fun onTypeChange(type: FeedType) = _uiState.update {
        it.copy(feedType = type, selectedBagId = if (type == FeedType.BREAST_MILK) it.selectedBagId else null)
    }

    fun onVolumeChange(text: String) = _uiState.update {
        it.copy(volumeText = text.filter { c -> c.isDigit() }, validationError = null)
    }

    fun onTimeChange(timestamp: Instant) = _uiState.update { it.copy(timestamp = timestamp) }

    fun onBagSelect(bagId: Long?) = _uiState.update { state ->
        val bagVolume = state.activeBags.firstOrNull { it.id == bagId }?.volumeMl
        state.copy(
            selectedBagId = bagId,
            volumeText = bagVolume?.toString() ?: state.volumeText,
            validationError = if (bagVolume != null) null else state.validationError,
        )
    }

    fun onNotesChange(text: String) = _uiState.update { it.copy(notes = text) }

    fun onConfirm() {
        val state = _uiState.value
        if (state.isSaving) return
        val input = state.parseBottleFeedInput()
        if (input == null) {
            _uiState.update { it.copy(validationError = BOTTLE_FEED_VOLUME_ERROR) }
            return
        }

        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            runCatching {
                val editing = editingEntry
                if (editing == null) {
                    logPartnerFeed(
                        timestamp = state.timestamp,
                        volumeMl = input.volumeMl,
                        type = state.feedType,
                        selectedBag = availableBags.firstOrNull { it.id == state.selectedBagId }
                            .takeIf { state.feedType == FeedType.BREAST_MILK },
                        notes = input.notes,
                    )
                } else {
                    editPartnerFeed(
                        entry = editing,
                        timestamp = state.timestamp,
                        volumeMl = input.volumeMl,
                        type = state.feedType,
                        notes = input.notes,
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, validationError = error.message ?: "Could not save")
                }
            }
        }
    }
}

private fun MilkBagSnapshot.toPickerBag(): MilkBag = MilkBag(
    id = id,
    collectionDate = Instant.ofEpochMilli(collectionDateMs),
    volumeMl = volumeMl,
    notes = notes,
    createdAt = Instant.EPOCH,
)
