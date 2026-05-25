# Settings UI Wiring — Data Export & Import — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-38

**Goal:** Add a "Data" section to Settings — Export PDF (date-range picker), Export backup (JSON, durable), Export CSV (zipped, shareable), Import backup (file picker + confirmation dialog with record counts) — driven by a `DataExportViewModel`, plus a non-blocking "last import may be incomplete" recovery notice.

**Architecture:** `DataExportViewModel` exposes a single `StateFlow<DataExportUiState>` and event handlers, orchestrating the PR1–PR3 use cases. The durable JSON write uses `ActivityResultContracts.CreateDocument` (fresh document → `BackupFileWriter.writeToUri`); CSV (zipped) and PDF use `BackupFileWriter.writeCacheBytes` + `ACTION_SEND`. Import uses `ActivityResultContracts.OpenDocument` → `BackupFileReader` → `ValidateBackupUseCase` (preview counts) → confirm → `ImportBackupUseCase`. The ViewModel never touches Android `Intent`/SAF directly — the Composable owns launchers and the share intent (with the required URI-grant flags); the ViewModel owns content generation + state.

**Tech Stack:** Jetpack Compose (Material 3), Hilt `@HiltViewModel`, Coroutines/Flow, JUnit 5 + MockK + Turbine (ViewModel), Robolectric + `createComposeRule` (UI smoke).

**Dependencies:** PR1 (`ExportBackupUseCase`, `ExportCsvUseCase`, `BackupFileWriter`), PR2 (`GeneratePdfReportUseCase`, `DateRange`), PR3 (`BackupFileReader`, `ValidateBackupUseCase`, `ImportBackupUseCase`, `ImportCounts`, `SettingsRepository.isImportInProgress`, backup exceptions). **Merge PR1–PR3 first.**

**Suggested implementation branch:** `feat/data-export-settings-ui`

---

## Background: verified codebase facts

- `SettingsScreen` (`ui/settings/SettingsScreen.kt`) is a `Scaffold` + scrolling `Column` of sections. Sections use a `labelMedium`/primary-color header `Text` and a reusable `SettingsRow(label, value, onClick)` composable, separated by `HorizontalDivider`. It already imports `rememberLauncherForActivityResult`, `ActivityResultContracts`, `LocalContext`, `AlertDialog`, `TextButton`, `Button`.
- `SettingsViewModel` is a separate `@HiltViewModel` exposing `StateFlow<SettingsUiState>`. This plan adds a **second** ViewModel (`DataExportViewModel`) obtained via `hiltViewModel()` in `SettingsScreen` — the Data section's state is independent of `SettingsUiState`, and bolting it onto the existing 15-flow `combine` would be unwieldy.
- Use cases available after PR1–PR3 (all `@Inject`-constructed, Hilt-ready):
  - `ExportBackupUseCase()` → JSON `String` (PR1).
  - `ExportCsvUseCase()` → `Map<String, String>` per-table CSV (PR1).
  - `GeneratePdfReportUseCase(range: DateRange)` → `ByteArray` (PR2).
  - `ValidateBackupUseCase(json: String)` → `BackupData` (PR3; throws `BackupTooNewException`/`InvalidBackupException`/`SerializationException`).
  - `ImportBackupUseCase(data: BackupData)` → `ImportCounts` (PR3).
  - `BackupFileReader.read(uri)` → `String` (PR3, suspend).
  - `BackupFileWriter.writeToUri(uri, content)` / `writeCacheBytes(fileName, bytes)` (PR1/PR2, suspend).
  - `SettingsRepository.isImportInProgress(): Flow<Boolean>` (PR3).
- `DateRange.lastDays(n)` builds 7/14/30-day presets; `DateRange(start, end)` for custom (PR2).
- FileProvider authority `${applicationId}.fileprovider` is registered (PR1).
- Compose Robolectric tests run via `createComposeRule()` in `src/test/` (CLAUDE.md); the JUnit4 Vintage engine (PR1) discovers them.

---

## File Structure

