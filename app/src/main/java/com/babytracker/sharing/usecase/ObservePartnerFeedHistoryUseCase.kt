package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.mergeFeedHistoryWithPendingCount
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        val uid = sharingRepository.signInAnonymously()
        return sharingRepository.observeOwnFeedOps(code, uid)
            .map { ops -> mergeFeedHistoryWithPendingCount(snapshotFeeds, ops) }
            .catch { error ->
                val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
                if (revoked != null) {
                    throw revoked
                }
                throw PartnerDataFetchException("Could not load feed history", error)
            }
    }
}
