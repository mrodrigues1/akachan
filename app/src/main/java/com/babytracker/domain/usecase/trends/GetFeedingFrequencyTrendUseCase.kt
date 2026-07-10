package com.babytracker.domain.usecase.trends

import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Counts feeds (completed breastfeeding sessions + bottle feeds) per local day over [range]. */
class GetFeedingFrequencyTrendUseCase @Inject constructor(
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange, feeds: TrendFeedInstants): List<DailyFeedingCount> =
        withContext(Dispatchers.Default) {
            val zone = clock.zone
            val today = LocalDate.now(clock)

            val countByDate = feeds.all
                .groupingBy { it.atZone(zone).toLocalDate() }
                .eachCount()

            trendWindowDates(today, range.days).map { date ->
                DailyFeedingCount(date, countByDate[date] ?: 0)
            }
        }
}
