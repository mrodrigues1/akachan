package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.RegressionInfo
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GenerateSleepScheduleUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        baby: Baby
    ): SleepSchedule {
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, LocalDate.now()).toInt()
        val mode = if (ageInWeeks < 16) ScheduleMode.DEMAND_DRIVEN else ScheduleMode.CLOCK_ALIGNED

        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val recentRecords = sleepRepository.getCompletedRecordsSince(sevenDaysAgo)
        val lastFeedSession = breastfeedingRepository.getLastSession()

        val defaultWakeWindows = getDefaultWakeWindows(ageInWeeks)
        val wakeWindowBounds = getWakeWindowBounds(ageInWeeks)

        val personalizationResult = personalizeFromData(
            recentRecords, defaultWakeWindows, wakeWindowBounds, ageInWeeks
        )
        val wakeWindows = personalizationResult.wakeWindows
        val averageNapDuration = personalizationResult.averageNapDuration
        val isPersonalized = personalizationResult.isPersonalized

        val storedWakeTime = settingsRepository.getWakeTime().first()
        val effectiveWakeTime = storedWakeTime ?: LocalTime.of(7, 0)

        val todayNaps = getTodayNaps(recentRecords)
        val shortNapAdjustment = if (todayNaps.isNotEmpty()) {
            val lastNap = todayNaps.last()
            val napDuration = lastNap.duration
            napDuration != null && napDuration < Duration.ofMinutes(30)
        } else false

        val napTimes = generateNapTimes(
            effectiveWakeTime, wakeWindows, averageNapDuration, ageInWeeks,
            mode, shortNapAdjustment
        )

        val bedtimeWindow = getBedtimeWindow(ageInWeeks)
        val bedtime = calculateBedtime(napTimes, wakeWindows, bedtimeWindow)

        val totalSleepRecommendation = getTotalSleepRecommendation(ageInWeeks)
        val totalSleepLogged = calculateAverageDailySleep(recentRecords)

        val regressionWarning = detectRegression(ageInWeeks)
        val napTransitionSuggestion = detectNapTransition(recentRecords, ageInWeeks)

        return SleepSchedule(
            ageInWeeks = ageInWeeks,
            mode = mode,
            wakeWindows = wakeWindows,
            napTimes = napTimes,
            bedtime = bedtime,
            bedtimeWindow = bedtimeWindow,
            totalSleepRecommendation = totalSleepRecommendation,
            totalSleepLogged = totalSleepLogged,
            regressionWarning = regressionWarning,
            napTransitionSuggestion = napTransitionSuggestion,
            lastFeedTime = lastFeedSession?.startTime,
            isPersonalized = isPersonalized
        )
    }

    // --- Wake Windows ---

    internal fun getDefaultWakeWindows(ageInWeeks: Int): List<Duration> = when {
        ageInWeeks < 6 -> listOf(45, 45, 45, 45, 45).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 8 -> listOf(60, 60, 75, 75).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 12 -> listOf(75, 80, 90, 90).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 16 -> listOf(90, 105, 120).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 24 -> listOf(105, 135, 150).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 36 -> listOf(150, 180, 210).map { Duration.ofMinutes(it.toLong()) }
        else -> listOf(180, 210, 240).map { Duration.ofMinutes(it.toLong()) }
    }

    internal fun getWakeWindowBounds(ageInWeeks: Int): Pair<Duration, Duration> = when {
        ageInWeeks < 6 -> Duration.ofMinutes(30) to Duration.ofMinutes(60)
        ageInWeeks < 8 -> Duration.ofMinutes(45) to Duration.ofMinutes(90)
        ageInWeeks < 12 -> Duration.ofMinutes(60) to Duration.ofMinutes(120)
        ageInWeeks < 16 -> Duration.ofMinutes(75) to Duration.ofMinutes(150)
        ageInWeeks < 24 -> Duration.ofMinutes(90) to Duration.ofMinutes(180)
        ageInWeeks < 36 -> Duration.ofMinutes(120) to Duration.ofMinutes(210)
        else -> Duration.ofMinutes(150) to Duration.ofMinutes(240)
    }

    // --- Personalization ---

    private data class PersonalizationResult(
        val wakeWindows: List<Duration>,
        val averageNapDuration: Duration?,
        val isPersonalized: Boolean
    )

    private fun personalizeFromData(
        recentRecords: List<SleepRecord>,
        defaultWakeWindows: List<Duration>,
        wakeWindowBounds: Pair<Duration, Duration>,
        ageInWeeks: Int
    ): PersonalizationResult {
        val completedNaps = recentRecords.filter { it.sleepType == SleepType.NAP && it.duration != null }

        if (completedNaps.size < 3) {
            return PersonalizationResult(defaultWakeWindows, null, false)
        }

        // Calculate average nap duration from logged data
        val avgNapDuration = completedNaps
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(completedNaps.size.toLong())

        // Calculate actual wake windows from consecutive sleep sessions
        val sortedRecords = recentRecords.sortedBy { it.startTime }
        val actualWakeWindows = mutableListOf<Duration>()
        for (i in 0 until sortedRecords.size - 1) {
            val currentEnd = sortedRecords[i].endTime ?: continue
            val nextStart = sortedRecords[i + 1].startTime
            val gap = Duration.between(currentEnd, nextStart)
            // Only count positive gaps under 6 hours as wake windows
            if (!gap.isNegative && gap < Duration.ofHours(6)) {
                actualWakeWindows.add(gap)
            }
        }

        if (actualWakeWindows.isEmpty()) {
            return PersonalizationResult(defaultWakeWindows, avgNapDuration, false)
        }

        val avgActualWakeWindow = actualWakeWindows
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(actualWakeWindows.size.toLong())

        // Blend: 40% age default, 60% actual data
        val (minBound, maxBound) = wakeWindowBounds
        val blendedWindows = defaultWakeWindows.map { defaultWw ->
            val blended = defaultWw.multipliedBy(40).plus(avgActualWakeWindow.multipliedBy(60)).dividedBy(100)
            clampDuration(blended, minBound, maxBound)
        }

        return PersonalizationResult(blendedWindows, avgNapDuration, true)
    }

    // --- Nap Schedule Generation ---

    private fun generateNapTimes(
        wakeUpTime: LocalTime,
        wakeWindows: List<Duration>,
        averageNapDuration: Duration?,
        ageInWeeks: Int,
        mode: ScheduleMode,
        shortNapAdjustment: Boolean
    ): List<ScheduleEntry> {
        val naps = mutableListOf<ScheduleEntry>()
        var currentTime = wakeUpTime
        val napCount = wakeWindows.size - 1 // Last wake window is for bedtime

        for (i in 0 until napCount) {
            var ww = wakeWindows[i]

            // Short nap adjustment: reduce next wake window by 15 min if last nap was short
            val isAdjusted = shortNapAdjustment && i == 0
            if (isAdjusted) {
                ww = ww.minus(Duration.ofMinutes(15))
                if (ww.isNegative) ww = Duration.ofMinutes(15)
            }

            currentTime = currentTime.plus(ww)

            // Apply circadian alignment for clock-aligned mode
            if (mode == ScheduleMode.CLOCK_ALIGNED) {
                currentTime = applyCircadianAlignment(currentTime, i, napCount)
            }

            val napDuration = averageNapDuration ?: getDefaultNapDuration(i, ageInWeeks)
            naps.add(
                ScheduleEntry(
                    startTime = currentTime,
                    duration = napDuration,
                    label = "Nap ${i + 1}",
                    isAdjusted = isAdjusted
                )
            )
            currentTime = currentTime.plus(napDuration)
        }
        return naps
    }

    private fun getDefaultNapDuration(napIndex: Int, ageInWeeks: Int): Duration = when {
        napIndex == 0 && ageInWeeks >= 24 -> Duration.ofMinutes(120)
        napIndex == 0 -> Duration.ofMinutes(90)
        else -> Duration.ofMinutes(60)
    }

    private fun applyCircadianAlignment(napTime: LocalTime, napIndex: Int, totalNaps: Int): LocalTime {
        // Bias morning nap toward 9:00-10:00 AM
        if (napIndex == 0) {
            val target = LocalTime.of(9, 30)
            if (isWithinMinutes(napTime, target, 30)) {
                return shiftToward(napTime, target)
            }
        }
        // Bias midday nap toward 12:30-14:00
        if ((napIndex == 1 && totalNaps >= 2) || (napIndex == 0 && totalNaps == 1)) {
            val target = LocalTime.of(13, 0)
            if (isWithinMinutes(napTime, target, 45)) {
                return shiftToward(napTime, target)
            }
        }
        return napTime
    }

    private fun isWithinMinutes(time: LocalTime, target: LocalTime, minutes: Int): Boolean {
        val diff = kotlin.math.abs(Duration.between(time, target).toMinutes())
        return diff <= minutes
    }

    private fun shiftToward(time: LocalTime, target: LocalTime): LocalTime {
        val diffMinutes = Duration.between(time, target).toMinutes()
        // Shift halfway toward the target
        val shiftMinutes = diffMinutes / 2
        return time.plusMinutes(shiftMinutes)
    }

    // --- Bedtime ---

    internal fun getBedtimeWindow(ageInWeeks: Int): ClosedRange<LocalTime> = when {
        ageInWeeks < 6 -> LocalTime.of(21, 0)..LocalTime.of(23, 0)
        ageInWeeks < 12 -> LocalTime.of(20, 0)..LocalTime.of(22, 0)
        ageInWeeks < 16 -> LocalTime.of(19, 30)..LocalTime.of(21, 0)
        ageInWeeks < 24 -> LocalTime.of(19, 0)..LocalTime.of(20, 0)
        else -> LocalTime.of(18, 30)..LocalTime.of(20, 0)
    }

    private fun calculateBedtime(
        napTimes: List<ScheduleEntry>,
        wakeWindows: List<Duration>,
        bedtimeWindow: ClosedRange<LocalTime>
    ): LocalTime {
        if (napTimes.isEmpty()) return bedtimeWindow.start

        val lastNap = napTimes.last()
        val lastNapEnd = lastNap.startTime.plus(lastNap.duration)
        val calculated = lastNapEnd.plus(wakeWindows.last())

        return clampLocalTime(calculated, bedtimeWindow)
    }

    // --- Total Sleep ---

    internal fun getTotalSleepRecommendation(ageInWeeks: Int): ClosedRange<Duration> = when {
        ageInWeeks < 16 -> Duration.ofHours(14)..Duration.ofHours(17)
        else -> Duration.ofHours(12)..Duration.ofHours(16)
    }

    private fun calculateAverageDailySleep(recentRecords: List<SleepRecord>): Duration? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.isEmpty()) return null

        val zone = ZoneId.systemDefault()
        val dailySleep = completedRecords
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .map { (_, records) ->
                records.mapNotNull { it.duration }.fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            }

        if (dailySleep.isEmpty()) return null

        return dailySleep
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(dailySleep.size.toLong())
    }

    // --- Regression Detection ---

    internal fun detectRegression(ageInWeeks: Int): RegressionInfo? = when {
        ageInWeeks in 14..22 -> RegressionInfo(
            name = "4-Month Sleep Regression",
            description = "Your baby's sleep architecture is maturing from 2-stage to 4-stage cycles. " +
                "This is a permanent and healthy change. Sleep may be more disrupted than usual.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 32..44 -> RegressionInfo(
            name = "8-10 Month Sleep Regression",
            description = "Object permanence, separation anxiety, and motor milestones (crawling, pulling up) " +
                "can temporarily disrupt sleep patterns.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 48..55 -> RegressionInfo(
            name = "12-Month Sleep Regression",
            description = "Walking, early language development, and nap resistance may cause temporary " +
                "sleep disruption.",
            durationWeeks = "1-3 weeks"
        )
        else -> null
    }

    // --- Nap Transition Detection ---

    private fun detectNapTransition(recentRecords: List<SleepRecord>, ageInWeeks: Int): String? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.size < 5) return null

        val zone = ZoneId.systemDefault()
        val dailyNapCounts = completedRecords
            .filter { it.sleepType == SleepType.NAP }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { it.value.size }

        if (dailyNapCounts.size < 3) return null

        val avgNapsPerDay = dailyNapCounts.values.average()
        val expectedNaps = getExpectedNapCount(ageInWeeks)

        // Check if baby averages fewer naps than expected
        if (avgNapsPerDay >= expectedNaps - 0.5) return null

        // Check if night sleep is adequate (>= 10 hours)
        val nightRecords = completedRecords.filter { it.sleepType == SleepType.NIGHT_SLEEP }
        if (nightRecords.isEmpty()) return null

        val avgNightSleep = nightRecords
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(nightRecords.size.toLong())

        if (avgNightSleep < Duration.ofHours(10)) return null

        val currentNaps = expectedNaps
        val targetNaps = (expectedNaps - 1).coerceAtLeast(1)
        return "Your baby may be ready to transition from $currentNaps to $targetNaps naps. " +
            "They've been averaging ${String.format("%.1f", avgNapsPerDay)} naps per day " +
            "with good night sleep."
    }

    private fun getExpectedNapCount(ageInWeeks: Int): Int = when {
        ageInWeeks < 6 -> 5
        ageInWeeks < 12 -> 4
        ageInWeeks < 16 -> 3
        ageInWeeks < 24 -> 3
        ageInWeeks < 36 -> 2
        else -> 2
    }

    // --- Today's Naps ---

    private fun getTodayNaps(recentRecords: List<SleepRecord>): List<SleepRecord> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return recentRecords
            .filter { it.sleepType == SleepType.NAP }
            .filter { it.startTime.atZone(zone).toLocalDate() == today }
            .filter { it.endTime != null }
            .sortedBy { it.startTime }
    }

    // --- Utility ---

    private fun clampDuration(value: Duration, min: Duration, max: Duration): Duration = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    private fun clampLocalTime(time: LocalTime, range: ClosedRange<LocalTime>): LocalTime = when {
        time < range.start -> range.start
        time > range.endInclusive -> range.endInclusive
        else -> time
    }
}
