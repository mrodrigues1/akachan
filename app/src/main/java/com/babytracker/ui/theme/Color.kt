package com.babytracker.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Raw palette — structured scale ─────────────────────────────
// Mirrors docs/design-System-handoff/akachan-design-system/project/colors_and_type.css.
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

// Milestones — extended accent (no M3 colorScheme slot; primary/secondary/tertiary are taken)
val Purple900 = Color(0xFF4A148C)
val Purple700 = Color(0xFF7B1FA2)
val Purple200 = Color(0xFFE1BEE7)
val Purple100 = Color(0xFFF3E5F5)

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
val OnSurfaceVariantGrey = Color(0xFF6D6A64)     // no scale equivalent — light scheme, 5.25:1 vs SurfaceYellow
val OnSurfaceVariantGreyDark = Color(0xFFCAC4D0) // no scale equivalent — dark scheme (matches CSS --color-on-surface-variant dark)

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
// OutlineLight uses the M3 canonical dark-stroke value; OutlineVariantLight is the lighter separator.
val OutlineLight = Color(0xFF79747E)
val OutlineVariantLight = Color(0xFFCAC4D0)

// Error (onError* omitted — not yet used in any screen)
val ErrorLight = Color(0xFFB00020)          // no scale equivalent
val ErrorContainerLight = Color(0xFFFFDAD6) // no scale equivalent
val OnErrorContainerLight = Color(0xFF410002) // no scale equivalent

// ─── Dark scheme — extended semantic tokens ───────────────────
val SurfaceVariantDark = Color(0xFF2B2930)  // no scale equivalent
val OutlineDark = Color(0xFF938F99)          // no scale equivalent
val OutlineVariantDark = Color(0xFF79747E)   // raised from #49454F; 5.5:1 contrast vs SurfaceDark — WCAG 1.4.11
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

// ─── Milestone semantic tokens (extended, non-M3) ────────────
// Accessed as top-level vals / via milestoneColors(), NOT through MaterialTheme.colorScheme.
// Mirrors the Warning/Amber extended-token convention.

// Light scheme — white-on-MilestonePurple ≈ 5.9:1; Purple900-on-Purple200 well above 4.5:1.
val MilestonePurple = Purple700
val OnMilestoneWhite = Color(0xFFFFFFFF)
val MilestoneContainerPurple = Purple200
val OnMilestoneContainerPurple = Purple900

// Dark scheme — brighter accent on dark surface; light text on the deep container.
val MilestonePurpleDark = Color(0xFFCE93D8)
val MilestoneContainerPurpleDark = Purple900
val OnMilestoneContainerPurpleDark = Purple200

// On-accent for the dark scheme. The dark accent (#CE93D8) is a LIGHT pastel, so content
// painted on it (FAB icon, filled Save label) must be DARK: Purple900 ≈ 4.97:1. Reusing the
// light-scheme on-container value (Purple200) here inverts to ~1.45:1 and fails WCAG.
val OnMilestonePurpleDark = Purple900

// ─── Bottle-feed dot — extended (non-M3) ─────────────────────
// Rhythm strip draws breast feeds with M3 primary (Pink700 crimson); bottle feeds need a
// distinct hue so the two dot kinds read apart at ~5px. Red/orange sat too close to the
// crimson primary; green separates cleanly. Accessed as top-level vals, NOT via colorScheme.
val BottleFeedGreen = Color(0xFF388E3C)      // Material Green 700 — light scheme
val BottleFeedGreenDark = Color(0xFF81C784)  // Material Green 300 — brighter on dark surfaces

// ─── Feed history row tint (extended, non-M3) ────────────────
// Soft, desaturated pink row background for breastfeeding/bottle/pumping history cards —
// deliberately softer than PrimaryContainerPink so it doesn't compete with the vivid
// M3 primaryContainer badge in the same row. Content color reuses the existing Pink900
// (light) / Pink200 (dark) semantic tokens directly — no new content token needed.
// Accessed as top-level vals, NOT via colorScheme. Consumed by FeedHistoryCard
// (BreastfeedingHistoryScreen), PumpingHistoryContent (PumpingHistoryScreen), and
// BottleFeedHistoryCard/BreastfeedingFeedHistoryCard (UnifiedFeedingHistoryScreen).
val FeedHistoryRowContainer = Color(0xFFFCE4EC)     // no scale equivalent — light scheme
val FeedHistoryRowContainerDark = Color(0xFF4A2A38) // no scale equivalent — dark scheme

// ─── Raw palette — Growth / Teal ─────────────────────────────
// Extended (non-M3) accent. Teal conveys growth/vitality and stays clear of the
// forest-green Success/Tertiary token. Scale semantics match Pink/Blue/Green
// (800 = primary action, 200 = container, 900 = on-container text, 100 = softest).
// Teal700 (#00897B) tops out at 4.34:1 under white, just shy of AA, so the action
// step is Teal800 like the deeper sibling accents (Purple700/Indigo700/BlueGrey700).
val Teal900 = Color(0xFF004D40)
val Teal800 = Color(0xFF00796B)
val Teal700 = Color(0xFF00897B)
val Teal200 = Color(0xFFB2DFDB)
val Teal100 = Color(0xFF80CBC4)

// ─── Growth semantic tokens (extended, non-M3) ───────────────
// Accessed as top-level vals / via growthColors(), NOT through MaterialTheme.colorScheme.
// Mirrors the Milestone/Warning extended-token convention.

