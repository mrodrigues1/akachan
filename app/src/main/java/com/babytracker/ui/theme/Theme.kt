package com.babytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.babytracker.domain.model.ThemeConfig

val LocalDarkTheme = compositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPink,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerPink,
    onPrimaryContainer = OnPrimaryContainerDarkPink,
    secondary = SecondaryBlue,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerBlue,
    onSecondaryContainer = OnSecondaryContainerDarkBlue,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerGreen,
    onTertiaryContainer = OnTertiaryContainerDarkGreen,
    surface = SurfaceYellow,
    background = SurfaceYellow,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantGrey,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPinkDark,
    onPrimary = OnPrimaryContainerDarkPink,
    primaryContainer = PrimaryContainerPinkDark,
    onPrimaryContainer = PrimaryContainerPink,
    secondary = SecondaryBlueDark,
    onSecondary = OnSecondaryContainerDarkBlue,
    secondaryContainer = SecondaryContainerBlueDark,
    onSecondaryContainer = SecondaryContainerBlue,
    tertiary = TertiaryGreenDark,
    onTertiary = OnTertiaryContainerDarkGreen,
    tertiaryContainer = TertiaryContainerGreenDark,
    onTertiaryContainer = TertiaryContainerGreen,
    surface = SurfaceDark,
    background = SurfaceDark,
    onSurface = OnSurfaceDarkTheme,
    onSurfaceVariant = OnSurfaceVariantGreyDark,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

@Composable
fun BabyTrackerTheme(
    themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    darkTheme: Boolean = when (themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    },
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AkachanTypography,
            shapes = AkachanShapes,
            content = content,
        )
    }
}
