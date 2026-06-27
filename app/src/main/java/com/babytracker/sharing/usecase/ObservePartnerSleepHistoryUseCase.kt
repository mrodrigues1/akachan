package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeSleepHistory
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Mirrors [ObservePartnerFeedHistoryUseCase] for sleep: maps the snapshot's sleep records overlaid
 * with the partner's own pending edit ops for optimistic display. The caller owns snapshot refresh.
 */
class ObservePartnerSleepHistoryUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotRecords: List<SleepSnapshot>): Flow<MergedSleepHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        // Debug offline partner mode: serve the seeded records with no pending ops instead of
        // hitting Firebase (mirrors FetchPartnerDataUseCase's placeholder-code seam).
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return flowOf(mergeSleepHistory(snapshotRecords, emptyList(), now().toEpochMilli()))
        }
        // signInAnonymously() lives inside the flow so a network/auth failure is routed through
        // .catch instead of crashing the caller — the suspend prelude is not covered otherwise.
        return flow {
            val uid = sharingRepository.signInAnonymously()
            emitAll(
                sharingRepository.observeOwnSleepOps(code, uid)
                    .map { ops -> mergeSleepHistory(snapshotRecords, ops, now().toEpochMilli()) },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) {
                throw revoked
            }
            throw PartnerDataFetchException("Could not load sleep history", error)
        }
    }
}
