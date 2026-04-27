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

class GenerateShareCodeUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val babyRepository: BabyRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
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
        val baby = babyRepository.getBabyProfile().first()
        val sessions = breastfeedingRepository.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sleepRepository.getRecentRecords(SYNC_LIMIT)
        return ShareSnapshot(
            lastSyncAt = Instant.now(),
            baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
            sessions = sessions.map { it.toSnapshot() },
            sleepRecords = sleepRecords.map { it.toSnapshot() },
        )
    }

    companion object {
        private const val CODE_LENGTH = 8
        private const val SYNC_LIMIT = 20
    }
}
