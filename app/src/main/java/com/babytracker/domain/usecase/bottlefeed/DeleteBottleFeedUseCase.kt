package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class DeleteBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(feed: BottleFeed) {
        check(repository.deleteWithInventoryRestore(feed)) { "Bottle feed no longer exists" }
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS_AND_INVENTORY) }
    }
}
