package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FetchPartnerDataUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): ShareSnapshot {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        if (!sharingRepository.isPartnerConnected(code, uid)) {
            settingsRepository.clearShareCode()
            settingsRepository.setAppMode(AppMode.NONE)
            error("Partner access revoked")
        }
        return sharingRepository.fetchSnapshot(code)
    }
}
