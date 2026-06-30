package com.babytracker.ui.milestone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.Milestone
import com.babytracker.ui.component.DashboardSkeleton
import com.babytracker.ui.component.EmptyState
import com.babytracker.ui.component.MilestoneIcon
import com.babytracker.ui.component.SectionLabel
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.MilestonePalette
import com.babytracker.ui.theme.milestoneColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        // A persistent bottom CTA, matching the Vaccine and Doctor-visit dashboards, instead of a
        // floating button — one consistent add affordance across the tracking sections.
        bottomBar = { AddMomentBar(colors = colors, onAddMoment = { showEditor = true }) },
    ) { padding ->
        when {
            uiState.isLoading && uiState.moments.isEmpty() ->
                DashboardSkeleton(heroHeight = HERO_SKELETON_HEIGHT.dp, modifier = Modifier.padding(padding))

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
    // The newest moment is promoted to the photo hero; the timeline lists everything after it, so
    // a moment is never shown twice (the hero owns it, the timeline owns the rest).
    val hero = moments.first()
    val rest = remember(moments) { moments.drop(1) }
    // Moments arrive newest-first, so a stable group preserves descending months
    // and descending entries within each month.
    val groups = remember(rest) { rest.groupBy { YearMonth.from(it.date) } }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "hero") {
            MomentHero(moment = hero, colors = colors, onClick = { onMomentClick(hero.id) })
            Spacer(Modifier.size(8.dp))
        }
        groups.forEach { (month, entries) ->
            stickyHeader(key = month.toString()) {
                Text(
                    text = monthFormatter.format(month).uppercase(Locale.getDefault()),
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

/**
 * The screen's centerpiece: the newest moment shown photo-first. The picture fills a full-width
 * card; a deep-purple bottom scrim keeps the overlaid title and date legible over any photo and
 * carries the section's palette across the whole image. With no photo it falls back to the soft
 * purple container wash (mirroring the detail hero) so the card never reads as a broken image.
 */
@Composable
private fun MomentHero(
    moment: Milestone,
    colors: MilestonePalette,
    onClick: () -> Unit,
) {
    val whenLabel = momentWhenLabel(moment)
    val heroDescription = stringResource(R.string.milestone_card_cd, moment.title, whenLabel)
    val bitmap = rememberMilestoneBitmap(moment.photoUri, MILESTONE_HERO_TARGET_PX)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("milestone_hero_card")
            .semantics(mergeDescendants = true) { contentDescription = heroDescription },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT_RATIO),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Scrim only the lower band where the text sits, so the top of the photo stays clear.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    HERO_SCRIM_START to Color.Transparent,
                                    1f to colors.heroScrim.copy(alpha = HERO_SCRIM_ALPHA),
                                ),
                            ),
                        ),
                )
                MomentHeroCaption(
                    title = moment.title,
                    whenLabel = whenLabel,
                    labelColor = Color.White.copy(alpha = 0.85f),
                    titleColor = Color.White,
                    metaColor = Color.White.copy(alpha = 0.9f),
                )
            } else {
                // No photo: centered camera over the purple wash, with the caption below it.
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(48.dp).clearAndSetSemantics {},
                        )
                    }
                    MomentHeroCaption(
                        title = moment.title,
                        whenLabel = whenLabel,
                        labelColor = colors.accent,
                        titleColor = colors.onContainer,
                        metaColor = colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentHeroCaption(
    title: String,
    whenLabel: String,
    labelColor: Color,
    titleColor: Color,
    metaColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        SectionLabel(
            text = stringResource(R.string.milestone_latest_label),
            color = labelColor,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = whenLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = metaColor,
        )
    }
}

@Composable
private fun MilestonesEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.milestone_empty_title),
        subtitle = stringResource(R.string.milestone_empty_subtitle),
        modifier = modifier,
    ) {
        MilestoneIcon(modifier = Modifier.size(64.dp))
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
    val whenLabel = momentWhenLabel(moment)
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

/** Persistent full-width add CTA pinned to the bottom, matching the other tracking dashboards. */
@Composable
private fun AddMomentBar(
    colors: MilestonePalette,
    onAddMoment: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column {
            // Hairline detaches the persistent CTA from content scrolling underneath it.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onAddMoment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp)
                    .testTag("milestone_add"),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.milestone_add_moment_cd),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** "MMM d, yyyy" with an optional " · h:mm a" when the parent recorded a time. */
@Composable
private fun momentWhenLabel(moment: Milestone): String {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    return buildString {
        append(moment.date.format(dateFormatter))
        moment.time?.let { append(" · ${it.format(timeFormatter)}") }
    }
}

private const val THUMBNAIL_SIZE = 72
private const val THUMBNAIL_RADIUS = 8
// One photo-friendly ratio for the hero, so an arbitrary photo isn't hard-cropped to a square.
private const val HERO_ASPECT_RATIO = 4f / 3f
// The scrim only darkens the lower ~45% of the photo, where the caption sits.
private const val HERO_SCRIM_START = 0.45f
private const val HERO_SCRIM_ALPHA = 0.92f
// Approximate on-screen height of the 4:3 hero, used to size the loading skeleton's lead block.
private const val HERO_SKELETON_HEIGHT = 240
