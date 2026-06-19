package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.VaccineReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineSettingsUiState(
    val reminderEnabled: Boolean = false,
    val leadDays: Int = DEFAULT_LEAD_DAYS,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 7
        val LEAD_DAYS_OPTIONS = listOf(1, 3, 7, 14)
    }
}

@HiltViewModel
class VaccineSettingsViewModel @Inject constructor(
    private val settings: VaccineSettingsRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<VaccineSettingsUiState> =
        combine(settings.getReminderEnabled(), settings.getReminderLeadDays()) { enabled, leadDays ->
            VaccineSettingsUiState(reminderEnabled = enabled, leadDays = leadDays)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaccineSettingsUiState())

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