**Create:**
- `app/src/main/java/com/babytracker/ui/settings/DataExportViewModel.kt` — `DataExportViewModel` + `DataExportUiState` + `ShareArtifact` + `ImportPreview`.
- `app/src/main/java/com/babytracker/ui/settings/DataSection.kt` — the "Data" section composable + range-picker dialog + import-confirm dialog.
- Tests: `app/src/test/java/com/babytracker/ui/settings/DataExportViewModelTest.kt`, `app/src/test/java/com/babytracker/ui/settings/DataSectionTest.kt`.

**Modify:**
- `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` — render `DataSection(...)` and wire the SAF launchers + share intent.

---

## Task 1: DataExportViewModel + UiState

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/settings/DataExportViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/settings/DataExportViewModelTest.kt`

State is a single data class: a `status`, an optional `pendingShare` (CSV/PDF to fire `ACTION_SEND`), an optional `importPreview` (counts for the confirm dialog), an optional `message` (success/error text), and `importIncomplete` (journal recovery notice). One-shot fields (`pendingShare`, `message`) are cleared by explicit `onXHandled()` calls so recomposition can't re-fire them.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.ui.settings

import android.net.Uri
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.data.BackupFileReader
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.usecase.ExportBackupUseCase
import com.babytracker.export.domain.usecase.ExportCsvUseCase
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import com.babytracker.export.domain.usecase.ImportBackupUseCase
import com.babytracker.export.domain.usecase.ValidateBackupUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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

class DataExportViewModelTest {

    private val exportBackup: ExportBackupUseCase = mockk()
    private val exportCsvUseCase: ExportCsvUseCase = mockk()
    private val generatePdf: GeneratePdfReportUseCase = mockk()
    private val validateBackup: ValidateBackupUseCase = mockk()
    private val importBackup: ImportBackupUseCase = mockk()
    private val reader: BackupFileReader = mockk()
    private val writer: BackupFileWriter = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val uri: Uri = mockk(relaxed = true)

    private lateinit var vm: DataExportViewModel

    private fun backup() = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null,
        settings = SettingsBackup("SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60),
        breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { settingsRepository.isImportInProgress() } returns flowOf(false)
        vm = DataExportViewModel(
            exportBackup, exportCsvUseCase, generatePdf, validateBackup, importBackup,
            reader, writer, settingsRepository,
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
            sleep = listOf(com.babytracker.export.domain.model.SleepBackup(1, 1, 2, "NAP", null)),
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

        vm.prepareImport(uri); advanceUntilIdle()
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

        vm.exportCsv(); advanceUntilIdle()

        assertEquals("application/zip", vm.uiState.value.pendingShare?.mimeType)
        coVerify { writer.writeCacheBytes(match { it.endsWith(".zip") }, any()) }
    }

    @Test
    fun `exportPdf produces a pdf share artifact`() = runTest {
        coEvery { generatePdf(any()) } returns byteArrayOf(37, 80, 68, 70)
        coEvery { writer.writeCacheBytes(any(), any()) } returns uri

        vm.exportPdf(DateRange.lastDays(7, Instant.parse("2026-05-24T00:00:00Z")))
        advanceUntilIdle()

        assertEquals("application/pdf", vm.uiState.value.pendingShare?.mimeType)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.DataExportViewModelTest" -PfastTests`
Expected: FAIL — unresolved `DataExportViewModel`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.data.BackupFileReader
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.usecase.ExportBackupUseCase
import com.babytracker.export.domain.usecase.ExportCsvUseCase
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import com.babytracker.export.domain.usecase.ImportBackupUseCase
import com.babytracker.export.domain.usecase.ValidateBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

