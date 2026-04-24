# Design System Task 1: Raw Palette Scales — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat raw-palette constants in `Color.kt` with a structured 100/200/700/900 lightness scale per brand color (pink, blue, green), matching the design-system handoff CSS.

**Architecture:** Single-file change in `Color.kt`. The 12 new scale constants are added, the 3 old unnamed-tone constants (`BabyBlue`, `BabyPink`, `SoftGreen`) are deleted (confirmed zero outside usages), and `SoftYellow` is retained as-is. Semantic tokens block is left untouched.

**Tech Stack:** Kotlin, Jetpack Compose `androidx.compose.ui.graphics.Color`

---

### Task 1: Verify old constants have no outside usages

**Files:**
- Read: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1: Run usage grep**

```bash
rg -n "BabyBlue|BabyPink|SoftGreen" app/src
```

Expected output — only `Color.kt` definitions, no other files:

```
app/src/main/java/com/babytracker/ui/theme/Color.kt:6:val BabyBlue = Color(0xFF89CFF0)
app/src/main/java/com/babytracker/ui/theme/Color.kt:7:val BabyPink = Color(0xFFF4C2C2)
app/src/main/java/com/babytracker/ui/theme/Color.kt:8:val SoftGreen = Color(0xFF90EE90)
```

If any other file appears → do NOT delete the constants; replace with backward-compat aliases instead (see spec). If output matches expected → proceed to Task 2.

---

### Task 2: Replace raw palette block in `Color.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt:5-9`

- [ ] **Step 1: Replace lines 5–9**

Replace the current raw palette block:

```kotlin
// Raw palette constants (kept as reference)
val BabyBlue = Color(0xFF89CFF0)
val BabyPink = Color(0xFFF4C2C2)
val SoftGreen = Color(0xFF90EE90)
val SoftYellow = Color(0xFFFFF9C4)
```

with the structured scale:

```kotlin
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
```

The full resulting file must look like this:

```kotlin
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

// Light scheme semantic tokens
val PrimaryPink = Color(0xFFC2185B)
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerPink = Color(0xFFF8BBD0)
val OnPrimaryContainerDarkPink = Color(0xFF880E4F)

val SecondaryBlue = Color(0xFF1976D2)
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerBlue = Color(0xFFB3E5FC)
val OnSecondaryContainerDarkBlue = Color(0xFF0D47A1)

val TertiaryGreen = Color(0xFF388E3C)
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerGreen = Color(0xFFC8E6C9)
val OnTertiaryContainerDarkGreen = Color(0xFF1B5E20)

val SurfaceYellow = Color(0xFFFFFDE7)
val OnSurfaceDark = Color(0xFF1A1A1A)
val OnSurfaceVariantGrey = Color(0xFF757575)

// Dark scheme semantic tokens
val PrimaryPinkDark = Color(0xFFF48FB1)
val PrimaryContainerPinkDark = Color(0xFF880E4F)
val SecondaryBlueDark = Color(0xFF90CAF9)
val SecondaryContainerBlueDark = Color(0xFF0D47A1)
val TertiaryGreenDark = Color(0xFFA5D6A7)
val TertiaryContainerGreenDark = Color(0xFF1B5E20)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDarkTheme = Color(0xFFE6E1E5)
```

---

### Task 3: Build verification

**Files:** none

- [ ] **Step 1: Run build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` with no compilation errors.

---

### Task 4: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/Color.kt
git commit -m "refactor(ui): introduce structured palette scale (pink/blue/green 100–900)

Mirrors the design-System-handoff colors_and_type.css scale. Pure additive
refactor — semantic tokens still point at literal hex values and will be
rewired in a follow-up."
```

---

## Acceptance Checklist

- [ ] 12 new scale constants (`Pink100/200/700/900`, `Blue100/200/700/900`, `Green100/200/700/900`) declared in `Color.kt`
- [ ] Old constants (`BabyBlue`, `BabyPink`, `SoftGreen`) deleted (confirmed zero outside usages)
- [ ] `SoftYellow` retained
- [ ] Semantic tokens block (lines after the raw palette) untouched
- [ ] `./gradlew build` passes
- [ ] No visible change in emulator — nothing yet references the new scale
