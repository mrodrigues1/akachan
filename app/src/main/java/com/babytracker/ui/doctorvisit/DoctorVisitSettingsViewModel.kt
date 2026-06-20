package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorVisitSettingsUiState(
    val reminderEnabled: Boolean = false,
    val leadDays: Int = DEFAULT_LEAD_DAYS,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 1
        val LEAD_DAYS_OPTIONS = listOf(1, 3, 7, 14)
    }
}

@HiltViewModel
class DoctorVisitSettingsViewModel @Inject constructor(
    private val settings: DoctorVisitSettingsRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<DoctorVisitSettingsUiState> =
        combine(settings.getReminderEnabled(), settings.getReminderLeadDays()) { enabled, leadDays ->
            DoctorVisitSettingsUiState(reminderEnabled = enabled, leadDays = leadDays)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DoctorVisitSettingsUiState())

    fun onReminderToggle(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            // Arm all upcoming visits (or cancel them when turning off).
            reminderScheduler.rescheduleAll()
        }
    }

    fun onLeadDaysChange(days: Int) {
        viewModelScope.launch {
            settings.setReminderLeadDays(days)
            reminderScheduler.rescheduleAll()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
