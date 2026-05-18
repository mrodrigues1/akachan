# Dark Mode Visibility — Design Spec

**Date:** 2026-05-09  
**Status:** Approved

---

## Problem

Two distinct visibility problems in dark mode:

1. **OutlinedCard borders invisible** — `OutlineVariantDark = #49454F` has 1.8:1 contrast against `SurfaceDark = #1C1B1F`. Fails WCAG 1.4.11 (minimum 3:1 for non-text UI components). Affects: Switch Side, Pause Session, Stop Session cards in `BreastfeedingScreen`.

2. **Borderless cards indistinguishable from background** — `HistoryCard`, Home summary cards, and `LastFeedingSummaryCard` use elevation-only (1–2dp). Material 3 tonal elevation at those levels applies ~5% primary tint overlay — imperceptible in practice.

---

## Solution

### 1. Token fix — `Color.kt`

| Token | Before | After | Contrast gain |
|-------|--------|-------|---------------|
| `OutlineVariantDark` | `#49454F` | `#79747E` | 1.8:1 → 5.5:1 |

No other tokens change. `OutlineDark` (`#938F99`) is already at 5.5:1 — left as-is. `SideSelector` unselected border uses `primaryContainer` (pink) — intentional branded design, left unchanged.

### 2. `LocalDarkTheme` CompositionLocal — `Theme.kt`

Add a `CompositionLocal<Boolean>` that exposes the **resolved** dark theme state (accounting for all three `ThemeConfig` modes: `SYSTEM`, `LIGHT`, `DARK`). Components consume this instead of `isSystemInDarkTheme()`.

`isSystemInDarkTheme()` only reads the OS setting — it misses the case where the user picks `ThemeConfig.DARK` in Settings while the system is in light mode. `LocalDarkTheme` is set to the same `darkTheme` value that `BabyTrackerTheme` already resolves, so all three modes are covered correctly.

```kotlin
val LocalDarkTheme = compositionLocalOf { false }

// Inside BabyTrackerTheme:
CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
    MaterialTheme(...)
}
```

### 3. Explicit dark-mode borders on cards

Cards that currently use elevation-only get a `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)` when `LocalDarkTheme.current` is true.

| File | Cards affected |
|------|---------------|
| `ui/component/HistoryCard.kt` | `HistoryCard` component |
| `ui/home/HomeScreen.kt` | Breastfeeding summary card, Sleep summary card |
| `ui/breastfeeding/BreastfeedingScreen.kt` | `LastFeedingSummaryCard` |

---

## What does NOT change

- `SideSelector` unselected border — stays `primaryContainer` (Pink900 `#880E4F`)
- `OutlineDark` — stays `#938F99`
- All light-mode colors — no light-scheme changes
- All other dark-scheme tokens

---

## Files touched

1. `app/src/main/java/com/babytracker/ui/theme/Color.kt` — one token value
2. `app/src/main/java/com/babytracker/ui/theme/Theme.kt` — add `LocalDarkTheme`, provide it in `BabyTrackerTheme`
3. `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt` — consume `LocalDarkTheme`, add border
4. `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — consume `LocalDarkTheme`, two cards
5. `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt` — consume `LocalDarkTheme`, one card

---

## Verification

All three `ThemeConfig` modes must be verified independently:

| Mode | OS theme | Expected |
|------|----------|----------|
| Settings → System | OS dark | Borders visible |
| Settings → System | OS light | Borders NOT visible |
| Settings → Dark | OS light | Borders visible |
| Settings → Dark | OS dark | Borders visible |
| Settings → Light | OS dark | Borders NOT visible |
| Settings → Light | OS light | Borders NOT visible |

Per-screen checklist (in dark mode):
- `BreastfeedingScreen`: Switch Side / Pause / Stop card borders clearly visible
- `HomeScreen`: Breastfeeding and Sleep summary cards have visible outlines
- `BreastfeedingScreen` (no active session): Last Feeding Summary card has visible outline
- `BreastfeedingHistoryScreen` and `SleepHistoryScreen`: HistoryCard entries have visible outlines
- `SideSelector` unselected border stays pink in dark mode (unchanged)
