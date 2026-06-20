# Doctor Visit Slate Theme Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-203

**Goal:** Add the **Slate Blue-Grey** section color identity for the Doctor Visit Log — a raw Blue Grey palette scale, light + dark semantic tokens, a `doctorVisitColors()` helper, and a design-system preview entry.

**Architecture:** Extended (non-M3) section tokens, accessed as top-level `val`s and via a `doctorVisitColors()` helper — **never** through `MaterialTheme.colorScheme` (per `CLAUDE.md`). Mirrors the existing Vaccine/Indigo and Growth/Teal token blocks in `ui/theme/Color.kt`.

**Tech Stack:** Jetpack Compose, Material 3, Kotlin.

## Global Constraints

- Section tokens are top-level `val`s in `ui/theme/Color.kt`; do not wire into `colorScheme`.
- Light on-accent text must reach ≥4.5:1 contrast on the accent; on-container text ≥4.5:1 on the container.
- Match the structure/naming of the existing `vaccineColors()` / `diaperColors()` helpers exactly.

**Dependencies:** None (can land independently). Plan 5 consumes `doctorVisitColors()` for the Home tile; plan 6 for history rows.

**Suggested implementation branch:** `feat/doctor-visit-slate-theme`

**Project convention:** Implement first, then preview. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: Raw Blue Grey palette scale

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1: Add the raw scale** near the other raw palette blocks (Teal/Yellow). Scale semantics match the existing palettes (700 = primary action, 200 = container, 900 = on-container text, 100 = softest). Material Blue Grey values.

```kotlin
// ─── Raw palette — Doctor Visit / Blue Grey ──────────────────
// Extended (non-M3) accent. A clinical slate (stethoscope steel / medical clipboard),
// distinct from sleep blue, vaccine indigo, growth teal, and the Success/Tertiary green.
// Scale semantics match Pink/Blue/Green/Teal (700 = primary, 200 = container, 900 = on-container).
val BlueGrey900 = Color(0xFF263238)
val BlueGrey700 = Color(0xFF455A64)
val BlueGrey200 = Color(0xFFCFD8DC)
val BlueGrey100 = Color(0xFFECEFF1)
```

> If any `BlueGrey*` val already exists in the file, reuse it instead of redeclaring (search first).

- [ ] **Step 2: Commit** `feat(doctor-visit): add Blue Grey raw palette scale`

---

### Task 2: Slate semantic tokens (light + dark)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt`

- [ ] **Step 1: Add the semantic tokens** mirroring the Vaccine/Indigo block.

```kotlin
// ─── Doctor Visit semantic tokens (extended, non-M3) ─────────
// Accessed as top-level vals / via doctorVisitColors(), NOT through MaterialTheme.colorScheme.
// Mirrors the Vaccine/Growth/Diaper extended-token convention.

// Light scheme — white-on-BlueGrey700 ≈ 7.4:1; BlueGrey900-on-BlueGrey200 well above 4.5:1.
val DoctorSlate = BlueGrey700
val OnDoctorWhite = Color(0xFFFFFFFF)
val DoctorContainerSlate = BlueGrey200
val OnDoctorContainerSlate = BlueGrey900

// Dark scheme — brighter accent on dark surface; light text on the deep container.
val DoctorSlateDark = Color(0xFF90A4AE)   // Blue Grey 300
val OnDoctorDark = BlueGrey900
val DoctorContainerSlateDark = Color(0xFF37474F) // Blue Grey 800
val OnDoctorContainerSlateDark = BlueGrey200
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add Slate Blue-Grey semantic tokens`

---

### Task 3: `doctorVisitColors()` helper

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt` (or the file where `vaccineColors()` / `diaperColors()` live — search for `fun vaccineColors(` and place this beside it, matching its exact return type)

- [ ] **Step 1: Find the `vaccineColors()` helper** and replicate its shape. It returns a small data class / struct of `(accent, onAccent, container, onContainer)` resolved by `isSystemInDarkTheme()`. Mirror it:

```kotlin
@Composable
fun doctorVisitColors(): SectionColors = if (isSystemInDarkTheme()) {
    SectionColors(
        accent = DoctorSlateDark,
        onAccent = OnDoctorDark,
        container = DoctorContainerSlateDark,
        onContainer = OnDoctorContainerSlateDark,
    )
} else {
    SectionColors(
        accent = DoctorSlate,
        onAccent = OnDoctorWhite,
        container = DoctorContainerSlate,
        onContainer = OnDoctorContainerSlate,
    )
}
```

> Replace `SectionColors` with whatever the existing `vaccineColors()` returns (it may be a named data class or a Compose-specific holder). The point is structural parity — copy the exact return type and `@Composable`/import setup from `vaccineColors()`.

- [ ] **Step 2: Commit** `feat(doctor-visit): add doctorVisitColors helper`

---

### Task 4: Design-system preview entry

**Files:**
- Modify: the design-system preview screen (search `rg "vaccineColors\(\)" -g "*.kt"` and `rg "Design.?System.?Preview" -g "*.kt"` to find where section swatches are listed — likely `ui/theme/` or a `*Preview`/`*Showcase` composable referenced in `docs/superpowers/specs/2026-04-20-design-system-task-5-design-system-preview.md`)

- [ ] **Step 1: Add a Doctor Visit swatch row** to the section-colors preview, mirroring the Vaccine entry: a labeled row showing accent + container, in both light and dark previews.
- [ ] **Step 2: Build the preview** — `./gradlew :app:compileDebugKotlin` — expect success (previews compile with the main source set).
- [ ] **Step 3: Commit** `feat(doctor-visit): add Slate swatch to design-system preview`

---

## Acceptance Criteria

- `./gradlew build` succeeds.
- `DoctorSlate` (#455A64) and its container/on-container tokens exist for light and dark, as top-level `val`s (not via `colorScheme`).
- `doctorVisitColors()` returns the same type as `vaccineColors()` and resolves dark/light correctly.
- The design-system preview shows the Slate section swatch.

## Self-Review Notes

- Spec coverage: raw scale, semantic tokens (light+dark), `doctorVisitColors()`, preview entry — all present.
- Colors chosen to be distinct from sleep blue (#1976D2), vaccine indigo (#303F9F), growth teal (#00897B), and the Success/Tertiary green; slate reads clinical.
- If the repo's section-color helper type differs from a `SectionColors` struct, the implementer copies the exact `vaccineColors()` signature — this plan's body is illustrative of structure, not a new type to invent.
