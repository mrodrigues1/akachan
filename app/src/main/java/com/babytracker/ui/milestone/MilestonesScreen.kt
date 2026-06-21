package com.babytracker.ui.milestone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.Milestone
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.MilestonePalette
import com.babytracker.ui.theme.milestoneColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilestonesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    val colors = milestoneColors()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.milestone_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            val addMomentDescription = stringResource(R.string.milestone_add_moment_cd)
            FloatingActionButton(
                onClick = { showEditor = true },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                modifier = Modifier.semantics { contentDescription = addMomentDescription }
                    .testTag("milestone_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.moments.isEmpty() ->
                MilestonesSkeleton(modifier = Modifier.fillMaxSize().padding(padding))

            uiState.moments.isEmpty() ->
                MilestonesEmptyState(modifier = Modifier.fillMaxSize().padding(padding))

            else ->
                MilestonesTimeline(
                    moments = uiState.moments,
                    colors = colors,
                    contentPadding = padding,
                    onMomentClick = onNavigateToDetail,
                )
        }
    }

    if (showEditor) {
        MilestoneEditorSheet(
            existing = null,
            onDismiss = { showEditor = false },
            onSave = { milestone ->
                viewModel.onSave(milestone)
                showEditor = false
            },
        )
    }
}

@Composable
private fun MilestonesTimeline(
    moments: List<Milestone>,
    colors: MilestonePalette,
    contentPadding: PaddingValues,
    onMomentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Moments arrive newest-first, so a stable group preserves descending months
    // and descending entries within each month.
    val groups = remember(moments) { moments.groupBy { YearMonth.from(it.date) } }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
    ) {
        groups.forEach { (month, entries) ->
            stickyHeader(key = month.toString()) {
                Text(
                    text = monthFormatter.format(month).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            items(entries, key = { it.id }) { moment ->
                MomentCard(
                    moment = moment,
                    colors = colors,
                    onClick = { onMomentClick(moment.id) },
                )
            }
        }
    }
}

@Composable
private fun MilestonesEmptyState(modifier: Modifier = Modifier) {
    val colors = milestoneColors()
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎉",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.milestone_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.milestone_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MomentCard(
    moment: Milestone,
    colors: MilestonePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val whenLabel = buildString {
        append(moment.date.format(dateFormatter))
        moment.time?.let { append(" · ${it.format(timeFormatter)}") }
    }
    val momentDescription = stringResource(R.string.milestone_card_cd, moment.title, whenLabel)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("moment_card_${moment.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = momentDescription
            },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MomentThumbnail(photoUri = moment.photoUri, colors = colors)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = whenLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                )
                moment.note?.let {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentThumbnail(
    photoUri: String?,
    colors: MilestonePalette,
) {
    val bitmap = rememberMilestoneBitmap(photoUri)
    Box(
        modifier = Modifier
            .size(THUMBNAIL_SIZE.dp)
            .clip(RoundedCornerShape(THUMBNAIL_RADIUS.dp))
            // No photo: a soft container wash with a small accent icon, mirroring the detail
            // hero. A full-accent fill would stack saturated slabs down the timeline.
            .then(if (bitmap == null) Modifier.background(colors.container) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp).clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Calm loading placeholder: a few card-shaped silhouettes that breathe softly while the
 * first read of the local store resolves. A skeleton (not a spinner) keeps the timeline's
 * layout stable so content does not jump in. See the product register: skeletons over spinners.
 */
@Composable
private fun MilestonesSkeleton(modifier: Modifier = Modifier) {
    val isDark = LocalDarkTheme.current
    val loadingLabel = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "milestoneSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "milestoneSkeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = loadingLabel },
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 12.dp)
                .height(12.dp)
                .width(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(block),
        )
        repeat(SKELETON_CARD_COUNT) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(THUMBNAIL_SIZE.dp)
                            .clip(RoundedCornerShape(THUMBNAIL_RADIUS.dp))
                            .background(block),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .height(14.dp)
                                .fillMaxWidth(SKELETON_TITLE_FRACTION)
                                .clip(RoundedCornerShape(4.dp))
                                .background(block),
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .height(12.dp)
                                .fillMaxWidth(SKELETON_META_FRACTION)
                                .clip(RoundedCornerShape(4.dp))
                                .background(block),
                        )
                    }
                }
            }
        }
    }
}

private const val THUMBNAIL_SIZE = 72
private const val THUMBNAIL_RADIUS = 8
private const val SKELETON_CARD_COUNT = 4
private const val SKELETON_TITLE_FRACTION = 0.6f
private const val SKELETON_META_FRACTION = 0.4f
