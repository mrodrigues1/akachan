package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import javax.inject.Inject

class ImportBackupUseCase @Inject constructor(
    private val importer: BackupImporter,
    private val settingsRepository: SettingsRepository,
) {
    /** [data] must already be validated by ValidateBackupUseCase. */
    suspend operator fun invoke(data: BackupData): ImportCounts {
        settingsRepository.markImportInProgress(System.currentTimeMillis())
        val counts = importer.merge(data)
        settingsRepository.restoreFromBackup(data)
        return counts
    }
}
