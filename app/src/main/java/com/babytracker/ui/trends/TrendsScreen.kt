package com.babytracker.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import com.babytracker.ui.theme.growthColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
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

private val RANGE_LABELS = mapOf(
    TrendRange.SEVEN_DAYS to "7d",
    TrendRange.FOURTEEN_DAYS to "14d",
    TrendRange.THIRTY_DAYS to "30d",
)

private val CHART_HEIGHT = 200.dp
private const val COLUMN_THICKNESS_DP = 12

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
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RangeSelector(
                selected = uiState.range,
                onSelected = viewModel::onRangeSelected,
            )
            FeedingFrequencyCard(uiState.feedingFrequency)
            SleepDurationCard(uiState.sleepDuration)
            FeedingIntervalCard(uiState.feedingInterval)
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
                Text(RANGE_LABELS.getValue(range))
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    chartTestTag: String,
    isEmpty: Boolean,
    chartContentDescription: String,
    chart: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (isEmpty) {
                Text(
                    text = "Not enough data yet — keep tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("${chartTestTag}_empty"),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag(chartTestTag)
                        .semantics { contentDescription = chartContentDescription },
                ) {
                    chart()
                }
            }
        }
    }
}

@Composable
private fun FeedingFrequencyCard(data: List<DailyFeedingCount>) {
    val isEmpty = data.all { it.count == 0 }
    val color = MaterialTheme.colorScheme.primary
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                columnSeries { series(data.indices.toList(), data.map { it.count }) }
            }
        }
    }
    ChartCard(
        title = "Feeds per day",
        chartTestTag = "trends_feeding_chart",
        isEmpty = isEmpty,
        chartContentDescription =
        "Feeds per day. Total ${data.sumOf { it.count }} over ${data.size} days.",
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(color), thickness = COLUMN_THICKNESS_DP.dp),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            producer,
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}

@Composable
private fun SleepDurationCard(data: List<DailySleepDuration>) {
    val isEmpty = data.all { it.totalHours == 0.0 }
    val nightColor = MaterialTheme.colorScheme.secondary
    val napColor = MaterialTheme.colorScheme.secondaryContainer
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
    ChartCard(
        title = "Sleep hours per day (night + nap)",
        chartTestTag = "trends_sleep_chart",
        isEmpty = isEmpty,
        chartContentDescription =
        "Sleep hours per day, night and nap stacked, over ${data.size} days.",
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
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            producer,
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}

@Composable
private fun FeedingIntervalCard(data: List<DailyFeedingInterval>) {
    // Split into contiguous runs of non-null days so gap days BREAK the line rather than letting a
    // single series connect across them (which would imply a false trend through missing days).
    val runs = remember(data) { contiguousNonNullRuns(data) }
    val isEmpty = runs.isEmpty()
    val color = MaterialTheme.colorScheme.primary
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                lineSeries { runs.forEach { run -> series(run.map { it.index }, run.map { it.value }) } }
            }
        }
    }
    val line = lineSpec(color)
    ChartCard(
        title = "Average hours between feeds",
        chartTestTag = "trends_interval_chart",
        isEmpty = isEmpty,
        chartContentDescription = "Average hours between feeds per day over ${data.size} days.",
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(List(runs.size) { line }),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            producer,
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
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
