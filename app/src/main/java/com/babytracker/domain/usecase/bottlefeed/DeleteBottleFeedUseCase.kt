package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import javax.inject.Inject

class DeleteBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
) {
    suspend operator fun invoke(feed: BottleFeed) {
        repository.delete(feed)
    }
}
