package com.babytracker.ui.milestone

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneProgress
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilestonesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Milestone?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Milestones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.progress, key = { it.milestone.name }) { progress ->
                MilestoneCard(progress = progress, onClick = { editing = progress.milestone })
            }
        }
    }

    editing?.let { milestone ->
        val progress = uiState.progress.firstOrNull { it.milestone == milestone }
        LogMilestoneSheet(
            milestone = milestone,
            existing = progress?.achievement,
            onDismiss = { editing = null },
            onSave = { achievedOn, photoUri, notes ->
                viewModel.onLog(milestone, achievedOn, photoUri, notes)
                editing = null
            },
            onDelete = {
                viewModel.onDelete(milestone)
                editing = null
            },
        )
    }
}

@Composable
private fun MilestoneCard(
    progress: MilestoneProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val achievement = progress.achievement
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("milestone_card_${progress.milestone.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.isAchieved) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.milestone.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = progress.milestone.windowLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                if (achievement != null) {
                    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                    Text(
                        text = "Achieved ${achievement.achievedOn.format(formatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    achievement.notes?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("milestone_log_${progress.milestone.name}"),
                    ) {
                        Text("Log this milestone")
                    }
                }
            }

            val thumbnail = rememberMilestoneBitmap(achievement?.photoUri)
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else if (progress.isAchieved) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

internal fun Milestone.windowLabel(): String =
    "Typically ${windowStartMonths.roundToInt()}–${windowEndMonths.roundToInt()} months"
