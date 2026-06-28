package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerSleepHistoryUiState(
    val entries: List<SleepSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
)

/** Mirrors [PartnerFeedHistoryViewModel] for sleep (read + edit-own; no delete in this domain). */
@HiltViewModel
class PartnerSleepHistoryViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerSleepHistory: ObservePartnerSleepHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerSleepHistoryUiState())
    val uiState: StateFlow<PartnerSleepHistoryUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        refresh()
    }

    /** Restarts the live collection (used by the screen's retry button). */
    fun refresh() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            observePartnerSleepHistory(observePartnerData().map { it.sleepRecords })
                .map { it.entries }
                .collectPartnerHistory(
                    widgetUpdater = widgetUpdater,
                    onItem = { entries -> _uiState.update { it.copy(entries = entries, isLoading = false) } },
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

    fun isEditable(entry: SleepSnapshot): Boolean =
        entry.startedBy == SleepAuthor.PARTNER.name && entry.clientId.isNotEmpty()
}
