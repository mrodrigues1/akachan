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
    // Deep-purple wash painted under the bottom of the photo hero so overlaid white text always
    // clears AA, regardless of the photo. Theme-independent: the photo (and thus the contrast it
    // demands) is the same in light and dark, so the scrim stays dark in both.
    val heroScrim: Color,
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
            heroScrim = Purple900,
        )
    } else {
        MilestonePalette(
            accent = MilestonePurple,
            container = MilestoneContainerPurple,
            onContainer = OnMilestoneContainerPurple,
            onAccent = OnMilestoneWhite,
            heroScrim = Purple900,
        )
    }
