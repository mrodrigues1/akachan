package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.babytracker.domain.repository.SleepSettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class NapReminderManagerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var sleepSettingsRepository: SleepSettingsRepository
    private lateinit var manager: NapReminderManager
    private lateinit var mockPi: PendingIntent

    @BeforeEach
    fun setup() {
        alarmManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sleepSettingsRepository = mockk()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.babytracker"

        mockkStatic(PendingIntent::class)
        mockPi = mockk(relaxed = true)
        every {
            PendingIntent.getBroadcast(any(), eq(NapReminderManager.RC_NAP_REMINDER), any(), any())
        } returns mockPi

        manager = NapReminderManager(context, sleepSettingsRepository)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `schedule computes triggerAt as napEndTime plus delayMinutes`() {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")
        val delayMinutes = 60
        val triggerSlot = slot<Long>()
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), capture(triggerSlot), any())
        } just runs

        manager.schedule(napEnd, delayMinutes)

        val expected = napEnd.plusSeconds(60 * 60L).toEpochMilli()
        assertEquals(expected, triggerSlot.captured)
    }

    @Test
    fun `schedule cancels existing alarm before setting new one`() {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")

        manager.schedule(napEnd, 30)

        verifyOrder {
            alarmManager.cancel(mockPi)
            alarmManager.setExactAndAllowWhileIdle(any(), any(), mockPi)
        }
    }

    @Test
    fun `schedule passes RTC_WAKEUP as alarm type`() {
        manager.schedule(Instant.now(), 60)

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `cancel calls alarmManager cancel with the same PendingIntent`() {
        manager.cancel()

        verify { alarmManager.cancel(mockPi) }
    }

    @Test
    fun `scheduleIfEnabled schedules alarm when nap reminder is enabled`() = runTest {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")
        every { sleepSettingsRepository.getNapReminderEnabled() } returns flowOf(true)
        every { sleepSettingsRepository.getNapReminderDelayMinutes() } returns flowOf(30)

        manager.scheduleIfEnabled(napEnd)

        coVerify { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `scheduleIfEnabled does not schedule alarm when nap reminder is disabled`() = runTest {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")
        every { sleepSettingsRepository.getNapReminderEnabled() } returns flowOf(false)

        manager.scheduleIfEnabled(napEnd)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `scheduleIfEnabled uses delay minutes from settings`() = runTest {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")
        val delayMinutes = 45
        every { sleepSettingsRepository.getNapReminderEnabled() } returns flowOf(true)
        every { sleepSettingsRepository.getNapReminderDelayMinutes() } returns flowOf(delayMinutes)
        val triggerSlot = slot<Long>()
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), capture(triggerSlot), any())
        } just runs

        manager.scheduleIfEnabled(napEnd)

        val expected = napEnd.plusSeconds(delayMinutes * 60L).toEpochMilli()
        assertEquals(expected, triggerSlot.captured)
    }
}
