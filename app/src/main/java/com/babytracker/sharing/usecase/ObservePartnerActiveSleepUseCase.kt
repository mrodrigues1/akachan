package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SharedSleepOpStream
import com.babytracker.sharing.domain.model.Reconciled
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeActiveSleep
import com.babytracker.sharing.domain.model.mergeSleepHistory
import com.babytracker.sharing.domain.model.reconcilePendingOps
import com.babytracker.sharing.domain.model.sleepActiveReflected
import com.babytracker.sharing.domain.model.sleepHistoryReflected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import java.time.Instant
import javax.inject.Inject

/** The partner's dashboard sleep controls state: the active session overlaid with the partner's own
 *  pending ops, plus the most-recently-completed / most-recent entries and the transient stopping flag. */
data class PartnerActiveSleep(
    val active: SleepSnapshot? = null,
    val lastCompleted: SleepSnapshot? = null,
    val mostRecent: SleepSnapshot? = null,
    val stopping: Boolean = false,
    val canEditActive: Boolean = false,
)

/**
 * The active-session sibling of [ObservePartnerSleepHistoryUseCase]: reconciles the partner's own
 * pending ops against the authoritative [snapshotRecords] stream and resolves the optimistic active
 * session (a fresh START surfaces immediately, a pending STOP raises "stopping…") alongside the
 * history-derived last-completed / most-recent entries. Two independent reconcile tracks are threaded
 * because the active and history overlays use different STOP-reflected rules ([sleepActiveReflected]
 * vs [sleepHistoryReflected]); this is the four-mutable-field state machine that previously lived in
 * PartnerSleepViewModel, moved into a scan over the (snapshot, ops) stream.
 *
 * No debug-seed short-circuit and no `.catch`: it mirrors the pre-extraction view model, which fed the
 * op overlay unconditionally, retried listener errors (now the shared stream's job) and surfaced no UI
 * error for the overlay. Sign-in failures propagate to the collector, which logs and skips the overlay.
 */
class ObservePartnerActiveSleepUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val sharedSleepOps: SharedSleepOpStream,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotRecords: Flow<List<SleepSnapshot>>): Flow<PartnerActiveSleep> {
        // No pairing yet: derive from the snapshot alone (no pending-op overlay), matching the
        // pre-extraction view model, which still merged snapshot records when the op listener was off.
        val codeValue = settingsRepository.getShareCode().first()
            ?: return snapshotRecords.map { records -> resolve(records, emptyList(), emptyList()) }

        return flow {
            val uid = service.signInAnonymously()
            emitAll(
                combine(snapshotRecords, sharedSleepOps.observe(codeValue, uid)) { records, ops ->
                    records to ops
                }.scan(ReconcileState()) { state, (records, liveOps) ->
                    ReconcileState(
                        records = records,
                        active = reconcilePendingOps(
                            isReflected = { sleepActiveReflected(it, records) },
                            liveOps = liveOps,
                            tracked = state.active.nextTracked,
                            nowMs = now().toEpochMilli(),
                        ),
                        history = reconcilePendingOps(
                            isReflected = { sleepHistoryReflected(it, records) },
                            liveOps = liveOps,
                            tracked = state.history.nextTracked,
                            nowMs = now().toEpochMilli(),
                        ),
                    )
                }.drop(1).map { state ->
                    resolve(state.records, state.active.effectiveOps, state.history.effectiveOps)
                },
            )
        }
    }

    private fun resolve(
        records: List<SleepSnapshot>,
        activeOps: List<SleepOp>,
        historyOps: List<SleepOp>,
    ): PartnerActiveSleep {
        val nowMs = now().toEpochMilli()
        val snapshotActive = records.firstOrNull { it.endTime == null }
        val merged = mergeActiveSleep(snapshotActive, activeOps, nowMs)
        val session = merged.session
        val historyEntries = mergeSleepHistory(records, historyOps, nowMs).entries
        return PartnerActiveSleep(
            active = session,
            lastCompleted = historyEntries.firstOrNull { it.endTime != null },
            mostRecent = historyEntries.firstOrNull(),
            stopping = merged.stopping,
            canEditActive = session != null &&
                session.startedBy == SleepAuthor.PARTNER &&
                session.clientId.isNotEmpty(),
        )
    }

    private data class ReconcileState(
        val records: List<SleepSnapshot> = emptyList(),
        val active: Reconciled<SleepOp> = Reconciled(emptyList(), emptyList()),
        val history: Reconciled<SleepOp> = Reconciled(emptyList(), emptyList()),
    )
}
