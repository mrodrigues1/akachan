package com.babytracker.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.UnregisterPartnerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val partnerStashNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val measurementSystem: MeasurementSystem = MeasurementSystem.METRIC,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val babyRepository: BabyRepository,
    private val settingsRepository: SettingsRepository,
    private val saveBabyProfile: SaveBabyProfileUseCase,
    private val unregisterPartner: UnregisterPartnerUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                babyRepository.getBabyProfile(),
                settingsRepository.getThemeConfig(),
                settingsRepository.getAutoUpdateEnabled(),
                settingsRepository.getRichNotificationsEnabled(),
                settingsRepository.getPartnerFeedStashNotificationsEnabled(),
                settingsRepository.getAppMode(),
                settingsRepository.getVolumeUnit(),
                settingsRepository.getMeasurementSystem(),
            ) { values ->
                val baby = values[0] as? Baby
                val themeConfig = values[1] as ThemeConfig
                val autoUpdate = values[2] as Boolean
                val richNotifications = values[3] as Boolean
                val partnerStashNotifications = values[4] as Boolean
                val appMode = values[5] as AppMode
                val volumeUnit = values[6] as VolumeUnit
                val measurementSystem = values[7] as MeasurementSystem
                SettingsUiState(
                    baby = baby,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdate,
                    richNotificationsEnabled = richNotifications,
                    partnerStashNotificationsEnabled = partnerStashNotifications,
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

    fun onPartnerStashNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPartnerFeedStashNotificationsEnabled(enabled) }
    }

    fun disconnect() {
        viewModelScope.launch {
            // Best-effort: local disconnect must succeed even when the server-side
            // revoke fails (offline, share already deleted by the owner).
            try {
                unregisterPartner()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                Log.w(TAG, "Failed to revoke partner registration on disconnect", ignored)
            }
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

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
