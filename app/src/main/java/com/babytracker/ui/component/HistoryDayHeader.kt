package com.babytracker.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Sticky day/section header shared across history screens.
 *
 * Renders the relative-day [label] uppercased in [MaterialTheme.typography.labelMedium], full-width
 * with the standard vertical padding. Callers pass the already-built (plural) label string without
 * applying `.uppercase()` themselves — the uppercasing happens here.
 */
@Composable
fun HistoryDayHeader(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}
