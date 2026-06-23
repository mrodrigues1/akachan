package com.babytracker.ui.trends

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.TrendRange
import com.babytracker.ui.theme.growthColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RANGE_LABELS = mapOf(
    TrendRange.SEVEN_DAYS to R.string.trends_range_7d,
    TrendRange.FOURTEEN_DAYS to R.string.trends_range_14d,
    TrendRange.THIRTY_DAYS to R.string.trends_range_30d,
)

private const val COLUMN_THICKNESS_DP = 12

// Supporting charts read as detail under the rhythm hero, so they sit shorter than a hero chart would.
private val DETAIL_CHART_HEIGHT = 180.dp

private val DATE_AXIS_FORMAT = DateTimeFormatter.ofPattern("dd/MM")

// Series x-values are 0-based day indices into the day list; map each back to its real date (dd/MM)
// so the axis shows actual days instead of a bare counter. Vico measures label widths at x-values
// that can fall *outside* the data range and throws if the formatter returns a blank string, so clamp
// into range and always emit a real date. Which days actually get a label is the ItemPlacer's job.
private fun dateAxisFormatter(dates: List<LocalDate>) = CartesianValueFormatter { _, x, _ ->
    val index = x.toInt().coerceIn(0, dates.lastIndex)
    dates[index].format(DATE_AXIS_FORMAT)
}

// Thin the labels to ~7 across any range so the dd/MM dates don't overlap (7d → every day,
// 14d → every other, 30d → every fifth).
private fun dayAxisItemPlacer(count: Int): HorizontalAxis.ItemPlacer =
    HorizontalAxis.ItemPlacer.aligned(spacing = { ((count + LABEL_TARGET - 1) / LABEL_TARGET).coerceAtLeast(1) })

private const val LABEL_TARGET = 7

// Vico's default axis label color is `vicoTheme.textColor`, which falls back to the *system* dark
// mode. The app always renders its light "Baby" palette, so on a dark system the labels turned white
// and unreadable. Pin both labels and titles to the M3 scheme so they stay legible in every mode.
@Composable
private fun rememberChartLabel() = rememberAxisLabelComponent(
    MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
)

@Composable
private fun rememberAxisTitle() = rememberAxisLabelComponent(
    MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trends_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        // Skeleton only on the first read, while there is genuinely nothing to show yet. Once data
        // has loaded, a range switch keeps the prior charts on screen (see TrendsViewModel), so we
        // never flash the skeleton between taps.
        if (uiState.isLoading && uiState.isEmptyOverall()) {
            TrendsSkeleton(Modifier.padding(padding).fillMaxSize())
        } else {
            TrendsContent(
                uiState = uiState,
                onRangeSelected = viewModel::onRangeSelected,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TrendsContent(
    uiState: TrendsUiState,
    onRangeSelected: (TrendRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        RangeSelector(selected = uiState.range, onSelected = onRangeSelected)

        if (uiState.isEmptyOverall()) {
            TrendsEmptyBody()
        } else {
            RhythmHero(uiState.dayRhythm)
            TrendsSection(stringResource(R.string.trends_section_feeding)) {
                FeedingFrequencyChart(uiState.feedingFrequency)
                FeedingIntervalChart(uiState.feedingInterval)
            }
            TrendsSection(stringResource(R.string.trends_section_sleep)) {
                SleepDurationChart(uiState.sleepDuration)
            }
        }
    }
}

@Composable
private fun RangeSelector(
    selected: TrendRange,
    onSelected: (TrendRange) -> Unit,
) {
    val ranges = TrendRange.entries
    val growth = growthColors()
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = growth.container,
                    activeContentColor = growth.onContainer,
                ),
                modifier = Modifier.testTag("trends_range_${range.days}"),
            ) {
                Text(stringResource(RANGE_LABELS.getValue(range)))
            }
        }
    }
}

/**
 * The hero: a full-width, flat 24h-per-day timeline. No card chrome, generous heading. This is the
 * care story the screen leads with; the charts below are supporting detail.
 */
