package com.babytracker.ui.milestone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.Milestone
import com.babytracker.ui.theme.MilestonePalette
import com.babytracker.ui.theme.milestoneColors
import com.babytracker.util.formatLongDate
import com.babytracker.util.formatShortTime

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
    // Once delete is committed the moment flow emits null while we navigate away. Hold the
    // skeleton during that window so the "unavailable" message never flashes on a normal delete.
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.milestone_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (moment != null) {
                        IconButton(
                            onClick = { showEditor = true },
                            modifier = Modifier.testTag("milestone_edit"),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("milestone_delete"),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            moment != null -> MomentDetailContent(moment = moment, modifier = contentModifier)
            uiState.isLoading || deleting -> MomentDetailSkeleton(modifier = contentModifier)
            else -> MomentUnavailable(onNavigateBack = onNavigateBack, modifier = contentModifier)
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
            title = { Text(stringResource(R.string.milestone_delete_title)) },
            text = { Text(stringResource(R.string.milestone_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleting = true
                        viewModel.onDelete(onDeleted = onNavigateBack)
                    },
                    modifier = Modifier.testTag("milestone_delete_confirm"),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
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
    val dateLabel = moment.date.formatLongDate()
    // Joined with a localized "at" connector instead of a middot: it reads naturally for
    // screen readers and lets each locale order date and time its own way.
    val whenLabel = moment.time
        ?.let { stringResource(R.string.milestone_when_date_time, dateLabel, it.formatShortTime()) }
        ?: dateLabel
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        MomentHero(
            photoUri = moment.photoUri,
            photoDescription = stringResource(R.string.milestone_photo_of, moment.title),
            colors = colors,
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                text = moment.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .testTag("milestone_detail_title")
                    .semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
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
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun MomentHero(
    photoUri: String?,
    photoDescription: String,
    colors: MilestonePalette,
) {
    val bitmap = rememberMilestoneBitmap(photoUri, MILESTONE_HERO_TARGET_PX)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = photoDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT_RATIO),
        )
    } else {
        // No photo: a soft container wash, not a full-accent slab. Accent stays small.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT_RATIO)
                .background(colors.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(48.dp).clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Shown when the moment can't be resolved: already deleted, or opened from a stale link or a
 * partner snapshot that no longer holds it. A calm dead-end with a way back, never a blank void.
 */
@Composable
private fun MomentUnavailable(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.milestone_detail_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            TextButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

/**
 * Calm loading placeholder mirroring the detail layout: a breathing hero band, a title bar,
 * a meta bar, and a couple of note lines. A skeleton (not a spinner) keeps the layout stable
 * while the moment resolves from the local store.
 */
@Composable
private fun MomentDetailSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "milestoneDetailSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "milestoneDetailSkeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)

    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = loadingLabel },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT_RATIO)
                .background(block),
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            SkeletonBar(widthFraction = 0.7f, height = 26.dp, color = block)
            Spacer(Modifier.size(10.dp))
            SkeletonBar(widthFraction = 0.4f, height = 16.dp, color = block)
            Spacer(Modifier.size(24.dp))
            SkeletonBar(widthFraction = 1f, height = 14.dp, color = block)
            Spacer(Modifier.size(8.dp))
            SkeletonBar(widthFraction = 0.85f, height = 14.dp, color = block)
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth(widthFraction)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

// One photo-friendly hero ratio for photo, placeholder, and skeleton, so the band keeps a stable
// height as the moment resolves (no skeleton-to-photo jump) and arbitrary photos aren't hard-cropped
// to a square.
private const val HERO_ASPECT_RATIO = 4f / 3f
