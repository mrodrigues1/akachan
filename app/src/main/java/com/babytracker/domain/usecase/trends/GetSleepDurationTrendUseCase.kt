package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Sums completed sleep hours per local day (by start day), split night vs nap, over [range]. */
class GetSleepDurationTrendUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailySleepDuration> = withContext(Dispatchers.Default) {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val byDate = sleepRepository.getCompletedRecordsSince(start)
            // Exclude future-dated entries (SaveSleepEntryUseCase only checks end > start), so a
            // sleep starting later today can never inflate today's bar.
            .filter { !it.startTime.isAfter(now) }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }

        trendWindowDates(today, range.days).map { date ->
            val dayRecords = byDate[date].orEmpty()
            fun hoursOf(type: SleepType) = dayRecords
                .filter { it.sleepType == type }
                .sumOf { (it.duration?.toMinutes() ?: 0L) / 60.0 }
            DailySleepDuration(
                date = date,
                nightHours = hoursOf(SleepType.NIGHT_SLEEP),
                napHours = hoursOf(SleepType.NAP),
            )
        }
    }
}
