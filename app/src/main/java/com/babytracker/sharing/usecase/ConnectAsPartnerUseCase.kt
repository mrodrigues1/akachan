package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import javax.inject.Inject

class ConnectAsPartnerUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(code: ShareCode) {
        require(ShareCode.isValid(code.value)) { "Invalid share code format" }
        val uid = service.signInAnonymously()
        check(service.isShareCodeValid(code.value)) { "Share code not found" }
        service.registerPartner(code.value, uid)
        settingsRepository.setShareCode(code.value)
        settingsRepository.setAppMode(AppMode.PARTNER)
    }
}
