package com.babytracker.ui.vaccine

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.babytracker.R
import com.babytracker.ui.settings.SettingsSwitchRow
import com.babytracker.ui.settings.WarningSurface
import com.babytracker.ui.theme.vaccineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaccineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vaccine = vaccineColors()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.refreshNotificationsPermission(granted) }

    // Re-check on every resume so returning from the system notification settings clears the warning.
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onLifecycleResume()
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
                title = { Text(stringResource(R.string.vaccine_reminder_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            // Hold a spinner until DataStore emits, so the toggle never flashes its default-off state.
            val loadingLabel = stringResource(R.string.loading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .semantics {
                        contentDescription = loadingLabel
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = vaccine.accent)
            }
            return@Scaffold
        }
        if (state.isError) {
            val announce = stringResource(R.string.vaccine_load_error)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announce
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.vaccine_load_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = viewModel::onRetry) {
                    Text(text = stringResource(R.string.try_again), color = vaccine.accent)
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (state.showPermissionWarning) {
                // Reminders are on but the OS blocks notifications, so they would never fire. Offer a
                // one-tap route to the system notification settings rather than failing silently.
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
                Spacer(Modifier.height(8.dp))
            }
            SettingsSwitchRow(
                label = stringResource(R.string.vaccine_reminder_toggle_title),
                description = stringResource(R.string.vaccine_reminder_toggle_subtitle),
                checked = state.reminderEnabled,
                onCheckedChange = { enabled ->
                    viewModel.onReminderToggle(enabled)
                    if (enabled) requestPermissionIfNeeded()
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.vaccine_reminder_lead_label),
                style = MaterialTheme.typography.titleSmall,
                color = if (state.reminderEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            val options = VaccineSettingsUiState.LEAD_DAYS_OPTIONS
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, days ->
                    SegmentedButton(
                        selected = state.leadDays == days,
                        onClick = { viewModel.onLeadDaysChange(days) },
                        enabled = state.reminderEnabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = vaccine.container,
                            activeContentColor = vaccine.onContainer,
                        ),
                        label = {
                            Text(pluralStringResource(R.plurals.vaccine_reminder_lead_option, days, days))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Spell out what the lead-days choice means, so the reminder feature isn't a silent toggle.
            Text(
                text = stringResource(R.string.vaccine_reminder_lead_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.vaccine_to_schedule_lead_label),
                style = MaterialTheme.typography.titleSmall,
                color = if (state.reminderEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            val toScheduleOptions = VaccineSettingsUiState.TO_SCHEDULE_LEAD_DAYS_OPTIONS
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                toScheduleOptions.forEachIndexed { index, days ->
                    SegmentedButton(
                        selected = state.toScheduleLeadDays == days,
                        onClick = { viewModel.onToScheduleLeadDaysChange(days) },
                        enabled = state.reminderEnabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = toScheduleOptions.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = vaccine.container,
                            activeContentColor = vaccine.onContainer,
                        ),
                        label = {
                            Text(pluralStringResource(R.plurals.vaccine_reminder_lead_option, days, days))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vaccine_to_schedule_lead_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
