package com.babytracker.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.usecase.DeletePartnerFeedUseCase
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.sharing.usecase.PartnerFeedHistoryException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerFeedHistoryUiState(
    val entries: List<BottleFeedSnapshot> = emptyList(),
    val milkBags: List<MilkBagSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
class PartnerFeedHistoryViewModel @Inject constructor(
    private val fetchPartnerData: FetchPartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val deletePartnerFeed: DeletePartnerFeedUseCase,
    private val widgetUpdater: WidgetUpdater,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerFeedHistoryUiState())
    val uiState: StateFlow<PartnerFeedHistoryUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null
    private var lastPendingOpIds = emptySet<String>()

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    fun refresh() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            try {
                val snapshot = fetchPartnerData()
                _uiState.update {
                    it.copy(
                        milkBags = snapshot.milkBags,
                        isLoading = false,
                    )
                }
                observePartnerFeedHistory(snapshot.bottleFeeds).collect { merged ->
                    val pendingNow = merged.pendingOpIds
                    _uiState.update {
                        it.copy(entries = merged.entries, isLoading = false)
                    }
                    if (lastPendingOpIds.any { it !in pendingNow }) {
                        lastPendingOpIds = pendingNow
                        refresh()
                        return@collect
                    }
                    lastPendingOpIds = pendingNow
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: PartnerAccessRevokedException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accessRevoked = true,
                        error = error.message,
                    )
                }
                widgetUpdater.updateAll()
            } catch (error: PartnerFeedHistoryException) {
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: PartnerDataFetchException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message,
                    )
                }
            } catch (error: IllegalStateException) {
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun onDelete(entry: BottleFeedSnapshot) {
        if (!isEditable(entry)) return
        viewModelScope.launch {
            try {
                deletePartnerFeed(entry)
            } catch (error: PartnerAccessRevokedException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accessRevoked = true,
                        error = error.message,
                    )
                }
                widgetUpdater.updateAll()
            } catch (error: PartnerDataFetchException) {
                _uiState.update { it.copy(error = error.message) }
            } catch (error: IllegalStateException) {
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun isEditable(entry: BottleFeedSnapshot): Boolean =
        entry.author == FeedAuthor.PARTNER.name && entry.clientId.isNotEmpty()
}
