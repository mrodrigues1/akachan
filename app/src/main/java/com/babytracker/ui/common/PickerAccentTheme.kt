package com.babytracker.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Scopes the Material 3 date/time picker dialogs to a section [accent] so the whole dialog
 * reads in one palette instead of the global M3 primary (carnation pink). Remaps `primary`
 * (selected day, clock hand/selector, OK/Cancel), `primaryContainer` (the selected
 * hour/minute field box) and `tertiaryContainer` (the AM/PM period selector).
 *
 * No-op when [accent] is null, keeping the default M3 colours, so callers without a section
 * palette (milk-bag/bottle-feed) wrap their pickers exactly the same way.
 */
@Composable
fun PickerAccentTheme(accent: FieldAccent?, content: @Composable () -> Unit) {
    if (accent == null) {
        content()
    } else {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent.accent,
                onPrimary = accent.onAccent,
                primaryContainer = accent.container,
                onPrimaryContainer = accent.onContainer,
                tertiaryContainer = accent.accent,
                onTertiaryContainer = accent.onAccent,
            ),
            content = content,
        )
    }
}
