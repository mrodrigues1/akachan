package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.feedInstantsSince
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Counts feeds (completed breastfeeding sessions + bottle feeds) per local day over [range]. */
class GetFeedingFrequencyTrendUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedingCount> = withContext(Dispatchers.Default) {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val feedInstants = breastfeedingRepository.feedInstantsSince(start, now) +
            bottleFeedRepository.feedInstantsSince(start, now)

        val countByDate = feedInstants
            .groupingBy { it.atZone(zone).toLocalDate() }
            .eachCount()

        trendWindowDates(today, range.days).map { date ->
            DailyFeedingCount(date, countByDate[date] ?: 0)
        }
    }
}
