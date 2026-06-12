package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.di.ApplicationScope
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class DeletePartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(entry: BottleFeedSnapshot) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be deleted" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }

        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        try {
            sharingRepository.writeFeedOp(
                code,
                FeedOp(
                    opId = UUID.randomUUID().toString(),
                    action = FeedOpAction.DELETE,
                    entryClientId = entry.clientId,
                    authorUid = uid,
                    createdAtMs = now().toEpochMilli(),
                ),
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
