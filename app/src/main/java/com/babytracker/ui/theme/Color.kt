package com.babytracker.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Raw palette — structured scale ─────────────────────────────
// Mirrors design-System-handoff/akachan-design-system/project/colors_and_type.css.
// 700 = primary action, 200 = container, 900 = on-container text, 100 = softest tone.

// Feeding / Primary
val Pink900 = Color(0xFF880E4F)
val Pink700 = Color(0xFFC2185B)
val Pink200 = Color(0xFFF8BBD0)
val Pink100 = Color(0xFFF4C2C2)

// Sleep / Secondary
val Blue900 = Color(0xFF0D47A1)
val Blue700 = Color(0xFF1976D2)
val Blue200 = Color(0xFFB3E5FC)
val Blue100 = Color(0xFF89CFF0)

// Success / Tertiary
val Green900 = Color(0xFF1B5E20)
val Green700 = Color(0xFF388E3C)
val Green200 = Color(0xFFC8E6C9)
val Green100 = Color(0xFF90EE90)

// Soft yellow — retained for surface (no scale equivalent)
val SoftYellow = Color(0xFFFFF9C4)

// ─── Light scheme semantic tokens ─────────────────────────────
val PrimaryPink = Pink700
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerPink = Pink200
val OnPrimaryContainerDarkPink = Pink900

val SecondaryBlue = Blue700
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerBlue = Blue200
val OnSecondaryContainerDarkBlue = Blue900

val TertiaryGreen = Green700
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerGreen = Green200
val OnTertiaryContainerDarkGreen = Green900

val SurfaceYellow = Color(0xFFFFFDE7)       // no scale equivalent
val OnSurfaceDark = Color(0xFF1A1A1A)       // no scale equivalent
val OnSurfaceVariantGrey = Color(0xFF757575) // no scale equivalent

// ─── Dark scheme semantic tokens ──────────────────────────────
val PrimaryPinkDark = Color(0xFFF48FB1)     // no scale equivalent (brighter than Pink100)
val PrimaryContainerPinkDark = Pink900
val SecondaryBlueDark = Color(0xFF90CAF9)   // no scale equivalent (brighter than Blue100)
val SecondaryContainerBlueDark = Blue900
val TertiaryGreenDark = Color(0xFFA5D6A7)   // no scale equivalent (brighter than Green100)
val TertiaryContainerGreenDark = Green900
val SurfaceDark = Color(0xFF1C1B1F)         // no scale equivalent
val OnSurfaceDarkTheme = Color(0xFFE6E1E5)  // no scale equivalent
