package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveBabyProfile: SaveBabyProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getBabyProfile(),
                settingsRepository.getMaxPerBreastMinutes(),
                settingsRepository.getMaxTotalFeedMinutes(),
                settingsRepository.getThemeConfig(),
                settingsRepository.getAutoUpdateEnabled(),
                settingsRepository.getRichNotificationsEnabled(),
                settingsRepository.getAppMode(),
            ) { values ->
                val baby = values[0] as? Baby
                val maxPerBreast = values[1] as Int
                val maxTotal = values[2] as Int
                val themeConfig = values[3] as ThemeConfig
                val autoUpdate = values[4] as Boolean
                val richNotifications = values[5] as Boolean
                val appMode = values[6] as AppMode
                SettingsUiState(
                    baby = baby,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdate,
                    richNotificationsEnabled = richNotifications,
                    appMode = appMode,
                )
            }.collect { next ->
                _uiState.update { current -> next.copy(isDisconnected = current.isDisconnected) }
            }
        }
    }

    fun onThemeConfigChanged(themeConfig: ThemeConfig) {
        viewModelScope.launch { settingsRepository.setThemeConfig(themeConfig) }
    }

    fun onMaxPerBreastChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setMaxPerBreastMinutes(minutes) }
    }

    fun onMaxTotalFeedChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setMaxTotalFeedMinutes(minutes) }
    }

    fun onSaveBabyProfile(baby: Baby) {
        viewModelScope.launch { saveBabyProfile(baby) }
    }

    fun onAutoUpdateChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateEnabled(enabled) }
    }

    fun onRichNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRichNotificationsEnabled(enabled) }
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsRepository.setAppMode(AppMode.NONE)
            settingsRepository.clearShareCode()
            _uiState.update { it.copy(isDisconnected = true) }
        }
    }
}
