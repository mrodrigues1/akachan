# Design System Task 3: Add Missing Semantic Color Tokens — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Declare 12 new color constants (6 light + 6 dark) for the `surfaceVariant`, `outline`, `outlineVariant`, `error`, `errorContainer`, and `onErrorContainer` Material 3 slots, and wire them into `LightColorScheme` / `DarkColorScheme` in `Theme.kt`.

**Architecture:** Two-file additive edit — constants appended to `Color.kt`, new named arguments appended to the two color scheme calls in `Theme.kt`. No existing token or assignment is touched. Material 3 defaults for these six slots are silently overridden the moment the named args are added.

**Tech Stack:** Kotlin, `androidx.compose.ui.graphics.Color`, Jetpack Compose Material 3 (`lightColorScheme` / `darkColorScheme`)

---

### Task 1: Confirm baseline

**Files:**
- Read: `app/src/main/java/com/babytracker/ui/theme/Color.kt`
- Read: `app/src/main/java/com/babytracker/ui/theme/Theme.kt`

- [ ] **Step 1: Verify Color.kt is at the Task-2 baseline**

Open `app/src/main/java/com/babytracker/ui/theme/Color.kt` and confirm:

1. Lines 5–28 contain the full raw palette scale (`Pink900`…`Green100` + `SoftYellow`).
2. Lines 30–48 contain light-scheme semantic tokens ending with `OnSurfaceVariantGrey`.
3. Lines 50–58 contain dark-scheme semantic tokens ending with `OnSurfaceDarkTheme`.
4. Neither `SurfaceVariantLight` nor `OutlineLight` nor `ErrorLight` appear yet (these are what Task 3 adds).

- [ ] **Step 2: Verify Theme.kt is at the expected baseline**

Open `app/src/main/java/com/babytracker/ui/theme/Theme.kt` and confirm:

1. `LightColorScheme` ends with `onSurfaceVariant = OnSurfaceVariantGrey,` — no `surfaceVariant`, `outline`, or `error` args yet.
2. `DarkColorScheme` ends with `onSurfaceVariant = OnSurfaceVariantGrey,` — same.

If either file already contains these additions, stop — Task 3 was already applied.

---

### Task 2: Append light-scheme token constants to Color.kt

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt:48`

- [ ] **Step 1: Append the six light-scheme constants**

In `Color.kt`, after line 48 (`val OnSurfaceVariantGrey = Color(0xFF757575) // no scale equivalent`), append:

```kotlin

// ─── Light scheme — extended semantic tokens ──────────────────
// Surface variant — inactive containers (e.g., non-current breast-side tile)
val SurfaceVariantLight = Color(0xFFF0EDE0)

// Outline — dividers and outlined-card borders (follows Screens.jsx canonical value)
val OutlineLight = Color(0xFFCAC4D0)
val OutlineVariantLight = Color(0xFFCAC4D0)

// Error
val ErrorLight = Color(0xFFB00020)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
```

The resulting block from line 30 onward should look like:

```kotlin
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

// ─── Light scheme — extended semantic tokens ──────────────────
// Surface variant — inactive containers (e.g., non-current breast-side tile)
val SurfaceVariantLight = Color(0xFFF0EDE0)

// Outline — dividers and outlined-card borders (follows Screens.jsx canonical value)
val OutlineLight = Color(0xFFCAC4D0)
val OutlineVariantLight = Color(0xFFCAC4D0)

// Error
val ErrorLight = Color(0xFFB00020)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
```

---

### Task 3: Append dark-scheme token constants to Color.kt

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt` (after OnSurfaceDarkTheme)

- [ ] **Step 1: Append the six dark-scheme constants**

In `Color.kt`, after the line `val OnSurfaceDarkTheme = Color(0xFFE6E1E5)  // no scale equivalent`, append:

```kotlin

// ─── Dark scheme — extended semantic tokens ───────────────────
val SurfaceVariantDark = Color(0xFF2B2930)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
```

The full dark-scheme block should now read:

```kotlin
// ─── Dark scheme semantic tokens ──────────────────────────────
val PrimaryPinkDark = Color(0xFFF48FB1)     // no scale equivalent (brighter than Pink100)
val PrimaryContainerPinkDark = Pink900
val SecondaryBlueDark = Color(0xFF90CAF9)   // no scale equivalent (brighter than Blue100)
val SecondaryContainerBlueDark = Blue900
val TertiaryGreenDark = Color(0xFFA5D6A7)   // no scale equivalent (brighter than Green100)
val TertiaryContainerGreenDark = Green900
val SurfaceDark = Color(0xFF1C1B1F)         // no scale equivalent
val OnSurfaceDarkTheme = Color(0xFFE6E1E5)  // no scale equivalent

// ─── Dark scheme — extended semantic tokens ───────────────────
val SurfaceVariantDark = Color(0xFF2B2930)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
```

