package com.babytracker.receiver

import android.content.Intent
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaccineReminderBootReceiverTest {
    private val settings = mockk<VaccineSettingsRepository>()
    private val scheduler = mockk<VaccineReminderScheduler>(relaxed = true)

    private fun receiver() = VaccineReminderBootReceiver().apply {
        settings = this@VaccineReminderBootReceiverTest.settings
        scheduler = this@VaccineReminderBootReceiverTest.scheduler
    }

    @Test
    fun `handles boot and time actions only`() {
        val r = receiver()
        assertTrue(r.shouldHandle(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(r.shouldHandle(Intent.ACTION_TIME_CHANGED))
        assertTrue(r.shouldHandle(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(r.shouldHandle("com.example.OTHER"))
        assertFalse(r.shouldHandle(null))
    }

    @Test
    fun `reschedules when enabled`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(true)
        receiver().handle()
        coVerify { scheduler.rescheduleAll() }
    }

    @Test
    fun `does not reschedule when disabled`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(false)
        receiver().handle()
        coVerify(exactly = 0) { scheduler.rescheduleAll() }
    }
}
