package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
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
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerFeedHistoryUiState())
    val uiState: StateFlow<PartnerFeedHistoryUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null
    private var lastPendingOpIds = emptySet<String>()
    // replay = 1 so a trigger emitted before the debounce collector subscribes is not lost.
    private val refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        refresh()
        viewModelScope.launch {
            // Debounce: rapid emissions while ops are consumed restart the delay, so a burst of
            // consumed ops costs one snapshot refetch + listener restart instead of one each.
            refreshTrigger.collectLatest {
                delay(REFRESH_DEBOUNCE_MS)
                refresh()
            }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    fun refresh() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            refreshPartnerHistory(
                fetchPartnerData = fetchPartnerData,
                widgetUpdater = widgetUpdater,
                observe = { observePartnerFeedHistory(it.bottleFeeds) },
                onLoadingStart = {
                    _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
                },
                onSnapshotLoaded = { snapshot ->
                    _uiState.update { it.copy(milkBags = snapshot.milkBags, isLoading = false) }
                },
                onMerged = { merged ->
                    _uiState.update { it.copy(entries = merged.entries, isLoading = false) }
                    if (hasConsumedPendingOps(lastPendingOpIds, merged.pendingOpIds)) {
                        refreshTrigger.tryEmit(Unit)
                    }
                    lastPendingOpIds = merged.pendingOpIds
                },
                onAccessRevoked = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            accessRevoked = true,
                            error = appContext.getString(R.string.error_partner_access_revoked),
                        )
                    }
                },
                onLoadFailed = {
                    _uiState.update {
                        it.copy(isLoading = false, error = appContext.getString(R.string.error_partner_load_failed))
                    }
                },
            )
        }
    }

    fun onDelete(entry: BottleFeedSnapshot) {
        if (!isEditable(entry)) return
        viewModelScope.launch {
            try {
                deletePartnerFeed(entry)
            } catch (_: PartnerAccessRevokedException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accessRevoked = true,
                        error = appContext.getString(R.string.error_partner_access_revoked),
                    )
                }
                widgetUpdater.updateAll()
            } catch (_: PartnerDataFetchException) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_partner_delete_failed)) }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_partner_delete_failed)) }
            }
        }
    }

    fun isEditable(entry: BottleFeedSnapshot): Boolean =
        entry.author == FeedAuthor.PARTNER.name && entry.clientId.isNotEmpty()

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
