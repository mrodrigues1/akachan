# SPEC — Task 6: Documentation Update

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** recommended last (so docs reflect merged reality), but technically independent.

---

## Problem

After Tasks 1–5 land, the source of truth for the design system spans three places:

1. `design-System-handoff/akachan-design-system/` — the external handoff bundle.
2. `app/src/main/java/com/babytracker/ui/theme/` — the Kotlin implementation.
3. `CLAUDE.md` — the developer-facing quick reference.

The existing `CLAUDE.md` "Theme & UI Tokens" table mentions a handful of tokens and a couple of typography roles. It doesn't mention the scale structure, the full role list, or that a canonical source exists elsewhere in the repo. And there is no SPEC document cross-referencing the handoff bundle.

## Scope

Two edits:

1. **Expand the `CLAUDE.md` theme section** so it reflects the full canonical token/role set and points at both the handoff bundle and the preview file.
2. **Create `specs/SPEC-004-DesignSystem.md`** as the durable design-system-of-record spec, sitting alongside `SPEC-001-APP-STRUCTURE.md`, `SPEC-002-Onboarding.md`, `SPEC-003-ThemeSelection.md`.

## Files to modify

- `CLAUDE.md`
- `specs/SPEC-004-DesignSystem.md` (new)

## Exact changes

### `CLAUDE.md` — replace the "Theme & UI Tokens" section

Replace the existing short table with:

```markdown
## Theme & UI Tokens

The canonical design system lives in `design-System-handoff/akachan-design-system/`. The Kotlin theme files under `ui/theme/` are the runtime implementation. For a live visual preview, open `ui/theme/DesignSystemPreview.kt` in Android Studio.

### Raw palette (structured scale)

| Scale | Pink (Feeding) | Blue (Sleep) | Green (Success) |
|-------|----------------|--------------|-----------------|
| 900   | `#880E4F`      | `#0D47A1`    | `#1B5E20`       |
| 700   | `#C2185B`      | `#1976D2`    | `#388E3C`       |
| 200   | `#F8BBD0`      | `#B3E5FC`    | `#C8E6C9`       |
| 100   | `#F4C2C2`      | `#89CFF0`    | `#90EE90`       |

Plus `SoftYellow` (`#FFF9C4`) and surface tones (`#FFFDE7` light / `#1C1B1F` dark).

### Semantic color tokens (Material 3)

Every Material 3 color role is now explicitly set in both `LightColorScheme` and `DarkColorScheme`. Key tokens:

| Role | Light | Dark | Usage |
|------|-------|------|-------|
| `primary` | `#C2185B` | `#F48FB1` | Breastfeeding / core actions |
| `secondary` | `#1976D2` | `#90CAF9` | Sleep / secondary actions |
| `tertiary` | `#388E3C` | `#A5D6A7` | Success / overtime alert |
| `surface` | `#FFFDE7` | `#1C1B1F` | Background, cards |
| `surfaceVariant` | `#F0EDE0` | `#2B2930` | Inactive container tiles |
| `outline` | `#CAC4D0` | `#938F99` | Dividers, outlined-card borders |
| `error` / `errorContainer` | `#B00020` / `#FFDAD6` | `#FFB4AB` / `#93000A` | Error states |

### Typography (full scale)

| Role | Weight | Size | Usage |
|------|--------|------|-------|
| `displaySmall` | ExtraBold (800) | 36sp | Timer clock |
| `headlineLarge` | Bold (700) | 32sp | Large headings |
| `titleLarge` | SemiBold (600) | 22sp | Screen titles |
| `titleMedium` | SemiBold (600) | 18sp | Card titles |
| `titleSmall` | SemiBold (600) | 14sp | Card subtitles, button labels |
| `bodyLarge` | Regular (400) | 16sp | Primary body text |
| `bodyMedium` | Regular (400) | 14sp | Supporting body text |
| `bodySmall` | Regular (400) | 12sp | Captions |
| `labelMedium` | Bold (700) | 12sp | Section headers (ALL CAPS) |
| `labelSmall` | Medium (500) | 11sp | Stat labels |

### Shapes

