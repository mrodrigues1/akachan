package com.babytracker.ui.inventory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventorySettingsUiState(
    val isExpirationEnabled: Boolean = false,
    val expirationDays: String = DEFAULT_DAYS.toString(),
    val isNotificationEnabled: Boolean = false,
    val notificationTimeMinutes: Int = DEFAULT_NOTIFICATION_TIME_MINUTES,
    val validationError: String? = null,
    val showTimePicker: Boolean = false,
)

@HiltViewModel
class InventorySettingsViewModel @Inject constructor(
    private val settings: InventorySettingsRepository,
    private val scheduler: StashExpirationScheduler,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventorySettingsUiState())
    val uiState: StateFlow<InventorySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.getExpirationEnabled(),
                settings.getExpirationDays(),
                settings.getExpirationNotifEnabled(),
                settings.getExpirationNotifTimeMinutes(),
            ) { isExpirationEnabled, days, isNotificationEnabled, notificationTimeMinutes ->
                SettingsSnapshot(
                    isExpirationEnabled = isExpirationEnabled,
                    days = days,
                    isNotificationEnabled = isNotificationEnabled,
                    notificationTimeMinutes = notificationTimeMinutes,
                )
            }.collect { snapshot ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    isExpirationEnabled = snapshot.isExpirationEnabled,
                    expirationDays = if (current.validationError == null) {
                        snapshot.days.toString()
                    } else {
                        current.expirationDays
                    },
                    isNotificationEnabled = snapshot.isNotificationEnabled,
                    notificationTimeMinutes = snapshot.notificationTimeMinutes,
                )
            }
        }
    }

    fun onExpirationEnabledChanged(isEnabled: Boolean) {
        viewModelScope.launch {
            settings.setExpirationEnabled(isEnabled)
            if (isEnabled) {
                val current = _uiState.value
                if (current.isNotificationEnabled) {
                    scheduler.scheduleDaily(current.notificationTimeMinutes)
                }
            } else {
                scheduler.cancel()
            }
        }
    }

    fun onDaysChanged(input: String) {
        val days = input.toIntOrNull()
        if (days == null || days < MIN_DAYS) {
            _uiState.value = _uiState.value.copy(
                expirationDays = input,
                validationError = appContext.getString(R.string.error_min_one_day),
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            expirationDays = input,
            validationError = null,
        )
        viewModelScope.launch {
            settings.setExpirationDays(days)
        }
    }

    fun onNotifEnabledChanged(isEnabled: Boolean) {
        viewModelScope.launch {
            settings.setExpirationNotifEnabled(isEnabled)
            if (isEnabled) {
                scheduler.scheduleDaily(_uiState.value.notificationTimeMinutes)
            } else {
                scheduler.cancel()
            }
        }
    }

    fun onNotifTimeChanged(minuteOfDay: Int) {
        val safeMinuteOfDay = minuteOfDay.coerceIn(MIN_MINUTE_OF_DAY, MAX_MINUTE_OF_DAY)
        _uiState.value = _uiState.value.copy(
            notificationTimeMinutes = safeMinuteOfDay,
            showTimePicker = false,
        )
        viewModelScope.launch {
            settings.setExpirationNotifTimeMinutes(safeMinuteOfDay)
            if (_uiState.value.isNotificationEnabled) {
                scheduler.scheduleDaily(safeMinuteOfDay)
            }
        }
    }

    fun onTimePickerOpen() {
        _uiState.value = _uiState.value.copy(showTimePicker = true)
    }

    fun onTimePickerDismiss() {
        _uiState.value = _uiState.value.copy(showTimePicker = false)
    }

    private data class SettingsSnapshot(
        val isExpirationEnabled: Boolean,
        val days: Int,
        val isNotificationEnabled: Boolean,
        val notificationTimeMinutes: Int,
    )
}

private const val DEFAULT_DAYS = 15
private const val DEFAULT_NOTIFICATION_TIME_MINUTES = 480
private const val MIN_DAYS = 1
private const val MIN_MINUTE_OF_DAY = 0
private const val MAX_MINUTE_OF_DAY = 1439
