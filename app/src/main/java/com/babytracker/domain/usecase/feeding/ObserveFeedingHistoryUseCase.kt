package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveFeedingHistoryUseCase @Inject constructor(
    private val getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    private val observeBottleFeeds: ObserveBottleFeedsUseCase,
) {
    operator fun invoke(): Flow<List<FeedEntry>> =
        combine(getBreastfeedingHistory(), observeBottleFeeds()) { sessions, bottles ->
            val entries = sessions.map { FeedEntry.Breastfeeding(it) } +
                bottles.map { FeedEntry.Bottle(it) }
            entries.sortedByDescending { it.timestamp }
        }
}
