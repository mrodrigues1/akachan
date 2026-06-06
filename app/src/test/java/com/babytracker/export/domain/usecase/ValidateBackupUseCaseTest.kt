package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValidateBackupUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var useCase: ValidateBackupUseCase

    @BeforeEach
    fun setup() {
        useCase = ValidateBackupUseCase(json)
    }

    private fun settings() = SettingsBackup(
        themeConfig = "SYSTEM", maxPerBreastMinutes = 0, maxTotalFeedMinutes = 0,
        wakeTimeMinuteOfDay = null, autoUpdateEnabled = true, richNotificationsEnabled = true,
        predictiveEnabled = false, predictiveLeadMinutes = 15, quietHoursStartMinute = 0,
        quietHoursEndMinute = 480, napReminderEnabled = false, napReminderDelayMinutes = 60,
    )

    private fun backup(
        version: Int = CURRENT_BACKUP_FORMAT_VERSION,
        sleep: List<SleepBackup> = emptyList(),
        pumping: List<PumpingBackup> = emptyList(),
        milkBags: List<MilkBagBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = version, roomSchemaVersion = 3, appVersion = "1.0.0",
        exportedAt = 0, baby = null, settings = settings(),
        breastfeeding = emptyList(), sleep = sleep, pumping = pumping, milkBags = milkBags,
    )

    @Test
    fun `accepts a valid equal-version backup`() {
        val data = backup(sleep = listOf(SleepBackup(1, 10, 20, "NAP", null)))
        val result = useCase(json.encodeToString(BackupData.serializer(), data))
        assertEquals(1, result.sleep.size)
    }

    @Test
    fun `canonicalizes legacy sleep type labels`() {
        val data = backup(
            sleep = listOf(
                SleepBackup(1, 10, 20, "Nap", null),
                SleepBackup(2, 30, 40, "Night Sleep", null),
            ),
        )

        val result = useCase(json.encodeToString(BackupData.serializer(), data))

        assertEquals(listOf("NAP", "NIGHT_SLEEP"), result.sleep.map { it.sleepType })
    }

    @Test
    fun `rejects a newer-than-build backup`() {
        val data = backup(version = CURRENT_BACKUP_FORMAT_VERSION + 1)
        assertThrows(BackupTooNewException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects an invalid enum value`() {
        val data = backup(sleep = listOf(SleepBackup(1, 10, 20, "NONSENSE", null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a milk bag referencing a pumping row absent from the backup`() {
        val data = backup(
            pumping = listOf(PumpingBackup(5, 1, 2, "LEFT", 100, null, null, 0)),
            milkBags = listOf(MilkBagBackup(1, 10, 90, sourceSessionId = 999, usedAt = null, notes = null, createdAt = 5)),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `accepts a milk bag with null source session id`() {
        val data = backup(
            milkBags = listOf(MilkBagBackup(1, 10, 90, sourceSessionId = null, usedAt = null, notes = null, createdAt = 5)),
        )
        val result = useCase(json.encodeToString(BackupData.serializer(), data))
        assertEquals(1, result.milkBags.size)
    }

    @Test
    fun `rejects end time before start time`() {
        val bad = backup(sleep = listOf(SleepBackup(1, 20, 5, "NAP", null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), bad))
        }
    }

    @Test
    fun `rejects negative pumping volume`() {
        val data = backup(pumping = listOf(PumpingBackup(1, 1, 2, "LEFT", -5, null, null, 0)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects duplicate pumping ids`() {
        val data = backup(
            pumping = listOf(
                PumpingBackup(7, 1, 2, "LEFT", 100, null, null, 0),
                PumpingBackup(7, 3, 4, "RIGHT", 90, null, null, 0),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `propagates malformed json`() {
        assertThrows(SerializationException::class.java) { useCase("{not valid json") }
    }
}
