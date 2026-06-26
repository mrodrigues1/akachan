package com.babytracker.sharing.usecase

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves the active share code, signs in anonymously and submits the [SleepOp] built from the
 * resolved author uid, applying the same partner-access error handling as [SubmitFeedOpUseCase].
 * Fire-and-forget: Firestore offline persistence queues the write (never awaited).
 *
 * @throws PartnerAccessRevokedException when the write is rejected because access was revoked;
 *   partner state is cleared before throwing.
 * @throws PartnerDataFetchException for any other Firestore failure while queueing.
 */
class SubmitSleepOpUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    suspend operator fun invoke(buildOp: (authorUid: String) -> SleepOp) {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        try {
            sharingRepository.writeSleepOp(
                code,
                buildOp(uid),
                onFailure = { error ->
                    error.clearPartnerStateIfRevokedLater(settingsRepository, code, applicationScope)
                },
            )
        } catch (error: FirebaseFirestoreException) {
            throw error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
                ?: PartnerDataFetchException("Could not queue sleep write", error)
        }
    }
}
