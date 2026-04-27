package com.babytracker.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.GenerateShareCodeUseCase
import com.babytracker.sharing.usecase.RevokePartnerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val sharingRepository: SharingRepository,
    private val generateShareCodeUseCase: GenerateShareCodeUseCase,
    private val revokePartnerUseCase: RevokePartnerUseCase,
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
                val partners = sharingRepository.getPartners(ShareCode(state.shareCode))
                _uiState.update { it.copy(partners = partners, isLoading = false, error = null) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't load partners. Check your connection.") }
            }
        }
    }

    fun startSharing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                generateShareCodeUseCase()
                val code = ShareCode(settingsRepository.getShareCode().first() ?: return@launch)
                val partners = sharingRepository.getPartners(code)
                _uiState.update { it.copy(partners = partners, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't enable sharing. Check your connection.") }
            }
        }
    }

    fun stopSharing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val code = settingsRepository.getShareCode().first()
                if (code != null) sharingRepository.deleteShareDocument(ShareCode(code))
                settingsRepository.clearShareCode()
                settingsRepository.setAppMode(AppMode.NONE)
                _uiState.update { it.copy(isLoading = false, partners = emptyList()) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't stop sharing. Check your connection.") }
            }
        }
    }

    fun generateNewCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val oldCode = settingsRepository.getShareCode().first()
                if (oldCode != null) sharingRepository.deleteShareDocument(ShareCode(oldCode))
                settingsRepository.clearShareCode()
                generateShareCodeUseCase()
                val newCode = ShareCode(settingsRepository.getShareCode().first() ?: return@launch)
                val partners = sharingRepository.getPartners(newCode)
                _uiState.update { it.copy(partners = partners, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't generate new code. Check your connection.") }
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
                _uiState.update { it.copy(error = "Couldn't remove partner. Check your connection.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
