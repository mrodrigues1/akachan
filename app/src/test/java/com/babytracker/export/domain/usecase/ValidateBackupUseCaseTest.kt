package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BottleFeedBackup
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.GrowthBackup
import com.babytracker.export.domain.model.MilestoneBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
        bottleFeeds: List<BottleFeedBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = version, roomSchemaVersion = 3, appVersion = "1.0.0",
        exportedAt = 0, baby = null, settings = settings(),
        breastfeeding = emptyList(), sleep = sleep, pumping = pumping, milkBags = milkBags,
        bottleFeeds = bottleFeeds,
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
    fun `tolerates a v1 backup missing growth, milestones and sex`() {
        // A genuine v1 export omits the v2-only keys entirely.
        val legacyJson = Json { encodeDefaults = false }.encodeToString(
            BackupData.serializer(),
            backup(version = 1, sleep = listOf(SleepBackup(1, 10, 20, "NAP", null))),
        )
        assertTrue("growth" !in legacyJson, "legacy JSON should omit the v2-only growth key")

        val result = useCase(legacyJson)

        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, result.backupFormatVersion)
        assertTrue(result.growth.isEmpty())
        assertTrue(result.milestones.isEmpty())
        assertEquals(1, result.sleep.size)
    }

    @Test
    fun `accepts a current backup carrying growth and free-form milestones`() {
        val data = backup().copy(
            growth = listOf(GrowthBackup(1, 1000, "WEIGHT", 5000, null)),
            milestones = listOf(MilestoneBackup(title = "First steps", dateEpochDay = 100, timeMinuteOfDay = 480)),
        )

        val result = useCase(json.encodeToString(BackupData.serializer(), data))

        assertEquals(1, result.growth.size)
        assertEquals(1, result.milestones.size)
        assertEquals("First steps", result.milestones.first().title)
    }

    @Test
    fun `drops pre-v3 milestones on migration but keeps other data`() {
        // Pre-v3 milestones used the removed WHO-enum shape and cannot map to free-form moments.
        val data = backup(version = 2).copy(
            growth = listOf(GrowthBackup(1, 1000, "WEIGHT", 5000, null)),
            milestones = listOf(MilestoneBackup(title = "legacy", dateEpochDay = 100)),
        )

        val result = useCase(json.encodeToString(BackupData.serializer(), data))

        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, result.backupFormatVersion)
        assertEquals(1, result.growth.size)
        assertTrue(result.milestones.isEmpty())
    }

    @Test
    fun `rejects an invalid growth type`() {
        val data = backup().copy(growth = listOf(GrowthBackup(1, 1000, "NONSENSE", 5000, null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects non-positive growth value`() {
        val data = backup().copy(growth = listOf(GrowthBackup(1, 1000, "WEIGHT", 0, null)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a blank milestone title`() {
        val data = backup().copy(milestones = listOf(MilestoneBackup(title = "   ", dateEpochDay = 100)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a milestone with a negative date`() {
        val data = backup().copy(milestones = listOf(MilestoneBackup(title = "Smile", dateEpochDay = -1)))
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a milestone with an out-of-range time`() {
        val data = backup().copy(
            milestones = listOf(MilestoneBackup(title = "Smile", dateEpochDay = 100, timeMinuteOfDay = 5000)),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects an invalid baby sex`() {
        val data = backup().copy(
            baby = com.babytracker.export.domain.model.BabyBackup("Leo", 100, emptyList(), null, sex = "ROBOT"),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
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
    fun `rejects an invalid bottle feed type`() {
        val data = backup(
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 120, "NONSENSE", null, null, 2_000),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a non-positive bottle feed volume`() {
        val data = backup(
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 0, "FORMULA", null, null, 2_000),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a bottle feed referencing a milk bag absent from the backup`() {
        val data = backup(
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 120, "BREAST_MILK", linkedMilkBagId = 999, notes = null, createdAt = 2_000),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `rejects a bottle feed linked to an active milk bag`() {
        val data = backup(
            milkBags = listOf(MilkBagBackup(3, 10, 90, sourceSessionId = null, usedAt = null, notes = null, createdAt = 5)),
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 120, "BREAST_MILK", linkedMilkBagId = 3, notes = null, createdAt = 2_000),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            useCase(json.encodeToString(BackupData.serializer(), data))
        }
    }

    @Test
    fun `accepts a bottle feed linked to an included milk bag`() {
        val data = backup(
            milkBags = listOf(MilkBagBackup(3, 10, 90, sourceSessionId = null, usedAt = 11, notes = null, createdAt = 5)),
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 120, "BREAST_MILK", linkedMilkBagId = 3, notes = null, createdAt = 2_000),
            ),
        )
        val result = useCase(json.encodeToString(BackupData.serializer(), data))
        assertEquals(1, result.bottleFeeds.size)
    }

    @Test
    fun `propagates malformed json`() {
        assertThrows(SerializationException::class.java) { useCase("{not valid json") }
    }
}
