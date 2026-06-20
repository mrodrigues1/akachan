package com.babytracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The Growth section accent, resolved for the active light/dark scheme. These are
 * extended (non-M3) tokens, so they live outside [androidx.compose.material3.MaterialTheme]
 * and are accessed through this helper rather than `MaterialTheme.colorScheme`.
 */
data class GrowthPalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
fun growthColors(): GrowthPalette =
    if (LocalDarkTheme.current) {
        GrowthPalette(
            accent = GrowthTealDark,
            container = GrowthContainerTealDark,
            onContainer = OnGrowthContainerTealDark,
            // Dark text on the bright dark-mode accent. Reusing the container's light on-color here
            // produced light-on-light (≈1.68:1) on the FAB and every filled button.
            onAccent = OnGrowthDark,
        )
    } else {
        GrowthPalette(
            accent = GrowthTeal,
            container = GrowthContainerTeal,
            onContainer = OnGrowthContainerTeal,
            onAccent = OnGrowthWhite,
        )
    }
