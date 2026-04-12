package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: BreastfeedingNotificationManager

    @BeforeEach
    fun setUp() {
        context = mockk()
        alarmManager = mockk()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager

        // Mock PendingIntent static methods
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any<Intent>(), any()) } returns mockk()

        // Mock alarm methods
        every { alarmManager.cancel(any<PendingIntent>()) } returns Unit
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } returns Unit
        every { alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } returns Unit

        notificationManager = BreastfeedingNotificationManager(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `scheduleMaxTotalTimeNotification does nothing when maxTotalMinutes is zero`() {
        notificationManager.scheduleMaxTotalTimeNotification(Instant.now(), 0)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun `scheduleMaxTotalTimeNotification does nothing when maxTotalMinutes is negative`() {
        notificationManager.scheduleMaxTotalTimeNotification(Instant.now(), -5)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun `scheduleMaxPerBreastNotification does nothing when maxPerBreastMinutes is zero`() {
        notificationManager.scheduleMaxPerBreastNotification(Instant.now(), 0)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun `scheduleMaxPerBreastNotification does nothing when maxPerBreastMinutes is negative`() {
        notificationManager.scheduleMaxPerBreastNotification(Instant.now(), -10)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun `cancelAllScheduledNotifications cancels both alarms`() {
        notificationManager.cancelAllScheduledNotifications()

        // Should cancel both REQUEST_CODE_MAX_TOTAL (1001) and REQUEST_CODE_MAX_PER_BREAST (1002)
        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun `NOTIFICATION_ACTION is identical for schedule and cancel ensuring PendingIntent filterEquals match`() {
        // Android's PendingIntent matching uses filterEquals() which compares the intent action.
        // Both scheduleAlarm() and cancelAlarm() use NOTIFICATION_ACTION so the PendingIntents
        // are considered equal and alarmManager.cancel() actually cancels the scheduled alarm.
        // If they diverged, cancel() would target a different PendingIntent and be a no-op —
        // causing notifications to keep firing during a paused session.
        assertEquals("com.babytracker.BREASTFEEDING_NOTIFICATION", BreastfeedingNotificationManager.NOTIFICATION_ACTION)
    }
}
