package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.Reconciled
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeSleepHistory
import com.babytracker.sharing.domain.model.reconcilePendingOps
import com.babytracker.sharing.domain.model.sleepHistoryReflected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import java.time.Instant
import javax.inject.Inject

/**
 * Mirrors [ObservePartnerFeedHistoryUseCase] for sleep over the LIVE snapshot stream: reconciles the
 * partner's own pending ops (retaining an op deleted before its snapshot update) before
 * [mergeSleepHistory], which overlays the partner's own START/STOP/UPDATE ops so a session the partner
 * just started or ended shows immediately, ahead of the primary's re-published snapshot.
 */
class ObservePartnerSleepHistoryUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotRecords: Flow<List<SleepSnapshot>>): Flow<MergedSleepHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return snapshotRecords.map { records -> mergeSleepHistory(records, emptyList(), now().toEpochMilli()) }
        }
        return flow {
            val uid = service.signInAnonymously()
            emitAll(
                combine(snapshotRecords, service.observeSleepOps(code.value, authorUid = uid)) { records, ops ->
                    records to ops
                }.scan(ReconcileState()) { state, (records, liveOps) ->
                    val reconciled = reconcilePendingOps(
                        isReflected = { sleepHistoryReflected(it, records) },
                        liveOps = liveOps,
                        tracked = state.reconciled.nextTracked,
                        nowMs = now().toEpochMilli(),
                    )
                    ReconcileState(records, reconciled)
                }.drop(1).map { state ->
                    mergeSleepHistory(state.records, state.reconciled.effectiveOps, now().toEpochMilli())
                },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) throw revoked
            throw PartnerDataFetchException("Could not load sleep history", error)
        }
    }

    private data class ReconcileState(
        val records: List<SleepSnapshot> = emptyList(),
        val reconciled: Reconciled<SleepOp> = Reconciled(emptyList(), emptyList()),
    )
}
