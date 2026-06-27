package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.mergeFeedHistory
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObservePartnerFeedHistoryUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * The caller owns snapshot refresh. When the pending-op set shrinks, Plan 06 must re-fetch the
     * snapshot before presenting the fallback state, because Plan 04 pushes the updated snapshot
     * before deleting consumed ops.
     */
    suspend operator fun invoke(snapshotFeeds: List<BottleFeedSnapshot>): Flow<MergedFeedHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        // Debug offline partner mode: serve the seeded feeds with no pending ops instead of hitting
        // Firebase (mirrors FetchPartnerDataUseCase's placeholder-code seam).
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return flowOf(mergeFeedHistory(snapshotFeeds, emptyList()))
        }
        // signInAnonymously() lives inside the flow so a network/auth failure is routed through
        // .catch instead of crashing the caller — the suspend prelude is not covered otherwise.
        return flow {
            val uid = sharingRepository.signInAnonymously()
            emitAll(
                sharingRepository.observeOwnFeedOps(code, uid)
                    .map { ops -> mergeFeedHistory(snapshotFeeds, ops) },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) {
                throw revoked
            }
            throw PartnerDataFetchException("Could not load feed history", error)
        }
    }
}
