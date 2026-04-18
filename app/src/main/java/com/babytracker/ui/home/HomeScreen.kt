package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatMinutesSeconds
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBreastfeeding: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.baby?.let { "Hi, ${it.name} 👋" } ?: "Akachan",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = LocalDate.now().format(
                                DateTimeFormatter.ofPattern("EEEE, MMM d")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Restaurant, contentDescription = "Feeding") },
                    label = { Text("Feeding") },
                    selected = false,
                    onClick = onNavigateToBreastfeeding
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Bedtime, contentDescription = "Sleep") },
                    label = { Text("Sleep") },
                    selected = false,
                    onClick = onNavigateToSleep
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onNavigateToSettings
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Active session banner
            AnimatedVisibility(
                visible = uiState.activeSession != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isFeeding = uiState.activeSession != null
                val emoji = if (isFeeding) "🍼" else "🌙"
                val label = if (isFeeding) "Feeding in progress" else "Sleep in progress"
                val bannerColor = if (isFeeding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                val bannerOnColor = if (isFeeding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = bannerColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = bannerOnColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.onStopActiveSession() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, bannerOnColor),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = bannerOnColor
                            )
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            val now by produceState(initialValue = Instant.now()) {
                while (true) {
                    value = Instant.now()
                    kotlinx.coroutines.delay(1_000L)
                }
            }
            val activeSession = uiState.activeSession
            val activeElapsedSeconds: Long? = activeSession?.let { session ->
                if (session.isPaused) {
                    val pausedAt = session.pausedAt!!
                    (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
                } else {
                    (now.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
                }.coerceAtLeast(0L)
            }

            // Summary cards row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Breastfeeding card
                Card(
                    onClick = onNavigateToBreastfeeding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "🍼", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Breastfeeding",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeSession != null && activeElapsedSeconds != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeSession.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeElapsedSeconds.formatMinutesSeconds(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            val lastStart = uiState.lastSessionStartTime
                            if (lastStart != null) {
                                Text(
                                    text = Duration.between(lastStart, now).formatElapsedAgo(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Sleep card
                Card(
                    onClick = onNavigateToSleep,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "🌙", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sleep",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        val sleepLabel = uiState.lastNightSleepDuration
                            ?.formatDuration()
                            ?.let { "$it last night" }
                            ?: "${uiState.recentSleepRecords.size} records"
                        Text(
                            text = sleepLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Tip card — suggests which side to try next (based on the less-used side last session)
            uiState.nextRecommendedSide?.let { nextSide ->
                val nextSideName = nextSide.name.lowercase().replaceFirstChar { it.uppercase() }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✨", style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "Tip",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Try $nextSideName breast next — used less last session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
