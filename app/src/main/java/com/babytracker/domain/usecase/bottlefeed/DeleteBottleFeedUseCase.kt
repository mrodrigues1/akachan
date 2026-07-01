package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import javax.inject.Inject

class DeleteBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val syncedWrite: SyncedWrite,
) {
    suspend operator fun invoke(feed: BottleFeed) {
        syncedWrite(SyncType.BOTTLE_FEEDS_AND_INVENTORY) {
            check(repository.deleteWithInventoryRestore(feed)) { "Bottle feed no longer exists" }
        }
    }
}
