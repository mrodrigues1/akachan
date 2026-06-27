package com.babytracker.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.growth.AddGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.DeleteGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.GetGrowthChartDataUseCase
import com.babytracker.domain.usecase.growth.UpdateGrowthMeasurementUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.syncSharedSnapshot
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val getGrowthChartData: GetGrowthChartDataUseCase,
    private val addGrowthMeasurement: AddGrowthMeasurementUseCase,
    private val updateGrowthMeasurement: UpdateGrowthMeasurementUseCase,
    private val deleteGrowthMeasurement: DeleteGrowthMeasurementUseCase,
    private val settingsRepository: SettingsRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
) : ViewModel() {

    private val selectedType = MutableStateFlow(GrowthType.WEIGHT)

    val uiState: StateFlow<GrowthUiState> =
        combine(
            selectedType,
            selectedType.flatMapLatest { getGrowthChartData(it) },
            settingsRepository.getMeasurementSystem(),
        ) { type, chart, system ->
            GrowthUiState(
                selectedType = type,
                measurementSystem = system,
                chart = chart,
                isLoading = false,
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
            addGrowthMeasurement(
                GrowthMeasurement(
                    takenAt = takenAt,
                    type = type,
                    valueCanonical = valueCanonical,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
            syncToFirestore.syncSharedSnapshot()
        }
    }

    fun onUpdateMeasurement(id: Long, type: GrowthType, valueCanonical: Long, takenAt: Instant, notes: String?) {
        viewModelScope.launch {
            updateGrowthMeasurement(
                GrowthMeasurement(
                    id = id,
                    takenAt = takenAt,
                    type = type,
                    valueCanonical = valueCanonical,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
            syncToFirestore.syncSharedSnapshot()
        }
    }

    fun onDeleteMeasurement(id: Long) {
        viewModelScope.launch {
            deleteGrowthMeasurement(id)
            syncToFirestore.syncSharedSnapshot()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
