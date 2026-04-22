package com.babytracker.manager

import java.time.Instant

interface NotificationScheduler {
    fun scheduleMaxTotalTimeNotification(
        sessionStartTime: Instant,
        maxTotalMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxPerBreastMinutes: Int
    )
    fun scheduleMaxPerBreastNotification(
        sessionStartTime: Instant,
        maxPerBreastMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxTotalMinutes: Int
    )
    fun scheduleMaxTotalTimeNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxTotalMinutes: Int,
        currentSide: String,
        maxPerBreastMinutes: Int
    )
    fun scheduleMaxPerBreastNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxPerBreastMinutes: Int,
        currentSide: String,
        maxTotalMinutes: Int
    )
    fun cancelAllScheduledNotifications()
}
