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
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val deletePartnerFeed: DeletePartnerFeedUseCase,
    private val widgetUpdater: WidgetUpdater,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerFeedHistoryUiState())
    val uiState: StateFlow<PartnerFeedHistoryUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    /** Restarts the live collection (used by the screen's retry button). */
    fun refresh() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            // ponytail: `snapshots` is subscribed twice (here + inside the feed-history use case), so the
            // dashboard read costs two listeners on one doc. Fine for a single screen; share if it matters.
            val snapshots = observePartnerData()
            combine(
                snapshots,
                observePartnerFeedHistory(snapshots.map { it.bottleFeeds }),
            ) { snapshot, history -> snapshot.milkBags to history.entries }
                .collectPartnerHistory(
                    widgetUpdater = widgetUpdater,
                    onItem = { (milkBags, entries) ->
                        _uiState.update { it.copy(milkBags = milkBags, entries = entries, isLoading = false) }
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
}
