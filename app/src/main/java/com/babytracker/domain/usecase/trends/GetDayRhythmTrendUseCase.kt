package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.RhythmBlock
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds a per-day 24h timeline (sleep blocks + feed marks) over [range] for the rhythm strip.
 *
 * Unlike the other sleep trend (which attributes a whole sleep to its start day), this **splits a
 * midnight-crossing sleep across day rows**: each record is clipped to every day window it
 * overlaps, so a 19:00→06:00 sleep yields a tail block on day N and a head block on day N+1.
 * Endpoints are stored as fractions of the *actual* day length, so DST-short/long days stay honest.
 */
class GetDayRhythmTrendUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DayRhythm> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val sleepRecords = sleepRepository.getCompletedRecordsSince(start)
            .filter { it.endTime != null && !it.startTime.isAfter(now) }

        val breastByDate = breastfeedingRepository
            .getCompletedSessionsBetween(start, now)
            .map { it.startTime }
            .groupBy { it.atZone(zone).toLocalDate() }
        val bottleByDate = bottleFeedRepository
            .getSince(start)
            .first()
            .map { it.timestamp }
            .filter { !it.isAfter(now) }
            .groupBy { it.atZone(zone).toLocalDate() }

        return trendWindowDates(today, range.days).map { date ->
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

            val blocks = sleepRecords.mapNotNull { record ->
                val recordEnd = record.endTime ?: return@mapNotNull null
                val clipStart = maxOf(record.startTime, dayStart)
                val clipEnd = minOf(recordEnd, dayEnd)
                if (!clipStart.isBefore(clipEnd)) return@mapNotNull null // no overlap with this day
                RhythmBlock(
                    startFraction = fractionOfDay(clipStart, dayStart, dayEnd),
                    endFraction = fractionOfDay(clipEnd, dayStart, dayEnd),
                    isNight = record.sleepType == SleepType.NIGHT_SLEEP,
                )
            }

            val breastMarks = breastByDate[date].orEmpty()
                .map { fractionOfDay(it, dayStart, dayEnd) }
                .sorted()
            val bottleMarks = bottleByDate[date].orEmpty()
                .map { fractionOfDay(it, dayStart, dayEnd) }
                .sorted()

            DayRhythm(
                date = date,
                sleepBlocks = blocks,
                breastFeedMarks = breastMarks,
                bottleFeedMarks = bottleMarks,
            )
        }
    }

    private fun fractionOfDay(instant: Instant, dayStart: Instant, dayEnd: Instant): Float {
        val total = Duration.between(dayStart, dayEnd).seconds.toDouble()
        val elapsed = Duration.between(dayStart, instant).seconds.toDouble()
        return (elapsed / total).toFloat().coerceIn(0f, 1f)
    }
}
