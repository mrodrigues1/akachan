package com.babytracker.receiver

import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StashExpirationBootReceiverTest {

    private lateinit var settings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler
    private lateinit var receiver: StashExpirationBootReceiver

    @BeforeEach
    fun setup() {
        settings = mockk()
        scheduler = mockk(relaxed = true)
        receiver = StashExpirationBootReceiver().apply {
            inventorySettings = settings
            this.scheduler = this@StashExpirationBootReceiverTest.scheduler
        }
        every { settings.getExpirationNotifTimeMinutes() } returns flowOf(480)
    }

    @Test
    fun `reschedules when both toggles enabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)

        receiver.handle()

        verify(exactly = 1) { scheduler.scheduleDaily(480) }
    }

    @Test
    fun `does not reschedule when master toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(false)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)

        receiver.handle()

        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `does not reschedule when notif toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(false)

        receiver.handle()

        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `shouldHandle accepts boot, time-set and timezone-change actions`() {
        assertTrue(receiver.shouldHandle("android.intent.action.BOOT_COMPLETED"))
        assertTrue(receiver.shouldHandle("android.intent.action.TIME_SET"))
        assertTrue(receiver.shouldHandle("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `shouldHandle ignores unrelated and null actions`() {
        assertFalse(receiver.shouldHandle("android.intent.action.AIRPLANE_MODE"))
        assertFalse(receiver.shouldHandle(null))
    }
}
