package com.babytracker.ui.sharing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.ConnectAsPartnerUseCase
import com.babytracker.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val widgetRefreshScheduler: WidgetRefreshScheduler,
    @ApplicationContext private val appContext: Context,
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
                // Best-effort: prime the widget cache now instead of waiting for the 15-min
                // periodic worker. Failure here must not undo the successful connection.
                runCatching { widgetRefreshScheduler.scheduleImmediateRefresh() }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_connect_code_missing)) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_connect_failed)) }
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 8
    }
}
