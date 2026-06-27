package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.ShareCode
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RevokePartnerUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(partnerUid: String) {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        service.revokePartner(code.value, partnerUid)
    }
}
