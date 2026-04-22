package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SleepActionReceiverTest {

    private lateinit var stopRecord: StopSleepRecordUseCase
    private lateinit var receiver: SleepActionReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        stopRecord = mockk()
        receiver = SleepActionReceiver()
        receiver.stopRecord = stopRecord

        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_STOP calls StopSleepRecordUseCase and cancels notification`() = runTest {
        coEvery { stopRecord(99L) } returns Unit

        receiver.handle(context, mockk<Intent>(relaxed = true).also {
            every { it.getStringExtra(SleepActionReceiver.EXTRA_ACTION) } returns SleepActionReceiver.ACTION_STOP
            every { it.getLongExtra(SleepActionReceiver.EXTRA_SESSION_ID, -1L) } returns 99L
        })

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
}
