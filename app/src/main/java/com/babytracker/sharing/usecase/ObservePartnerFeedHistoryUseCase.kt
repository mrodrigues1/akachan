package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.Reconciled
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.feedOpReflected
import com.babytracker.sharing.domain.model.mergeFeedHistory
import com.babytracker.sharing.domain.model.reconcilePendingOps
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
 * The partner's feed history over the LIVE snapshot stream: each (snapshot, own-ops) emission is run
 * through [reconcilePendingOps] (so an op deleted before its snapshot update keeps overlaying until the
 * snapshot converges or the TTL fires) and then the existing [mergeFeedHistory].
 */
class ObservePartnerFeedHistoryUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotFeeds: Flow<List<BottleFeedSnapshot>>): Flow<MergedFeedHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        // Debug offline partner mode: merge the seeded feeds with no pending ops.
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return snapshotFeeds.map { feeds -> mergeFeedHistory(feeds, emptyList()) }
        }
        return flow {
            val uid = service.signInAnonymously()
            emitAll(
                combine(snapshotFeeds, service.observeFeedOps(code.value, authorUid = uid)) { feeds, ops ->
                    feeds to ops
                }.scan(ReconcileState()) { state, (feeds, liveOps) ->
                    val reconciled = reconcilePendingOps(
                        isReflected = { feedOpReflected(it, feeds) },
                        liveOps = liveOps,
                        tracked = state.reconciled.nextTracked,
                        nowMs = now().toEpochMilli(),
                    )
                    ReconcileState(feeds, reconciled)
                }.drop(1).map { state ->
                    mergeFeedHistory(state.feeds, state.reconciled.effectiveOps)
                },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) throw revoked
            throw PartnerDataFetchException("Could not load feed history", error)
        }
    }

    private data class ReconcileState(
        val feeds: List<BottleFeedSnapshot> = emptyList(),
        val reconciled: Reconciled<FeedOp> = Reconciled(emptyList(), emptyList()),
    )
}
