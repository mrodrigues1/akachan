package com.babytracker.sharing.usecase

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.ShareCode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Resolves the active share code, signs in anonymously and submits the [FeedOp]
 * built from the resolved author uid, applying the partner-access error handling
 * shared by the log/edit/delete partner feed use cases.
 *
 * @throws PartnerAccessRevokedException when the write is rejected because access
 *   was revoked; partner state is cleared before throwing.
 * @throws PartnerDataFetchException for any other Firestore failure while queueing.
 */
class SubmitFeedOpUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    suspend operator fun invoke(buildOp: (authorUid: String) -> FeedOp) {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = service.signInAnonymously()
        try {
            service.writeFeedOp(
                code.value,
                buildOp(uid),
                onFailure = { error ->
                    error.clearPartnerStateIfRevokedLater(settingsRepository, code, applicationScope)
                },
            )
        } catch (error: FirebaseFirestoreException) {
            throw error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
                ?: PartnerDataFetchException("Could not queue feed write", error)
        }
    }
}

internal fun requireValidFeedInput(
    volumeMl: Int,
    timestamp: Instant,
    now: Instant,
) {
    require(volumeMl > 0) { "Volume must be greater than 0" }
    require(!timestamp.isAfter(now)) { "Feed time cannot be in the future" }
}
