package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.SettingsBackup
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportBackupUseCaseTest {

    private lateinit var importer: BackupImporter
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ImportBackupUseCase

    private val data = BackupData(
        backupFormatVersion = 1,
        roomSchemaVersion = 3,
        appVersion = "1.0.0",
        exportedAt = 0,
        baby = null,
        settings = SettingsBackup("SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60),
        breastfeeding = emptyList(),
        sleep = emptyList(),
        pumping = emptyList(),
        milkBags = emptyList(),
    )

    @BeforeEach
    fun setup() {
        importer = mockk()
        settingsRepository = mockk()
        useCase = ImportBackupUseCase(importer, settingsRepository)
    }

    @Test
    fun `runs mark, merge, restore in that order`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } returns ImportCounts(0, 0, 0, 0)
        coEvery { settingsRepository.restoreFromBackup(data) } just Runs

        useCase(data)

        coVerifyOrder {
            settingsRepository.markImportInProgress(any())
            importer.merge(data)
            settingsRepository.restoreFromBackup(data)
        }
    }

    @Test
    fun `leaves journal set when room merge fails`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } throws RuntimeException("room boom")

        assertThrows(RuntimeException::class.java) { kotlinx.coroutines.runBlocking { useCase(data) } }

        coVerify(exactly = 0) { settingsRepository.restoreFromBackup(any()) }
    }

    @Test
    fun `returns merge counts`() = runTest {
        coEvery { settingsRepository.markImportInProgress(any()) } just Runs
        coEvery { importer.merge(data) } returns ImportCounts(1, 2, 3, 4)
        coEvery { settingsRepository.restoreFromBackup(data) } just Runs

        assertEquals(ImportCounts(1, 2, 3, 4), useCase(data))
    }
}
