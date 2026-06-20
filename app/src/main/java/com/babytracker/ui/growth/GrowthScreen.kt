package com.babytracker.ui.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.growth.GROWTH_RECENCY
import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.growth.latestByRecency
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.ui.theme.growthColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.babytracker.util.formatLength
import com.babytracker.util.formatWeight
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GrowthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GrowthMeasurement?>(null) }

    GrowthTealTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.growth_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = growthColors().accent,
                    contentColor = growthColors().onAccent,
                    modifier = Modifier.testTag("growth_add_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.growth_add_measurement_cd))
                }
            },
        ) { padding ->
            GrowthContent(
                uiState = uiState,
                onTypeSelected = viewModel::onTypeSelected,
                onRequestDelete = { pendingDelete = it },
                onSetSex = onNavigateToSettings,
                modifier = Modifier.padding(padding),
            )
        }

        pendingDelete?.let { measurement ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.growth_delete_title)) },
                text = {
                    Text(stringResource(R.string.growth_delete_message, formatValue(measurement.type, measurement.valueCanonical, uiState.measurementSystem)))
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDeleteMeasurement(measurement.id)
                        pendingDelete = null
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        if (showAddSheet) {
            AddMeasurementSheet(
                type = uiState.selectedType,
                system = uiState.measurementSystem,
                onDismiss = { showAddSheet = false },
                onSave = { valueCanonical, takenAt, notes ->
                    viewModel.onAddMeasurement(uiState.selectedType, valueCanonical, takenAt, notes)
                    showAddSheet = false
                },
            )
        }
    }
}

/**
 * Scopes a Teal accent to the Growth section by overriding the `primary` family on the inherited
 * [MaterialTheme] colorScheme. Every M3 component inside (TabRow, buttons, text fields, date picker)
 * then renders Teal without per-call-site overrides. Teal is sourced from the extended
 * [growthColors] tokens so light/dark resolution stays consistent.
 */
@Composable
private fun GrowthTealTheme(content: @Composable () -> Unit) {
    val growth = growthColors()
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = growth.accent,
            onPrimary = growth.onAccent,
            primaryContainer = growth.container,
            onPrimaryContainer = growth.onContainer,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@Composable
private fun GrowthContent(
    uiState: GrowthUiState,
    onTypeSelected: (GrowthType) -> Unit,
    onRequestDelete: (GrowthMeasurement) -> Unit,
    onSetSex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chart = uiState.chart
    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = GrowthType.entries.indexOf(uiState.selectedType)) {
            GrowthType.entries.forEach { type ->
                Tab(
                    selected = uiState.selectedType == type,
                    onClick = { onTypeSelected(type) },
                    text = { Text(stringResource(type.tabLabelRes())) },
                    modifier = Modifier.testTag("growth_tab_${type.name}"),
                )
            }
        }

        if (chart == null || chart.measurements.isEmpty()) {
            GrowthEmptyState(modifier = Modifier.padding(24.dp))
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GrowthSummaryCard(uiState.selectedType, uiState.measurementSystem, chart)
            }
            if (!chart.isSexSpecified) {
                item { SetSexCard(onSetSex = onSetSex) }
            }
            item {
                GrowthChart(
                    chart = chart,
                    yAxisTitle = yAxisUnit(uiState.selectedType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("growth_chart"),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.growth_history),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(chart.measurements.sortedWith(GROWTH_RECENCY.reversed()), key = { it.id }) { measurement ->
                GrowthHistoryItem(
                    measurement = measurement,
                    type = uiState.selectedType,
                    system = uiState.measurementSystem,
                    onDelete = { onRequestDelete(measurement) },
                )
            }
        }
    }
}

@Composable
private fun GrowthEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📏", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.growth_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.growth_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GrowthSummaryCard(
    type: GrowthType,
    system: MeasurementSystem,
    chart: GrowthChartData,
) {
    val latest = chart.measurements.latestByRecency()
    val growth = growthColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = growth.container,
            contentColor = growth.onContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.growth_latest, stringResource(type.tabLabelRes()).lowercase()),
                style = MaterialTheme.typography.labelMedium,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = latest?.let { formatValue(type, it.valueCanonical, system) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = chart.latestPercentile?.let { stringResource(R.string.growth_percentile_who, percentileLabel(it)) }
                    ?: if (chart.isSexSpecified) {
                        stringResource(R.string.growth_percentile_unavailable)
                    } else {
                        stringResource(R.string.growth_set_sex_percentile)
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = growth.onContainer,
            )
        }
    }
}

