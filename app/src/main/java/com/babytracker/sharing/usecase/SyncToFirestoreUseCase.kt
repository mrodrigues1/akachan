package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
import com.babytracker.sharing.domain.model.toSnapshotFields
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SyncToFirestoreUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: SnapshotSources,
    private val now: () -> Instant = Instant::now,
) {
    enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY, BOTTLE_FEEDS }

    suspend operator fun invoke(syncType: SyncType = SyncType.FULL) {
        if (settingsRepository.getAppMode().first() != AppMode.PRIMARY) return
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        when (syncType) {
            SyncType.FULL -> syncFull(code)
            SyncType.SESSIONS -> syncSessions(code)
            SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
            SyncType.BABY -> syncBaby(code)
            SyncType.INVENTORY -> syncInventory(code)
            SyncType.BOTTLE_FEEDS -> syncBottleFeeds(code)
        }
    }

    private suspend fun syncFull(code: ShareCode) {
        val baby = sources.baby.getBabyProfile().first()
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        val summary = sources.inventory.currentSummary()
        val bottleFeeds = sources.bottleFeeds.getAll().first().take(SYNC_LIMIT)
        val updatedAtMs = now().toEpochMilli()
        val prediction = if (settingsRepository.getPredictiveSleepEnabled().first()) {
            sources.predictSleepWindow().first().toSnapshot(updatedAtMs)
        } else {
            null
        }
        sharingRepository.syncFullSnapshot(
            code,
            ShareSnapshot(
                lastSyncAt = Instant.ofEpochMilli(updatedAtMs),
                baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
                sessions = sessions.map { it.toSnapshot() },
                sleepRecords = sleepRecords.map { it.toSnapshot() },
                bottleFeeds = bottleFeeds.map { it.toSnapshot() },
                inventoryTotalMl = summary.totalMl,
                inventoryBagCount = summary.bagCount,
                inventoryUpdatedAt = updatedAtMs,
                sleepPrediction = prediction,
            ),
        )
    }

    private suspend fun syncSessions(code: ShareCode) {
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        sharingRepository.syncSessions(code, sessions.map { it.toSnapshot() })
    }

    private suspend fun syncSleepRecords(code: ShareCode) {
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        sharingRepository.syncSleepRecords(code, sleepRecords.map { it.toSnapshot() })
    }

    private suspend fun syncBaby(code: ShareCode) {
        val baby = sources.baby.getBabyProfile().first()
        sharingRepository.syncBaby(code, baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()))
    }

    private suspend fun syncInventory(code: ShareCode) {
        val summary = sources.inventory.currentSummary()
        sharingRepository.syncInventory(
            code,
            summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
        )
    }

    private suspend fun syncBottleFeeds(code: ShareCode) {
        val feeds = sources.bottleFeeds.getAll().first().take(SYNC_LIMIT)
        sharingRepository.syncBottleFeeds(code, feeds.map { it.toSnapshot() })
    }

    companion object {
        private const val SYNC_LIMIT = 20
    }
}
