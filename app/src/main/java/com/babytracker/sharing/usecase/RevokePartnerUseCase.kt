package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RevokePartnerUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(partnerUid: String) {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        sharingRepository.revokePartner(code, partnerUid)
    }
}
