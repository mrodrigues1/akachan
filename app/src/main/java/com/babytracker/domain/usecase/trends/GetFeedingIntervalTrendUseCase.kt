package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Average hours between consecutive same-day feeds per local day; null when <2 feeds that day. */
class GetFeedingIntervalTrendUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedingInterval> = withContext(Dispatchers.Default) {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val feedInstants =
            breastfeedingRepository.getCompletedSessionsBetween(start, now).map { it.startTime } +
                bottleFeedRepository.getSince(start).first().map { it.timestamp }
                    .filter { !it.isAfter(now) } // cap future-dated bottle feeds
        val byDate = feedInstants.groupBy { it.atZone(zone).toLocalDate() }

        trendWindowDates(today, range.days).map { date ->
            val sorted = byDate[date].orEmpty().sorted()
            val average = if (sorted.size < 2) {
                null
            } else {
                sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes() / 60.0 }.average()
            }
            DailyFeedingInterval(date, average)
        }
    }
}
