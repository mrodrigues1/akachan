package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBottleFeedsUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
) {
    operator fun invoke(): Flow<List<BottleFeed>> = repository.getAll()
}
