package com.babytracker.manager

import com.babytracker.domain.model.SleepType
import java.time.Instant

interface SleepNotificationScheduler {
    suspend fun show(sessionId: Long, sleepType: SleepType, startTime: Instant)
    fun cancel()
}
