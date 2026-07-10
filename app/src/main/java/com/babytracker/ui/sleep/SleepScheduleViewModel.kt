package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepScheduleUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val isRegressionExpanded: Boolean = true,
)

@HiltViewModel
class SleepScheduleViewModel @Inject constructor(
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val babyRepository: BabyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepScheduleUiState())
    val uiState: StateFlow<SleepScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    fun onToggleRegression() {
        _uiState.value = _uiState.value.copy(isRegressionExpanded = !_uiState.value.isRegressionExpanded)
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = babyRepository.getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
