# Vaccine Indigo Theme Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-196

**Goal:** Add the Vaccine section's visual identity — an **Indigo** accent — as extended (non-M3) color tokens and a `vaccineColors()` resolver, mirroring `DiaperPalette` / `GrowthPalette` / `MilestonePalette`.

**Architecture:** Each section ships a raw color scale + semantic tokens as top-level `val`s in `ui/theme/Color.kt` (accessed by name, **never** through `MaterialTheme.colorScheme` — see `CLAUDE.md`), plus a small `*Palette.kt` data class + `@Composable @ReadOnlyComposable fun *Colors()` that resolves light/dark via `LocalDarkTheme.current`. Indigo is unused by any existing section (distinct from sleep blue `#1976D2`, milestone purple `#7B1FA2`, growth teal, diaper yellow, breastfeeding pink, bottle-feed red, warning amber).

**Indigo contrast note:** unlike the yellow Diaper accent (light in both schemes, so `onAccent` is always dark), the Indigo accent flips: the light-scheme accent `Indigo700 #303F9F` is dark (white text reads on it), while the dark-scheme accent `Indigo200 #9FA8DA` is light (dark text reads on it). So `onAccent` differs per scheme.

**Tech Stack:** Jetpack Compose, Material 3, Kotlin.

**Dependencies:** None (pure theme additions). Plans 4–6 consume `vaccineColors()`; this plan can land before or in parallel with them.

**Suggested implementation branch:** `feat/vaccine-indigo-theme`

**Project convention:** Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Indigo raw palette + Vaccine semantic tokens in `Color.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1:** Append after the Diaper semantic-token block (≈ line 186). Keep the existing section-comment style.

```kotlin
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
```

- [ ] **Step 2: Commit** `feat(ui): add Indigo raw palette and Vaccine color tokens`

---

### Task 2: `VaccinePalette` + `vaccineColors()`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/theme/VaccinePalette.kt`

- [ ] **Step 1: Create the resolver** (mirrors `DiaperPalette.kt`, but `onAccent` flips by scheme)

```kotlin
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
```

- [ ] **Step 2: Commit** `feat(ui): add VaccinePalette and vaccineColors resolver`

---

### Task 3: Add Vaccine to the design-system preview (if present)

**Files:**
- Modify: the design-system preview/gallery composable, if one exists.

- [ ] **Step 1:** Search for the design-system preview that renders the section palettes (e.g. `rg -l "diaperColors|GrowthPalette|DesignSystem" app/src/main`; the design-system preview was added in `docs/superpowers/specs/2026-04-20-design-system-task-5-design-system-preview.md`). If found, add a Vaccine swatch row using `vaccineColors()` alongside the Diaper/Growth/Milestone rows. If no such gallery exists, skip this task (note it in the commit/PR).
- [ ] **Step 2: Commit** `feat(ui): show Vaccine palette in the design-system preview`

---

## Acceptance Criteria

- `./gradlew build` compiles with the new tokens and resolver.
- `vaccineColors()` returns the Indigo accent set, switching correctly between light and dark via `LocalDarkTheme`.
- No section reuses the Indigo scale; the tokens are top-level `val`s (not added to `MaterialTheme.colorScheme`).
- Text-on-accent and text-on-container pairs meet WCAG AA (~4.5:1+) in both schemes.

## Self-Review Notes

- Mirrors the established `DiaperPalette` structure exactly except for the per-scheme `onAccent` flip, which is required because Indigo (unlike yellow) is a dark accent in the light scheme.
- These are pure presentation tokens with no business logic, so no unit test is added (consistent with the other `*Palette.kt` files). The design-system preview is the visual regression surface.
- Plans 4–6 import `vaccineColors()` and the named tokens; keep the token names stable (`VaccineIndigo`, `VaccineContainerIndigo`, `OnVaccine`, and the `*Dark` pairs).
