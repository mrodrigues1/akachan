package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepActionReceiverTest {

    private lateinit var stopRecord: StopSleepRecordUseCase
    private lateinit var sleepSettingsRepository: SleepSettingsRepository
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var receiver: SleepActionReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        stopRecord = mockk()
        sleepSettingsRepository = mockk()
        napReminderScheduler = mockk(relaxed = true)
        receiver = SleepActionReceiver()
        receiver.stopRecord = stopRecord
        receiver.sleepSettingsRepository = sleepSettingsRepository
        receiver.napReminderScheduler = napReminderScheduler

        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun buildStopIntent(sessionId: Long = 99L): Intent = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(SleepActionReceiver.EXTRA_ACTION) } returns SleepActionReceiver.ACTION_STOP
        every { it.getLongExtra(SleepActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }

    @Test
    fun `ACTION_STOP calls StopSleepRecordUseCase and cancels notification`() = runTest {
        coEvery { stopRecord(99L) } returns null

        receiver.handle(context, buildStopIntent())

        coVerify { stopRecord(99L) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID) }
    }

    @Test
    fun `unknown action does not stop record and does not cancel notification`() = runTest {
        receiver.handle(context, mockk<Intent>(relaxed = true).also {
            every { it.getStringExtra(SleepActionReceiver.EXTRA_ACTION) } returns "unknown"
            every { it.getLongExtra(SleepActionReceiver.EXTRA_SESSION_ID, -1L) } returns 1L
        })

        coVerify(exactly = 0) { stopRecord(any()) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(any(), any()) }
    }

    @Test
    fun `ACTION_STOP with NAP record schedules nap reminder when enabled`() = runTest {
        val napRecord = SleepRecord(id = 99L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NAP)
        coEvery { stopRecord(99L) } returns napRecord
        every { sleepSettingsRepository.getNapReminderEnabled() } returns flowOf(true)
        every { sleepSettingsRepository.getNapReminderDelayMinutes() } returns flowOf(10)

        receiver.handle(context, buildStopIntent())

        verify { napReminderScheduler.schedule(any(), 10) }
    }

    @Test
    fun `ACTION_STOP with NIGHT_SLEEP record does not schedule nap reminder`() = runTest {
        val nightRecord = SleepRecord(id = 99L, startTime = Instant.now().minusSeconds(28800), sleepType = SleepType.NIGHT_SLEEP)
        coEvery { stopRecord(99L) } returns nightRecord

        receiver.handle(context, buildStopIntent())

        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
    }

    @Test
    fun `ACTION_STOP with NAP record does not schedule nap reminder when disabled`() = runTest {
        val napRecord = SleepRecord(id = 99L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NAP)
        coEvery { stopRecord(99L) } returns napRecord
        every { sleepSettingsRepository.getNapReminderEnabled() } returns flowOf(false)

        receiver.handle(context, buildStopIntent())

        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
    }
}
