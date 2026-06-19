package com.babytracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The Vaccine section accent (Indigo), resolved for the active light/dark scheme. These are
 * extended (non-M3) tokens, so they live outside [androidx.compose.material3.MaterialTheme]
 * and are accessed through this helper rather than `MaterialTheme.colorScheme`.
 *
 * Unlike the Diaper (yellow) palette, [onAccent] flips by scheme: the light-scheme accent
 * (Indigo700) is dark so white text reads on it, while the dark-scheme accent (Indigo200) is
 * light so dark text is what reaches WCAG contrast.
 */
data class VaccinePalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
fun vaccineColors(): VaccinePalette =
    if (LocalDarkTheme.current) {
        VaccinePalette(
            accent = VaccineIndigoDark,
            container = VaccineContainerIndigoDark,
            onContainer = OnVaccineContainerIndigoDark,
            onAccent = OnVaccineDark,
        )
    } else {
        VaccinePalette(
            accent = VaccineIndigo,
            container = VaccineContainerIndigo,
            onContainer = OnVaccineContainerIndigo,
            onAccent = OnVaccine,
        )
    }
