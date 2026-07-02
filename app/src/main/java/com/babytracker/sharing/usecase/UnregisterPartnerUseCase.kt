package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes this partner's own `shares/{code}/partners/{uid}` registration so the owner's
 * partner list stops showing a device that has disconnected.
 */
class UnregisterPartnerUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        val code = settingsRepository.getShareCode().first() ?: return
        service.revokePartner(code, service.signInAnonymously())
    }
}
