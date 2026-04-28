package com.babytracker.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.ConnectAsPartnerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectPartnerUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
)

@HiltViewModel
class ConnectPartnerViewModel @Inject constructor(
    private val connectAsPartnerUseCase: ConnectAsPartnerUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectPartnerUiState())
    val uiState: StateFlow<ConnectPartnerUiState> = _uiState.asStateFlow()

    fun onCodeChanged(input: String) {
        val normalized = input.filter { it.isLetterOrDigit() }.uppercase().take(CODE_LENGTH)
        _uiState.update { it.copy(code = normalized, error = null) }
    }

    fun onConnect() {
        val code = _uiState.value.code
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                connectAsPartnerUseCase(ShareCode(code))
                _uiState.update { it.copy(isLoading = false, isConnected = true) }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(isLoading = false, error = "This code doesn't exist. Check with your partner.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't connect. Check your connection.") }
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 8
    }
}
