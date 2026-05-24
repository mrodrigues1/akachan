package com.babytracker.receiver

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class NapReminderReceiverTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var receiver: NapReminderReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        receiver = NapReminderReceiver()
        receiver.settingsRepository = settingsRepository
        mockkObject(NotificationHelper)
        mockkStatic(LocalTime::class)
        every { NotificationHelper.showNapReminder(any()) } returns Unit
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun stubQuietHours(startMinute: Int, endMinute: Int) {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(startMinute)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(endMinute)
    }

    private fun stubCurrentTime(hourOfDay: Int, minute: Int) {
        val localTime = mockk<LocalTime>()
        every { localTime.hour } returns hourOfDay
        every { localTime.minute } returns minute
        every { LocalTime.now() } returns localTime
    }

    @Test
    fun `posts notification when current time is outside normal quiet window`() = runTest {
        stubQuietHours(startMinute = 0, endMinute = 480)   // 00:00–08:00
        stubCurrentTime(hourOfDay = 10, minute = 0)        // 10:00 → minute 600

        receiver.handle(context)

        verify(exactly = 1) { NotificationHelper.showNapReminder(context) }
    }

    @Test
    fun `suppresses notification when current time is inside normal quiet window`() = runTest {
        stubQuietHours(startMinute = 0, endMinute = 480)   // 00:00–08:00
        stubCurrentTime(hourOfDay = 1, minute = 0)         // 01:00 → minute 60

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `suppresses notification when inside overnight quiet window (wraps midnight)`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00 overnight
        stubCurrentTime(hourOfDay = 0, minute = 30)         // 00:30 → minute 30

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `posts notification when outside overnight quiet window`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00 overnight
        stubCurrentTime(hourOfDay = 5, minute = 0)          // 05:00 → minute 300

        receiver.handle(context)

        verify(exactly = 1) { NotificationHelper.showNapReminder(context) }
    }

    @Test
    fun `suppresses notification when current time equals start of overnight window`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00
        stubCurrentTime(hourOfDay = 23, minute = 0)         // 23:00 → minute 1380

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `suppresses notification when nap reminder feature is disabled`() = runTest {
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
        stubQuietHours(startMinute = 0, endMinute = 480)
        stubCurrentTime(hourOfDay = 10, minute = 0)         // outside quiet window

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }
}
