package com.babytracker.widget.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import com.babytracker.ui.theme.ErrorContainerDark
import com.babytracker.ui.theme.ErrorContainerLight
import com.babytracker.ui.theme.ErrorDark
import com.babytracker.ui.theme.ErrorLight
import com.babytracker.ui.theme.OnErrorContainerDark
import com.babytracker.ui.theme.OnErrorContainerLight
import com.babytracker.ui.theme.OnPrimaryContainerDarkPink
import com.babytracker.ui.theme.OnPrimaryWhite
import com.babytracker.ui.theme.OnSecondaryContainerDarkBlue
import com.babytracker.ui.theme.OnSecondaryWhite
import com.babytracker.ui.theme.OnSurfaceDark
import com.babytracker.ui.theme.OnSurfaceDarkTheme
import com.babytracker.ui.theme.OnSurfaceVariantGrey
import com.babytracker.ui.theme.OnSurfaceVariantGreyDark
import com.babytracker.ui.theme.OnTertiaryContainerDarkGreen
import com.babytracker.ui.theme.OnTertiaryWhite
import com.babytracker.ui.theme.OutlineDark
import com.babytracker.ui.theme.OutlineLight
import com.babytracker.ui.theme.OutlineVariantDark
import com.babytracker.ui.theme.OutlineVariantLight
import com.babytracker.ui.theme.PrimaryContainerPink
import com.babytracker.ui.theme.PrimaryContainerPinkDark
import com.babytracker.ui.theme.PrimaryPink
import com.babytracker.ui.theme.PrimaryPinkDark
import com.babytracker.ui.theme.SecondaryBlue
import com.babytracker.ui.theme.SecondaryBlueDark
import com.babytracker.ui.theme.SecondaryContainerBlue
import com.babytracker.ui.theme.SecondaryContainerBlueDark
import com.babytracker.ui.theme.SurfaceDark
import com.babytracker.ui.theme.SurfaceVariantDark
import com.babytracker.ui.theme.SurfaceVariantLight
import com.babytracker.ui.theme.SurfaceYellow
import com.babytracker.ui.theme.TertiaryContainerGreen
import com.babytracker.ui.theme.TertiaryContainerGreenDark
import com.babytracker.ui.theme.TertiaryGreen
import com.babytracker.ui.theme.TertiaryGreenDark

private val LightWidgetColorScheme = lightColorScheme(
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

private val DarkWidgetColorScheme = darkColorScheme(
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

// `androidx.glance.material3.ColorProviders` is a factory function returning
// `androidx.glance.color.ColorProviders`, so we alias the factory and keep the
// imported type for the val declaration.
val BabyWidgetColors: ColorProviders = material3ColorProviders(
    light = LightWidgetColorScheme,
    dark = DarkWidgetColorScheme,
)
