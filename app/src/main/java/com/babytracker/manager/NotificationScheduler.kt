package com.babytracker.manager

import java.time.Instant

interface NotificationScheduler {
    fun scheduleMaxTotalTimeNotification(sessionStartTime: Instant, maxTotalMinutes: Int)
    fun scheduleMaxPerBreastNotification(sessionStartTime: Instant, maxPerBreastMinutes: Int)
    fun cancelAllScheduledNotifications()
}
