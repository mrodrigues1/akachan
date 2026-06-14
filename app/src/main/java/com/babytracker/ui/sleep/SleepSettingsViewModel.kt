package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepSettingsUiState(
    val napReminderEnabled: Boolean = false,
    val napReminderDelayMinutes: Int = DEFAULT_NAP_DELAY_MINUTES,
    val predictiveSleepEnabled: Boolean = false,
    val predictiveSleepLeadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndMinute: Int = DEFAULT_QUIET_HOURS_END,
    val notificationsPermissionGranted: Boolean = true,
    val showPermissionWarning: Boolean = false,
)

@HiltViewModel
class SleepSettingsViewModel @Inject constructor(
    private val sleepSettingsRepository: SleepSettingsRepository,
    // Quiet hours are shared with predictive feeding notifications, so they stay on
    // SettingsRepository rather than moving to SleepSettingsRepository.
    private val settingsRepository: SettingsRepository,
    private val napReminderScheduler: NapReminderScheduler,
    private val notificationPermissionChecker: NotificationPermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepSettingsUiState())
    val uiState: StateFlow<SleepSettingsUiState> = _uiState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(
        notificationPermissionChecker.areNotificationsEnabled(),
    )

    init {
        viewModelScope.launch {
            combine<Any, SleepSettingsUiState>(
                sleepSettingsRepository.getNapReminderEnabled(),
                sleepSettingsRepository.getNapReminderDelayMinutes(),
                sleepSettingsRepository.getPredictiveSleepEnabled(),
                sleepSettingsRepository.getPredictiveSleepLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
                _permissionGranted,
            ) { values ->
                val napEnabled = values[0] as Boolean
                val napDelay = values[1] as Int
                val predictiveSleepEnabled = values[2] as Boolean
                val predictiveSleepLead = values[3] as Int
                val quietStart = values[4] as Int
                val quietEnd = values[5] as Int
                val permissionGranted = values[6] as Boolean
                SleepSettingsUiState(
                    napReminderEnabled = napEnabled,
                    napReminderDelayMinutes = napDelay,
                    predictiveSleepEnabled = predictiveSleepEnabled,
                    predictiveSleepLeadMinutes = predictiveSleepLead,
                    quietHoursStartMinute = quietStart,
                    quietHoursEndMinute = quietEnd,
                    notificationsPermissionGranted = permissionGranted,
                    showPermissionWarning = (napEnabled || predictiveSleepEnabled) && !permissionGranted,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onNapReminderToggleChanged(enabled: Boolean) {
        viewModelScope.launch {
            sleepSettingsRepository.setNapReminderEnabled(enabled)
            if (!enabled) napReminderScheduler.cancel()
        }
    }

    fun onNapReminderDelayChanged(minutes: Int) {
        viewModelScope.launch { sleepSettingsRepository.setNapReminderDelayMinutes(minutes) }
    }

    fun onPredictiveSleepToggleChanged(enabled: Boolean) {
        viewModelScope.launch { sleepSettingsRepository.setPredictiveSleepEnabled(enabled) }
    }

    fun onSleepLeadMinutesChanged(minutes: Int) {
        viewModelScope.launch { sleepSettingsRepository.setPredictiveSleepLeadMinutes(minutes) }
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
}

private const val DEFAULT_NAP_DELAY_MINUTES = 60
private const val DEFAULT_LEAD_MINUTES = 15
private const val DEFAULT_QUIET_HOURS_END = 480