@Composable
private fun RhythmHero(data: List<DayRhythm>) {
    val isEmpty = data.all {
        it.sleepBlocks.isEmpty() && it.breastFeedMarks.isEmpty() && it.bottleFeedMarks.isEmpty()
    }
    val contentDescription = stringResource(R.string.trends_rhythm_cd, data.size)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.trends_rhythm_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isEmpty) {
            EmptyHint("trends_rhythm_chart")
        } else {
            // Most recent day on top: a tired parent reads last night first. The data arrives
            // oldest-first (matching the time-series charts' left-to-right axis), so reverse for display.
            val ordered = remember(data) { data.asReversed() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trends_rhythm_chart"),
            ) {
                // Collapse the strip's many ruler/date/legend text nodes into one spoken summary, so
                // TalkBack reads the rhythm as a single meaningful description, not a stream of ticks.
                Box(Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
                    RhythmStrip(ordered)
                }
            }
        }
    }
}

/** A domain band: an uppercase label header (the system's one sanctioned uppercase use) over its charts. */
@Composable
private fun TrendsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * A single flat chart: a small title over the chart (or an inline hint when that domain has no data
 * for the range). No card; separation comes from the section grouping and spacing around it.
 */
@Composable
private fun ChartBlock(
    title: String,
    chartTestTag: String,
    isEmpty: Boolean,
    chartContentDescription: String,
    chartHeight: Dp = DETAIL_CHART_HEIGHT,
    chart: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isEmpty) {
            EmptyHint(chartTestTag)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .testTag(chartTestTag)
                    .semantics { contentDescription = chartContentDescription },
            ) {
                chart()
            }
        }
    }
}

