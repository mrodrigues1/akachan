package com.babytracker.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerDashboardUiState(
    val snapshot: ShareSnapshot? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val lastRefreshAt: Long = 0L,
)

@HiltViewModel
class PartnerDashboardViewModel @Inject constructor(
    private val fetchPartnerDataUseCase: FetchPartnerDataUseCase,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerDashboardUiState())
    val uiState: StateFlow<PartnerDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        var shouldRefresh = false
        _uiState.update { state ->
            if (state.isLoading) {
                state
            } else {
                shouldRefresh = true
                state.copy(isLoading = true, error = null)
            }
        }

        if (!shouldRefresh) return

        viewModelScope.launch {
            try {
                val snapshot = fetchPartnerDataUseCase()
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        isLoading = false,
                        isDisconnected = false,
                        lastRefreshAt = System.currentTimeMillis(),
                    )
                }
            } catch (_: IllegalStateException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDisconnected = true,
                        lastRefreshAt = System.currentTimeMillis(),
                    )
                }
                // AppMode is now NONE (use case cleared it atomically on confirmed revoke).
                // Re-render immediately so the widget stops showing stale partner data.
                widgetUpdater.updateAll()
            } catch (_: Exception) {
                _uiState.update {
                    val errorMessage = if (it.snapshot == null) {
                        "We couldn't check for shared updates. Pull down to try again."
                    } else {
                        "We couldn't check just now. Showing the last shared update."
                    }
                    it.copy(
                        isLoading = false,
                        error = errorMessage,
                        lastRefreshAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
