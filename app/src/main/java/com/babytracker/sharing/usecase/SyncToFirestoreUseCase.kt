package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
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
    private val babyRepository: BabyRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val inventoryRepository: InventoryRepository,
    private val now: () -> Instant,
) {
    enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY }

    suspend operator fun invoke(syncType: SyncType = SyncType.FULL) {
        if (settingsRepository.getAppMode().first() != AppMode.PRIMARY) return
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        when (syncType) {
            SyncType.FULL -> syncFull(code)
            SyncType.SESSIONS -> syncSessions(code)
            SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
            SyncType.BABY -> syncBaby(code)
            SyncType.INVENTORY -> syncInventory(code)
        }
    }

    private suspend fun syncFull(code: ShareCode) {
        val baby = babyRepository.getBabyProfile().first()
        val sessions = breastfeedingRepository.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sleepRepository.getRecentRecords(SYNC_LIMIT)
        val summary = inventoryRepository.currentSummary()
        val updatedAtMs = now().toEpochMilli()
        sharingRepository.syncFullSnapshot(
            code,
            ShareSnapshot(
                lastSyncAt = Instant.ofEpochMilli(updatedAtMs),
                baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
                sessions = sessions.map { it.toSnapshot() },
                sleepRecords = sleepRecords.map { it.toSnapshot() },
                inventoryTotalMl = summary.totalMl,
                inventoryBagCount = summary.bagCount,
                inventoryUpdatedAt = updatedAtMs,
            ),
        )
    }

    private suspend fun syncSessions(code: ShareCode) {
        val sessions = breastfeedingRepository.getRecentSessions(SYNC_LIMIT)
        sharingRepository.syncSessions(code, sessions.map { it.toSnapshot() })
    }

    private suspend fun syncSleepRecords(code: ShareCode) {
        val sleepRecords = sleepRepository.getRecentRecords(SYNC_LIMIT)
        sharingRepository.syncSleepRecords(code, sleepRecords.map { it.toSnapshot() })
    }

    private suspend fun syncBaby(code: ShareCode) {
        val baby = babyRepository.getBabyProfile().first()
        sharingRepository.syncBaby(code, baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()))
    }

    private suspend fun syncInventory(code: ShareCode) {
        val summary = inventoryRepository.currentSummary()
        sharingRepository.syncInventory(
            code,
            summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
        )
    }

    companion object {
        private const val SYNC_LIMIT = 20
    }
}
