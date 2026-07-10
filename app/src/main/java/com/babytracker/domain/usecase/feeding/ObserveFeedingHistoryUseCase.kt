package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Windowed slice of the merged feeding history, newest first. */
data class FeedingHistoryWindow(
    val entries: List<FeedEntry> = emptyList(),
    val hasMore: Boolean = false,
)

class ObserveFeedingHistoryUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
) {
    /**
     * Merges the newest [limit] entries across both feed sources. Each source is queried one row
     * past the limit, so a merged size beyond [limit] proves more rows exist without a count query.
     */
    operator fun invoke(limit: Int): Flow<FeedingHistoryWindow> =
        combine(
            breastfeedingRepository.getRecentSessionsFlow(limit + 1),
            bottleFeedRepository.getRecentFlow(limit + 1),
        ) { sessions, bottles ->
            val entries = sessions.map { FeedEntry.Breastfeeding(it) } +
                bottles.map { FeedEntry.Bottle(it) }
            val sorted = entries.sortedByDescending { it.timestamp }
            FeedingHistoryWindow(
                entries = sorted.take(limit),
                hasMore = sorted.size > limit,
            )
        }
}
