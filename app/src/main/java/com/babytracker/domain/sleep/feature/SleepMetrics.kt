package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepType

data class SleepMetrics(
    val lastWakeMillis: Long?,
    val lastSleepType: SleepType?,
    val completedWakeIntervals: List<Long>,
    val medianWakeIntervalMillis: Long?,
    val wakeIntervalIqrMillis: Long?,
    val sleepLast24hMillis: Long,
    val napCountToday: Int,
    val medianBedtimeMinuteOfDay: Int?,
    val napStats: WakeIntervalStats = WakeIntervalStats(),
    val bedtimeStats: WakeIntervalStats = WakeIntervalStats(),
    val avgDailySleepMillis: Long? = null,
)
