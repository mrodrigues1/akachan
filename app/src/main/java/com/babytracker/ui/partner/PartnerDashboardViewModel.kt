package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerDashboardUiState(
    val snapshot: ShareSnapshot? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
class PartnerDashboardViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerDashboardUiState(isLoading = true))
    val uiState: StateFlow<PartnerDashboardUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        start()
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    private fun start() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // ponytail: observePartnerData() is subscribed twice (here + inside the feed-history use
            // case) -> two listeners on one doc. Fine for one screen; share if it ever matters.
            val snapshots = observePartnerData()
            combine(
                snapshots,
                observePartnerFeedHistory(snapshots.map { it.bottleFeeds }),
            ) { snapshot, feedHistory -> snapshot.copy(bottleFeeds = feedHistory.entries) }
                .catch { error -> handleError(error) }
                .collect { snapshot ->
                    _uiState.update {
                        it.copy(snapshot = snapshot, isLoading = false, isDisconnected = false, error = null)
                    }
                }
        }
    }

    private suspend fun handleError(error: Throwable) {
        if (error is PartnerAccessRevokedException) {
            // AppMode is now NONE (the use case cleared it atomically on confirmed revoke).
            // Re-render the widget so it stops showing stale partner data.
            _uiState.update { it.copy(isLoading = false, isDisconnected = true) }
            widgetUpdater.updateAll()
        } else {
            _uiState.update {
                val message = if (it.snapshot == null) {
                    appContext.getString(R.string.error_partner_refresh_no_data)
                } else {
                    appContext.getString(R.string.error_partner_refresh_stale)
                }
                it.copy(isLoading = false, error = message)
            }
        }
    }

    /** Restarts the live collection (used by the Error/Empty state retry buttons). */
    fun retry() = start()

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
