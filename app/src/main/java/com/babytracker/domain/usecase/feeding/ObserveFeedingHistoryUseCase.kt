package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveFeedingHistoryUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
) {
    operator fun invoke(): Flow<List<FeedEntry>> =
        combine(breastfeedingRepository.getAllSessions(), bottleFeedRepository.getAll()) { sessions, bottles ->
            val entries = sessions.map { FeedEntry.Breastfeeding(it) } +
                bottles.map { FeedEntry.Bottle(it) }
            entries.sortedByDescending { it.timestamp }
        }
}
