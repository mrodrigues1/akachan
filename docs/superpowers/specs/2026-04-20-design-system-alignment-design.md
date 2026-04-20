# Design System Alignment — Master Design

**Date:** 2026-04-20
**Status:** Approved
**Source of truth:** `design-System-handoff/akachan-design-system/`

---

## Context

Anthropic's Claude Design tool produced a handoff bundle for Akachan under `design-System-handoff/akachan-design-system/`. The bundle's README states the system was **"derived entirely from codebase analysis"** — it is effectively a formal externalisation of the app's existing visual language, not a new design.

A delta audit confirms the app is already ~95% compliant with the documented tokens, components, and animations. The remaining gaps fall into two categories:

1. **Unformalised tokens** — the design system names typography roles and color tokens that the app currently relies on Material 3 defaults for, rather than defining explicitly in its own theme. Examples: `titleMedium`, `bodyMedium`, `outline`, `surfaceVariant`, `errorContainer`.
2. **Visible micro-polish** — `HistoryCard` badge size (36dp vs. spec 40–44dp), status pill glyph style, bottom-nav tab count ambiguity.

This design covers category 1 only. Category 2 is deliberately out of scope.

## Goal

Canonicalise the design system as a formal source of truth inside the app. After this work:

- Every semantic token and typography role referenced by `design-System-handoff/` has an explicit definition in `Color.kt`, `Type.kt`, `Theme.kt`.
- The raw palette follows the structured scale defined in `colors_and_type.css` (`Pink900/700/200/100`, blue, green).
- Developers can see the design system inside Android Studio via a live `@Preview` composable.
- Documentation cross-references the handoff bundle.

**Zero user-visible change** is expected. All overrides resolve to the same hex / sp values Material 3 was already providing.

## Non-goals

- No screen edits. No `@Composable` refactors beyond the theme files and the new preview file.
- No behaviour changes. No component polish (badge sizes, pill glyphs, etc.) — those are separate future work.
- No bottom-nav restructuring — the design system's own docs contradict themselves (3 vs. 4 tabs) and the question is parked until a product decision is made.
- No migration tooling. No deprecation warnings for the old `BabyBlue`/`BabyPink`/`SoftGreen`/`SoftYellow` raw constants — they stay or are removed based on actual usage, nothing more.

## Approach

Bottom-up token-ordered rollout across six small, independently shippable tasks:

```
Task 1 (raw palette scales)
   └─ Task 2 (rewire semantic tokens to scales)

Task 3 (add missing semantic color tokens) — independent
Task 4 (add missing typography roles)      — independent
Task 5 (design system preview composable)  — independent
Task 6 (documentation update)              — independent
```

Only Task 2 has a hard dependency (on Task 1). The others can ship in any order. Each task is scoped to one file or one concern, with a single small commit expected per task.

## Token decisions

### Raw palette — new structured scale (from `colors_and_type.css`)

```
Pink900 = #880E4F   Pink700 = #C2185B   Pink200 = #F8BBD0   Pink100 = #F4C2C2
Blue900 = #0D47A1   Blue700 = #1976D2   Blue200 = #B3E5FC   Blue100 = #89CFF0
Green900= #1B5E20   Green700= #388E3C   Green200= #C8E6C9   Green100= #90EE90
```

Yellow surface (`#FFFDE7`) and dark surface (`#1C1B1F`) remain as-is — they don't fit a 900/700/200/100 lightness scale.

### Semantic color tokens to add (light / dark)

| Token | Light | Dark |
|---|---|---|
| `surfaceVariant` | `#F0EDE0` | `#2B2930` |
| `outline` | `#CAC4D0` (mapped from Material 3 default `#79747E` — design spec uses `#CAC4D0`; we follow the spec) | `#938F99` |
| `outlineVariant` | `#CAC4D0` | `#49454F` |
| `error` | `#B00020` | `#FFB4AB` |
| `errorContainer` | `#FFDAD6` | `#93000A` |
| `onErrorContainer` | `#410002` | `#FFDAD6` |

Note on `outline`: the design system CSS has `--color-outline: #79747E` in its light block (line 73) but `#CAC4D0` elsewhere. The `Screens.jsx` prototype uses `AK.outline = '#CAC4D0'` (line 20). We follow `Screens.jsx` since it's the canonical component layer.

### Typography roles to add

| Role | Weight | Size | Line-height | Letter-spacing |
|---|---|---|---|---|
| `titleMedium` | SemiBold (600) | 18sp | 26sp | 0 |
| `titleSmall` | SemiBold (600) | 14sp | 20sp | 0 |
| `bodyMedium` | Regular (400) | 14sp | 20sp | 0 |
| `bodySmall` | Regular (400) | 12sp | 16sp | 0 |

Values sourced from `colors_and_type.css` lines 183–219.

## Verification

- **Per task:** `./gradlew build` must pass. Task 5 adds a visual preview that serves as a living spec from that point forward.
- **No new unit tests.** Token additions are inert — unit-testing them just restates the source. The `@Preview` composable in Task 5 is the verification surface.
- **Manual spot-check after Task 3 and Task 4:** run the app in emulator and confirm no visible regression. The explicit tokens we're adding should resolve to the same values Material 3 was providing.

## Per-task SPECs

Seven individual SPEC files live alongside this document:

1. `2026-04-20-design-system-task-1-raw-palette-scales.md`
2. `2026-04-20-design-system-task-2-rewire-semantic-tokens.md`
3. `2026-04-20-design-system-task-3-semantic-color-tokens.md`
4. `2026-04-20-design-system-task-4-typography-roles.md`
5. `2026-04-20-design-system-task-5-design-system-preview.md`
6. `2026-04-20-design-system-task-6-documentation.md`
7. `2026-04-20-design-system-task-7-history-card-badge-size.md` — scoped visible-polish exception (HistoryCard badge 36dp → 44dp)

Each SPEC is self-contained: problem, files, exact changes, acceptance criteria, rollback plan.

## Risks

- **Outline color mismatch**: We're overriding Material 3's default outline (`#79747E`) with `#CAC4D0`. This will lighten dividers and outlined-card borders slightly in light mode. If this turns out to be visually noticeable and undesired, revert by omitting `outline` from `LightColorScheme` in Task 3.
- **Behavior change from Material 3 override**: Any component that used `MaterialTheme.colorScheme.outlineVariant` or `MaterialTheme.typography.titleMedium` etc. will now get the explicit canonical value. If Material 3's defaults differed meaningfully from our canonical values for any role, the change will be visible. Mitigation: the post-Task-3 and post-Task-4 manual spot-check.
- **Dead-code retention**: `BabyBlue`, `BabyPink`, `SoftGreen`, `SoftYellow` may be unused after Task 1. Removal is in scope if usage is zero; otherwise keep. Each task's SPEC specifies whether deletion is expected.

## Rollback

All six tasks are additive or rewiring. Rollback per task is `git revert <sha>`. No DB migrations, no data format changes, nothing shared across tasks that can't be unwound individually.
