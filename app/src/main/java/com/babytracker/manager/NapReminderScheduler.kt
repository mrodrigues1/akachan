package com.babytracker.manager

import java.time.Instant

interface NapReminderScheduler {
    fun schedule(napEndTime: Instant, delayMinutes: Int)
    fun cancel()
    suspend fun scheduleIfEnabled(napEndTime: Instant)
}
