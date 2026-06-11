package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class GenerateShareCodeUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: SnapshotSources,
    private val now: () -> Instant,
) {
    suspend operator fun invoke() {
        val uid = sharingRepository.signInAnonymously()
        val code = generateUniqueCode()
        sharingRepository.createShareDocument(code, uid)
        sharingRepository.syncFullSnapshot(code, buildSnapshot())
        settingsRepository.setShareCode(code.value)
        settingsRepository.setAppMode(AppMode.PRIMARY)
    }

    private suspend fun generateUniqueCode(): ShareCode {
        var code: ShareCode
        do {
            code = generateCode()
        } while (sharingRepository.isShareCodeValid(code))
        return code
    }

    private fun generateCode(): ShareCode {
        val chars = ('A'..'Z') + ('0'..'9')
        return ShareCode((1..CODE_LENGTH).map { chars.random() }.joinToString(""))
    }

    private suspend fun buildSnapshot(): ShareSnapshot {
        val baby = sources.baby.getBabyProfile().first()
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        val summary = sources.inventory.currentSummary()
        val bottleFeeds = sources.bottleFeeds.getAll().first().take(SYNC_LIMIT)
        val timestamp = now()
        return ShareSnapshot(
            lastSyncAt = timestamp,
            baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
            sessions = sessions.map { it.toSnapshot() },
            sleepRecords = sleepRecords.map { it.toSnapshot() },
            bottleFeeds = bottleFeeds.map { it.toSnapshot() },
            inventoryTotalMl = summary.totalMl,
            inventoryBagCount = summary.bagCount,
            inventoryUpdatedAt = timestamp.toEpochMilli(),
        )
    }

    companion object {
        private const val CODE_LENGTH = 8
        private const val SYNC_LIMIT = 20
    }
}
