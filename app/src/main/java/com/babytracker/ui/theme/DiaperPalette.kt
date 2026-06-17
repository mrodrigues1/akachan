package com.babytracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The Diaper section accent, resolved for the active light/dark scheme. These are
 * extended (non-M3) tokens, so they live outside [androidx.compose.material3.MaterialTheme]
 * and are accessed through this helper rather than `MaterialTheme.colorScheme`.
 *
 * [onAccent] stays dark in both schemes: the yellow accent is light by nature, so dark
 * text is what reaches WCAG contrast on it (white never does).
 */
data class DiaperPalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
fun diaperColors(): DiaperPalette =
    if (LocalDarkTheme.current) {
        DiaperPalette(
            accent = DiaperYellowDark,
            container = DiaperContainerYellowDark,
            onContainer = OnDiaperContainerYellowDark,
            onAccent = OnDiaperDark,
        )
    } else {
        DiaperPalette(
            accent = DiaperYellow,
            container = DiaperContainerYellow,
            onContainer = OnDiaperContainerYellow,
            onAccent = OnDiaperDark,
        )
    }
