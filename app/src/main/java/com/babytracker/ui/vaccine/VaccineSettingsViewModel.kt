package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.manager.VaccineReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineSettingsUiState(
    // Loading until the first DataStore emission, so the toggle doesn't flash its default-off state
    // before the saved preference arrives.
    val isLoading: Boolean = true,
    /** Set when a preferences read throws, so the screen can offer retry instead of a silent default. */
    val isError: Boolean = false,
    val reminderEnabled: Boolean = false,
    val leadDays: Int = DEFAULT_LEAD_DAYS,
    val toScheduleLeadDays: Int = DEFAULT_TO_SCHEDULE_LEAD_DAYS,
    /** Reminders are on but the OS has notifications blocked, so the reminder would never fire. */
    val showPermissionWarning: Boolean = false,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 7
        val LEAD_DAYS_OPTIONS = listOf(1, 3, 7, 14)
        const val DEFAULT_TO_SCHEDULE_LEAD_DAYS = 14
        val TO_SCHEDULE_LEAD_DAYS_OPTIONS = listOf(7, 14, 30)
    }
}

@HiltViewModel
class VaccineSettingsViewModel @Inject constructor(
    private val settings: VaccineSettingsRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val notificationPermissionChecker: NotificationPermissionChecker,
) : ViewModel() {

    // Bumped by onRetry so flatMapLatest rebuilds the flow after a read failure.
    private val retryTrigger = MutableStateFlow(0)

    // Held outside the settings flow so it survives retries and updates on resume / permission result.
    private val permissionGranted = MutableStateFlow(notificationPermissionChecker.areNotificationsEnabled())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineSettingsUiState> =
        retryTrigger.flatMapLatest {
            combine(
                settings.getReminderEnabled(),
                settings.getReminderLeadDays(),
                settings.getToScheduleLeadDays(),
                permissionGranted,
            ) { enabled, leadDays, toScheduleLeadDays, granted ->
                VaccineSettingsUiState(
                    isLoading = false,
                    reminderEnabled = enabled,
                    leadDays = leadDays,
                    toScheduleLeadDays = toScheduleLeadDays,
                    // Only warn when reminders are on: a blocked-but-disabled reminder is not a problem.
                    showPermissionWarning = enabled && !granted,
                )
            }
                // Preferences reads rarely fail, but if DataStore throws, surface an explicit error+retry
                // instead of silently masquerading as "reminders off".
                .catch { emit(VaccineSettingsUiState(isLoading = false, isError = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaccineSettingsUiState())

    fun onRetry() {
        retryTrigger.value++
    }

    /** Result of the runtime permission request. */
    fun refreshNotificationsPermission(granted: Boolean) {
        permissionGranted.value = granted
    }

    /** Re-check on resume so returning from system settings clears (or re-shows) the warning. */
    fun onLifecycleResume() {
        permissionGranted.value = notificationPermissionChecker.areNotificationsEnabled()
    }

    fun onReminderToggle(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            // Arm all future scheduled vaccines (or cancel them when turning off).
            reminderScheduler.rescheduleAll()
        }
    }

    fun onLeadDaysChange(days: Int) {
        viewModelScope.launch {
            settings.setReminderLeadDays(days)
            reminderScheduler.rescheduleAll()
        }
    }

    fun onToScheduleLeadDaysChange(days: Int) {
        viewModelScope.launch {
            settings.setToScheduleLeadDays(days)
            reminderScheduler.rescheduleAll()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
