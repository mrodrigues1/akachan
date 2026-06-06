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
    val napWakeIntervalCount: Int = 0,
    val napWakeP25Millis: Long? = null,
    val napWakeP50Millis: Long? = null,
    val napWakeP75Millis: Long? = null,
    val bedtimeWakeIntervalCount: Int = 0,
    val bedtimeWakeP25Millis: Long? = null,
    val bedtimeWakeP50Millis: Long? = null,
    val bedtimeWakeP75Millis: Long? = null,
    val avgDailySleepMillis: Long? = null,
)