---

### Task 4: Wire tokens into LightColorScheme in Theme.kt

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Theme.kt:26`

- [ ] **Step 1: Add six named args to lightColorScheme**

In `Theme.kt`, locate the `LightColorScheme` definition. It currently ends with:

```kotlin
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantGrey,
)
```

Replace that closing with:

```kotlin
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantGrey,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)
```

The full resulting `LightColorScheme` block should be:

```kotlin
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
```

---

### Task 5: Wire tokens into DarkColorScheme in Theme.kt

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Theme.kt:45`

- [ ] **Step 1: Add six named args to darkColorScheme**

In `Theme.kt`, locate the `DarkColorScheme` definition. It currently ends with:

```kotlin
    onSurface = OnSurfaceDarkTheme,
    onSurfaceVariant = OnSurfaceVariantGrey,
)
```

Replace that closing with:

```kotlin
    onSurface = OnSurfaceDarkTheme,
    onSurfaceVariant = OnSurfaceVariantGrey,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)
```

The full resulting `DarkColorScheme` block should be:

```kotlin
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
    onSurfaceVariant = OnSurfaceVariantGrey,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)
```

---

### Task 6: Build verification

**Files:** none

- [ ] **Step 1: Run the build**

```bash
./gradlew build
```

Expected output ends with:

```
BUILD SUCCESSFUL in Xs
```

If compilation fails, the most likely cause is a typo in a constant name (e.g., `ErrorContainerLight` vs `ErrorcontainerLight`) — re-read the new token block in `Color.kt` and cross-check against the named args in `Theme.kt`.

---

### Task 7: Manual visual spot-check

**Files:** none

- [ ] **Step 1: Run the app in an emulator and navigate to the Breastfeeding screen**

Launch the app in an Android emulator (API 26+). Start a breastfeeding session. Observe the inactive breast-side tile:

- **Expected (after Task 3):** subtle warm-grey tint (`#F0EDE0`) — noticeably warmer than the previous M3 default cool-grey.
- **Unexpected:** same cool-grey as before (means `surfaceVariant` assignment didn't compile in), or a jarring colour change (means wrong constant wired).

Also confirm no crash on launch, home screen, or settings screen.

---

### Task 8: Commit

**Files:** none

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/Color.kt \
        app/src/main/java/com/babytracker/ui/theme/Theme.kt
git commit -m "feat(ui): add canonical semantic color tokens (surfaceVariant, outline, error)

Formalises the surfaceVariant, outline, outlineVariant, error,
errorContainer, and onErrorContainer tokens in both light and dark
color schemes, per design-system-handoff. Replaces Material 3 defaults
with the canonical brand values."
```

---

## Acceptance Checklist

- [ ] `SurfaceVariantLight = Color(0xFFF0EDE0)` declared in `Color.kt`
- [ ] `OutlineLight = Color(0xFFCAC4D0)` declared in `Color.kt`
- [ ] `OutlineVariantLight = Color(0xFFCAC4D0)` declared in `Color.kt`
- [ ] `ErrorLight = Color(0xFFB00020)` declared in `Color.kt`
- [ ] `ErrorContainerLight = Color(0xFFFFDAD6)` declared in `Color.kt`
- [ ] `OnErrorContainerLight = Color(0xFF410002)` declared in `Color.kt`
- [ ] `SurfaceVariantDark = Color(0xFF2B2930)` declared in `Color.kt`
- [ ] `OutlineDark = Color(0xFF938F99)` declared in `Color.kt`
- [ ] `OutlineVariantDark = Color(0xFF49454F)` declared in `Color.kt`
- [ ] `ErrorDark = Color(0xFFFFB4AB)` declared in `Color.kt`
- [ ] `ErrorContainerDark = Color(0xFF93000A)` declared in `Color.kt`
- [ ] `OnErrorContainerDark = Color(0xFFFFDAD6)` declared in `Color.kt`
- [ ] `LightColorScheme` in `Theme.kt` references all six light constants
- [ ] `DarkColorScheme` in `Theme.kt` references all six dark constants
- [ ] All pre-existing tokens in `Color.kt` and assignments in `Theme.kt` are unchanged
- [ ] `./gradlew build` passes
- [ ] No crashes in emulator
- [ ] Inactive breast-side tile shows warm-grey (`#F0EDE0`) in light mode
