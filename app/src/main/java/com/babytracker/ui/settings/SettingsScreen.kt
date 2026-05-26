package com.babytracker.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.BuildConfig
import com.babytracker.R
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LEAD_TIME_MINUTES = listOf(5, 10, 15, 30)
private const val MINUTES_PER_DAY = 1440

private enum class SettingsSheet {
    NAME, BIRTH_DATE, ALLERGIES, MAX_PER_BREAST, MAX_TOTAL_FEED, THEME
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
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            dataVm.onMessageShown()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshNotificationsPermission(granted)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onLifecycleResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
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

                HorizontalDivider()
                Text(
                    text = stringResource(R.string.settings_section_feeding_reminders),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                SettingsSwitchRow(
                    label = stringResource(R.string.settings_predictive_toggle_title),
                    description = stringResource(R.string.settings_predictive_toggle_subtitle),
                    checked = uiState.predictiveEnabled,
                    onCheckedChange = { newValue ->
                        if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = NotificationManagerCompat.from(context)
                                .areNotificationsEnabled()
                            viewModel.onPredictiveToggleChanged(true)
                            if (!granted) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.onPredictiveToggleChanged(newValue)
                        }
                    },
                    modifier = Modifier.testTag("predictive_switch"),
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                )

                if (uiState.predictiveEnabled && uiState.validIntervalCount < 3) {
                    val remaining = 3 - uiState.validIntervalCount
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.settings_predictive_feeds_remaining,
                                    remaining,
                                    remaining,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        icon = {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }

                if (uiState.showPermissionWarning) {
                    WarningRow(
                        title = stringResource(R.string.settings_notifications_blocked_title),
                        subtitle = stringResource(R.string.settings_notifications_blocked_subtitle),
                        actionLabel = stringResource(R.string.settings_open_settings),
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                },
                            )
                        },
                    )
                }

                LeadTimeSegmentedRow(
                    enabled = uiState.predictiveEnabled && uiState.validIntervalCount >= 3,
                    selectedMinutes = uiState.predictiveLeadMinutes,
                    onSelect = viewModel::onLeadMinutesChanged,
                )

                val is24Hour = DateFormat.is24HourFormat(context)
                QuietHoursRow(
                    enabled = uiState.predictiveEnabled && uiState.validIntervalCount >= 3,
                    startMinute = uiState.quietHoursStartMinute,
                    endMinute = uiState.quietHoursEndMinute,
                    is24Hour = is24Hour,
                    onStartPicked = viewModel::onQuietHoursStartChanged,
                    onEndPicked = viewModel::onQuietHoursEndChanged,
                )

                HorizontalDivider()
                Text(
                    text = stringResource(R.string.settings_section_sleep_reminders),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                SettingsSwitchRow(
                    label = stringResource(R.string.settings_nap_reminder_toggle_title),
                    description = stringResource(R.string.settings_nap_reminder_toggle_subtitle),
                    checked = uiState.napReminderEnabled,
                    onCheckedChange = { newValue ->
                        if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = NotificationManagerCompat.from(context)
                                .areNotificationsEnabled()
                            viewModel.onNapReminderToggleChanged(true)
                            if (!granted) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.onNapReminderToggleChanged(newValue)
                        }
                    },
                    modifier = Modifier.testTag("nap_reminder_switch"),
                    leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
                )

                OutlinedTextField(
                    value = if (uiState.napReminderDelayMinutes > 0) uiState.napReminderDelayMinutes.toString() else "",
                    onValueChange = { input ->
                        input.toIntOrNull()?.let { viewModel.onNapReminderDelayChanged(it) }
                    },
                    label = { Text(stringResource(R.string.settings_nap_reminder_delay_label)) },
                    supportingText = { Text("Fires this many minutes after a tracked nap ends. Set to 0 for immediate.") },
                    enabled = uiState.napReminderEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .alpha(if (uiState.napReminderEnabled) 1f else 0.38f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
                HorizontalDivider()
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
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(12.dp))
        }
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

@Composable
private fun WarningRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = WarningAmber,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadTimeSegmentedRow(
    enabled: Boolean,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
) {
    val leadTimeLabels = mapOf(
        5 to R.string.settings_lead_time_5,
        10 to R.string.settings_lead_time_10,
        15 to R.string.settings_lead_time_15,
        30 to R.string.settings_lead_time_30,
    )
    val options = LEAD_TIME_MINUTES.map { it to leadTimeLabels.getValue(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Text(
            text = stringResource(R.string.settings_lead_time_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (minutes, labelRes) ->
                SegmentedButton(
                    selected = selectedMinutes == minutes,
                    onClick = { onSelect(minutes) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursRow(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    is24Hour: Boolean,
    onStartPicked: (Int) -> Unit,
    onEndPicked: (Int) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val formatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
    fun formatMinute(minuteOfDay: Int): String =
        LocalTime.ofSecondOfDay((minuteOfDay % MINUTES_PER_DAY) * 60L).format(formatter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Text(
            text = stringResource(R.string.settings_quiet_hours_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showStartPicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_quiet_hours_start),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatMinute(startMinute),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showEndPicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_quiet_hours_end),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatMinute(endMinute),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val helperText = if (startMinute == endMinute) {
            stringResource(R.string.settings_quiet_hours_disabled)
        } else {
            stringResource(R.string.settings_quiet_hours_helper)
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismiss = { showStartPicker = false },
            onConfirm = {
                onStartPicked(state.hour * 60 + state.minute)
                showStartPicker = false
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismiss = { showEndPicker = false },
            onConfirm = {
                onEndPicked(state.hour * 60 + state.minute)
                showEndPicker = false
            },
        ) {
            TimePicker(state = state)
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        text = { content() },
    )
}