// Light scheme — white-on-GrowthTeal ≈ 5.3:1; Teal900-on-Teal200 ≈ 6.8:1.
val GrowthTeal = Teal800
val OnGrowthWhite = Color(0xFFFFFFFF)
val GrowthContainerTeal = Teal200
val OnGrowthContainerTeal = Teal900

// Dark scheme — light accent on dark surface paired with DARK on-accent text, mirroring
// the Vaccine/Doctor/Diaper dark convention. The earlier Teal200 on-accent was light-on-light
// (≈1.68:1); OnGrowthDark restores ≈5.2:1 on the bright accent. OnGrowthContainerTealDark
// stays light — it is the on-color for the deep Teal900 container, not the accent.
val GrowthTealDark = Color(0xFF4DB6AC)
val OnGrowthDark = Color(0xFF003A2F) // dark teal text on the bright accent (dark scheme); no scale equivalent
val GrowthContainerTealDark = Teal900
val OnGrowthContainerTealDark = Teal200

// ─── Raw palette — Diaper / Yellow ───────────────────────────
// Extended (non-M3) accent. A warm, sunny yellow — distinct from the orange
// Warning/Amber700 (#E65100) and from the forest-green Success/Tertiary token that
// the Diaper section previously borrowed. Scale semantics match Pink/Blue/Green
// (700 = primary action, 200 = container, 900 = on-container text, 100 = softest).
// Yellow is intrinsically light, so the accent always pairs with DARK on-accent text
// (white can never reach 4.5:1 on a vivid yellow).
val Yellow900 = Color(0xFF5F4B00)
val Yellow700 = Color(0xFFF9A825)
val Yellow200 = Color(0xFFFFE9A8)
val Yellow100 = Color(0xFFFFF3C4)

// ─── Diaper semantic tokens (extended, non-M3) ───────────────
// Accessed as top-level vals / via diaperColors(), NOT through MaterialTheme.colorScheme.
// Mirrors the Milestone/Growth extended-token convention.

// Light scheme — OnDiaperDark-on-Yellow700 ≈ 6.7:1; Yellow900-on-Yellow200 ≈ 6.9:1.
val DiaperYellow = Yellow700
val OnDiaperDark = Color(0xFF3E2E00)        // dark text on the bright accent (no scale equivalent)
val DiaperContainerYellow = Yellow200
val OnDiaperContainerYellow = Yellow900

// Dark scheme — brighter accent on dark surface; light gold text on the deep container.
// OnDiaperDark stays dark on the bright accent (dark text reads on yellow in both schemes).
val DiaperYellowDark = Color(0xFFFFD54F)
val DiaperContainerYellowDark = Color(0xFF4A3800)
val OnDiaperContainerYellowDark = Yellow200

// ─── Raw palette — Vaccine / Indigo ──────────────────────────
// New scale, not borrowed by any other section. Scale semantics match Pink/Blue/Green.
val Indigo900 = Color(0xFF1A237E)
val Indigo800 = Color(0xFF283593)
val Indigo700 = Color(0xFF303F9F)
val Indigo200 = Color(0xFF9FA8DA)
val Indigo100 = Color(0xFFC5CAE9)

// ─── Vaccine semantic tokens (extended, non-M3) ──────────────
// Light scheme — white-on-Indigo700 ≈ 8.6:1; Indigo900-on-Indigo100 ≈ 9.7:1.
val VaccineIndigo = Indigo700
val OnVaccine = Color(0xFFFFFFFF) // white text on the dark accent (light scheme)
val VaccineContainerIndigo = Indigo100
val OnVaccineContainerIndigo = Indigo900

// Dark scheme — the Indigo200 accent is light, so dark text reaches contrast on it.
val VaccineIndigoDark = Indigo200
val OnVaccineDark = Indigo900 // dark text on the light accent (dark scheme)
val VaccineContainerIndigoDark = Indigo800
val OnVaccineContainerIndigoDark = Indigo100

// ─── Raw palette — Doctor Visit / Blue Grey ──────────────────
// Extended (non-M3) accent. A clinical slate (stethoscope steel / medical clipboard),
// distinct from sleep blue, vaccine indigo, growth teal, and the Success/Tertiary green.
// Scale semantics match Pink/Blue/Green/Teal (700 = primary, 200 = container, 900 = on-container).
val BlueGrey900 = Color(0xFF263238)
val BlueGrey800 = Color(0xFF37474F)
val BlueGrey700 = Color(0xFF455A64)
val BlueGrey300 = Color(0xFF90A4AE)
val BlueGrey200 = Color(0xFFCFD8DC)
val BlueGrey100 = Color(0xFFECEFF1)

// ─── Doctor Visit semantic tokens (extended, non-M3) ─────────
// Accessed via doctorVisitColors(), NOT through MaterialTheme.colorScheme.
// Light scheme — white-on-BlueGrey700 ≈ 7.4:1; BlueGrey900-on-BlueGrey200 well above 4.5:1.
val DoctorSlate = BlueGrey700
val OnDoctorWhite = Color(0xFFFFFFFF) // white text on the dark accent (light scheme)
val DoctorContainerSlate = BlueGrey200
val OnDoctorContainerSlate = BlueGrey900

// Dark scheme — the BlueGrey300 accent is light, so dark text reaches contrast on it.
val DoctorSlateDark = BlueGrey300
val OnDoctorDark = BlueGrey900 // dark text on the light accent (dark scheme)
val DoctorContainerSlateDark = BlueGrey800
val OnDoctorContainerSlateDark = BlueGrey200
