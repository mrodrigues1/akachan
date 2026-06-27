package com.babytracker.ui.breastfeeding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.babytracker.R
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.ui.settings.LeadTimeSegmentedRow
import com.babytracker.ui.settings.MinutesEditSheet
import com.babytracker.ui.settings.QuietHoursRow
import com.babytracker.ui.settings.SettingsRow
import com.babytracker.ui.settings.SettingsSwitchRow
import com.babytracker.ui.settings.WarningSurface
import kotlinx.coroutines.launch

private enum class FeedSettingsSheet { MAX_PER_BREAST, MAX_TOTAL_FEED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    var activeSheet by rememberSaveable { mutableStateOf<FeedSettingsSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.refreshNotificationsPermission(granted) }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onLifecycleResume()
        }
    }

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = null }
    }

    val remindersEnabled = uiState.predictiveEnabled && uiState.validIntervalCount >= PredictionTuning.SAMPLE_SIZE_MIN

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_settings_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.feed_settings_limits_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            SettingsRow(
                label = stringResource(R.string.feed_settings_max_per_breast),
                value = if (uiState.maxPerBreastMinutes > 0) {
                    stringResource(R.string.feed_settings_minutes_value, uiState.maxPerBreastMinutes)
                } else {
                    stringResource(R.string.disabled)
                },
                onClick = { activeSheet = FeedSettingsSheet.MAX_PER_BREAST },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingsRow(
                label = stringResource(R.string.feed_settings_max_total),
                value = if (uiState.maxTotalFeedMinutes > 0) {
                    stringResource(R.string.feed_settings_minutes_value, uiState.maxTotalFeedMinutes)
                } else {
                    stringResource(R.string.disabled)
                },
                onClick = { activeSheet = FeedSettingsSheet.MAX_TOTAL_FEED },
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
                        val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
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

            if (uiState.predictiveEnabled && uiState.validIntervalCount < PredictionTuning.SAMPLE_SIZE_MIN) {
                val remaining = PredictionTuning.SAMPLE_SIZE_MIN - uiState.validIntervalCount
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_predictive_feeds_remaining,
                            remaining,
                            remaining,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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

            LeadTimeSegmentedRow(
                enabled = remindersEnabled,
                selectedMinutes = uiState.predictiveLeadMinutes,
                onSelect = viewModel::onLeadMinutesChanged,
            )

            QuietHoursRow(
                enabled = remindersEnabled,
                startMinute = uiState.quietHoursStartMinute,
                endMinute = uiState.quietHoursEndMinute,
                is24Hour = is24Hour,
                onStartPicked = viewModel::onQuietHoursStartChanged,
                onEndPicked = viewModel::onQuietHoursEndChanged,
            )
        }
    }

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
        ) {
            when (activeSheet) {
                FeedSettingsSheet.MAX_PER_BREAST -> MinutesEditSheet(
                    title = stringResource(R.string.feed_settings_max_per_breast),
                    currentMinutes = uiState.maxPerBreastMinutes,
                    onSave = { minutes ->
                        viewModel.onMaxPerBreastChanged(minutes)
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                FeedSettingsSheet.MAX_TOTAL_FEED -> MinutesEditSheet(
                    title = stringResource(R.string.feed_settings_max_total),
                    currentMinutes = uiState.maxTotalFeedMinutes,
                    onSave = { minutes ->
                        viewModel.onMaxTotalFeedChanged(minutes)
                        dismissSheet()
                    },
                    onDismiss = { dismissSheet() },
                )

                null -> Unit
            }
        }
    }
}
