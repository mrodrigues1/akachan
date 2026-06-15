package com.babytracker.ui.growth

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.growth.GROWTH_RECENCY
import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.growth.latestByRecency
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.ui.theme.growthColors
import com.babytracker.util.formatLength
import com.babytracker.util.formatWeight
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                    title = { Text("Growth") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    Icon(Icons.Default.Add, contentDescription = "Add measurement")
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
                title = { Text("Delete measurement?") },
                text = {
                    Text("This removes ${formatValue(measurement.type, measurement.valueCanonical, uiState.measurementSystem)} from your baby's history. This can't be undone.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDeleteMeasurement(measurement.id)
                        pendingDelete = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
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
                    text = { Text(type.tabLabel()) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("growth_chart"),
                )
            }
            item {
                Text(
                    text = "History",
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
            text = "No measurements yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to log your baby's first measurement and see it on the WHO chart.",
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
                text = "Latest ${type.tabLabel().lowercase()}",
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
                text = chart.latestPercentile?.let { "${percentileLabel(it)} (WHO)" }
                    ?: if (chart.isSexSpecified) "Percentile unavailable for this age" else "Set sex to see percentile",
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
                text = "See WHO percentiles",
                style = MaterialTheme.typography.titleSmall,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "WHO growth curves are sex-specific. Set your baby's sex to compare against them.",
                style = MaterialTheme.typography.bodyMedium,
                color = growth.onContainer,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSetSex, modifier = Modifier.testTag("growth_set_sex")) {
                Text("Set sex")
            }
        }
    }
}

@Composable
private fun GrowthChart(
    chart: GrowthChartData,
    modifier: Modifier = Modifier,
) {
    val growth = growthColors()
    val curveColor = MaterialTheme.colorScheme.outline
    val medianColor = growth.onContainer
    val pointColor = growth.accent
    val description = chartContentDescription(chart)

    Box(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val ages = chart.plotted.map { it.ageMonths }
            val curveValues = chart.curves.flatMap { c -> c.points.map { it.value } }
            val plotValues = chart.plotted.map { it.value }
            val allValues = curveValues + plotValues
            if (allValues.isEmpty()) return@Canvas

            val maxAge = (ages + chart.curves.flatMap { c -> c.points.map { it.ageMonths.toDouble() } })
                .maxOrNull()?.coerceAtLeast(MIN_AGE_SPAN) ?: MIN_AGE_SPAN
            val minValue = allValues.min()
            val maxValue = allValues.max()
            val valueSpan = (maxValue - minValue).coerceAtLeast(1.0)

            fun x(ageMonths: Double): Float = (ageMonths / maxAge * size.width).toFloat()
            fun y(value: Double): Float =
                (size.height - (value - minValue) / valueSpan * size.height).toFloat()

            chart.curves.forEach { curve ->
                if (curve.points.size < 2) return@forEach
                val path = Path()
                curve.points.forEachIndexed { index, point ->
                    val px = x(point.ageMonths.toDouble())
                    val py = y(point.value)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path = path,
                    color = if (curve.percentile == MEDIAN_PERCENTILE) medianColor else curveColor,
                    style = Stroke(width = if (curve.percentile == MEDIAN_PERCENTILE) 3f else 2f),
                )
            }

            chart.plotted.forEach { point ->
                drawCircle(
                    color = pointColor,
                    radius = 7f,
                    center = Offset(x(point.ageMonths), y(point.value)),
                )
            }
        }
    }
}

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
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "Delete measurement from $date" },
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

private fun chartContentDescription(chart: GrowthChartData): String {
    val count = chart.measurements.size
    val rank = chart.latestPercentile?.let { ", latest ${percentileLabel(it)}" } ?: ""
    return "Growth chart with $count measurement${if (count == 1) "" else "s"}$rank"
}

private const val MEDIAN_PERCENTILE = 50
private const val MIN_AGE_SPAN = 12.0
