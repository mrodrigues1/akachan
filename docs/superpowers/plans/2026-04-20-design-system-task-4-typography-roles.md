# Design System Task 4: Complete Typography Roles — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the 7 missing Material 3 typography slots to `AkachanTypography` so every text role used across all screens has an intentional definition rather than silently falling back to M3 defaults.

**Architecture:** Single-file additive edit — 7 new named parameters appended to the `Typography(...)` call in `Type.kt`. No existing slot or any call-site (`MaterialTheme.typography.*`) is touched. The moment a slot is defined, Material 3 automatically serves it to every composable that references that slot.

**Tech Stack:** Kotlin, `androidx.compose.ui.text.TextStyle`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.sp`, Jetpack Compose Material 3 `Typography`

---

### Task 1: Confirm baseline

**Files:**
- Read: `app/src/main/java/com/babytracker/ui/theme/Type.kt`

- [ ] **Step 1: Verify Type.kt has exactly 6 defined slots**

Open `app/src/main/java/com/babytracker/ui/theme/Type.kt` and confirm:

1. `AkachanTypography` is declared as `val AkachanTypography = Typography(...)`.
2. Exactly 6 named parameters exist: `displaySmall`, `headlineLarge`, `titleLarge`, `bodyLarge`, `labelMedium`, `labelSmall`.
3. None of these slots are present yet (these are what Task 4 adds): `headlineMedium`, `headlineSmall`, `titleMedium`, `titleSmall`, `bodyMedium`, `bodySmall`, `labelLarge`.

If any of those 7 slots already appear, stop — Task 4 was already applied.

The current baseline should look exactly like:

```kotlin
val AkachanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

### Task 2: Add the 7 missing typography slots

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Type.kt:51` (before the closing `)`)

**Slot-to-usage mapping** (for reference — guides the weight choices):

| Slot | Where used in this app |
|------|----------------------|
| `headlineMedium` | WelcomeStepContent title, 🍼/🌙 emoji on HomeScreen cards, SideSelector emoji |
| `headlineSmall` | Onboarding step headings (AllergiesStepContent, BabyInfoStepContent) |
| `titleMedium` | Card subtitles, session info titles (BreastfeedingScreen, history screens, HomeScreen) |
| `titleSmall` | Button labels ("Start Session", "+ Add Sleep Entry"), small card titles |
| `bodyMedium` | Descriptions, metadata across breastfeeding, sleep, home, and settings screens |
| `bodySmall` | Hints, disclaimers, schedule detail rows, allergy chips |
| `labelLarge` | SideSelector "LEFT"/"RIGHT" label |

**Weight convention** followed by the existing 6 slots: weights are equal to or one step heavier than M3 defaults (M3 uses Medium for most text — this app uses SemiBold or Bold in equivalent roles).

- [ ] **Step 1: Add the 7 slots**

In `Type.kt`, locate the closing `)` on line 52 (after `labelSmall`). Replace it with the 7 new slots plus the closing `)`:

```kotlin
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
```

The complete resulting `Type.kt` should look like:

```kotlin
package com.babytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AkachanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

### Task 3: Build verification

**Files:** none

- [ ] **Step 1: Run the build**

```bash
./gradlew build
```

Expected output ends with:

```
BUILD SUCCESSFUL in Xs
```

If it fails, the most likely cause is a syntax error in the new `TextStyle` blocks (mismatched parentheses or a trailing comma issue). Re-read the `Typography(...)` call and ensure every slot ends with `,` except the last one before the closing `)`.

---

### Task 4: Run unit tests

**Files:** none

- [ ] **Step 1: Run unit tests**

```bash
./gradlew test
```

Expected output ends with:

```
BUILD SUCCESSFUL in Xs
```

Typography changes are pure configuration — no unit test changes are needed. This step confirms nothing else regressed.

---

### Task 5: Manual visual spot-check

**Files:** none

- [ ] **Step 1: Launch the app and verify key screens**

Run the app on an emulator (API 26+). Check each screen listed below and verify text is rendered with the correct weight/size:

| Screen | Element | Slot | Expected appearance |
|--------|---------|------|---------------------|
| Onboarding → Welcome | Main heading ("Welcome to Akachan") | `headlineMedium` | Bold 28sp — noticeably heavier than M3 default Medium |
| Onboarding → Baby Info / Allergies | Step heading | `headlineSmall` | SemiBold 24sp |
| Home | 🍼 / 🌙 emoji on summary cards | `headlineMedium` | Bold 28sp |
| Home → Breastfeeding card | Session title ("Last Session") | `titleMedium` | SemiBold 16sp — slightly heavier than M3 Medium |
| Home → Quick Action chip | Action label | `titleSmall` | Medium 14sp |
| Breastfeeding | Notes / metadata below timer | `bodyMedium` | Normal 14sp |
| Sleep Schedule | Schedule detail row | `bodySmall` | Normal 12sp |
| Breastfeeding → SideSelector | "LEFT" / "RIGHT" label | `labelLarge` | SemiBold 14sp |

Also confirm no crashes on home, breastfeeding, sleep, settings, and onboarding screens.

---

### Task 6: Commit

**Files:** none

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/Type.kt
git commit -m "feat(ui): complete AkachanTypography with all 7 missing M3 slots

Adds headlineMedium, headlineSmall, titleMedium, titleSmall,
bodyMedium, bodySmall, and labelLarge to AkachanTypography.
Every text role used across all screens now has an intentional
definition aligned with the app weight convention (equal to or one
step heavier than M3 defaults), replacing silent M3 fallbacks."
```

---

## Acceptance Checklist

- [ ] `headlineMedium`: Bold 28sp in `AkachanTypography`
- [ ] `headlineSmall`: SemiBold 24sp in `AkachanTypography`
- [ ] `titleMedium`: SemiBold 16sp in `AkachanTypography`
- [ ] `titleSmall`: Medium 14sp in `AkachanTypography`
- [ ] `bodyMedium`: Normal 14sp in `AkachanTypography`
- [ ] `bodySmall`: Normal 12sp in `AkachanTypography`
- [ ] `labelLarge`: SemiBold 14sp in `AkachanTypography`
- [ ] All 6 pre-existing slots are unchanged
- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes
- [ ] No crashes on any screen in the emulator
- [ ] Onboarding welcome heading renders visibly heavier/larger than before
- [ ] SideSelector "LEFT"/"RIGHT" label renders at SemiBold weight
