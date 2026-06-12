package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.di.ApplicationScope
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class LogPartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        selectedBag: MilkBagSnapshot?,
        notes: String?,
    ): String {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        val entryClientId = UUID.randomUUID().toString()
        try {
            sharingRepository.writeFeedOp(
                code,
                FeedOp(
                    opId = UUID.randomUUID().toString(),
                    action = FeedOpAction.CREATE,
                    entryClientId = entryClientId,
                    authorUid = uid,
                    createdAtMs = now().toEpochMilli(),
                    timestampMs = timestamp.toEpochMilli(),
                    volumeMl = volumeMl,
                    type = type.name,
                    notes = notes,
                    consumedBagId = selectedBag?.id,
                ),
                onFailure = { error ->
                    error.clearPartnerStateIfRevokedLater(settingsRepository, code, applicationScope)
                },
            )
        } catch (error: FirebaseFirestoreException) {
            throw error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
                ?: PartnerDataFetchException("Could not queue feed write", error)
        }
        return entryClientId
    }
}