@Composable
private fun SetSexCard(onSetSex: () -> Unit) {
    val growth = growthColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = growth.container,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.growth_see_who),
                style = MaterialTheme.typography.titleSmall,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.growth_who_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSetSex, modifier = Modifier.testTag("growth_set_sex")) {
                Text(stringResource(R.string.growth_set_sex))
            }
        }
    }
}

/**
 * Renders the WHO reference curves and the baby's measurements on a shared age (X) / WHO-unit (Y)
 * axis using Vico — the same charting library as the Trends screen. Each WHO curve and the
 * measurement track is a separate [lineSeries]; the series order pushed to the producer matches the
 * [LineCartesianLayer.Line] list order so per-series styling lines up (median emphasized, other
 * percentiles muted, measurements drawn with points).
 */
@Composable
private fun GrowthChart(
    chart: GrowthChartData,
    yAxisTitle: String,
    modifier: Modifier = Modifier,
) {
    val growth = growthColors()
    val curveColor = MaterialTheme.colorScheme.outline
    val medianColor = growth.onContainer
    val pointColor = growth.accent
    val description = chartContentDescription(chart)

    // Show only the outer bounds and median — too many reference curves crowd the plot.
    val curves = remember(chart.curves) { chart.curves.filter { it.percentile in REFERENCE_PERCENTILES } }
    // Vico connects each series' points in order, so a measurement line needs ascending age. WHO
    // curve points already arrive age-ordered from the calculator.
    val plotted = remember(chart.plotted) { chart.plotted.sortedBy { it.ageMonths } }
    val hasData = curves.isNotEmpty() || plotted.isNotEmpty()

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(curves, plotted) {
        if (hasData) {
            producer.runTransaction {
                lineSeries {
                    curves.forEach { curve ->
                        series(curve.points.map { it.ageMonths.toDouble().roundX() }, curve.points.map { it.value })
                    }
                    if (plotted.isNotEmpty()) {
                        series(plotted.map { it.ageMonths.roundX() }, plotted.map { it.value })
                    }
                }
            }
        }
    }

    val curveLine = whoCurveLine(curveColor)
    val medianLine = whoMedianLine(medianColor)
    val measurementLine = measurementLine(pointColor)
    val lines = buildList {
        curves.forEach { curve ->
            add(if (curve.percentile == MEDIAN_PERCENTILE) medianLine else curveLine)
        }
        if (plotted.isNotEmpty()) add(measurementLine)
    }

    // Vico's default axis label color is theme-agnostic black; tie it to the M3 scheme so it stays
    // legible in dark mode.
    val axisLabel = rememberAxisLabelComponent(
        MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    // Slightly heavier title component so the axis meaning reads as a heading, not another tick label.
    val axisTitle = rememberAxisLabelComponent(
        MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        ),
    )

    Box(modifier = modifier.semantics { contentDescription = description }) {
        if (lines.isNotEmpty()) {
            CartesianChartHost(
                rememberCartesianChart(
                    rememberLineCartesianLayer(
                        LineCartesianLayer.LineProvider.series(lines),
                        rangeProvider = GrowthRangeProvider,
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        guideline = null,
                        titleComponent = axisTitle,
                        title = { yAxisTitle },
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabel,
                        valueFormatter = monthAxisFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { AXIS_MONTH_SPACING }),
                        guideline = null,
                        titleComponent = axisTitle,
                        title = { "Age (months)" },
                    ),
                    // WHO curves use whole-month x-values while measurements fall on fractional
                    // months; without a fixed step Vico derives one from the GCD of those deltas,
                    // which collapses to a near-zero step and renders an unreadable axis.
                    getXStep = { 1.0 },
                ),
                producer,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

/** Labels the X axis as a whole number of months. */
private val monthAxisFormatter = CartesianValueFormatter { _, value, _ -> value.roundToInt().toString() }

/**
 * Fits the Y axis to the plotted values instead of anchoring to zero (the default [auto] behavior),
 * with a little padding so curves and points aren't flush against the edges. Growth values sit well
 * above zero, so a zero-anchored axis squashes everything into a thin, unreadable band.
 */
private object GrowthRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        minY - growthYPadding(minY, maxY)

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        maxY + growthYPadding(minY, maxY)
}

/**
 * Vertical padding added on each side of the data range by [GrowthRangeProvider].
 *
 * Normally a fraction of the span. When the span is zero — the first measurement plotted with no WHO
 * curves, so every Y value is equal — that fraction collapses to 0 and would hand Vico a zero-height
 * range to divide by, crashing the chart at draw. Fall back to a fraction of the value's magnitude
 * (with an absolute floor for values near zero) so the axis always has real height.
 */
internal fun growthYPadding(minY: Double, maxY: Double): Double {
    val span = maxY - minY
    return if (span > 0.0) {
        span * RANGE_PADDING
    } else {
        (abs(maxY) * SINGLE_POINT_PADDING).coerceAtLeast(MIN_Y_PADDING)
    }
}

/** A muted, thin reference curve (non-median WHO percentiles) — stroke only, no points. */
@Composable
private fun whoCurveLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = WHO_CURVE_THICKNESS_DP.dp),
    )

