package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

        val sessionInstants = breastfeedingRepository
            .getCompletedSessionsBetween(start, now) // already capped at `now`
            .map { it.startTime }
        val bottleInstants = bottleFeedRepository
            .getSince(start)
            .first()
            .map { it.timestamp }
            .filter { !it.isAfter(now) } // cap future-dated bottle feeds, matching the session cap

        val countByDate = (sessionInstants + bottleInstants)
            .groupingBy { it.atZone(zone).toLocalDate() }
            .eachCount()

        trendWindowDates(today, range.days).map { date ->
            DailyFeedingCount(date, countByDate[date] ?: 0)
        }
    }
}