| Token | Radius | Usage |
|-------|--------|-------|
| `extraSmall` | 4dp | Small chips, inline elements |
| `small` | 8dp | History cards |
| `medium` | 16dp | Main cards |
| `large` | 24dp | Home summary cards, banners |
| `extraLarge` | 50dp | Pill buttons, FABs, status chips |
```

### Create `specs/SPEC-004-DesignSystem.md`

```markdown
# SPEC-004 — Design System

## Status

Active. Aligns with `design-System-handoff/akachan-design-system/` bundle.

## Sources of truth

1. **Handoff bundle** — `design-System-handoff/akachan-design-system/` (CSS tokens, HTML prototypes, Android UI kit JSX). The external canonical spec.
2. **Kotlin implementation** — `app/src/main/java/com/babytracker/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`). Maps 1:1 to the handoff bundle's tokens.
3. **Live preview** — `app/src/main/java/com/babytracker/ui/theme/DesignSystemPreview.kt`. Open in Android Studio for a visual reference of the palette and typography scale, light and dark.

## Token architecture

### Raw palette (primitives)

Three lightness scales — Pink (feeding), Blue (sleep), Green (success) — each with stops at 100/200/700/900. Plus `SoftYellow` and surface tones. See `Color.kt`.

### Semantic tokens (theme)

Material 3 color roles (`primary`, `surfaceVariant`, `outline`, `error`, etc.) wired into `LightColorScheme` and `DarkColorScheme` in `Theme.kt`. Semantic tokens reference the raw palette where the relationship is exact; dark-scheme bright tones (`#F48FB1`, `#90CAF9`, `#A5D6A7`) remain as literals because they have no scale equivalent.

### Component usage rule

Never reference raw palette constants (`Pink700`, `Blue200`, etc.) directly from UI code. Always go through `MaterialTheme.colorScheme.*`. The raw scale is a primitive layer for future palette evolution; the semantic tokens are the app-facing API.

## Typography

Ten Material 3 roles, all defined explicitly in `AkachanTypography` (`Type.kt`). Screens should always use `MaterialTheme.typography.*` — never hand-roll `TextStyle` inline.

## Shapes

Five-step radius scale (4/8/16/24/50 dp) in `AkachanShapes` (`Shape.kt`). Use `MaterialTheme.shapes.*`.

## Spacing

Not tokenised in Kotlin yet. Screens use literal `dp` values per `CLAUDE.md`'s spacing guidelines (`16dp` screen margin, `12–20dp` card padding, `8–12dp` inter-element). The handoff bundle's `colors_and_type.css` exposes a `--space-*` scale that we may formalise in a future task if it becomes useful.

## Change process

1. Update the handoff bundle under `design-System-handoff/` if the change originates from a Claude Design export.
2. Update `ui/theme/` Kotlin files to match.
3. Update `CLAUDE.md`'s token/role tables.
4. Open `DesignSystemPreview.kt` in Android Studio to verify the visual result.

## See also

- `CLAUDE.md` — quick reference tables.
- `specs/SPEC-001-APP-STRUCTURE.md` — overall architecture.
- `docs/superpowers/specs/2026-04-20-design-system-alignment-design.md` — the alignment rollout that produced the current state.
```

## Acceptance criteria

- [ ] `CLAUDE.md` "Theme & UI Tokens" section replaced with the full-scale version above.
- [ ] `specs/SPEC-004-DesignSystem.md` exists with the content above.
- [ ] Every token and role referenced in the new `CLAUDE.md` table is declared in `Color.kt` / `Type.kt` (verified by grep).
- [ ] No broken links. Paths referenced (`design-System-handoff/`, `ui/theme/DesignSystemPreview.kt`, `specs/SPEC-001-APP-STRUCTURE.md`) resolve in the repo.

## Rollback

`git revert <sha>`. Pure docs change, no code impact.

## Commit message

```
docs: formalise design system reference and cross-link handoff bundle

Expands CLAUDE.md theme tables to cover the full palette scale, semantic
tokens, and typography roles. Adds SPEC-004-DesignSystem.md linking the
three sources of truth (handoff bundle, Kotlin theme, preview composable).
```
