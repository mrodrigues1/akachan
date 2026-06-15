# Growth Tracking — Visual Identity (Teal) Design

**Date:** 2026-06-15
**Project:** Milestone Tracker & Growth Tracking (AKA)

## Problem

Growth tracking has no visual identity of its own. It borrows two unrelated
feature colors:

- The Home **Growth tile** uses `secondaryContainer` / `onSecondaryContainer`
  — the **Blue** Sleep palette.
- Inside **GrowthScreen**, the latest-percentile text and the chart plotted
  points use `colorScheme.primary` — the **Pink/red** Feeding palette. The
  `SetSexCard` reuses Blue; the chart median line uses `tertiary` (Green).

Result: Growth looks like Sleep on the Home grid and like Feeding once opened.
It reads as borrowed, not as its own section.

## Decision

Give Growth a dedicated **Teal** identity, distinct from every existing feature
color (Feeding Pink, Sleep Blue, Success Green, Milestones Purple, Warning
Amber). Teal conveys growth/vitality/freshness while staying clear of the app's
forest-green Success token.

Teal is an **extended, non-M3 token** — `primary`/`secondary`/`tertiary` are
already taken — so it follows the exact pattern Milestones uses: a raw scale +
semantic `Growth*` tokens in `Color.kt`, surfaced through a `growthColors()`
helper, accessed outside `MaterialTheme.colorScheme`.

### Palette (M3-style scale: 700 action, 200 container, 900 on-container)

| Token | Light | Dark |
|---|---|---|
| Teal900 | `#004D40` | — |
| Teal700 | `#00897B` | — |
| Teal200 | `#B2DFDB` | — |
| Teal100 | `#80CBC4` | — |
| accent | `#00897B` (Teal700) | `#4DB6AC` |
| container | `#B2DFDB` (Teal200) | `#004D40` (Teal900) |
| onContainer | `#004D40` (Teal900) | `#B2DFDB` (Teal200) |
| onAccent | `#FFFFFF` | `#B2DFDB` |

Contrast: white-on-Teal700 ≈ 4.6:1; Teal900-on-Teal200 well above 4.5:1; dark
accent `#4DB6AC` on `SurfaceDark` clears WCAG AA.

## Reimagined section

| Element | Now | After |
|---|---|---|
| Home Growth tile container | `secondaryContainer` (Blue) | `growthColors().container` |
| Home Growth tile text | `onSecondaryContainer` | `growthColors().onContainer` |
| Latest-percentile text | `primary` (Pink) | `growthColors().accent` |
| Chart plotted points | `primary` (Pink) | `growthColors().accent` |
| Chart median line | `tertiary` (Green) | `growthColors().accent` |
| `SetSexCard` container/text | Blue | growth container / onContainer |
| Chart curve guide lines | `outline` | unchanged (neutral guides) |

## Components

- **`ui/theme/Color.kt`** — add the Teal raw scale + `Growth*` light/dark
  semantic tokens, mirroring the existing Milestone block.
- **`ui/theme/GrowthPalette.kt`** (new) — `data class GrowthPalette(accent,
  container, onContainer, onAccent)` + `@Composable @ReadOnlyComposable
  fun growthColors()`, a direct mirror of `MilestonePalette.kt` using
  `LocalDarkTheme.current`.
- **`ui/home/HomeScreen.kt`** — `GrowthHomeCard` swaps the two Blue references
  for `growthColors()`.
- **`ui/growth/GrowthScreen.kt`** — percentile text, plotted points, median
  line, and `SetSexCard` switch to `growthColors()`.

## Non-goals

- No change to the app's M3 `colorScheme` (Feeding/Sleep/Success keep their M3
  slots).
- No change to growth data, WHO curves, units, or chart math.
- Milestones, Trends, and all other sections are untouched.

## Testing

- Reuse the `MilestonePalette` light/dark resolution pattern; cover
  `growthColors()` swatch values per scheme if a theme unit test exists for the
  Milestone equivalent, otherwise rely on Compose previews.
- Update `GrowthScreenTest` only if it asserts specific colors (it does not
  today); keep behavioral assertions intact.

## Rollout (PR-sized issues)

1. **Theme foundation** — Teal scale + `Growth*` tokens + `growthColors()`
   helper. Self-contained, no UI consumer yet.
2. **Apply Growth identity** — recolor `GrowthHomeCard` + `GrowthScreen`;
   depends on (1).
