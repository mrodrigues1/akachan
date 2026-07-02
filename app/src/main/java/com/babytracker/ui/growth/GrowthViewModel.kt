package com.babytracker.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.growth.AddGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.GetGrowthChartDataUseCase
import com.babytracker.domain.usecase.growth.UpdateGrowthMeasurementUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class GrowthUiState(
    val selectedType: GrowthType = GrowthType.WEIGHT,
    val measurementSystem: MeasurementSystem = MeasurementSystem.METRIC,
    val chart: GrowthChartData? = null,
    val isLoading: Boolean = true,
    val saveError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val getGrowthChartData: GetGrowthChartDataUseCase,
    private val addGrowthMeasurement: AddGrowthMeasurementUseCase,
    private val updateGrowthMeasurement: UpdateGrowthMeasurementUseCase,
    private val growthRepository: GrowthRepository,
    private val settingsRepository: SettingsRepository,
    private val syncedWrite: SyncedWrite,
) : ViewModel() {

    private val selectedType = MutableStateFlow(GrowthType.WEIGHT)
    private val saveError = MutableStateFlow(false)

    val uiState: StateFlow<GrowthUiState> =
        combine(
            selectedType,
            selectedType.flatMapLatest { getGrowthChartData(it) },
            settingsRepository.getMeasurementSystem(),
            saveError,
        ) { type, chart, system, error ->
            GrowthUiState(
                selectedType = type,
                measurementSystem = system,
                chart = chart,
                isLoading = false,
                saveError = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = GrowthUiState(),
        )

    fun onTypeSelected(type: GrowthType) {
        selectedType.value = type
    }

    fun onAddMeasurement(type: GrowthType, valueCanonical: Long, takenAt: Instant, notes: String?) {
        viewModelScope.launch {
            val result = runCatching {
                addGrowthMeasurement(
                    GrowthMeasurement(
                        takenAt = takenAt,
                        type = type,
                        valueCanonical = valueCanonical,
                        notes = notes?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (result.isFailure) {
                saveError.value = true
                return@launch
            }
            syncedWrite.sync()
        }
    }

    fun onUpdateMeasurement(id: Long, type: GrowthType, valueCanonical: Long, takenAt: Instant, notes: String?) {
        viewModelScope.launch {
            val result = runCatching {
                updateGrowthMeasurement(
                    GrowthMeasurement(
                        id = id,
                        takenAt = takenAt,
                        type = type,
                        valueCanonical = valueCanonical,
                        notes = notes?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (result.isFailure) {
                saveError.value = true
                return@launch
            }
            syncedWrite.sync()
        }
    }

    fun onDeleteMeasurement(id: Long) {
        viewModelScope.launch {
            val result = runCatching { growthRepository.deleteMeasurement(id) }
            if (result.isFailure) {
                saveError.value = true
                return@launch
            }
            syncedWrite.sync()
        }
    }

    fun onSaveErrorConsumed() {
        saveError.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
