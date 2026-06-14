package com.babytracker.ui.breastfeeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NotificationPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedSettingsUiState(
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val predictiveEnabled: Boolean = false,
    val predictiveLeadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndMinute: Int = DEFAULT_QUIET_HOURS_END,
    val notificationsPermissionGranted: Boolean = true,
    val showPermissionWarning: Boolean = false,
    val validIntervalCount: Int = 0,
)

@HiltViewModel
class FeedSettingsViewModel @Inject constructor(
    private val feedSettingsRepository: FeedSettingsRepository,
    // Quiet hours are shared with predictive sleep notifications, so they stay on
    // SettingsRepository rather than moving to FeedSettingsRepository.
    private val settingsRepository: SettingsRepository,
    private val countRecentValidIntervals: CountRecentValidIntervalsUseCase,
    private val notificationPermissionChecker: NotificationPermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedSettingsUiState())
    val uiState: StateFlow<FeedSettingsUiState> = _uiState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(
        notificationPermissionChecker.areNotificationsEnabled(),
    )

    init {
        viewModelScope.launch {
            combine<Any, FeedSettingsUiState>(
                feedSettingsRepository.getMaxPerBreastMinutes(),
                feedSettingsRepository.getMaxTotalFeedMinutes(),
                feedSettingsRepository.getPredictiveEnabled(),
                feedSettingsRepository.getPredictiveLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
                countRecentValidIntervals(),
                _permissionGranted,
            ) { values ->
                val maxPerBreast = values[0] as Int
                val maxTotal = values[1] as Int
                val predictiveEnabled = values[2] as Boolean
                val predictiveLead = values[3] as Int
                val quietStart = values[4] as Int
                val quietEnd = values[5] as Int
                val validIntervalCount = values[6] as Int
                val permissionGranted = values[7] as Boolean
                FeedSettingsUiState(
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    predictiveEnabled = predictiveEnabled,
                    predictiveLeadMinutes = predictiveLead,
                    quietHoursStartMinute = quietStart,
                    quietHoursEndMinute = quietEnd,
                    validIntervalCount = validIntervalCount,
                    notificationsPermissionGranted = permissionGranted,
                    showPermissionWarning = predictiveEnabled && !permissionGranted,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onMaxPerBreastChanged(minutes: Int) {
        viewModelScope.launch { feedSettingsRepository.setMaxPerBreastMinutes(minutes) }
    }

    fun onMaxTotalFeedChanged(minutes: Int) {
        viewModelScope.launch { feedSettingsRepository.setMaxTotalFeedMinutes(minutes) }
    }

    fun onPredictiveToggleChanged(enabled: Boolean) {
        viewModelScope.launch { feedSettingsRepository.setPredictiveEnabled(enabled) }
    }

    fun onLeadMinutesChanged(minutes: Int) {
        viewModelScope.launch { feedSettingsRepository.setPredictiveLeadMinutes(minutes) }
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

private const val DEFAULT_LEAD_MINUTES = 15
private const val DEFAULT_QUIET_HOURS_END = 480
