package com.babytracker.ui.feeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedingDayGroup
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.DeleteBottleFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveFeedingHistoryUseCase
import com.babytracker.domain.usecase.feeding.groupFeedEntriesByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class FeedingHistoryUiState(
    val days: List<FeedingDayGroup> = emptyList(),
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deletedBottleId: Long? = null,
    val deleteError: String? = null,
)

@HiltViewModel
class FeedingHistoryViewModel @Inject constructor(
    observeFeedingHistory: ObserveFeedingHistoryUseCase,
    private val deleteBottleFeed: DeleteBottleFeedUseCase,
    settingsRepository: SettingsRepository,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedingHistoryUiState())
    val uiState: StateFlow<FeedingHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeFeedingHistory(),
                settingsRepository.getVolumeUnit(),
            ) { entries, unit ->
                FeedingHistoryUiState(
                    days = groupFeedEntriesByDay(entries, zone),
                    volumeUnit = unit,
                    isLoading = false,
                )
            }.collect { next ->
                _uiState.update { current ->
                    next.copy(
                        isDeleting = current.isDeleting,
                        deletedBottleId = current.deletedBottleId,
                        deleteError = current.deleteError,
                    )
                }
            }
        }
    }

    fun onDeleteBottle(feed: BottleFeed) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deleteError = null, deletedBottleId = null) }
            runCatching { deleteBottleFeed(feed) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isDeleting = false, deletedBottleId = feed.id)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteError = error.message ?: "Could not delete feed",
                        )
                    }
                }
        }
    }

    fun onDeleteResultConsumed() {
        _uiState.update { it.copy(deletedBottleId = null) }
    }

    fun onDeleteErrorShown() {
        _uiState.update { it.copy(deleteError = null) }
    }
}
