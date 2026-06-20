package com.babytracker.export.domain.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupDataVersionTest {
    private val json = Json { encodeDefaults = false }

    private fun settings() = SettingsBackup(
        themeConfig = "SYSTEM", maxPerBreastMinutes = 0, maxTotalFeedMinutes = 0,
        wakeTimeMinuteOfDay = null, autoUpdateEnabled = true, richNotificationsEnabled = true,
        predictiveEnabled = false, predictiveLeadMinutes = 15, quietHoursStartMinute = 0,
        quietHoursEndMinute = 480, napReminderEnabled = false, napReminderDelayMinutes = 60,
    )

    @Test
    fun `v5 payload without doctor-visit fields deserializes them as empty`() {
        val v5 = BackupData(
            backupFormatVersion = 5, roomSchemaVersion = 14, appVersion = "1.0", exportedAt = 0,
            baby = null, settings = settings(), breastfeeding = emptyList(), sleep = emptyList(),
            pumping = emptyList(), milkBags = emptyList(),
        )
        // encodeDefaults = false omits the default-empty doctor-visit lists, mimicking a real v5 file.
        val encoded = json.encodeToString(BackupData.serializer(), v5)
        assertFalse(encoded.contains("doctorVisits"))
        assertFalse(encoded.contains("visitQuestions"))

        val decoded = json.decodeFromString(BackupData.serializer(), encoded)
        assertEquals(5, decoded.backupFormatVersion)
        assertTrue(decoded.doctorVisits.isEmpty())
        assertTrue(decoded.visitQuestions.isEmpty())
    }

    @Test
    fun `current format version is 6`() {
        assertEquals(6, CURRENT_BACKUP_FORMAT_VERSION)
    }
}
