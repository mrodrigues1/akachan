package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
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

class SleepNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var manager: SleepNotificationManager

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk()
        manager = SleepNotificationManager(context, settingsRepository)
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSleepActive(any(), any(), any(), any(), any()) } returns Unit
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `show delegates to NotificationHelper with rich enabled`() = runTest {
        coEvery { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)
        val start = Instant.ofEpochMilli(1_000_000L)

        manager.show(7L, SleepType.NAP, start)

        verify { NotificationHelper.showSleepActive(context, 7L, "NAP", 1_000_000L, true) }
    }

    @Test
    fun `show delegates to NotificationHelper with rich disabled`() = runTest {
        coEvery { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)

        manager.show(3L, SleepType.NIGHT_SLEEP, Instant.now())

        verify { NotificationHelper.showSleepActive(context, 3L, "NIGHT_SLEEP", any(), false) }
    }

    @Test
    fun `cancel removes sleep notification`() {
        manager.cancel()

        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID) }
    }
}
