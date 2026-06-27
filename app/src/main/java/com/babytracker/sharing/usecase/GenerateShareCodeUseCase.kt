package com.babytracker.sharing.usecase

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class GenerateShareCodeUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val sources: SnapshotSources,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) {
    suspend operator fun invoke() {
        val uid = service.signInAnonymously()
        val code = generateUniqueCode()
        service.createShareDocument(code.value, uid)
        service.syncFullSnapshot(
            code.value,
            buildShareSnapshot(sources, sleepSettingsRepository, appContext, now()),
        )
        settingsRepository.setShareCode(code.value)
        settingsRepository.setAppMode(AppMode.PRIMARY)
    }

    private suspend fun generateUniqueCode(): ShareCode {
        var code: ShareCode
        do {
            code = generateCode()
        } while (service.isShareCodeValid(code.value))
        return code
    }

    private fun generateCode(): ShareCode {
        val chars = ('A'..'Z') + ('0'..'9')
        return ShareCode((1..CODE_LENGTH).map { chars.random() }.joinToString(""))
    }

    companion object {
        private const val CODE_LENGTH = 8
    }
}
