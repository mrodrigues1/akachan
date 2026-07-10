package com.babytracker.ui.partner

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerActiveSleepUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Editor state for a partner-owned sleep session (active or completed). */
data class PartnerSleepEditorState(
    val clientId: String,
    val sleepType: SleepType,
    val startTime: Instant,
    val endTime: Instant?,
    val notes: String,
    val isSaving: Boolean = false,
    val validationError: String? = null,
)

data class PartnerSleepUiState(
    val active: SleepSnapshot? = null,
    // Sleep records overlaid with the partner's own pending START/STOP ops so a session the partner just
    // started/ended shows on the dashboard ahead of the primary's re-published snapshot. lastCompleted =
    // most recent ended session (tile); mostRecent = most recent of any (the timeline's "Last sleep").
    val lastCompleted: SleepSnapshot? = null,
    val mostRecent: SleepSnapshot? = null,
    val stopping: Boolean = false,
    // The active session was started by the partner and can be edited from the dashboard.
    val canEditActive: Boolean = false,
    val isBusy: Boolean = false,
    val editor: PartnerSleepEditorState? = null,
    val accessRevoked: Boolean = false,
    // Transient: set when a Start/Stop tap fails; cleared on the next start/stop attempt (mirrors
    // the editor's validationError convention below).
    val startStopError: String? = null,
)

/**
 * Partner-side sleep controls for the dashboard: start a nap / night sleep, stop the active session
 * (shared — whoever started it), and edit the partner's OWN sessions. The reconcile/merge pipeline that
 * resolves the active session and history overlays lives in [ObservePartnerActiveSleepUseCase]; this
 * view model just collects it into [uiState] and owns the UI-only concerns (busy/error/editor/revoked)
 * and the start/stop/edit action handlers.
 */
@HiltViewModel
class PartnerSleepViewModel @Inject constructor(
    private val startSleep: StartPartnerSleepUseCase,
    private val stopSleep: StopPartnerSleepUseCase,
    private val updateSleep: UpdatePartnerSleepUseCase,
    private val observeActiveSleep: ObservePartnerActiveSleepUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerSleepUiState())
    val uiState: StateFlow<PartnerSleepUiState> = _uiState.asStateFlow()

    // The authoritative snapshot sleep records, pushed in by the dashboard so the active merge stays
    // current. Feeds the use case's reconcile stream; the history screen never pushes (edit-only there).
    private val snapshotRecords = MutableStateFlow<List<SleepSnapshot>>(emptyList())

    init {
        // Subscription-scoped: run the reconcile collection only while uiState has a collector, so a
        // backstacked dashboard releases its share of the shared sleep-op listener (which then detaches
        // once no view model is subscribed). collectLatest cancels the inner collect when the last
        // collector leaves and restarts it when one returns.
        viewModelScope.launch {
            _uiState.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { hasSubscribers ->
                    if (!hasSubscribers) return@collectLatest
                    runCatching {
                        observeActiveSleep(snapshotRecords).collect { active ->
                            _uiState.update {
                                it.copy(
                                    active = active.active,
                                    lastCompleted = active.lastCompleted,
                                    mostRecent = active.mostRecent,
                                    stopping = active.stopping,
                                    canEditActive = active.canEditActive,
                                )
                            }
                        }
                    }.onFailure { t ->
                        // Sign-in itself failed (the shared stream retries listener errors on its own) —
                        // the pending-ops overlay never starts. No UI surface today; log so it isn't invisible.
                        Log.w(TAG, "sign-in for own sleep op listener failed; pending-ops overlay will not start", t)
                    }
                }
        }
    }

    /** Fed the latest snapshot sleep records by the dashboard so the active merge stays current. */
    fun onSleepRecordsAvailable(records: List<SleepSnapshot>) {
        snapshotRecords.value = records
    }

    fun onStartNap() = start(SleepType.NAP)

    fun onStartNightSleep() = start(SleepType.NIGHT_SLEEP)

    private fun start(type: SleepType) {
        if (_uiState.value.isBusy || _uiState.value.active != null) return
        submit { startSleep(type) }
    }

    fun onStop() {
        val clientId = _uiState.value.active?.clientId
        if (_uiState.value.isBusy || clientId.isNullOrEmpty()) return
        submit { stopSleep(clientId) }
    }

    private fun submit(block: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true, startStopError = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(isBusy = false) } }
                .onFailure { handleFailure(it) }
        }
    }

    private fun handleFailure(t: Throwable) {
        if (t is PartnerAccessRevokedException) {
            _uiState.update { it.copy(isBusy = false, accessRevoked = true) }
            return
        }
        Log.w(TAG, "partner start/stop sleep failed", t)
        _uiState.update {
            it.copy(isBusy = false, startStopError = appContext.getString(R.string.error_partner_sleep_action_failed))
        }
    }

    // --- editing (own sessions only) ---

    fun onEditActive() {
        _uiState.value.active?.let { startEditing(it) }
    }

    /** Opens the editor for a partner-owned session (active or a completed one shown on the dashboard). */
    fun startEditing(session: SleepSnapshot) {
        if (session.startedBy != SleepAuthor.PARTNER || session.clientId.isEmpty()) return
        _uiState.update {
            it.copy(
                editor = PartnerSleepEditorState(
                    clientId = session.clientId,
                    sleepType = session.sleepType,
                    startTime = Instant.ofEpochMilli(session.startTime),
                    endTime = session.endTime?.let(Instant::ofEpochMilli),
                    notes = session.notes.orEmpty(),
                ),
            )
        }
    }

    fun onEditorTypeChange(type: SleepType) = updateEditor { it.copy(sleepType = type) }

    fun onEditorStartChange(startTime: Instant) = updateEditor { it.copy(startTime = startTime, validationError = null) }

    fun onEditorEndChange(endTime: Instant?) = updateEditor { it.copy(endTime = endTime, validationError = null) }

    fun onEditorNotesChange(notes: String) = updateEditor { it.copy(notes = notes) }

    fun onDismissEditor() = _uiState.update { it.copy(editor = null) }

    fun onConfirmEdit() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return
        _uiState.update { it.copy(editor = editor.copy(isSaving = true, validationError = null)) }
        viewModelScope.launch {
            runCatching {
                updateSleep(
                    clientId = editor.clientId,
                    startTime = editor.startTime,
                    endTime = editor.endTime,
                    sleepType = editor.sleepType,
                    notes = editor.notes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.update { it.copy(editor = null) }
            }.onFailure { t ->
                if (t is PartnerAccessRevokedException) {
                    _uiState.update { it.copy(editor = null, accessRevoked = true) }
                } else {
                    updateEditor {
                        it.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save))
                    }
                }
            }
        }
    }

    fun onAccessRevokedHandled() = _uiState.update { it.copy(accessRevoked = false) }

    private inline fun updateEditor(transform: (PartnerSleepEditorState) -> PartnerSleepEditorState) {
        _uiState.update { state -> state.editor?.let { state.copy(editor = transform(it)) } ?: state }
    }

    private companion object {
        const val TAG = "PartnerSleepVM"
    }
}
