package com.babytracker.domain.model

import java.time.Duration
import java.time.LocalTime

data class SleepSchedule(
    val ageInWeeks: Int,
    val wakeWindows: List<Duration>,
    val napTimes: List<ScheduleEntry>,
    val bedtime: LocalTime
)

data class ScheduleEntry(
    val startTime: LocalTime,
    val duration: Duration,
    val label: String
)
