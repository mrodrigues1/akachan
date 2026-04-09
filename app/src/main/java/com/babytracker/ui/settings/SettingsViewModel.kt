package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
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
            ) { baby, maxPerBreast, maxTotal, themeConfig, autoUpdateEnabled ->
                SettingsUiState(
                    baby = baby,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdateEnabled,
                )
            }.collect { _uiState.value = it }
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
}
