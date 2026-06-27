package com.babytracker.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

// Tap or drag a data point to surface its value. Vico shows this on touch when passed to
// rememberCartesianChart(marker = ...); the value label, dot indicator, and guideline come for free.
// Colors pinned to the M3 scheme so the app stays on its light "Baby" palette even on a dark system.
// Shared by the Trends and Growth charts.
@Composable
fun rememberValueMarker(): CartesianMarker {
    val labelBackground = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = MarkerCornerBasedShape(CircleShape),
        strokeFill = Fill(MaterialTheme.colorScheme.outline),
        strokeThickness = 1.dp,
    )
    val label = rememberTextComponent(
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        padding = Insets(MARKER_LABEL_PADDING_H_DP.dp, MARKER_LABEL_PADDING_V_DP.dp),
        background = labelBackground,
        minWidth = TextComponent.MinWidth.fixed(MARKER_LABEL_MIN_WIDTH_DP.dp),
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = DefaultCartesianMarker.ValueFormatter.default(),
        indicator = { color ->
            LayeredComponent(
                back = ShapeComponent(Fill(color.copy(alpha = MARKER_HALO_ALPHA)), CircleShape),
                front = ShapeComponent(Fill(color), CircleShape),
                padding = Insets(MARKER_INDICATOR_PADDING_DP.dp),
            )
        },
        indicatorSize = MARKER_INDICATOR_SIZE_DP.dp,
        guideline = rememberAxisGuidelineComponent(),
    )
}

private const val MARKER_LABEL_PADDING_H_DP = 8
private const val MARKER_LABEL_PADDING_V_DP = 4
private const val MARKER_LABEL_MIN_WIDTH_DP = 40
private const val MARKER_INDICATOR_SIZE_DP = 24
private const val MARKER_INDICATOR_PADDING_DP = 6
private const val MARKER_HALO_ALPHA = 0.15f
