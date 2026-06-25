package com.babytracker.ui.pumping

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.pumping.PausePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.ResumePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.SavePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StartPumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StopPumpingSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class PumpingMode { MANUAL, TIMER }

data class BagPromptState(
    val sessionId: Long?,
    val collectionDate: Instant,
    val volumeMl: Int,
    val notes: String = "",
    val volumeError: String? = null,
    val isSaving: Boolean = false,
)

data class ManualEntryState(
    val startTime: Instant,
    val endTime: Instant,
    val breast: PumpingBreast = PumpingBreast.LEFT,
    val volumeMl: String = "",
    val notes: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class PumpingUiState(
    val mode: PumpingMode = PumpingMode.MANUAL,
    val activeSession: PumpingSession? = null,
    val selectedBreast: PumpingBreast = PumpingBreast.BOTH,
    val isStarting: Boolean = false,
    val manual: ManualEntryState? = null,
    val bagPrompt: BagPromptState? = null,
    val error: String? = null,
)

@HiltViewModel
class PumpingViewModel @Inject constructor(
    private val pumpingRepository: PumpingRepository,
    private val startUseCase: StartPumpingSessionUseCase,
    private val stopUseCase: StopPumpingSessionUseCase,
    private val pauseUseCase: PausePumpingSessionUseCase,
    private val resumeUseCase: ResumePumpingSessionUseCase,
    private val saveManual: SavePumpingSessionUseCase,
    private val addBag: AddMilkBagUseCase,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        run {
            val nowInstant = now()
            PumpingUiState(
                mode = PumpingMode.MANUAL,
                manual = ManualEntryState(startTime = nowInstant.minusSeconds(15 * 60), endTime = nowInstant),
            )
        },
    )
    val uiState: StateFlow<PumpingUiState> = _uiState.asStateFlow()

    private val stoppingSessionIds = mutableSetOf<Long>()
    private val promptedStoppedSessionIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            pumpingRepository.getActiveSession().collect { session ->
                // A live session is only reachable on the Timer tab, and the tab switch is
                // locked while one is running — so force Timer when we resume into one.
                _uiState.value = if (session != null) {
                    _uiState.value.copy(activeSession = session, mode = PumpingMode.TIMER)
                } else {
                    _uiState.value.copy(activeSession = session)
                }
            }
        }
    }

    fun onModeChange(mode: PumpingMode) {
        val newManual = if (mode == PumpingMode.MANUAL && _uiState.value.manual == null) {
            val nowInstant = now()
            ManualEntryState(startTime = nowInstant.minusSeconds(15 * 60), endTime = nowInstant)
        } else {
            _uiState.value.manual
        }
        _uiState.value = _uiState.value.copy(mode = mode, manual = newManual)
    }

    fun onBreastSelected(breast: PumpingBreast) {
        _uiState.value = _uiState.value.copy(selectedBreast = breast)
    }

    fun onStartTimer() {
        if (_uiState.value.isStarting || _uiState.value.activeSession != null) return
        _uiState.value = _uiState.value.copy(isStarting = true)
        viewModelScope.launch {
            runCatching { startUseCase(_uiState.value.selectedBreast) }
                .onFailure { _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_pumping_start)) }
            _uiState.value = _uiState.value.copy(isStarting = false)
        }
    }

    fun onPause() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { runCatching { pauseUseCase(session) } }
    }

    fun onResume() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { runCatching { resumeUseCase(session) } }
    }

    fun onStopTimer(volumeMl: Int?) {
        val session = _uiState.value.activeSession ?: return
        if (!stoppingSessionIds.add(session.id)) return
        viewModelScope.launch {
            runCatching { stopUseCase(session, volumeMl) }
                .onSuccess { stopped ->
                    if (!promptedStoppedSessionIds.add(stopped.id)) return@onSuccess
                    openBagPrompt(
                        sessionId = stopped.id,
                        volumeMl = stopped.volumeMl ?: 0,
                        collectionDate = stopped.endTime!!,
                    )
                }
                .onFailure {
                    stoppingSessionIds.remove(session.id)
                    _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_pumping_stop))
                }
        }
    }

    fun onManualFieldChange(transform: (ManualEntryState) -> ManualEntryState) {
        val current = _uiState.value.manual ?: return
        _uiState.value = _uiState.value.copy(manual = transform(current))
    }

    fun onManualSave() {
        val manual = _uiState.value.manual ?: return
        if (manual.isSaving) return
        val volume = manual.volumeMl.toIntOrNull()
        val validationError = when {
            volume == null || volume <= 0 -> appContext.getString(R.string.error_volume_required)
            !manual.endTime.isAfter(manual.startTime) -> appContext.getString(R.string.error_end_after_start)
            else -> null
        }
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(manual = manual.copy(validationError = validationError))
            return
        }
        _uiState.value = _uiState.value.copy(manual = manual.copy(isSaving = true, validationError = null))
        viewModelScope.launch {
            runCatching {
                saveManual(
                    startTime = manual.startTime,
                    endTime = manual.endTime,
                    breast = manual.breast,
                    volumeMl = volume!!,
                    notes = manual.notes.ifBlank { null },
                )
            }.onSuccess { saved ->
                val nowInstant = now()
                _uiState.value = _uiState.value.copy(
                    manual = ManualEntryState(
                        startTime = nowInstant.minusSeconds(15 * 60),
                        endTime = nowInstant,
                    ),
                )
                openBagPrompt(
                    sessionId = saved.id,
                    volumeMl = volume!!,
                    collectionDate = saved.endTime!!,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    manual = manual.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save)),
                )
            }
        }
    }

    private fun openBagPrompt(sessionId: Long, volumeMl: Int, collectionDate: Instant) {
        _uiState.value = _uiState.value.copy(
            bagPrompt = BagPromptState(
                sessionId = sessionId,
                collectionDate = collectionDate,
                volumeMl = volumeMl,
            ),
        )
    }

    fun onBagPromptFieldChange(transform: (BagPromptState) -> BagPromptState) {
        val current = _uiState.value.bagPrompt ?: return
        _uiState.value = _uiState.value.copy(bagPrompt = transform(current))
    }

    fun onBagPromptSkip() {
        _uiState.value = _uiState.value.copy(bagPrompt = null)
    }

    fun onBagPromptConfirm() {
        val prompt = _uiState.value.bagPrompt ?: return
        if (prompt.isSaving) return
        if (prompt.volumeMl <= 0) {
            _uiState.value = _uiState.value.copy(
                bagPrompt = prompt.copy(volumeError = appContext.getString(R.string.error_volume_greater_than_zero)),
            )
            return
        }
        _uiState.value = _uiState.value.copy(bagPrompt = prompt.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                addBag(
                    collectionDate = prompt.collectionDate,
                    volumeMl = prompt.volumeMl,
                    sourceSessionId = prompt.sessionId,
                    notes = prompt.notes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(bagPrompt = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    bagPrompt = prompt.copy(isSaving = false, volumeError = appContext.getString(R.string.error_pumping_save_bag)),
                )
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
