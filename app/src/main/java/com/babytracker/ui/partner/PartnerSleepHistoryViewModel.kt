package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PartnerSleepHistoryUiState(
    val entries: List<SleepSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
)

/** Mirrors [PartnerFeedHistoryViewModel] for sleep (read + edit-own; no delete in this domain). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartnerSleepHistoryViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerSleepHistory: ObservePartnerSleepHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // Bumped by [refresh] to restart the pipeline (re-reads the share code and re-signs in). The live
    // collection itself is subscription-scoped via WhileSubscribed below, so a backstacked screen
    // detaches its Firestore listeners instead of holding them for the view model's lifetime.
    private val restartTrigger = MutableStateFlow(0)

    val uiState: StateFlow<PartnerSleepHistoryUiState> =
        restartTrigger
            .flatMapLatest { historyStates() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PartnerSleepHistoryUiState())

    private fun historyStates(): Flow<PartnerSleepHistoryUiState> {
        // Carried so a terminal error keeps the last-loaded rows visible, matching the previous
        // copy-based accumulation (the retry button only shows once entries are empty anyway).
        var lastEntries: List<SleepSnapshot> = emptyList()
        return flow {
            emit(PartnerSleepHistoryUiState(isLoading = true))
            emitAll(
                observePartnerSleepHistory(observePartnerData().map { it.sleepRecords })
                    .map { merged ->
                        lastEntries = merged.entries
                        PartnerSleepHistoryUiState(entries = merged.entries, isLoading = false)
                    },
            )
        }.catch { error ->
            when (error) {
                is CancellationException -> throw error
                is PartnerAccessRevokedException -> {
                    widgetUpdater.updateAll()
                    emit(
                        PartnerSleepHistoryUiState(
                            entries = lastEntries,
                            isLoading = false,
                            accessRevoked = true,
                            error = appContext.getString(R.string.error_partner_access_revoked),
                        ),
                    )
                }
                else -> emit(
                    PartnerSleepHistoryUiState(
                        entries = lastEntries,
                        isLoading = false,
                        error = appContext.getString(R.string.error_partner_load_failed),
                    ),
                )
            }
        }
    }

    /** Restarts the live collection (used by the screen's retry button). */
    fun refresh() {
        restartTrigger.update { it + 1 }
    }

    fun isEditable(entry: SleepSnapshot): Boolean =
        entry.startedBy == SleepAuthor.PARTNER && entry.clientId.isNotEmpty()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
