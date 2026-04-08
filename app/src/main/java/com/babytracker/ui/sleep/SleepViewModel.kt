package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = getSleepHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.getWakeTime().collect { wakeTime ->
                _uiState.value = _uiState.value.copy(wakeTime = wakeTime)
            }
        }
        loadSchedule()
    }

    fun onAddEntryClick() {
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            entryStartTime = LocalTime.now(),
            entryEndTime = LocalTime.now(),
            entryError = null
        )
    }

    fun onDismissSheet() {
        _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
    }

    fun onEntryTypeChanged(type: SleepType) {
        _uiState.value = _uiState.value.copy(entryType = type)
    }

    fun onEntryStartTimeChanged(time: LocalTime) {
        _uiState.value = _uiState.value.copy(entryStartTime = time, entryError = null)
    }

    fun onEntryEndTimeChanged(time: LocalTime) {
        _uiState.value = _uiState.value.copy(entryEndTime = time, entryError = null)
    }

    fun onSaveEntry() {
        val state = _uiState.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        var startInstant = state.entryStartTime.atDate(today).atZone(zone).toInstant()
        val endInstant = state.entryEndTime.atDate(today).atZone(zone).toInstant()

        // Cross-midnight night sleep: start was yesterday
        if (startInstant > endInstant) {
            startInstant = state.entryStartTime.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }

        if (endInstant <= startInstant) {
            _uiState.value = state.copy(entryError = "End time must be after start time")
            return
        }

        viewModelScope.launch {
            saveSleepEntry(startInstant, endInstant, state.entryType)
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
        }
    }

    fun onSetWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
            loadSchedule()
        }
    }

    fun onGenerateSchedule() {
        loadSchedule()
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
