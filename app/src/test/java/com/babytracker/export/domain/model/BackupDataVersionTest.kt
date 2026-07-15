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
    fun `current format version is 7`() {
        assertEquals(7, CURRENT_BACKUP_FORMAT_VERSION)
    }

    @Test
    fun `v6 payload without question answers deserializes them as null`() {
        val v6 = BackupData(
            backupFormatVersion = 6, roomSchemaVersion = 15, appVersion = "1.0", exportedAt = 0,
            baby = null, settings = settings(), breastfeeding = emptyList(), sleep = emptyList(),
            pumping = emptyList(), milkBags = emptyList(),
            visitQuestions = listOf(
                VisitQuestionBackup(id = 1, text = "rash?", answered = true, createdAt = 100),
            ),
        )
        // encodeDefaults = false omits the default-null answer, mimicking a real v6 file.
        // Match the exact key so "answered" (a distinct field) doesn't produce a false positive.
        val encoded = json.encodeToString(BackupData.serializer(), v6)
        assertFalse(encoded.contains("\"answer\":"))

        val decoded = json.decodeFromString(BackupData.serializer(), encoded)
        assertEquals(6, decoded.backupFormatVersion)
        assertEquals(null, decoded.visitQuestions.single().answer)
    }
}
