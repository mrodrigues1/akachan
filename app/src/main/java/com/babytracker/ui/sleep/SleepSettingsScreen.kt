package com.babytracker.ui.sleep

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.babytracker.R
import com.babytracker.ui.settings.LeadTimeSegmentedRow
import com.babytracker.ui.settings.QuietHoursRow
import com.babytracker.ui.settings.SettingsSwitchRow
import com.babytracker.ui.settings.WarningSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshNotificationsPermission(granted)
    }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onLifecycleResume()
        }
    }

    var napDelayDraft by remember {
        mutableStateOf(uiState.napReminderDelayMinutes.takeIf { it > 0 }?.toString() ?: "")
    }
    var napDelayFieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.napReminderDelayMinutes) {
        if (!napDelayFieldFocused) {
            napDelayDraft = uiState.napReminderDelayMinutes.takeIf { it > 0 }?.toString() ?: ""
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (napDelayFieldFocused) {
                napDelayDraft.toIntOrNull()?.let { viewModel.onNapReminderDelayChanged(it) }
            }
        }
    }

    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            if (uiState.showPermissionWarning) {
                WarningSurface(
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

            SettingsSwitchRow(
                label = stringResource(R.string.settings_nap_reminder_toggle_title),
                description = stringResource(R.string.settings_nap_reminder_toggle_subtitle),
                checked = uiState.napReminderEnabled,
                onCheckedChange = { newValue ->
                    viewModel.onNapReminderToggleChanged(newValue)
                    if (newValue) requestPermissionIfNeeded()
                },
                leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
            )

            OutlinedTextField(
                value = napDelayDraft,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }
                    if (filtered.length <= 3) napDelayDraft = filtered
                },
                label = { Text(stringResource(R.string.settings_nap_reminder_delay_label)) },
                supportingText = { Text(stringResource(R.string.sleep_settings_nap_delay_helper)) },
                enabled = uiState.napReminderEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .alpha(if (uiState.napReminderEnabled) 1f else 0.38f)
                    .onFocusChanged { focusState ->
                        napDelayFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            val minutes = napDelayDraft.toIntOrNull()
                            if (minutes != null) {
                                viewModel.onNapReminderDelayChanged(minutes)
                            } else {
                                napDelayDraft = uiState.napReminderDelayMinutes.takeIf { it > 0 }?.toString() ?: ""
                            }
                        }
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    val minutes = napDelayDraft.toIntOrNull()
                    if (minutes != null) {
                        viewModel.onNapReminderDelayChanged(minutes)
                    } else {
                        napDelayDraft = uiState.napReminderDelayMinutes.takeIf { it > 0 }?.toString() ?: ""
                    }
                }),
                singleLine = true,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            SettingsSwitchRow(
                label = stringResource(R.string.settings_predictive_sleep_toggle_title),
                description = stringResource(R.string.settings_predictive_sleep_toggle_subtitle),
                checked = uiState.predictiveSleepEnabled,
                onCheckedChange = { newValue ->
                    viewModel.onPredictiveSleepToggleChanged(newValue)
                    if (newValue) requestPermissionIfNeeded()
                },
                leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
            )
            LeadTimeSegmentedRow(
                enabled = uiState.predictiveSleepEnabled,
                selectedMinutes = uiState.predictiveSleepLeadMinutes,
                onSelect = viewModel::onSleepLeadMinutesChanged,
            )
            QuietHoursRow(
                enabled = uiState.predictiveSleepEnabled,
                startMinute = uiState.quietHoursStartMinute,
                endMinute = uiState.quietHoursEndMinute,
                is24Hour = is24Hour,
                onStartPicked = viewModel::onQuietHoursStartChanged,
                onEndPicked = viewModel::onQuietHoursEndChanged,
            )
        }
    }
}
