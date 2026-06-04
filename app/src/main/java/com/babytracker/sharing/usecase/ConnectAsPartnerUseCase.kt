package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import javax.inject.Inject

class ConnectAsPartnerUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(code: ShareCode) {
        require(ShareCode.isValid(code.value)) { "Invalid share code format" }
        val uid = sharingRepository.signInAnonymously()
        check(sharingRepository.isShareCodeValid(code)) { "Share code not found" }
        sharingRepository.registerPartner(code, uid)
        settingsRepository.setShareCode(code.value)
        settingsRepository.setAppMode(AppMode.PARTNER)
    }
}
