package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

data class PartnerSleepHistoryUiState(
    val entries: List<SleepSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
)

/** Mirrors [PartnerFeedHistoryViewModel] for sleep (read + edit-own; no delete in this domain). */
@HiltViewModel
class PartnerSleepHistoryViewModel @Inject constructor(
    private val fetchPartnerData: FetchPartnerDataUseCase,
    private val observePartnerSleepHistory: ObservePartnerSleepHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerSleepHistoryUiState())
    val uiState: StateFlow<PartnerSleepHistoryUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null
    private var lastPendingOpIds = emptySet<String>()
    private val refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        refresh()
        viewModelScope.launch {
            refreshTrigger.collectLatest {
                delay(REFRESH_DEBOUNCE_MS)
                refresh()
            }
        }
    }

    fun refresh() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            try {
                val snapshot = fetchPartnerData()
                observePartnerSleepHistory(snapshot.sleepRecords).collect { merged ->
                    _uiState.update { it.copy(entries = merged.entries, isLoading = false) }
                    if (hasConsumedPendingOps(lastPendingOpIds, merged.pendingOpIds)) {
                        refreshTrigger.tryEmit(Unit)
                    }
                    lastPendingOpIds = merged.pendingOpIds
                }
            } catch (error: CancellationException) {
                throw error
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
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_partner_load_failed)) }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_partner_load_failed)) }
            }
        }
    }

    fun isEditable(entry: SleepSnapshot): Boolean =
        entry.startedBy == SleepAuthor.PARTNER.name && entry.clientId.isNotEmpty()

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
