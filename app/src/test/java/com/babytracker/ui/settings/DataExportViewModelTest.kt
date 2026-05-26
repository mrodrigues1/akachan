package com.babytracker.ui.settings

import android.net.Uri
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.export.data.BackupFileReader
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import com.babytracker.export.domain.usecase.ExportBackupUseCase
import com.babytracker.export.domain.usecase.ExportCsvUseCase
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import com.babytracker.export.domain.usecase.ImportBackupUseCase
import com.babytracker.export.domain.usecase.ValidateBackupUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DataExportViewModelTest {

    private val exportBackup: ExportBackupUseCase = mockk()
    private val exportCsvUseCase: ExportCsvUseCase = mockk()
    private val generatePdf: GeneratePdfReportUseCase = mockk()
    private val validateBackup: ValidateBackupUseCase = mockk()
    private val importBackup: ImportBackupUseCase = mockk()
    private val reader: BackupFileReader = mockk()
    private val writer: BackupFileWriter = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val pumpingRepository: PumpingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()
    private val syncToFirestore: SyncToFirestoreUseCase = mockk()
    private val uri: Uri = mockk(relaxed = true)

    private lateinit var vm: DataExportViewModel

    private fun backup() = BackupData(
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
        Dispatchers.setMain(StandardTestDispatcher())
        every { settingsRepository.isImportInProgress() } returns flowOf(false)
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        coEvery { sleepRepository.getRecentRecords(1) } returns emptyList()
        coEvery { syncToFirestore(any()) } just Runs
        vm = DataExportViewModel(
            exportBackup, exportCsvUseCase, generatePdf, validateBackup, importBackup,
            reader, writer, settingsRepository, breastfeedingRepository, pumpingRepository,
            sleepRepository, syncToFirestore,
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exportJsonTo writes durable backup and reports success`() = runTest {
        coEvery { exportBackup() } returns "{\"x\":1}"
        coEvery { writer.writeToUri(uri, "{\"x\":1}") } just Runs

        vm.exportJsonTo(uri)
        advanceUntilIdle()

        coVerify { writer.writeToUri(uri, "{\"x\":1}") }
        assertEquals(DataExportUiState.Status.SUCCESS, vm.uiState.value.status)
    }

    @Test
    fun `prepareImport surfaces preview counts`() = runTest {
        val data = backup().copy(
            sleep = listOf(SleepBackup(1L, 1L, 2L, "NAP", null)),
        )
        coEvery { reader.read(uri) } returns "json"
        every { validateBackup("json") } returns data

        vm.prepareImport(uri)
        advanceUntilIdle()

        val preview = vm.uiState.value.importPreview!!
        assertEquals(1, preview.sleep)
        assertEquals(0, preview.pumping)
    }

    @Test
    fun `prepareImport maps validation failure to error`() = runTest {
        coEvery { reader.read(uri) } returns "json"
        every { validateBackup("json") } throws InvalidBackupException("bad")

        vm.prepareImport(uri)
        advanceUntilIdle()

        assertEquals(DataExportUiState.Status.ERROR, vm.uiState.value.status)
        assertNull(vm.uiState.value.importPreview)
    }

    @Test
    fun `confirmImport runs import and clears preview`() = runTest {
        val data = backup()
        coEvery { reader.read(uri) } returns "json"
        every { validateBackup("json") } returns data
        coEvery { importBackup(data) } returns ImportCounts(0, 0, 0, 0)

        vm.prepareImport(uri)
        advanceUntilIdle()
        vm.confirmImport()
        vm.confirmImport()
        advanceUntilIdle()

        coVerify(exactly = 1) { importBackup(data) }
        assertNull(vm.uiState.value.importPreview)
        assertEquals(DataExportUiState.Status.SUCCESS, vm.uiState.value.status)
    }

    @Test
    fun `exportCsv produces a zip share artifact`() = runTest {
        coEvery { exportCsvUseCase() } returns mapOf("sleep" to "id\n1\n")
        coEvery { writer.writeCacheBytes(any(), any()) } returns uri

        vm.exportCsv()
        advanceUntilIdle()

        assertEquals("application/zip", vm.uiState.value.pendingShare?.mimeType)
        coVerify { writer.writeCacheBytes(match { it.endsWith(".zip") }, any()) }
    }

    @Test
    fun `exportPdf produces a pdf share artifact`() = runTest {
        coEvery { generatePdf(any()) } returns byteArrayOf(37, 80, 68, 70)
        coEvery { writer.writeCacheBytes(any(), any()) } returns uri

        vm.exportPdf(DateRange.lastDays(7L, Instant.parse("2026-05-24T00:00:00Z")))
        advanceUntilIdle()

        assertEquals("application/pdf", vm.uiState.value.pendingShare?.mimeType)
    }

    @Test
    fun `prepareImport blocks when active breastfeeding session exists`() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(mockk<BreastfeedingSession>())

        vm.prepareImport(uri)
        advanceUntilIdle()

        assertEquals(DataExportUiState.Status.ERROR, vm.uiState.value.status)
        assertNull(vm.uiState.value.importPreview)
    }

    @Test
    fun `confirmImport triggers firestore sync`() = runTest {
        val data = backup()
        coEvery { reader.read(uri) } returns "json"
        every { validateBackup("json") } returns data
        coEvery { importBackup(data) } returns ImportCounts(0, 0, 0, 0)

        vm.prepareImport(uri)
        advanceUntilIdle()
        vm.confirmImport()
        advanceUntilIdle()

        coVerify { syncToFirestore(any()) }
    }
}
