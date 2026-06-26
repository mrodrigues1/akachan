package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeSleepHistory
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        val uid = sharingRepository.signInAnonymously()
        return sharingRepository.observeOwnSleepOps(code, uid)
            .map { ops -> mergeSleepHistory(snapshotRecords, ops, now().toEpochMilli()) }
            .catch { error ->
                val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
                if (revoked != null) {
                    throw revoked
                }
                throw PartnerDataFetchException("Could not load sleep history", error)
            }
    }
}
