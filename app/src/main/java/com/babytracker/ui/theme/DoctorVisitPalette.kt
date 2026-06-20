package com.babytracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The Doctor Visit section accent (Slate Blue-Grey), resolved for the active light/dark scheme.
 * These are extended (non-M3) tokens, so they live outside
 * [androidx.compose.material3.MaterialTheme] and are accessed through this helper rather than
 * `MaterialTheme.colorScheme`.
 *
 * Like the Vaccine (indigo) palette, [onAccent] flips by scheme: the light-scheme accent
 * (BlueGrey700) is dark so white text reads on it, while the dark-scheme accent (BlueGrey300) is
 * light so dark text is what reaches WCAG contrast.
 */
data class DoctorVisitPalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
fun doctorVisitColors(): DoctorVisitPalette =
    if (LocalDarkTheme.current) {
        DoctorVisitPalette(
            accent = DoctorSlateDark,
            container = DoctorContainerSlateDark,
            onContainer = OnDoctorContainerSlateDark,
            onAccent = OnDoctorDark,
        )
    } else {
        DoctorVisitPalette(
            accent = DoctorSlate,
            container = DoctorContainerSlate,
            onContainer = OnDoctorContainerSlate,
            onAccent = OnDoctorWhite,
        )
    }
