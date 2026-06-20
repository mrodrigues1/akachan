package com.babytracker.manager

import android.app.AlarmManager
import android.content.Context
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DoctorVisitReminderManagerTest {
    private val zone = ZoneId.of("UTC")
    private lateinit var manager: DoctorVisitReminderManager

    @BeforeEach
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        // getSystemService(AlarmManager) is called in the manager's init; stub it with a real mock.
        every { context.getSystemService(AlarmManager::class.java) } returns mockk<AlarmManager>(relaxed = true)
        manager = DoctorVisitReminderManager(
            context = context,
            repository = mockk<DoctorVisitRepository>(relaxed = true),
            settings = mockk<DoctorVisitSettingsRepository>(relaxed = true),
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `fires at 9am leadDays before the visit date`() {
        val visit = at(2026, 7, 10, 12)
        val now = at(2026, 7, 1, 0)
        val trigger = manager.computeTriggerAtMs(visit, leadDays = 1, nowMs = now, zone = zone)
        assertEquals(at(2026, 7, 9, 9), trigger)
    }

    @Test
    fun `respects a larger lead time`() {
        val visit = at(2026, 7, 10, 12)
        val now = at(2026, 7, 1, 0)
        val trigger = manager.computeTriggerAtMs(visit, leadDays = 7, nowMs = now, zone = zone)
        assertEquals(at(2026, 7, 3, 9), trigger)
    }

    @Test
    fun `falls back to the next 9am strictly after now when the lead window has passed`() {
        val visit = at(2026, 7, 10, 12)
        val now = at(2026, 7, 9, 8) // after lead trigger (09:00 on 7/9 with leadDays=1), before 09:00 today
        val trigger = manager.computeTriggerAtMs(visit, leadDays = 1, nowMs = now, zone = zone)
        assertEquals(at(2026, 7, 9, 9), trigger)
        assertTrue(trigger!! > now)
    }

    @Test
    fun `returns null when the only remaining 9am is at or after the visit instant`() {
        val visit = at(2026, 7, 1, 8) // before 09:00
        val now = at(2026, 6, 30, 10)
        assertNull(manager.computeTriggerAtMs(visit, leadDays = 1, nowMs = now, zone = zone))
    }

    @Test
    fun `returns null when the visit instant is now or in the past`() {
        val visit = at(2026, 6, 1, 9)
        assertNull(manager.computeTriggerAtMs(visit, leadDays = 1, nowMs = at(2026, 6, 1, 9), zone = zone))
        assertNull(manager.computeTriggerAtMs(visit, leadDays = 1, nowMs = at(2026, 6, 2, 0), zone = zone))
    }
}
