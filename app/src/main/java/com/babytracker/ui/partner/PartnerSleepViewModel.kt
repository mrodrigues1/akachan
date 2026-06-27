package com.babytracker.ui.partner

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.toSleepTypeOrNull
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeActiveSleep
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min

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
    val stopping: Boolean = false,
    // The active session was started by the partner and can be edited from the dashboard.
    val canEditActive: Boolean = false,
    val isBusy: Boolean = false,
    val editor: PartnerSleepEditorState? = null,
    val accessRevoked: Boolean = false,
    val errorMessage: String? = null,
    // Bumped when an op the partner submitted is applied by the primary (it disappears from the op
    // listener). The dashboard's snapshot is pull-based, so it must refetch then or the applied
    // change keeps showing as stale (e.g. a stopped session reappears as active).
    val snapshotRefreshTick: Int = 0,
)

/**
 * Partner-side sleep controls for the dashboard: start a nap / night sleep, stop the active session
 * (shared — whoever started it), and edit the partner's OWN sessions. The active session shown is
 * the authoritative snapshot overlaid with the partner's own pending ops (optimistic feedback).
 */
@HiltViewModel
class PartnerSleepViewModel @Inject constructor(
    private val startSleep: StartPartnerSleepUseCase,
    private val stopSleep: StopPartnerSleepUseCase,
    private val updateSleep: UpdatePartnerSleepUseCase,
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerSleepUiState())
    val uiState: StateFlow<PartnerSleepUiState> = _uiState.asStateFlow()

    private var snapshotRecords: List<SleepSnapshot> = emptyList()
    private var pendingOps: List<SleepOp> = emptyList()
    private var trackedOpIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            val codeValue = settingsRepository.getShareCode().first() ?: return@launch
            runCatching {
                val uid = sharingRepository.signInAnonymously()
                sharingRepository.observeOwnSleepOps(ShareCode(codeValue), uid)
                    .retryWhen { cause, attempt ->
                        // A transient Firestore listener error must not kill the stream for good:
                        // op tracking (and the dashboard refetch it drives) would freeze. Re-subscribe
                        // with capped backoff; trackedOpIds survives so applied-op detection still works.
                        Log.w(TAG, "own sleep op listener error (attempt $attempt); retrying", cause)
                        delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                        true
                    }
                    .collect { ops ->
                    val ids = ops.mapTo(mutableSetOf()) { it.opId }
                    // A previously-pending op vanished -> the primary applied (or dropped) it and has
                    // already re-pushed the snapshot (it pushes before deleting), so the stale
                    // dashboard snapshot must be refetched to reflect the change.
                    val applied = trackedOpIds.any { it !in ids }
                    trackedOpIds = ids
                    pendingOps = ops
                    if (applied) _uiState.update { it.copy(snapshotRefreshTick = it.snapshotRefreshTick + 1) }
                    recomputeActive()
                }
            }
        }
    }

    /** Fed the latest snapshot sleep records by the dashboard so the active merge stays current. */
    fun onSleepRecordsAvailable(records: List<SleepSnapshot>) {
        snapshotRecords = records
        recomputeActive()
    }

    private fun recomputeActive() {
        val snapshotActive = snapshotRecords.firstOrNull { it.endTime == null }
        val merged = mergeActiveSleep(snapshotActive, pendingOps, now().toEpochMilli())
        val session = merged.session
        _uiState.update {
            it.copy(
                active = session,
                stopping = merged.stopping,
                canEditActive = session != null &&
                    session.startedBy == SleepAuthor.PARTNER.name &&
                    session.clientId.isNotEmpty(),
            )
        }
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
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(isBusy = false) } }
                .onFailure { handleFailure(it) }
        }
    }

    private fun handleFailure(t: Throwable) {
        _uiState.update {
            if (t is PartnerAccessRevokedException) {
                it.copy(isBusy = false, accessRevoked = true)
            } else {
                it.copy(isBusy = false, errorMessage = appContext.getString(R.string.error_could_not_save))
            }
        }
    }

    // --- editing (own sessions only) ---

    fun onEditActive() {
        _uiState.value.active?.let { startEditing(it) }
    }

    /** Opens the editor for a partner-owned session (active or a completed one shown on the dashboard). */
    fun startEditing(session: SleepSnapshot) {
        if (session.startedBy != SleepAuthor.PARTNER.name || session.clientId.isEmpty()) return
        _uiState.update {
            it.copy(
                editor = PartnerSleepEditorState(
                    clientId = session.clientId,
                    sleepType = session.sleepType.toSleepTypeOrNull() ?: SleepType.NAP,
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

    fun onErrorHandled() = _uiState.update { it.copy(errorMessage = null) }

    private inline fun updateEditor(transform: (PartnerSleepEditorState) -> PartnerSleepEditorState) {
        _uiState.update { state -> state.editor?.let { state.copy(editor = transform(it)) } ?: state }
    }

    private companion object {
        const val TAG = "PartnerSleepVM"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
    }
}
