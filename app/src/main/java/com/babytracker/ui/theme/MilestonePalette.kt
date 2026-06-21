package com.babytracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The Milestones section accent, resolved for the active light/dark scheme. These are
 * extended (non-M3) tokens, so they live outside [androidx.compose.material3.MaterialTheme]
 * and are accessed through this helper rather than `MaterialTheme.colorScheme`.
 */
data class MilestonePalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
fun milestoneColors(): MilestonePalette =
    if (LocalDarkTheme.current) {
        MilestonePalette(
            accent = MilestonePurpleDark,
            container = MilestoneContainerPurpleDark,
            onContainer = OnMilestoneContainerPurpleDark,
            onAccent = OnMilestonePurpleDark,
        )
    } else {
        MilestonePalette(
            accent = MilestonePurple,
            container = MilestoneContainerPurple,
            onContainer = OnMilestoneContainerPurple,
            onAccent = OnMilestoneWhite,
        )
    }
