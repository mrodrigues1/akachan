package com.babytracker.manager

import android.app.AlarmManager
import android.content.Context
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class VaccineReminderManagerTest {
    private val zone = ZoneId.of("UTC")
    private lateinit var manager: VaccineReminderManager

    @BeforeEach
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        // getSystemService(AlarmManager) is called in the manager's init; the relaxed default
        // returns a bare Object that fails to cast, so stub it with a real mock.
        every { context.getSystemService(AlarmManager::class.java) } returns mockk<AlarmManager>(relaxed = true)
        manager = VaccineReminderManager(
            context = context,
            settings = mockk<VaccineSettingsRepository>(relaxed = true),
            repository = mockk<VaccineRepository>(relaxed = true),
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `fires at 9am leadDays before the scheduled date`() {
        val scheduled = at(2026, 7, 1, 12)
        val now = at(2026, 6, 1, 0)
        val trigger = manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = now, zone = zone)
        assertEquals(at(2026, 6, 24, 9), trigger)
    }

    @Test
    fun `falls back to the next 9am strictly after now when the lead window has passed`() {
        val scheduled = at(2026, 7, 1, 12)
        // now is after the lead trigger (2026-06-24 09:00) but before 09:00 today.
        val now = at(2026, 6, 28, 8)
        val trigger = manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = now, zone = zone)
        assertEquals(at(2026, 6, 28, 9), trigger)
        assertTrue(trigger!! > now)
    }

    @Test
    fun `uses tomorrow 9am when today 9am has already passed`() {
        val scheduled = at(2026, 7, 1, 12)
        val now = at(2026, 6, 28, 10) // past 09:00 today
        val trigger = manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = now, zone = zone)
        assertEquals(at(2026, 6, 29, 9), trigger)
    }

    @Test
    fun `returns null when the only remaining 9am is at or after the scheduled instant`() {
        val scheduled = at(2026, 7, 1, 8) // before 09:00
        val now = at(2026, 6, 30, 10)
        val trigger = manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = now, zone = zone)
        assertNull(trigger)
    }

    @Test
    fun `returns null when the scheduled instant is now or in the past`() {
        val scheduled = at(2026, 6, 1, 9)
        val now = at(2026, 6, 1, 9)
        assertNull(manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = now, zone = zone))
        assertNull(manager.computeTriggerAtMs(scheduled, leadDays = 7, nowMs = at(2026, 6, 2, 0), zone = zone))
    }
}
