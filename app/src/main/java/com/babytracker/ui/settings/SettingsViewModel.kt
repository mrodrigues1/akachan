package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
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
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val measurementSystem: MeasurementSystem = MeasurementSystem.METRIC,
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
                settingsRepository.getThemeConfig(),
                settingsRepository.getAutoUpdateEnabled(),
                settingsRepository.getRichNotificationsEnabled(),
                settingsRepository.getAppMode(),
                settingsRepository.getVolumeUnit(),
                settingsRepository.getMeasurementSystem(),
            ) { values ->
                val baby = values[0] as? Baby
                val themeConfig = values[1] as ThemeConfig
                val autoUpdate = values[2] as Boolean
                val richNotifications = values[3] as Boolean
                val appMode = values[4] as AppMode
                val volumeUnit = values[5] as VolumeUnit
                val measurementSystem = values[6] as MeasurementSystem
                SettingsUiState(
                    baby = baby,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdate,
                    richNotificationsEnabled = richNotifications,
                    appMode = appMode,
                    volumeUnit = volumeUnit,
                    measurementSystem = measurementSystem,
                )
            }.collect { next ->
                _uiState.update { current -> next.copy(isDisconnected = current.isDisconnected) }
            }
        }
    }

    fun onThemeConfigChanged(themeConfig: ThemeConfig) {
        viewModelScope.launch { settingsRepository.setThemeConfig(themeConfig) }
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

    fun onVolumeUnitChanged(unit: VolumeUnit) {
        viewModelScope.launch { settingsRepository.setVolumeUnit(unit) }
    }

    fun onMeasurementSystemChanged(system: MeasurementSystem) {
        viewModelScope.launch { settingsRepository.setMeasurementSystem(system) }
    }
}
