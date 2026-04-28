package com.babytracker.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
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
)

@HiltViewModel
class PartnerDashboardViewModel @Inject constructor(
    private val fetchPartnerDataUseCase: FetchPartnerDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerDashboardUiState())
    val uiState: StateFlow<PartnerDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = fetchPartnerDataUseCase()
                _uiState.update { it.copy(snapshot = snapshot, isLoading = false) }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(isLoading = false, isDisconnected = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't refresh. Check your connection.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
