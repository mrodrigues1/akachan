package com.babytracker.util

import android.app.AlarmManager
import android.app.PendingIntent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AlarmSchedulingExtTest {

    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val pendingIntent: PendingIntent = mockk()

    @Test
    fun `setExactWithFallback schedules an exact alarm when exact alarms are allowed`() {
        val triggerAtMs = 1_000L

        alarmManager.setExactWithFallback(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent, TAG)

        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
        verify(exactly = 0) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any())
        }
    }

    @Test
    fun `setExactWithFallback falls back to an inexact alarm when exact scheduling throws SecurityException`() {
        val triggerAtMs = 2_000L
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        } throws SecurityException("SCHEDULE_EXACT_ALARM revoked")

        alarmManager.setExactWithFallback(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent, TAG)

        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
    }

    private companion object {
        const val TAG = "AlarmSchedulingExtTest"
    }
}
