package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class EditPartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        entry: BottleFeedSnapshot,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        notes: String?,
    ) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be edited" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        sharingRepository.writeFeedOp(
            code,
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.UPDATE,
                entryClientId = entry.clientId,
                authorUid = uid,
                createdAtMs = now().toEpochMilli(),
                timestampMs = timestamp.toEpochMilli(),
                volumeMl = volumeMl,
                type = type.name,
                notes = notes,
            ),
        )
    }
}