data class DataExportUiState(
    val status: Status = Status.IDLE,
    val pendingShare: ShareArtifact? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataExportUiState())
    val uiState: StateFlow<DataExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.isImportInProgress().collect { inProgress ->
                _uiState.update { it.copy(importIncomplete = inProgress) }
            }
        }
    }

    fun exportJsonTo(uri: Uri) = run("Backup saved") {
        writer.writeToUri(uri, exportBackup())
    }

    fun exportCsv() = run {
        val zip = zipCsvs(exportCsvUseCase())
        val uri = writer.writeCacheBytes("baby-data-${System.currentTimeMillis()}.zip", zip)
        _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, pendingShare = ShareArtifact(uri, "application/zip")) }
    }

    fun exportPdf(range: DateRange) = run {
        val bytes = generatePdf(range)
        val uri = writer.writeCacheBytes("baby-report-${System.currentTimeMillis()}.pdf", bytes)
        _uiState.update { it.copy(status = DataExportUiState.Status.IDLE, pendingShare = ShareArtifact(uri, "application/pdf")) }
    }

    fun prepareImport(uri: Uri) = run {
        val data = validateBackup(reader.read(uri))
        _uiState.update {
            it.copy(
                status = DataExportUiState.Status.IDLE,
                importPreview = ImportPreview(
                    data = data,
                    breastfeeding = data.breastfeeding.size,
                    sleep = data.sleep.size,
                    pumping = data.pumping.size,
                    milkBags = data.milkBags.size,
                ),
            )
        }
    }

    fun confirmImport() {
        val preview = _uiState.value.importPreview ?: return
        _uiState.update { it.copy(importPreview = null) }
        runWithMessage("Backup imported") {
            importBackup(preview.data)
        }
    }

    fun cancelImport() {
        _uiState.update { it.copy(importPreview = null, status = DataExportUiState.Status.IDLE) }
    }

    fun onShareHandled() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null, status = DataExportUiState.Status.IDLE) }
    }

    // --- helpers ---

    /** Runs [block]; on success sets SUCCESS + [successMessage]; on failure ERROR + the error text. */
    private fun run(successMessage: String? = null, block: suspend () -> Unit) {
        runWithMessage(successMessage, block)
    }

    private fun runWithMessage(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
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
                        it // a block that set its own terminal state (e.g. share/preview) wins
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(status = DataExportUiState.Status.ERROR, message = e.message ?: "Something went wrong")
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
```

> The two-arg `run`/`runWithMessage` share one launch+try/catch so every operation gets WORKING → SUCCESS/ERROR and exceptions (incl. `BackupTooNewException`/`InvalidBackupException`/`SerializationException` from import, IO failures from export) surface as `ERROR` + `message`. Blocks that set their own terminal state (share artifact ready, import preview ready) leave `status` non-WORKING and are preserved. `catch (e: Exception)` is intentional at this UI boundary — every failure must become a user-visible error, never crash the screen.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.DataExportViewModelTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/DataExportViewModel.kt app/src/test/java/com/babytracker/ui/settings/DataExportViewModelTest.kt
git commit -m "feat(settings): add DataExportViewModel for export/import orchestration"
```

---

## Task 2: Data section composable + dialogs

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/settings/DataSection.kt`

The section renders four rows + a recovery banner. It is stateless about SAF — it takes callbacks the `SettingsScreen` wires to launchers. It owns the two dialogs (range picker, import confirm).

- [ ] **Step 1: Write the composable**

```kotlin
package com.babytracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.babytracker.export.domain.model.DateRange
import java.time.Instant

@Composable
fun DataSection(
    state: DataExportUiState,
    onExportPdf: (DateRange) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
) {
    var showRangeDialog by remember { mutableStateOf(false) }

    Text(
        text = "Data",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    if (state.importIncomplete) {
        Text(
            text = "Your last import may be incomplete. Re-import your backup to finish.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("importIncompleteNotice"),
        )
    }

    val working = state.status == DataExportUiState.Status.WORKING
    SettingsRow(label = "Export PDF report", value = "", onClick = { if (!working) showRangeDialog = true })
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    SettingsRow(label = "Export backup (JSON)", value = "", onClick = { if (!working) onExportJson() })
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    SettingsRow(label = "Export data (CSV)", value = "", onClick = { if (!working) onExportCsv() })
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    SettingsRow(label = "Import backup", value = "", onClick = { if (!working) onImport() })

    if (showRangeDialog) {
        RangePickerDialog(
            onDismiss = { showRangeDialog = false },
            onConfirm = { days ->
                showRangeDialog = false
                onExportPdf(DateRange.lastDays(days.toLong(), Instant.now()))
            },
        )
    }

    val preview = state.importPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("Import backup?") },
            text = {
                Text(
                    "This will merge into your current data:\n" +
                        "• ${preview.breastfeeding} feeding sessions\n" +
                        "• ${preview.sleep} sleep records\n" +
                        "• ${preview.pumping} pumping sessions\n" +
                        "• ${preview.milkBags} milk bags\n\n" +
                        "Existing records are kept; duplicates are skipped.",
                    modifier = Modifier.testTag("importConfirmText"),
                )
            },
            confirmButton = { TextButton(onClick = onConfirmImport, enabled = !working) { Text("Import") } },
            dismissButton = { TextButton(onClick = onCancelImport) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RangePickerDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val options = listOf(7, 14, 30)
    var selected by remember { mutableIntStateOf(7) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF date range") },
        text = {
            Column {
                options.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == days, onClick = { selected = days })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected == days, onClick = { selected = days })
                        Text("Last $days days")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

> Custom range (arbitrary start/end) is intentionally out of scope for v1 of this UI — the three presets cover the doctor-visit use case. `DateRange` already supports custom bounds (PR2) if a date-range picker is added later. `SettingsRow` is the existing composable in `SettingsScreen.kt`; if it is `private` there, hoist it to a top-level `internal` composable (small refactor) so `DataSection` can reuse it. Do not duplicate it.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`. (If `SettingsRow` was `private`, this step fails until it is hoisted — make it `internal`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/DataSection.kt app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): add Data section composable with range and import dialogs"
```

---

## Task 3: Wire SAF launchers + share intent into SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add the ViewModel + launchers + share effect**

Inside `SettingsScreen`, after the existing `viewModel`/`uiState`, add (the `context` val already exists):

```kotlin
val dataVm: DataExportViewModel = hiltViewModel()
val dataState by dataVm.uiState.collectAsStateWithLifecycle()

val createDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
) { uri -> uri?.let(dataVm::exportJsonTo) }

val openDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
) { uri -> uri?.let(dataVm::prepareImport) }

// Fire ACTION_SEND once when a share artifact (CSV zip / PDF) is ready.
LaunchedEffect(dataState.pendingShare) {
    val share = dataState.pendingShare ?: return@LaunchedEffect
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = share.mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, share.uri)
        // Required so the receiving app can actually read the FileProvider Uri (PR1 grant note).
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("", share.uri)
    }
    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share"))
    dataVm.onShareHandled()
}

// Surface success/error text as a toast, then clear it.
LaunchedEffect(dataState.message) {
    dataState.message?.let {
        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        dataVm.onMessageShown()
    }
}
```

- [ ] **Step 2: Render the Data section in the scrolling Column**

Add at an appropriate point in the section `Column` (e.g. after the feed-timing section, before the Developer/debug section), guarded the same way other owner-only sections are (`uiState.appMode != null && uiState.appMode != AppMode.PARTNER`) — a partner device is read-only and must not export/import, including during app-mode loading:

```kotlin
if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
    DataSection(
        state = dataState,
        onExportPdf = dataVm::exportPdf,
        onExportJson = { createDocLauncher.launch("baby-tracker-backup.json") },
        onExportCsv = dataVm::exportCsv,
        onImport = { openDocLauncher.launch(arrayOf("application/json")) },
        onConfirmImport = dataVm::confirmImport,
        onCancelImport = dataVm::cancelImport,
    )
}
```

> `CreateDocument.launch("baby-tracker-backup.json")` supplies the suggested filename; the system picker creates a **fresh** document (auto-suffixing on collision), satisfying PR1's never-overwrite-in-place contract. `OpenDocument.launch(arrayOf("application/json"))` filters to JSON backups.

- [ ] **Step 3: Verify it compiles + manual smoke**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

Manual (emulator, per CLAUDE.md WSL instructions — note in the PR if you cannot run a device): open Settings → Data; verify Export JSON opens the create-document picker and writes a file; Export CSV/PDF open the share sheet; Import opens the file picker, shows the confirm dialog with counts, and merges on confirm.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): wire SAF launchers and share intent for data export/import"
```

---

## Task 4: Compose UI test + arch/full suite

**Files:**
- Create: `app/src/test/java/com/babytracker/ui/settings/DataSectionTest.kt`

- [ ] **Step 1: Write the Robolectric Compose test**

```kotlin
package com.babytracker.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun wrap(content: @Composable () -> Unit) { MaterialTheme { content() } }

    @Test
    fun `shows import confirm dialog with counts`() {
        val preview = ImportPreview(
            data = mockBackup(), breastfeeding = 3, sleep = 2, pumping = 1, milkBags = 4,
        )
        composeRule.setContent {
            wrap {
                DataSection(
                    state = DataExportUiState(importPreview = preview),
                    onExportPdf = {}, onExportJson = {}, onExportCsv = {},
                    onImport = {}, onConfirmImport = {}, onCancelImport = {},
                )
            }
        }
        composeRule.onNodeWithTag("importConfirmText").assertIsDisplayed()
        composeRule.onNodeWithText("3 feeding sessions", substring = true).assertIsDisplayed()
    }

    @Test
    fun `shows incomplete-import recovery notice`() {
        composeRule.setContent {
            wrap {
                DataSection(
                    state = DataExportUiState(importIncomplete = true),
                    onExportPdf = {}, onExportJson = {}, onExportCsv = {},
                    onImport = {}, onConfirmImport = {}, onCancelImport = {},
                )
            }
        }
        composeRule.onNodeWithTag("importIncompleteNotice").assertIsDisplayed()
    }

    private fun mockBackup() = com.babytracker.export.domain.model.BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null,
        settings = com.babytracker.export.domain.model.SettingsBackup(
            "SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60,
        ),
        breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
    )
}
```

- [ ] **Step 2: Run the Compose test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.DataSectionTest" -PfastTests`
Expected: PASS. If `createComposeRule` under Robolectric needs a specific SDK, add `@Config(sdk = [34])` per the project's other Compose-Robolectric tests (if none exist yet, the default Robolectric SDK should work; verify on first run).

- [ ] **Step 3: Architecture + formatting + full suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: PASS. (UI may import `export.domain`/`export.data` use cases + `BackupFileReader`/`BackupFileWriter` — the UI-layer rule only forbids `com.babytracker.data` (core data) and `.dao.` imports in ViewModels; the export use cases/readers are the public surface, mirroring how UI already depends on other use cases. Confirm the `ViewModels do not import Room DAOs directly` rule still passes — `DataExportViewModel` imports use cases + `BackupFileReader`/`BackupFileWriter`, not DAOs.)

Run: `./gradlew ktlintFormat detekt`
Expected: `BUILD SUCCESSFUL`. Fix detekt findings by changing code (never `@Suppress`).

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/babytracker/ui/settings/DataSectionTest.kt
git commit -m "test(settings): add Compose test for Data section dialogs"
```
(Plus a `style(settings): apply ktlint formatting` commit if needed.)

---

## Acceptance Criteria

- Settings shows a "Data" section (hidden while app mode is loading and for `PARTNER` app mode) with Export PDF (range dialog), Export backup (JSON), Export data (CSV), Import backup rows.
- `DataExportViewModel` exposes one `StateFlow<DataExportUiState>` (IDLE/WORKING/SUCCESS/ERROR) plus `pendingShare`, `importPreview`, `message`, `importIncomplete`; every operation transitions WORKING→SUCCESS/ERROR and import/validation exceptions surface as user-visible errors.
- Export JSON writes durably via `CreateDocument` + `writeToUri`; Export CSV (zipped) and PDF write via `writeCacheBytes` and share via `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData`.
- Import reads via `OpenDocument` → `BackupFileReader` → `ValidateBackupUseCase` (confirm dialog shows per-table counts) → `ImportBackupUseCase` on confirm.
- A non-blocking recovery notice appears when `SettingsRepository.isImportInProgress()` is `true`.
- `./gradlew :app:testDebugUnitTest`, `ktlintFormat`, `detekt`, and the architecture tests all pass.

## Notes

- This completes the Data Export & Import project (PRs 1–4). After merge, update `CLAUDE.md`: document the `export/` package and correct the stale Room v2 → v3 reference.
- The launch-time recovery notice is surfaced in Settings here; if a more prominent prompt is wanted (e.g. on Home at app start), that is a small follow-up reading the same `isImportInProgress()` flow.
