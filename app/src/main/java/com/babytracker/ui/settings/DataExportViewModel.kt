package com.babytracker.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.export.data.BackupFileReader
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.usecase.ExportBackupUseCase
import com.babytracker.export.domain.usecase.ExportCsvUseCase
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import com.babytracker.export.domain.usecase.ImportBackupUseCase
import com.babytracker.export.domain.usecase.ValidateBackupUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class ShareArtifact(val uri: Uri, val mimeType: String)

data class ImportPreview(
    val data: BackupData,
    val breastfeeding: Int,
    val sleep: Int,
    val pumping: Int,
    val milkBags: Int,
    val bottleFeeds: Int = 0,
    val growth: Int = 0,
    val milestones: Int = 0,
)

data class DataExportUiState(
    val status: Status = Status.IDLE,
    val pendingShare: ShareArtifact? = null,
    val pendingPdfSave: String? = null,
    val savedPdfUri: Uri? = null,
    val importPreview: ImportPreview? = null,
    val message: String? = null,
    val importIncomplete: Boolean = false,
) {
    enum class Status { IDLE, WORKING, SUCCESS, ERROR }
}

@HiltViewModel
class DataExportViewModel @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val generatePdf: GeneratePdfReportUseCase,
    private val validateBackup: ValidateBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    private val reader: BackupFileReader,
    private val writer: BackupFileWriter,
    private val settingsRepository: SettingsRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val pumpingRepository: PumpingRepository,
    private val sleepRepository: SleepRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataExportUiState())
    val uiState: StateFlow<DataExportUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var pendingPdfBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            settingsRepository.isImportInProgress().collect { inProgress ->
                _uiState.update { it.copy(importIncomplete = inProgress) }
            }
        }
    }

    fun exportJsonTo(uri: Uri) = execute(appContext.getString(R.string.msg_backup_saved)) {
        writer.writeToUri(uri, exportBackup())
    }

    fun exportCsv() = execute {
        val zip = zipCsvs(exportCsvUseCase())
        val uri = writer.writeCacheBytes("baby-data-${System.currentTimeMillis()}.zip", zip)
        _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, pendingShare = ShareArtifact(uri, "application/zip")) }
    }

    fun exportPdfToDevice(range: DateRange) = execute {
        val bytes = generatePdf(range)
        val fileName = "baby-report-${System.currentTimeMillis()}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = writer.savePdfToDownloads(fileName, bytes)
            _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, savedPdfUri = uri) }
        } else {
            pendingPdfBytes = bytes
            _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, pendingPdfSave = fileName) }
        }
    }

    fun onPdfSaveLaunched() {
        _uiState.update { it.copy(pendingPdfSave = null) }
    }

    fun savePdfToUri(uri: Uri) = execute {
        val bytes = pendingPdfBytes ?: return@execute
        writer.writeBytesToUri(uri, bytes)
        pendingPdfBytes = null
        _uiState.update { it.copy(savedPdfUri = uri) }
    }

    fun exportPdfToShare(range: DateRange) = execute {
        val bytes = generatePdf(range)
        val uri = writer.writeCacheBytes("baby-report-${System.currentTimeMillis()}.pdf", bytes)
        _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, pendingShare = ShareArtifact(uri, "application/pdf")) }
    }

    fun prepareImport(uri: Uri) = execute {
        if (hasAnyActiveSession()) {
            _uiState.update {
                it.copy(
                    status = DataExportUiState.Status.ERROR,
                    message = appContext.getString(R.string.error_import_stop_sessions),
                )
            }
            return@execute
        }
        // reader.read does its own IO; the parse + multi-pass validation is CPU-bound, so keep it
        // off the main thread to avoid a long frame / ANR on large backups.
        val raw = reader.read(uri)
        val data = withContext(Dispatchers.Default) { validateBackup(raw) }
        _uiState.update {
            it.copy(
                status = DataExportUiState.Status.IDLE,
                importPreview = ImportPreview(
                    data = data,
                    breastfeeding = data.breastfeeding.size,
                    sleep = data.sleep.size,
                    pumping = data.pumping.size,
                    milkBags = data.milkBags.size,
                    bottleFeeds = data.bottleFeeds.size,
                    growth = data.growth.size,
                    milestones = data.milestones.size,
                ),
            )
        }
    }

    fun confirmImport() {
        val preview = _uiState.value.importPreview ?: return
        _uiState.update { it.copy(importPreview = null) }
        execute(appContext.getString(R.string.msg_backup_imported)) {
            importBackup(preview.data)
            runCatching { syncToFirestore() }
        }
    }

    fun cancelImport() {
        _uiState.update { it.copy(importPreview = null, status = DataExportUiState.Status.IDLE) }
    }

    fun onShareHandled() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    fun onPdfOpenHandled() {
        _uiState.update { it.copy(savedPdfUri = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null, status = DataExportUiState.Status.IDLE) }
    }

    private suspend fun hasAnyActiveSession(): Boolean =
        breastfeedingRepository.getActiveSession().first() != null ||
            pumpingRepository.getActiveSession().first() != null ||
            sleepRepository.getRecentRecords(1).firstOrNull()?.let { it.endTime == null } == true

    private fun execute(successMessage: String? = null, block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(status = DataExportUiState.Status.WORKING, message = null) }
            try {
                block()
                _uiState.update {
                    if (it.status == DataExportUiState.Status.WORKING) {
                        it.copy(
                            status = if (successMessage != null) DataExportUiState.Status.SUCCESS else DataExportUiState.Status.IDLE,
                            message = successMessage,
                        )
                    } else {
                        it // block set its own terminal state (share artifact / import preview)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(status = DataExportUiState.Status.ERROR, message = appContext.getString(R.string.error_export_failed))
                }
            }
        }
    }

    private fun zipCsvs(tables: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            tables.forEach { (table, csv) ->
                zip.putNextEntry(ZipEntry("$table.csv"))
                zip.write(csv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
