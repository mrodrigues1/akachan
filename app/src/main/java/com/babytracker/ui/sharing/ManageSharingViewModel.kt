package com.babytracker.ui.sharing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.usecase.GenerateShareCodeUseCase
import com.babytracker.sharing.usecase.RevokePartnerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageSharingUiState(
    val appMode: AppMode = AppMode.NONE,
    val shareCode: String? = null,
    val partners: List<PartnerInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ManageSharingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val service: FirestoreSharingService,
    private val generateShareCodeUseCase: GenerateShareCodeUseCase,
    private val revokePartnerUseCase: RevokePartnerUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageSharingUiState())
    val uiState: StateFlow<ManageSharingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.getAppMode(),
                settingsRepository.getShareCode(),
            ) { mode, code -> mode to code }
                .collect { (mode, code) ->
                    _uiState.update { it.copy(appMode = mode, shareCode = code) }
                }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.appMode != AppMode.PRIMARY || state.shareCode == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val partners = service.getPartners(state.shareCode)
                _uiState.update { it.copy(partners = partners, isLoading = false, error = null) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_sharing_load_partners)) }
            }
        }
    }

    fun startSharing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                regenerateAndLoad()
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_sharing_enable)) }
            }
        }
    }

    /** Generates a fresh share code, then loads its (empty) partner list into state. */
    private suspend fun regenerateAndLoad() {
        generateShareCodeUseCase()
        val code = settingsRepository.getShareCode().first() ?: return
        val partners = service.getPartners(code)
        _uiState.update { it.copy(partners = partners, isLoading = false) }
    }

    fun stopSharing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val code = settingsRepository.getShareCode().first()
                if (code != null) service.deleteShareDocument(code)
                settingsRepository.clearShareCode()
                settingsRepository.setAppMode(AppMode.NONE)
                _uiState.update { it.copy(isLoading = false, partners = emptyList()) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_sharing_stop)) }
            }
        }
    }

    fun generateNewCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val oldCode = settingsRepository.getShareCode().first()
                if (oldCode != null) service.deleteShareDocument(oldCode)
                settingsRepository.clearShareCode()
                regenerateAndLoad()
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_sharing_new_code)) }
            }
        }
    }

    fun revokePartner(partnerUid: String) {
        viewModelScope.launch {
            try {
                revokePartnerUseCase(partnerUid)
                _uiState.update { state ->
                    state.copy(partners = state.partners.filter { it.uid != partnerUid })
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_sharing_remove_partner)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
