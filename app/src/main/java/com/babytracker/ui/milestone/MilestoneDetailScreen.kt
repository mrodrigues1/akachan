package com.babytracker.ui.milestone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.Milestone
import com.babytracker.ui.theme.milestoneColors
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilestoneDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val moment = uiState.milestone
    var showEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Moment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (moment != null) {
                        IconButton(
                            onClick = { showEditor = true },
                            modifier = Modifier.testTag("milestone_edit"),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("milestone_delete"),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (moment != null) {
            MomentDetailContent(
                moment = moment,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showEditor && moment != null) {
        MilestoneEditorSheet(
            existing = moment,
            onDismiss = { showEditor = false },
            onSave = {
                viewModel.onSave(it)
                showEditor = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete moment?") },
            text = { Text("This moment and its photo will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.onDelete(onDeleted = onNavigateBack)
                    },
                    modifier = Modifier.testTag("milestone_delete_confirm"),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MomentDetailContent(
    moment: Milestone,
    modifier: Modifier = Modifier,
) {
    val colors = milestoneColors()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val whenLabel = buildString {
        append(moment.date.format(dateFormatter))
        moment.time?.let { append(" · ${it.format(timeFormatter)}") }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        MomentHero(photoUri = moment.photoUri, accent = colors.accent, onAccent = colors.onAccent)
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = moment.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("milestone_detail_title"),
            )
            Text(
                text = whenLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
            )
            moment.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun MomentHero(
    photoUri: String?,
    accent: androidx.compose.ui.graphics.Color,
    onAccent: androidx.compose.ui.graphics.Color,
) {
    val bitmap = rememberMilestoneBitmap(photoUri)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Moment photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DEFAULT_ASPECT_RATIO),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(WIDE_ASPECT_RATIO)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private const val DEFAULT_ASPECT_RATIO = 1f
private const val WIDE_ASPECT_RATIO = 2.4f
