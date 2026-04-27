package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
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
) {
    enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY }

    suspend operator fun invoke(syncType: SyncType = SyncType.FULL) {
        if (settingsRepository.getAppMode().first() != AppMode.PRIMARY) return
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        when (syncType) {
            SyncType.FULL -> syncFull(code)
            SyncType.SESSIONS -> syncSessions(code)
            SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
            SyncType.BABY -> syncBaby(code)
        }
    }

    private suspend fun syncFull(code: ShareCode) {
        val baby = babyRepository.getBabyProfile().first()
        val sessions = breastfeedingRepository.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sleepRepository.getRecentRecords(SYNC_LIMIT)
        sharingRepository.syncFullSnapshot(
            code,
            ShareSnapshot(
                lastSyncAt = Instant.now(),
                baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
                sessions = sessions.map { it.toSnapshot() },
                sleepRecords = sleepRecords.map { it.toSnapshot() },
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

    companion object {
        private const val SYNC_LIMIT = 20
    }
}
