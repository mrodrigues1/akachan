package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.RhythmBlock
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.feedInstantsSince
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend operator fun invoke(range: TrendRange): List<DayRhythm> = withContext(Dispatchers.Default) {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val sleepRecords = sleepRepository.getCompletedRecordsSince(start)
            .filter { it.endTime != null && !it.startTime.isAfter(now) }

        // Bucket each sleep into every local day its [start, end] span overlaps so the per-day loop
        // below scans only candidate records. Was O(days x records): every day rescanned the whole
        // list. The clip guard inside still drops boundary non-overlaps, so output is identical.
        val sleepByDate = HashMap<LocalDate, MutableList<SleepRecord>>()
        for (record in sleepRecords) {
            val recordEnd = record.endTime ?: continue
            var day = record.startTime.atZone(zone).toLocalDate()
            val endDay = recordEnd.atZone(zone).toLocalDate()
            while (!day.isAfter(endDay)) {
                sleepByDate.getOrPut(day) { mutableListOf() }.add(record)
                day = day.plusDays(1)
            }
        }

        val breastByDate = breastfeedingRepository.feedInstantsSince(start, now)
            .groupBy { it.atZone(zone).toLocalDate() }
        val bottleByDate = bottleFeedRepository.feedInstantsSince(start, now)
            .groupBy { it.atZone(zone).toLocalDate() }

        trendWindowDates(today, range.days).map { date ->
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

            val blocks = sleepByDate[date].orEmpty().mapNotNull { record ->
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
