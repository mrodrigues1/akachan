package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatMinutesSeconds
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBreastfeeding: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToConnectPartner: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            kotlinx.coroutines.delay(1_000L)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.baby?.let { "Hi, ${it.name} 👋" } ?: "Akachan",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = now.atZone(ZoneId.systemDefault()).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNavigateToBreastfeeding,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    ) {
                        Text("Feeding", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onNavigateToSleep,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                    ) {
                        Text("Sleep", style = MaterialTheme.typography.labelLarge)
                    }
                }
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

            val activeSession = uiState.activeSession
            val activeElapsedSeconds: Long? = activeSession?.let { session ->
                if (session.isPaused) {
                    val pausedAt = session.pausedAt!!
                    (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
                } else {
                    (now.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
                }.coerceAtLeast(0L)
            }
            val activeSleepRecord = uiState.activeSleepRecord
            val sleepElapsedSeconds: Long? = activeSleepRecord?.let { record ->
                ((now.toEpochMilli() - record.startTime.toEpochMilli()) / 1000).coerceAtLeast(0L)
            }

            // Summary cards row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Breastfeeding card
                val isActiveFeeding = activeSession != null
                Card(
                    onClick = onNavigateToBreastfeeding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isActiveFeeding) 4.dp else 2.dp
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "🍼",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            if (isActiveFeeding && activeSession != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeSession.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = if (activeSession.isPaused) "Paused" else "Live",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Breastfeeding",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActiveFeeding && activeElapsedSeconds != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeElapsedSeconds.formatMinutesSeconds(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val lastStart = uiState.lastSessionStartTime
                            if (lastStart != null) {
                                Text(
                                    text = Duration.between(lastStart, now).formatElapsedAgo(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Sleep card
                val isActiveSleep = activeSleepRecord != null
                Card(
                    onClick = onNavigateToSleep,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isActiveSleep) 4.dp else 2.dp
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "🌙",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            if (isActiveSleep) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Live",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sleep",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActiveSleep && sleepElapsedSeconds != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Duration.ofSeconds(sleepElapsedSeconds).formatDuration(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val sleepLabel = uiState.lastNightSleepDuration
                                ?.formatDuration()
                                ?.let { "$it last night" }
                                ?: "${uiState.recentSleepRecords.size} records"
                            Text(
                                text = sleepLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                        Text(
                            text = "✨",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "TIP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Try $nextSideName breast next, used less last session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.appMode == AppMode.NONE,
                enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Card(
                    onClick = onNavigateToConnectPartner,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "👨‍👩‍👧",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Partner View",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect to see shared baby data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