@Composable
private fun EmptyHint(chartTestTag: String) {
    Text(
        text = stringResource(R.string.trends_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("${chartTestTag}_empty"),
    )
}

/** Brand-new, zero-data state. One warm teaching block under the range selector, never five empty boxes. */
@Composable
private fun TrendsEmptyBody() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp)
            .testTag("trends_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Timeline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.trends_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.trends_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

/**
 * Calm first-load placeholder mirroring the rest of the app: silhouettes that breathe softly while
 * the first read of the local store resolves. A skeleton (not a spinner) keeps the layout stable.
 */
@Composable
private fun TrendsSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "trendsSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trendsSkeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)

    Column(
        modifier = modifier
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(SKELETON_RANGE_COUNT) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(block),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(block, width = 140.dp, height = 22.dp)
            repeat(SKELETON_RHYTHM_ROWS) {
                SkeletonBox(block, height = 18.dp, radius = 9.dp)
            }
        }
        repeat(SKELETON_DETAIL_BLOCKS) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(block, width = 120.dp, height = 14.dp)
                SkeletonBox(block, height = 140.dp, radius = 16.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBox(
    color: Color,
    height: Dp,
    width: Dp? = null,
    radius: Dp = 4.dp,
) {
    Box(
        modifier = Modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(color),
    )
}

private const val SKELETON_RANGE_COUNT = 3
private const val SKELETON_RHYTHM_ROWS = 6
private const val SKELETON_DETAIL_BLOCKS = 2

@Composable
private fun FeedingFrequencyChart(data: List<DailyFeedingCount>) {
    val isEmpty = data.all { it.count == 0 }
    val color = MaterialTheme.colorScheme.primary
    val label = rememberChartLabel()
    val axisTitle = rememberAxisTitle()
    val feedsTitle = stringResource(R.string.trends_axis_feeds)
    val dateTitle = stringResource(R.string.trends_axis_date)
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                columnSeries { series(data.indices.toList(), data.map { it.count }) }
            }
        }
    }
    // Hoisted so the date list + formatter lambda aren't reallocated on every recomposition.
    val dateFormatter = remember(data) { dateAxisFormatter(data.map { it.date }) }
    ChartBlock(
        title = stringResource(R.string.trends_feeds_per_day_title),
        chartTestTag = "trends_feeding_chart",
        isEmpty = isEmpty,
        chartContentDescription = stringResource(R.string.trends_feeds_per_day_cd, data.sumOf { it.count }, data.size),
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(color), thickness = COLUMN_THICKNESS_DP.dp),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = label,
                    titleComponent = axisTitle,
                    title = { feedsTitle },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = label,
                    valueFormatter = dateFormatter,
                    itemPlacer = dayAxisItemPlacer(data.size),
                    titleComponent = axisTitle,
                    title = { dateTitle },
                ),
            ),
            producer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SleepDurationChart(data: List<DailySleepDuration>) {
    val isEmpty = data.all { it.totalHours == 0.0 }
    val nightColor = MaterialTheme.colorScheme.secondary
    val napColor = MaterialTheme.colorScheme.secondaryContainer
    val label = rememberChartLabel()
    val axisTitle = rememberAxisTitle()
    val hoursTitle = stringResource(R.string.trends_axis_hours)
    val dateTitle = stringResource(R.string.trends_axis_date)
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                columnSeries {
                    series(data.indices.toList(), data.map { it.nightHours })
                    series(data.indices.toList(), data.map { it.napHours })
                }
            }
        }
    }
    // Hoisted so the date list + formatter lambda aren't reallocated on every recomposition.
    val dateFormatter = remember(data) { dateAxisFormatter(data.map { it.date }) }
    ChartBlock(
        title = stringResource(R.string.trends_sleep_hours_title),
        chartTestTag = "trends_sleep_chart",
        isEmpty = isEmpty,
        chartContentDescription = stringResource(R.string.trends_sleep_hours_cd, data.size),
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(nightColor), thickness = COLUMN_THICKNESS_DP.dp),
                        rememberLineComponent(fill = Fill(napColor), thickness = COLUMN_THICKNESS_DP.dp),
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = label,
                    titleComponent = axisTitle,
                    title = { hoursTitle },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = label,
                    valueFormatter = dateFormatter,
                    itemPlacer = dayAxisItemPlacer(data.size),
                    titleComponent = axisTitle,
                    title = { dateTitle },
                ),
            ),
            producer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FeedingIntervalChart(data: List<DailyFeedingInterval>) {
    // Split into contiguous runs of non-null days so gap days BREAK the line rather than letting a
    // single series connect across them (which would imply a false trend through missing days).
    val runs = remember(data) { contiguousNonNullRuns(data) }
    val isEmpty = runs.isEmpty()
    val color = MaterialTheme.colorScheme.primary
    val label = rememberChartLabel()
    val axisTitle = rememberAxisTitle()
    val hoursTitle = stringResource(R.string.trends_axis_hours)
    val dateTitle = stringResource(R.string.trends_axis_date)
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                lineSeries { runs.forEach { run -> series(run.map { it.index }, run.map { it.value }) } }
            }
        }
    }
    val line = lineSpec(color)
    // Hoisted so neither the date list + formatter lambda nor the per-run line list are reallocated
    // on every recomposition.
    val dateFormatter = remember(data) { dateAxisFormatter(data.map { it.date }) }
    val lines = remember(runs.size, line) { List(runs.size) { line } }
    ChartBlock(
        title = stringResource(R.string.trends_interval_title),
        chartTestTag = "trends_interval_chart",
        isEmpty = isEmpty,
        chartContentDescription = stringResource(R.string.trends_interval_cd, data.size),
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(lines),
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = label,
                    titleComponent = axisTitle,
                    title = { hoursTitle },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = label,
                    valueFormatter = dateFormatter,
                    itemPlacer = dayAxisItemPlacer(data.size),
                    titleComponent = axisTitle,
                    title = { dateTitle },
                ),
            ),
            producer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun lineSpec(color: Color): LineCartesianLayer.Line {
    // One styled line (same color) with visible points so single-day runs render.
    return LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(color), CircleShape)),
        ),
    )
}

/** Contiguous runs of days with a non-null average, each item carrying (dayIndex, hours). */
private fun contiguousNonNullRuns(
    data: List<DailyFeedingInterval>,
): List<List<IndexedValue<Double>>> = buildList {
    var current = mutableListOf<IndexedValue<Double>>()
    data.forEachIndexed { index, item ->
        val avg = item.averageHours
        if (avg != null) {
            current.add(IndexedValue(index, avg))
        } else if (current.isNotEmpty()) {
            add(current)
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) add(current)
}
