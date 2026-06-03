package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.BuildConfig
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
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
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
    val predictiveEnabled: Boolean = false,
    val predictiveLeadMinutes: Int = 15,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndMinute: Int = 480,
    val notificationsPermissionGranted: Boolean = true,
    val showPermissionWarning: Boolean = false,
    val validIntervalCount: Int = 0,
    val napReminderEnabled: Boolean = false,
    val napReminderDelayMinutes: Int = 60,
    val predictiveSleepEnabled: Boolean = false,
    val predictiveSleepLeadMinutes: Int = 15,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveBabyProfile: SaveBabyProfileUseCase,
    private val countRecentValidIntervals: CountRecentValidIntervalsUseCase,
    private val notificationPermissionChecker: NotificationPermissionChecker,
    private val napReminderScheduler: NapReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(
        notificationPermissionChecker.areNotificationsEnabled()
    )

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
                settingsRepository.getPredictiveEnabled(),
                settingsRepository.getPredictiveLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
                countRecentValidIntervals(),
                _permissionGranted,
                settingsRepository.getNapReminderEnabled(),
                settingsRepository.getNapReminderDelayMinutes(),
                settingsRepository.getPredictiveSleepEnabled(),
                settingsRepository.getPredictiveSleepLeadMinutes(),
            ) { values ->
                val baby = values[0] as? Baby
                val maxPerBreast = values[1] as Int
                val maxTotal = values[2] as Int
                val themeConfig = values[3] as ThemeConfig
                val autoUpdate = values[4] as Boolean
                val richNotifications = values[5] as Boolean
                val appMode = values[6] as AppMode
                val predictiveEnabled = values[7] as Boolean
                val predictiveLeadMinutes = values[8] as Int
                val quietHoursStart = values[9] as Int
                val quietHoursEnd = values[10] as Int
                val validIntervalCount = values[11] as Int
                val permissionGranted = values[12] as Boolean
                val napReminderEnabled = values[13] as Boolean
                val napReminderDelayMinutes = values[14] as Int
                val predictiveSleepEnabled = values[15] as Boolean
                val predictiveSleepLeadMinutes = values[16] as Int
                SettingsUiState(
                    baby = baby,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdate,
                    richNotificationsEnabled = richNotifications,
                    appMode = appMode,
                    predictiveEnabled = predictiveEnabled,
                    predictiveLeadMinutes = predictiveLeadMinutes,
                    quietHoursStartMinute = quietHoursStart,
                    quietHoursEndMinute = quietHoursEnd,
                    notificationsPermissionGranted = permissionGranted,
                    showPermissionWarning = (predictiveEnabled || napReminderEnabled || (BuildConfig.DEBUG && predictiveSleepEnabled)) && !permissionGranted,
                    validIntervalCount = validIntervalCount,
                    napReminderEnabled = napReminderEnabled,
                    napReminderDelayMinutes = napReminderDelayMinutes,
                    predictiveSleepEnabled = predictiveSleepEnabled,
                    predictiveSleepLeadMinutes = predictiveSleepLeadMinutes,
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

    fun onPredictiveToggleChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPredictiveEnabled(enabled)
        }
    }

    fun onLeadMinutesChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setPredictiveLeadMinutes(minutes) }
    }

    fun onQuietHoursStartChanged(minute: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursStartMinute(minute) }
    }

    fun onQuietHoursEndChanged(minute: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursEndMinute(minute) }
    }

    fun refreshNotificationsPermission(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun onLifecycleResume() {
        _permissionGranted.value = notificationPermissionChecker.areNotificationsEnabled()
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsRepository.setAppMode(AppMode.NONE)
            settingsRepository.clearShareCode()
            _uiState.update { it.copy(isDisconnected = true) }
        }
    }

    fun onNapReminderToggleChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNapReminderEnabled(enabled)
            if (!enabled) napReminderScheduler.cancel()
        }
    }

    fun onNapReminderDelayChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setNapReminderDelayMinutes(minutes) }
    }

    fun onPredictiveSleepToggleChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPredictiveSleepEnabled(enabled) }
    }

    fun onSleepLeadMinutesChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setPredictiveSleepLeadMinutes(minutes) }
    }
}
