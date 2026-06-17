package com.babytracker.domain.usecase.trends

import com.babytracker.domain.trends.DailyFeedVsSleep
import com.babytracker.domain.trends.TrendRange
import javax.inject.Inject

/**
 * Pairs daily feed count with total sleep hours over [range] for the Feeds-vs-Sleep overlay.
 *
 * Composes the two existing trend use cases instead of re-querying repositories: both emit one
 * entry per day in the same [com.babytracker.domain.trends.trendWindowDates] order, so they are
 * index-aligned and can be zipped directly. No duplicated day-bucketing logic.
 */
class GetFeedVsSleepTrendUseCase @Inject constructor(
    private val getFeedingFrequencyTrend: GetFeedingFrequencyTrendUseCase,
    private val getSleepDurationTrend: GetSleepDurationTrendUseCase,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedVsSleep> {
        val feeds = getFeedingFrequencyTrend(range)
        val sleep = getSleepDurationTrend(range)
        return feeds.zip(sleep) { feed, sleepDay ->
            DailyFeedVsSleep(
                date = feed.date,
                feedCount = feed.count,
                sleepHours = sleepDay.totalHours,
            )
        }
    }
}
