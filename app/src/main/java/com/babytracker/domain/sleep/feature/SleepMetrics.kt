package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepType

data class SleepMetrics(
    val lastWakeMillis: Long?,
    val lastSleepType: SleepType?,
    val lastSleepDurationMillis: Long?,
    val completedWakeIntervals: List<Long>,
    val medianWakeIntervalMillis: Long?,
    val wakeIntervalIqrMillis: Long?,
    val sleepLast24hMillis: Long,
    val daySleepTodayMillis: Long,
    val napCountToday: Int,
    val medianBedtimeMinuteOfDay: Int?,
    val medianMorningWakeMinuteOfDay: Int?,
)