/** The emphasized 50th-percentile median curve — thicker and in the accent-container color. */
@Composable
private fun whoMedianLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = WHO_MEDIAN_THICKNESS_DP.dp),
    )

/** The baby's measurement track — accent line with visible points so a single entry still renders. */
@Composable
private fun measurementLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = MEASUREMENT_THICKNESS_DP.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(color), CircleShape)),
        ),
    )

@Composable
private fun GrowthHistoryItem(
    measurement: GrowthMeasurement,
    type: GrowthType,
    system: MeasurementSystem,
    onDelete: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val date = remember(measurement.takenAt) {
        measurement.takenAt.atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatValue(type, measurement.valueCanonical, system),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            measurement.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val deleteRowDescription = stringResource(R.string.growth_delete_row_cd, date)
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = deleteRowDescription },
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

internal fun GrowthType.tabLabel(): String = when (this) {
    GrowthType.WEIGHT -> "Weight"
    GrowthType.LENGTH -> "Length"
    GrowthType.HEAD_CIRC -> "Head"
}

@androidx.annotation.StringRes
internal fun GrowthType.tabLabelRes(): Int = when (this) {
    GrowthType.WEIGHT -> R.string.growth_tab_weight
    GrowthType.LENGTH -> R.string.growth_tab_length
    GrowthType.HEAD_CIRC -> R.string.growth_tab_head
}

internal fun formatValue(type: GrowthType, valueCanonical: Long, system: MeasurementSystem): String =
    when (type) {
        GrowthType.WEIGHT -> formatWeight(valueCanonical, system)
        GrowthType.LENGTH, GrowthType.HEAD_CIRC -> formatLength(valueCanonical, system)
    }

internal fun percentileLabel(percentile: Double): String {
    val rounded = percentile.roundToInt().coerceIn(1, 99)
    val suffix = when {
        rounded % 100 in 11..13 -> "th"
        rounded % 10 == 1 -> "st"
        rounded % 10 == 2 -> "nd"
        rounded % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$rounded$suffix percentile"
}

/**
 * Y-axis unit shown as the axis title. The plotted values come from
 * [com.babytracker.domain.growth.WhoPercentileCalculator.canonicalToWhoUnit], which always emits WHO
 * (metric) units — kg for weight, cm for length and head circumference — regardless of the user's
 * [MeasurementSystem] preference, so the title must match those units, not the preference.
 */
private fun yAxisUnit(type: GrowthType): String = when (type) {
    GrowthType.WEIGHT -> "kg"
    GrowthType.LENGTH, GrowthType.HEAD_CIRC -> "cm"
}

private fun chartContentDescription(chart: GrowthChartData): String {
    val count = chart.measurements.size
    val rank = chart.latestPercentile?.let { ", latest ${percentileLabel(it)}" } ?: ""
    return "Growth chart with $count measurement${if (count == 1) "" else "s"}$rank"
}

/**
 * Vico rejects x-values with more than four decimal places (it computes a GCD of x-deltas). Age in
 * months is `days / 30.4375`, which is effectively irrational, so round before plotting.
 */
private fun Double.roundX(): Double = (this * X_PRECISION).roundToInt() / X_PRECISION

private const val X_PRECISION = 10_000.0
private const val AXIS_MONTH_SPACING = 3
internal const val RANGE_PADDING = 0.08

// Fallbacks for a zero-span (single point, no curves) Y range: a fraction of the value's magnitude,
// floored by a small absolute padding so a value of ~0 still produces a non-degenerate axis.
private const val SINGLE_POINT_PADDING = 0.1
internal const val MIN_Y_PADDING = 0.5
private const val MEDIAN_PERCENTILE = 50
private val REFERENCE_PERCENTILES = setOf(3, MEDIAN_PERCENTILE, 97)
private const val WHO_CURVE_THICKNESS_DP = 1.5f
private const val WHO_MEDIAN_THICKNESS_DP = 2.5f
private const val MEASUREMENT_THICKNESS_DP = 2f
