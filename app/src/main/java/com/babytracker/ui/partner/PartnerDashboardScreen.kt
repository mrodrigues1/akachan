package com.babytracker.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.AllergyType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDashboardScreen(
    onDisconnected: () -> Unit = {},
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isDisconnected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("You've been disconnected") },
            text = { Text("Ask your partner for a new code to reconnect.") },
            confirmButton = {
                TextButton(onClick = onDisconnected) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Baby Tracker") },
                actions = {
                    TextButton(
                        onClick = viewModel::refresh,
                        enabled = !uiState.isLoading,
                    ) {
                        Text("↻ Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading && uiState.snapshot == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.snapshot != null -> {
                    DashboardContent(
                        snapshot = uiState.snapshot!!,
                        error = uiState.error,
                        onClearError = viewModel::clearError,
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
                else -> {
                    Text(
                        text = "No data yet.\nAsk your partner to open their app.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    snapshot: ShareSnapshot,
    error: String?,
    onClearError: () -> Unit,
) {
    val activeSession = snapshot.sessions.firstOrNull { it.endTime == null }
    val completedSessions = snapshot.sessions.filter { it.endTime != null }.take(3)
    val lastSleep = snapshot.sleepRecords.firstOrNull()
    val lastUpdatedText = remember(snapshot.lastSyncAt) {
        "Last updated ${Duration.between(snapshot.lastSyncAt, Instant.now()).formatElapsedAgo()}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = lastUpdatedText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onClearError) { Text("Dismiss") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        BabySection(baby = snapshot.baby)
        Spacer(modifier = Modifier.height(16.dp))
        if (activeSession != null) {
            ActiveSessionCard(session = activeSession)
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (completedSessions.isNotEmpty()) {
            SectionHeader(text = "RECENT FEEDINGS")
            Spacer(modifier = Modifier.height(8.dp))
            completedSessions.forEach { session ->
                SessionRow(session = session)
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (lastSleep != null) {
            SectionHeader(text = "LAST SLEEP")
            Spacer(modifier = Modifier.height(8.dp))
            SleepCard(sleep = lastSleep)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BabySection(baby: BabySnapshot) {
    val ageWeeks = Duration.between(Instant.ofEpochMilli(baby.birthDateMs), Instant.now()).toDays() / 7

    Column(modifier = Modifier.fillMaxWidth()) {
        if (baby.name.isNotEmpty()) {
            Text(text = baby.name, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "${ageWeeks}w old",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (baby.allergies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                baby.allergies.forEach { allergyName ->
                    val label = AllergyType.entries.find { it.name == allergyName }?.label ?: allergyName
                    SuggestionChip(
                        onClick = {},
                        label = { Text(label) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = WarningContainerAmber,
                            labelColor = OnWarningContainerAmber,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(session: SessionSnapshot) {
    val elapsed = remember(session.startTime, session.pausedDurationMs) {
        Duration.between(Instant.ofEpochMilli(session.startTime), Instant.now())
            .minusMillis(session.pausedDurationMs)
            .let { if (it.isNegative) Duration.ZERO else it }
    }
    val sideLabel = if (session.startingSide == "LEFT") "Left" else "Right"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "FEEDING IN PROGRESS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$sideLabel side · ${elapsed.formatDuration()}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SessionRow(session: SessionSnapshot) {
    val startInstant = Instant.ofEpochMilli(session.startTime)
    val endInstant = Instant.ofEpochMilli(session.endTime!!)
    val duration = remember(session.startTime, session.endTime, session.pausedDurationMs) {
        Duration.between(startInstant, endInstant)
            .minusMillis(session.pausedDurationMs)
            .let { if (it.isNegative) Duration.ZERO else it }
    }
    val timeAgo = remember(session.startTime) {
        Duration.between(startInstant, Instant.now()).formatElapsedAgo()
    }
    val sideText = remember(session.startingSide, session.switchTime) {
        val from = if (session.startingSide == "LEFT") "L" else "R"
        if (session.switchTime != null) "$from→${if (from == "L") "R" else "L"}" else from
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = sideText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(text = duration.formatDuration(), style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = timeAgo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepCard(sleep: SleepSnapshot) {
    val startInstant = Instant.ofEpochMilli(sleep.startTime)
    val endInstant = sleep.endTime?.let { Instant.ofEpochMilli(it) }
    val duration = endInstant?.let { Duration.between(startInstant, it) }
    val timeAgo = endInstant?.let { Duration.between(it, Instant.now()).formatElapsedAgo() }
    val typeLabel = if (sleep.sleepType == "NAP") "Nap" else "Night Sleep"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(text = typeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (duration != null) duration.formatDuration() else "In progress",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (timeAgo != null) {
                Text(
                    text = "Ended $timeAgo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}
