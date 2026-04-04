package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.SleepSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GenerateSleepScheduleUseCase @Inject constructor() {
    operator fun invoke(baby: Baby, wakeUpTime: LocalTime = LocalTime.of(7, 0)): SleepSchedule {
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, Instant.now()).toInt()
        val wakeWindows = getWakeWindowsForAge(ageInWeeks)
        val napTimes = generateNapTimes(wakeUpTime, wakeWindows)
        val bedtime = calculateBedtime(napTimes, wakeWindows)

        return SleepSchedule(
            ageInWeeks = ageInWeeks,
            wakeWindows = wakeWindows,
            napTimes = napTimes,
            bedtime = bedtime
        )
    }

    private fun getWakeWindowsForAge(ageInWeeks: Int): List<Duration> = when {
        ageInWeeks < 8 -> listOf(Duration.ofMinutes(60), Duration.ofMinutes(60), Duration.ofMinutes(60), Duration.ofMinutes(60))
        ageInWeeks < 16 -> listOf(Duration.ofMinutes(75), Duration.ofMinutes(90), Duration.ofMinutes(90), Duration.ofMinutes(90))
        ageInWeeks < 24 -> listOf(Duration.ofMinutes(90), Duration.ofMinutes(120), Duration.ofMinutes(120))
        ageInWeeks < 36 -> listOf(Duration.ofMinutes(120), Duration.ofMinutes(150), Duration.ofMinutes(150))
        else -> listOf(Duration.ofMinutes(150), Duration.ofMinutes(180))
    }

    private fun generateNapTimes(wakeUpTime: LocalTime, wakeWindows: List<Duration>): List<ScheduleEntry> {
        val naps = mutableListOf<ScheduleEntry>()
        var currentTime = wakeUpTime

        for (i in 0 until wakeWindows.size - 1) {
            currentTime = currentTime.plus(wakeWindows[i])
            val napDuration = Duration.ofMinutes(if (i == 0) 90 else 60)
            naps.add(
                ScheduleEntry(
                    startTime = currentTime,
                    duration = napDuration,
                    label = "Nap ${i + 1}"
                )
            )
            currentTime = currentTime.plus(napDuration)
        }
        return naps
    }

    private fun calculateBedtime(napTimes: List<ScheduleEntry>, wakeWindows: List<Duration>): LocalTime {
        if (napTimes.isEmpty()) return LocalTime.of(19, 0)
        val lastNap = napTimes.last()
        val lastNapEnd = lastNap.startTime.plus(lastNap.duration)
        return lastNapEnd.plus(wakeWindows.last())
    }
}
