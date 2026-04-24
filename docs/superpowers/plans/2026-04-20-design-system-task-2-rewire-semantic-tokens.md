# Design System Task 2: Rewire Semantic Tokens — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-point every semantic color token in `Color.kt` whose hex value has an exact match in the new palette scale to reference that scale constant instead of repeating a literal.

**Architecture:** Single-file edit of `Color.kt`. Twelve literal `Color(0xFF…)` calls in the light- and dark-scheme semantic token blocks are replaced with scale constant references (`Pink700`, `Blue200`, `Pink900`, etc.). Three dark-scheme tones that are lighter than the `100` level and three neutral/surface tokens have no scale equivalent and stay as literals. `Theme.kt` is not touched.

**Tech Stack:** Kotlin, `androidx.compose.ui.graphics.Color`

---

### Task 1: Confirm scale constants are present and Color.kt is at the expected baseline

**Files:**
- Read: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1: Read the file and verify the baseline**

Open `app/src/main/java/com/babytracker/ui/theme/Color.kt` and confirm:

1. The raw palette block (lines 5–28) contains all 12 scale constants:
   `Pink900`, `Pink700`, `Pink200`, `Pink100`,
   `Blue900`, `Blue700`, `Blue200`, `Blue100`,
   `Green900`, `Green700`, `Green200`, `Green100`.
2. The light-scheme semantic tokens (lines 30–48) still use `Color(0xFF…)` literals.
3. The dark-scheme semantic tokens (lines 50–58) still use `Color(0xFF…)` literals.

If the file matches the expected state, proceed to Task 2. If the scale constants are missing, Task 1 was not applied — do not continue until Task 1 is complete.

---

### Task 2: Rewire light-scheme semantic tokens

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt:30-48`

- [ ] **Step 1: Replace the light-scheme semantic token block**

Replace this exact block (lines 30–48):

```kotlin
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
```

with:

```kotlin
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
```

**Rewiring rationale per token:**

| Token | Old literal | Scale ref | Why |
|---|---|---|---|
| `PrimaryPink` | `0xFFC2185B` | `Pink700` | Exact match |
| `PrimaryContainerPink` | `0xFFF8BBD0` | `Pink200` | Exact match |
| `OnPrimaryContainerDarkPink` | `0xFF880E4F` | `Pink900` | Exact match |
| `SecondaryBlue` | `0xFF1976D2` | `Blue700` | Exact match |
| `SecondaryContainerBlue` | `0xFFB3E5FC` | `Blue200` | Exact match |
| `OnSecondaryContainerDarkBlue` | `0xFF0D47A1` | `Blue900` | Exact match |
| `TertiaryGreen` | `0xFF388E3C` | `Green700` | Exact match |
| `TertiaryContainerGreen` | `0xFFC8E6C9` | `Green200` | Exact match |
| `OnTertiaryContainerDarkGreen` | `0xFF1B5E20` | `Green900` | Exact match |
| `OnPrimaryWhite` | `0xFFFFFFFF` | *(keep literal)* | Pure white — no brand scale membership |
| `OnSecondaryWhite` | `0xFFFFFFFF` | *(keep literal)* | Same |
| `OnTertiaryWhite` | `0xFFFFFFFF` | *(keep literal)* | Same |
| `SurfaceYellow` | `0xFFFFFDE7` | *(keep literal)* | Not in scale |
| `OnSurfaceDark` | `0xFF1A1A1A` | *(keep literal)* | Not in scale |
| `OnSurfaceVariantGrey` | `0xFF757575` | *(keep literal)* | Not in scale |

---

### Task 3: Rewire dark-scheme semantic tokens

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt:50-58`

- [ ] **Step 1: Replace the dark-scheme semantic token block**

Replace this exact block (lines 50–58):

```kotlin
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

with:

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
```

**Rewiring rationale per token:**

| Token | Old literal | Scale ref | Why |
|---|---|---|---|
| `PrimaryContainerPinkDark` | `0xFF880E4F` | `Pink900` | Exact match |
| `SecondaryContainerBlueDark` | `0xFF0D47A1` | `Blue900` | Exact match |
| `TertiaryContainerGreenDark` | `0xFF1B5E20` | `Green900` | Exact match |
| `PrimaryPinkDark` | `0xFFF48FB1` | *(keep literal)* | `#F48FB1` is brighter than `Pink100 (#F4C2C2)` — not in scale |
| `SecondaryBlueDark` | `0xFF90CAF9` | *(keep literal)* | `#90CAF9` is brighter than `Blue100 (#89CFF0)` — not in scale |
| `TertiaryGreenDark` | `0xFFA5D6A7` | *(keep literal)* | `#A5D6A7` is brighter than `Green100 (#90EE90)` — not in scale |
| `SurfaceDark` | `0xFF1C1B1F` | *(keep literal)* | Not in scale |
| `OnSurfaceDarkTheme` | `0xFFE6E1E5` | *(keep literal)* | Not in scale |

---

### Task 4: Verify the full resulting file

**Files:**
- Read: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1: Confirm the full file content**

After both edits, `Color.kt` must match exactly:

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
```

---

### Task 5: Build verification

**Files:** none

- [ ] **Step 1: Run the build**

```bash
./gradlew build
```

Expected output ends with:

```
BUILD SUCCESSFUL in Xs
```

If there are compilation errors, the most likely cause is a missing import or a typo in a scale constant name (`Pink700` vs `Pink700`). Re-read `Color.kt` and fix the mismatch.

---

### Task 6: Commit

**Files:** none

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/Color.kt
git commit -m "refactor(ui): rewire semantic color tokens to palette scale constants

Light-scheme primary/secondary/tertiary tokens and dark-scheme containers
now reference Pink/Blue/Green scale constants from Task 1 instead of
duplicate hex literals. No behaviour change."
```

---

## Acceptance Checklist

- [ ] `PrimaryPink = Pink700` (not `Color(0xFFC2185B)`)
- [ ] `PrimaryContainerPink = Pink200`
- [ ] `OnPrimaryContainerDarkPink = Pink900`
- [ ] `SecondaryBlue = Blue700`
- [ ] `SecondaryContainerBlue = Blue200`
- [ ] `OnSecondaryContainerDarkBlue = Blue900`
- [ ] `TertiaryGreen = Green700`
- [ ] `TertiaryContainerGreen = Green200`
- [ ] `OnTertiaryContainerDarkGreen = Green900`
- [ ] `PrimaryContainerPinkDark = Pink900`
- [ ] `SecondaryContainerBlueDark = Blue900`
- [ ] `TertiaryContainerGreenDark = Green900`
- [ ] `OnPrimaryWhite`, `OnSecondaryWhite`, `OnTertiaryWhite` remain `Color(0xFFFFFFFF)`
- [ ] `PrimaryPinkDark`, `SecondaryBlueDark`, `TertiaryGreenDark` remain literal hex
- [ ] `SurfaceYellow`, `OnSurfaceDark`, `OnSurfaceVariantGrey`, `SurfaceDark`, `OnSurfaceDarkTheme` unchanged
- [ ] `Theme.kt` NOT modified
- [ ] `./gradlew build` passes
