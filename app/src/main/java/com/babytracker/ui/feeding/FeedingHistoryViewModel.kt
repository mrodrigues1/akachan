package com.babytracker.ui.feeding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedingDayGroup
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.DeleteBottleFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveFeedingHistoryUseCase
import com.babytracker.domain.usecase.feeding.groupFeedEntriesByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class FeedingHistoryUiState(
    val days: List<FeedingDayGroup> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deletedBottleId: Long? = null,
    val deleteError: String? = null,
) {
    val entryCount: Int get() = days.sumOf { it.entries.size }
}

private const val HISTORY_PAGE_SIZE = 50

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedingHistoryViewModel @Inject constructor(
    observeFeedingHistory: ObserveFeedingHistoryUseCase,
    private val deleteBottleFeed: DeleteBottleFeedUseCase,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedingHistoryUiState())
    val uiState: StateFlow<FeedingHistoryUiState> = _uiState.asStateFlow()

    private val historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    init {
        viewModelScope.launch {
            combine(
                // Bounded window instead of the two full tables: the use case queries one row past
                // the limit per source so hasMore never needs a count query.
                historyLimit.flatMapLatest { limit -> observeFeedingHistory(limit) },
                settingsRepository.getVolumeUnit(),
            ) { window, unit ->
                FeedingHistoryUiState(
                    days = groupFeedEntriesByDay(window.entries, zone),
                    hasMoreHistory = window.hasMore,
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

    fun onLoadMoreHistory() {
        val state = _uiState.value
        // Ignore repeats until the previously requested window has emitted: the load-more
        // sentinel can leave and re-enter composition before Room delivers the bigger page.
        if (!state.hasMoreHistory || state.entryCount < historyLimit.value) return
        historyLimit.value += HISTORY_PAGE_SIZE
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
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteError = appContext.getString(R.string.error_feed_delete),
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
