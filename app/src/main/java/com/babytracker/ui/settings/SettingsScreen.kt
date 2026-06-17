package com.babytracker.ui.settings

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.BuildConfig
import com.babytracker.R
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class SettingsSheet {
    NAME, BIRTH_DATE, SEX, ALLERGIES, THEME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDesignSystem: () -> Unit = {},
    onNavigateToManageSharing: () -> Unit = {},
    onNavigateToConnectPartner: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    dataVm: DataExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isDisconnected) {
        if (uiState.isDisconnected) onDisconnect()
    }

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = null }
    }

    val context = LocalContext.current

    val dataState by dataVm.uiState.collectAsStateWithLifecycle()

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(dataVm::exportJsonTo) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(dataVm::savePdfToUri) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(dataVm::prepareImport) }

    LaunchedEffect(dataState.pendingShare) {
        val share = dataState.pendingShare ?: return@LaunchedEffect
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = share.mimeType
            putExtra(Intent.EXTRA_STREAM, share.uri)
            // Required so the receiving app can read the FileProvider Uri.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", share.uri)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share"))
        dataVm.onShareHandled()
    }

    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            if (dataState.status != DataExportUiState.Status.ERROR) {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                dataVm.onMessageShown()
            }
            // ERROR state: DataSection shows an inline persistent surface
        }
    }

    LaunchedEffect(dataState.pendingPdfSave) {
        val fileName = dataState.pendingPdfSave ?: return@LaunchedEffect
        savePdfLauncher.launch(fileName)
        dataVm.onPdfSaveLaunched()
    }

    LaunchedEffect(dataState.savedPdfUri) {
        val pdfUri = dataState.savedPdfUri ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "PDF saved to Downloads",
            actionLabel = "Open",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
        dataVm.onPdfOpenHandled()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
                Text(
                    text = "Baby Profile",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                SettingsRow(
                    label = "Name",
                    value = uiState.baby?.name ?: "Not set",
                    onClick = { activeSheet = SettingsSheet.NAME }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

                val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                val birthDateText = uiState.baby?.let { baby ->
                    "${baby.birthDate.format(dateFormatter)} (${baby.ageInWeeks}w old)"
                } ?: "Not set"
                SettingsRow(
                    label = "Date of birth",
                    value = birthDateText,
                    onClick = { activeSheet = SettingsSheet.BIRTH_DATE }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

                SettingsRow(
                    label = "Sex",
                    value = uiState.baby?.sex?.label ?: "Not set",
                    onClick = { activeSheet = SettingsSheet.SEX }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeSheet = SettingsSheet.ALLERGIES }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allergies",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val allergies = uiState.baby?.allergies.orEmpty()
                        if (allergies.isEmpty()) {
                            Text(
                                text = "None",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                allergies.forEach { allergy ->
                                    SuggestionChip(
                                        onClick = { activeSheet = SettingsSheet.ALLERGIES },
                                        label = { Text(allergy.label) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { activeSheet = SettingsSheet.ALLERGIES }) {
                        Text("Edit")
                    }
                }

                HorizontalDivider()
            }

            Text(
                text = "App Settings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsRow(
                label = "Theme",
                value = when (uiState.themeConfig) {
                    ThemeConfig.SYSTEM -> "System"
                    ThemeConfig.LIGHT -> "Light"
                    ThemeConfig.DARK -> "Dark"
                },
                onClick = { activeSheet = SettingsSheet.THEME }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            VolumeUnitRow(
                selected = uiState.volumeUnit,
                onSelect = viewModel::onVolumeUnitChanged,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            MeasurementSystemRow(
                selected = uiState.measurementSystem,
                onSelect = viewModel::onMeasurementSystemChanged,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            SettingsSwitchRow(
                label = "Auto-update",
                description = "Check for new versions on launch",
                checked = uiState.autoUpdateEnabled,
                onCheckedChange = viewModel::onAutoUpdateChanged,
            )

            if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
                HorizontalDivider()
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                SettingsSwitchRow(
                    label = "Rich notifications",
                    description = "Show progress bars and quick actions on notifications",
                    checked = uiState.richNotificationsEnabled,
                    onCheckedChange = { viewModel.onRichNotificationsToggled(it) },
                )
                SettingsSwitchRow(
                    label = "Partner stash use",
                    description = "Notify me when my partner logs a bottle that uses milk from the stash",
                    checked = uiState.partnerStashNotificationsEnabled,
                    onCheckedChange = { viewModel.onPartnerStashNotificationsToggled(it) },
                )
            }

            if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
                HorizontalDivider()
                DataSection(
                    state = dataState,
                    onSavePdf = dataVm::exportPdfToDevice,
                    onSharePdf = dataVm::exportPdfToShare,
                    onExportJson = { createDocLauncher.launch("baby-tracker-backup.json") },
                    onExportCsv = dataVm::exportCsv,
                    onImport = { openDocLauncher.launch(arrayOf("application/json")) },
                    onConfirmImport = dataVm::confirmImport,
                    onCancelImport = dataVm::cancelImport,
                    onDismissMessage = dataVm::onMessageShown,
                )
            }

            Text(
                text = "Partner Access",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            when (uiState.appMode) {
                AppMode.NONE -> {
                    SettingsRow(
                        label = "Partner Sharing",
                        value = "Set up as primary parent",
                        onClick = onNavigateToManageSharing,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsRow(
                        label = "Connect as Partner",
                        value = "View a partner's baby data",
                        onClick = onNavigateToConnectPartner,
                    )
                }
                AppMode.PRIMARY -> SettingsRow(
                    label = "Manage Sharing",
                    value = "Active",
                    onClick = onNavigateToManageSharing,
                )
                AppMode.PARTNER -> SettingsRow(
                    label = "Disconnect",
                    value = "Stop viewing as partner",
                    actionLabel = "Disconnect",
                    actionColor = MaterialTheme.colorScheme.error,
                    onClick = viewModel::disconnect,
                )
                null -> Unit
            }

            HorizontalDivider()

            if (BuildConfig.DEBUG) {
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                SettingsRow(
                    label = "Design System",
                    value = "Colors, typography, shapes",
                    onClick = onNavigateToDesignSystem,
                )

                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Akachan v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
        ) {
            when (activeSheet) {
                SettingsSheet.NAME -> NameEditSheet(
                    currentName = uiState.baby?.name ?: "",
                    onSave = { newName ->
                        val baby = uiState.baby
                        if (baby != null && newName.isNotBlank()) {
                            viewModel.onSaveBabyProfile(baby.copy(name = newName))
                        }
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                SettingsSheet.BIRTH_DATE -> BirthDateEditSheet(
                    currentDate = uiState.baby?.birthDate ?: LocalDate.now(),
                    onSave = { newDate ->
                        val baby = uiState.baby
                        if (baby != null) {
                            viewModel.onSaveBabyProfile(baby.copy(birthDate = newDate))
                        }
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                SettingsSheet.SEX -> SexSelectionSheet(
                    currentSex = uiState.baby?.sex ?: BabySex.UNSPECIFIED,
                    onSave = { newSex ->
                        val baby = uiState.baby
                        if (baby != null) {
                            viewModel.onSaveBabyProfile(baby.copy(sex = newSex))
                        }
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                SettingsSheet.ALLERGIES -> AllergiesEditSheet(
                    currentAllergies = uiState.baby?.allergies.orEmpty().toSet(),
                    currentCustomNote = uiState.baby?.customAllergyNote ?: "",
                    babyName = uiState.baby?.name ?: "your baby",
                    onSave = { allergies, customNote ->
                        val baby = uiState.baby
                        if (baby != null) {
                            viewModel.onSaveBabyProfile(
                                baby.copy(
                                    allergies = allergies.toList(),
                                    customAllergyNote = customNote.takeIf { it.isNotBlank() },
                                )
                            )
                        }
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                SettingsSheet.THEME -> ThemeSelectionSheet(
                    currentConfig = uiState.themeConfig,
                    onSave = { themeConfig ->
                        viewModel.onThemeConfigChanged(themeConfig)
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                null -> Unit
            }
        }
    }
}

@Composable
internal fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    actionLabel: String = "Edit",
    actionColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(actionLabel, color = actionColor)
        }
    }
}

@Composable
private fun NameEditSheet(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .imePadding(),
    ) {
        Text(
            text = "Edit name",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 50) name = it },
            label = { Text("Baby's name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateEditSheet(
    currentDate: LocalDate,
    onSave: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(currentDate) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Edit date of birth",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = selectedDate.format(formatter),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date of birth") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { showPicker = true }) {
            Text("Change date")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onSave(selectedDate) }) { Text("Save") }
        }
    }

    if (showPicker) {
        val todayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val initialMillis = selectedDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergiesEditSheet(
    currentAllergies: Set<AllergyType>,
    currentCustomNote: String,
    babyName: String,
    onSave: (Set<AllergyType>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAllergies by remember { mutableStateOf(currentAllergies) }
    var customNote by remember { mutableStateOf(currentCustomNote) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Edit allergies",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AllergiesStepContent(
            babyName = babyName,
            selectedAllergies = selectedAllergies,
            customNote = customNote,
            isSaving = false,
            onAllergyToggled = { allergy ->
                selectedAllergies = if (allergy in selectedAllergies) {
                    selectedAllergies - allergy
                } else {
                    selectedAllergies + allergy
                }
                if (allergy == AllergyType.OTHER && allergy !in selectedAllergies) {
                    customNote = ""
                }
            },
            onAllergiesCleared = {
                selectedAllergies = emptySet()
                customNote = ""
            },
            onCustomNoteChanged = { customNote = it },
            onBack = {},
            onFinish = {},
            showHeader = false,
            showActions = false,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onSave(selectedAllergies, customNote) }) { Text("Save") }
        }
    }
}

@Composable
private fun ThemeSelectionSheet(
    currentConfig: ThemeConfig,
    onSave: (ThemeConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Select theme",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))

        ThemeOption(
            label = "System",
            selected = currentConfig == ThemeConfig.SYSTEM,
            onClick = { onSave(ThemeConfig.SYSTEM) },
        )
        ThemeOption(
            label = "Light",
            selected = currentConfig == ThemeConfig.LIGHT,
            onClick = { onSave(ThemeConfig.LIGHT) },
        )
        ThemeOption(
            label = "Dark",
            selected = currentConfig == ThemeConfig.DARK,
            onClick = { onSave(ThemeConfig.DARK) },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
private fun SexSelectionSheet(
    currentSex: BabySex,
    onSave: (BabySex) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Baby's sex",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Used to show growth percentiles against the WHO charts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        BabySex.entries.forEach { sex ->
            ThemeOption(
                label = sex.label,
                selected = currentSex == sex,
                onClick = { onSave(sex) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeUnitRow(
    selected: VolumeUnit,
    onSelect: (VolumeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(VolumeUnit.ML to "mL", VolumeUnit.OZ to "oz")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_volume_unit_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (unit, label) ->
                SegmentedButton(
                    selected = selected == unit,
                    onClick = { onSelect(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementSystemRow(
    selected: MeasurementSystem,
    onSelect: (MeasurementSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        MeasurementSystem.METRIC to "Metric",
        MeasurementSystem.IMPERIAL to "Imperial",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_measurement_system_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (system, label) ->
                SegmentedButton(
                    selected = selected == system,
                    onClick = { onSelect(system) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}
