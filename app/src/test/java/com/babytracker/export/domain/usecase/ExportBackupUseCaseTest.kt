package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.PreferencesSnapshot
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportBackupUseCaseTest {

    private lateinit var source: BackupSource
    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private lateinit var useCase: ExportBackupUseCase

    private fun settings() = SettingsBackup(
        themeConfig = "SYSTEM", maxPerBreastMinutes = 15, maxTotalFeedMinutes = 30,
        wakeTimeMinuteOfDay = 420, autoUpdateEnabled = true, richNotificationsEnabled = true,
        predictiveEnabled = false, predictiveLeadMinutes = 15, quietHoursStartMinute = 0,
        quietHoursEndMinute = 480, napReminderEnabled = false, napReminderDelayMinutes = 60,
    )

    @BeforeEach
    fun setup() {
        source = mockk()
        coEvery { source.readTracking() } returns TrackingSnapshot(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, null, null, 0),
            ),
            sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
            bottleFeeds = emptyList(),
        )
        coEvery { source.readPreferences() } returns PreferencesSnapshot(
            baby = BabyBackup("Mia", 100, listOf("DAIRY"), null),
            settings = settings(),
        )
        useCase = ExportBackupUseCase(
            source, json, ExportMetadata(appVersion = "1.2.3", roomSchemaVersion = 3),
        )
    }

    @Test
    fun `exports all data into parseable backup json`() = runTest {
        val parsed = json.decodeFromString(BackupData.serializer(), useCase())
        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, parsed.backupFormatVersion)
        assertEquals(3, parsed.roomSchemaVersion)
        assertEquals("1.2.3", parsed.appVersion)
        assertEquals(1, parsed.breastfeeding.size)
        assertEquals("Mia", parsed.baby?.name)
        assertEquals(420, parsed.settings.wakeTimeMinuteOfDay)
    }

    @Test
    fun `does not leak sharing state into json`() = runTest {
        val jsonString = useCase()
        assertFalse(jsonString.contains("appMode", ignoreCase = true))
        assertFalse(jsonString.contains("shareCode", ignoreCase = true))
        assertTrue(jsonString.contains("breastfeeding"))
    }

    @Test
    fun `preserves active sessions with null endTime in backup`() = runTest {
        coEvery { source.readTracking() } returns TrackingSnapshot(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, null, "LEFT", null, null, pausedAt = 50, pausedDurationMs = 5),
            ),
            sleep = listOf(SleepBackup(2, 10, null, "NAP", null)),
            pumping = listOf(
                PumpingBackup(3, 10, null, "BOTH", null, null, pausedAt = 60, pausedDurationMs = 7),
            ),
            milkBags = emptyList(),
            bottleFeeds = emptyList(),
        )
        coEvery { source.readPreferences() } returns PreferencesSnapshot(null, settings())

        val parsed = json.decodeFromString(BackupData.serializer(), useCase())
        val bf = parsed.breastfeeding.single()
        val pump = parsed.pumping.single()

        assertNull(bf.endTime, "active breastfeeding endTime must remain null")
        assertEquals(50L, bf.pausedAt, "pausedAt must be preserved")
        assertEquals(5L, bf.pausedDurationMs, "pausedDurationMs must not be mutated")
        assertNull(parsed.sleep.single().endTime, "active sleep endTime must remain null")
        assertNull(pump.endTime, "active pumping endTime must remain null")
        assertEquals(60L, pump.pausedAt, "pumping pausedAt must be preserved")
        assertEquals(7L, pump.pausedDurationMs, "pumping pausedDurationMs must not be mutated")
    }
}
