package com.babytracker.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Switch
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.BuildConfig
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class SettingsSheet {
    NAME, BIRTH_DATE, ALLERGIES, MAX_PER_BREAST, MAX_TOTAL_FEED, THEME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDesignSystem: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = null }
    }

    Scaffold(
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
            // Baby Profile section
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

            // Allergies row with chips
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
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
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

            // Feeding Limits section
            Text(
                text = "Feeding Limits",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsRow(
                label = "Max per breast",
                value = if (uiState.maxPerBreastMinutes > 0) "${uiState.maxPerBreastMinutes} min" else "Disabled",
                onClick = { activeSheet = SettingsSheet.MAX_PER_BREAST }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            SettingsRow(
                label = "Max total feed",
                value = if (uiState.maxTotalFeedMinutes > 0) "${uiState.maxTotalFeedMinutes} min" else "Disabled",
                onClick = { activeSheet = SettingsSheet.MAX_TOTAL_FEED }
            )

            HorizontalDivider()
            
            // App settings section
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

            SettingsSwitchRow(
                label = "Auto-update",
                description = "Check for new versions on launch",
                checked = uiState.autoUpdateEnabled,
                onCheckedChange = viewModel::onAutoUpdateChanged,
            )

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

            // Version footer
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

    // Bottom sheets
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

                SettingsSheet.MAX_PER_BREAST -> MinutesEditSheet(
                    title = "Max per breast",
                    currentMinutes = uiState.maxPerBreastMinutes,
                    onSave = { minutes ->
                        viewModel.onMaxPerBreastChanged(minutes)
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                SettingsSheet.MAX_TOTAL_FEED -> MinutesEditSheet(
                    title = "Max total feed",
                    currentMinutes = uiState.maxTotalFeedMinutes,
                    onSave = { minutes ->
                        viewModel.onMaxTotalFeedChanged(minutes)
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
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
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
            Text("Edit")
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
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
            },
            onCustomNoteChanged = { customNote = it },
            onBack = {},
            onFinish = {},
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
private fun MinutesEditSheet(
    title: String,
    currentMinutes: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "")
    }
    val minutes = text.toIntOrNull() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .imePadding(),
    ) {
        Text(
            text = "Edit $title",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set to 0 to disable the limit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                if (filtered.length <= 3) text = filtered
            },
            label = { Text("Minutes (0 = disabled)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onSave(minutes) }) { Text("Save") }
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
