package com.babytracker.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Uppercase section header shared by the dashboards and edit sheets: a [Text] styled as
 * `labelMedium` with the given [color] (defaults to the section primary). The [text] is uppercased
 * here so every section header reads identically; pass already-uppercase strings safely since
 * `uppercase()` is idempotent. Extra spacing/semantics flow through [modifier].
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}
