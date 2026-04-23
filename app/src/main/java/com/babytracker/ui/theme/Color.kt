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
val OnPrimaryWhite = Color(0xFFFFFFFF)      // no scale equivalent (pure white)
val PrimaryContainerPink = Pink200
val OnPrimaryContainerDarkPink = Pink900

val SecondaryBlue = Blue700
val OnSecondaryWhite = Color(0xFFFFFFFF)    // no scale equivalent (pure white)
val SecondaryContainerBlue = Blue200
val OnSecondaryContainerDarkBlue = Blue900

val TertiaryGreen = Green700
val OnTertiaryWhite = Color(0xFFFFFFFF)     // no scale equivalent (pure white)
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

// ─── Light scheme — extended semantic tokens ──────────────────
// Surface variant — inactive containers (e.g., non-current breast-side tile)
val SurfaceVariantLight = Color(0xFFF0EDE0) // no scale equivalent

// Outline — dividers and outlined-card borders (design-system-handoff canonical value)
val OutlineLight = Color(0xFFCAC4D0)
val OutlineVariantLight = Color(0xFFCAC4D0) // intentionally same as OutlineLight; may diverge in future

// Error (onError* omitted — not yet used in any screen)
val ErrorLight = Color(0xFFB00020)          // no scale equivalent
val ErrorContainerLight = Color(0xFFFFDAD6) // no scale equivalent
val OnErrorContainerLight = Color(0xFF410002) // no scale equivalent

// ─── Dark scheme — extended semantic tokens ───────────────────
val SurfaceVariantDark = Color(0xFF2B2930)  // no scale equivalent
val OutlineDark = Color(0xFF938F99)          // no scale equivalent
val OutlineVariantDark = Color(0xFF49454F)   // intentionally same as OutlineDark family; may diverge
val ErrorDark = Color(0xFFFFB4AB)            // no scale equivalent
val ErrorContainerDark = Color(0xFF93000A)   // no scale equivalent
val OnErrorContainerDark = Color(0xFFFFDAD6) // no scale equivalent

// ─── Raw palette — Warning / Amber ───────────────────────────
// Non-M3 extended palette for limit / over-limit states.
// Scale semantics match Pink/Blue/Green (700 = primary, 200 = container,
// 900 = on-container text, 100 = softest tone).
// Amber800 is off-scale: only the dark-scheme warning container uses it.
val Amber900 = Color(0xFF7A3600)
val Amber800 = Color(0xFF7A4800)
val Amber700 = Color(0xFFE65100)
val Amber200 = Color(0xFFFFE0B2)
val Amber100 = Color(0xFFFFCC80)

// ─── Warning semantic tokens (extended, non-M3) ──────────────
// Accessed as top-level vals — NOT wired through MaterialTheme.colorScheme.
// Consumed directly by NotificationHelper for the Feeding Limit notification.

// Light scheme
val WarningAmber = Amber700
val WarningContainerAmber = Amber200
val OnWarningContainerAmber = Amber900

// Dark scheme
val WarningAmberDark = Amber100
val WarningContainerAmberDark = Amber800
val OnWarningContainerAmberDark = Amber200
