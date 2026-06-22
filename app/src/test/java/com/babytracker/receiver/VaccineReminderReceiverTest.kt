package com.babytracker.receiver

import android.content.Context
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.util.VaccineNotificationHelper
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

class VaccineReminderReceiverTest {
    private val repository = mockk<VaccineRepository>()
    private val settings = mockk<VaccineSettingsRepository>()
    private val context = mockk<Context>(relaxed = true)
    private lateinit var receiver: VaccineReminderReceiver

    @BeforeEach
    fun setup() {
        receiver = VaccineReminderReceiver().apply {
            repository = this@VaccineReminderReceiverTest.repository
            settings = this@VaccineReminderReceiverTest.settings
        }
        mockkObject(VaccineNotificationHelper)
        every { VaccineNotificationHelper.show(any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `does not notify when reminders disabled`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(false)
        receiver.handle(context, 1)
        verify(exactly = 0) { VaccineNotificationHelper.show(any(), any(), any(), any()) }
    }

    @Test
    fun `does not notify for a missing record`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(true)
        coEvery { repository.getById(1) } returns null
        receiver.handle(context, 1)
        verify(exactly = 0) { VaccineNotificationHelper.show(any(), any(), any(), any()) }
    }

    @Test
    fun `does not notify for an administered record`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(true)
        coEvery { repository.getById(1) } returns VaccineRecord(
            id = 1, name = "MMR", status = VaccineStatus.ADMINISTERED,
            administeredDate = Instant.ofEpochMilli(1), createdAt = Instant.ofEpochMilli(1),
        )
        receiver.handle(context, 1)
        verify(exactly = 0) { VaccineNotificationHelper.show(any(), any(), any(), any()) }
    }

    @Test
    fun `notifies for a scheduled record when enabled`() = runTest {
        val scheduled = Instant.ofEpochMilli(50_000)
        every { settings.getReminderEnabled() } returns flowOf(true)
        coEvery { repository.getById(1) } returns VaccineRecord(
            id = 1, name = "BCG", status = VaccineStatus.SCHEDULED,
            scheduledDate = scheduled, createdAt = Instant.ofEpochMilli(1),
        )
        receiver.handle(context, 1)
        verify(exactly = 1) { VaccineNotificationHelper.show(context, "BCG", scheduled, false) }
    }

    @Test
    fun `notifies for a to-schedule record with the book copy`() = runTest {
        val target = Instant.ofEpochMilli(50_000)
        every { settings.getReminderEnabled() } returns flowOf(true)
        coEvery { repository.getById(1) } returns VaccineRecord(
            id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
            scheduledDate = target, createdAt = Instant.ofEpochMilli(1),
        )
        receiver.handle(context, 1)
        verify(exactly = 1) { VaccineNotificationHelper.show(context, "MMR", target, true) }
    }
}
