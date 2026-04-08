package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepUiState(
    val activeRecord: SleepRecord? = null,
    val selectedType: SleepType = SleepType.NAP,
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val startSleepRecord: StartSleepRecordUseCase,
    private val stopSleepRecord: StopSleepRecordUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val repository: SleepRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = getSleepHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getActiveRecord().collect { record ->
                _uiState.value = _uiState.value.copy(activeRecord = record)
            }
        }
        loadSchedule()
    }

    fun onTypeSelected(type: SleepType) {
        _uiState.value = _uiState.value.copy(selectedType = type)
    }

    fun onStartTracking() {
        viewModelScope.launch {
            startSleepRecord(_uiState.value.selectedType)
        }
    }

    fun onStopTracking() {
        val record = _uiState.value.activeRecord ?: return
        viewModelScope.launch {
            stopSleepRecord(record)
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
