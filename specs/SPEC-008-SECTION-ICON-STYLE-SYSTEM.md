# SPEC-008: Section Icon Style System

**Status:** Approved design direction
**Date:** 2026-06-18
**Linear issue:** [AKA-190](https://linear.app/akachan/issue/AKA-190/define-custom-icon-style-system)
**Source audit:** [Emoji Section Icon Audit](../docs/emoji-section-icon-audit.md)

## Goal

Replace emoji section markers with a consistent Akachan-owned icon style that remains legible in the app's smallest placements, feels calm in 3am use, and preserves the domain color language already established by the Material 3 theme.

This spec defines the visual rules for future section icons. It does not require replacing every emoji in the same PR.

## Design Principles

| Principle | Rule |
|---|---|
| Calm over clever | Icons clarify sections; they do not add personality through decoration. Avoid cartoon detail, facial expressions, sparkle effects, and literal baby-brand motifs. |
| One-hand trust | Shapes must be recognizable during a quick glance, at arm's length, and with the device held in one hand. |
| Domain color consistency | Feeding uses primary pink, sleep uses secondary blue, pumping and inventory-adjacent success states use tertiary green or neutral containers where specified. |
| Small-size first | Each icon must be designed for 16dp and 20dp before decorative larger placements are considered. |
| Accessibility by structure | Meaning comes from labels and parent semantics first. Icons supplement meaning; they do not carry essential copy alone. |

## Icon Set

The replacement set uses simple rounded line icons with one optional filled anchor shape. Every icon is authored as a 24dp vector and exported as Android vector drawables.

| Section | Icon concept | Color role | Notes |
|---|---|---|---|
| Breastfeeding | Parent-and-baby feeding curve, abstracted to a soft cradle arc plus small drop | `primary` / `primaryContainer` | Do not reuse the bottle icon. Breastfeeding must be distinct from bottle feed. |
| Bottle feed | Bottle silhouette with rounded nipple and internal level line | `primary` / `primaryContainer` | Represents logged bottle feeds and active feed notification contexts. |
| Feeding history | Stacked list with small feeding dot marker | `primary` / `primaryContainer` | Use for combined feed history rather than a generic clipboard. |
| Pumping | Drop inside a simple pump flange circle | `tertiary` / `tertiaryContainer` | Keep it close to milk extraction, not a glass or cup. |
| Inventory / stash | Storage bag or rounded container with horizontal level mark | `surfaceVariant` plus `tertiary` accent | Avoid ice/freezer literalism as the primary metaphor. |
| Sleep | Crescent moon nested in a rounded sleep arc | `secondary` / `secondaryContainer` | Partner sleep aligns to this same icon. |
| Nap | Small moon plus short clock tick | `secondary` / `secondaryContainer` | Derived from sleep; used where nap differs from overnight sleep. |
| Wake time | Horizon line plus small rising dot | `secondary` / `secondaryContainer` | Supportive cue, not a primary section marker. |
| Diapers | Rounded diaper outline with two tabs | `surfaceVariant` plus contextual type accent | Section icon should not imply wet or dirty state. |
| Wet diaper | Single drop | `secondary` / `secondaryContainer` | Type icon for segmented controls and history rows. |
| Dirty diaper | Soft filled dot cluster in diaper outline | `tertiary` / `tertiaryContainer` | Keep abstract; no literal waste shape. |
| Mixed diaper | Drop plus dot cluster | `secondary` and `tertiary` accents only at 24dp+ | At 16dp, prefer a single combined outline to avoid visual noise. |
| Growth | Measuring mark with upward child-height tick | `tertiary` / `tertiaryContainer` | Standardizes the audit's `chart` vs `ruler` conflict around measurement. |
| Trends | Line chart with three rounded points | `tertiary` / `tertiaryContainer` | Data view, not achievement or performance. |
| Milestones | Small flag on rounded path | `primary` / `primaryContainer` | Avoid confetti and celebration bursts. |
| Tips | Small lightbulb with one quiet ray | `onSurfaceVariant` on `surface` | Contextual support, not a section color. |
| Partner sharing | Two person circles with link arc | `onSurfaceVariant` on `surface` | Existing Material people icon can remain until custom set is implemented. |
| Sleep/event cues | Small cue glyphs derived from base set | Per cue context | Keep current chip labels as the accessible text. |
| Warning / timer | Existing warning notification icons | Warning token set | Not part of section icon replacement. Keep warning tokens separate from M3 color scheme. |

## Grid And Geometry

### Authoring Grid

- Use a 24dp artboard for every source vector.
- Keep the primary silhouette inside a 20dp live area inset 2dp from each edge.
- Use optical alignment over mathematical centering when the icon has an obvious weight imbalance.
- Align key vertices to whole dp coordinates wherever possible.
- Use half-dp coordinates only for stroke centering, not for major silhouette edges.

### Stroke

- Default stroke: 2dp.
- Minimum stroke: 1.75dp only when a 2dp stroke closes a counter at 16dp.
- Stroke caps: round.
- Stroke joins: round.
- Avoid miter joins, sharp points, loose sketch strokes, and interior detail that disappears below 20dp.

### Fill

- The default section style is outline-first.
- One filled anchor shape is allowed per icon when it improves recognition at 16dp. Examples: a milk drop, chart point, or small moon fill.
- Filled anchors must be structural, not decorative.
- Do not use gradients, shadows, inner highlights, or texture inside icons.

### Detail Budget

| Render size | Maximum visible elements | Rule |
|---|---:|---|
| 16dp | 2 | Primary silhouette plus one anchor detail. |
| 18dp to 20dp | 3 | Add one internal mark only if it remains clear. |
| 24dp to 28dp | 4 | Standard app icon detail budget. |
| 32dp+ | 5 | Larger empty states may keep all source details, but never introduce a separate large-only illustration. |

## Placement Sizes

| Placement | Container | Icon size | Style |
|---|---:|---:|---|
| Inline chip label | Chip height 32dp min | 16dp | Monochrome, no container badge. |
| Diaper type segmented control | 48dp min touch target | 18dp | Monochrome or two-color mixed type at 24dp+ only. |
| Feature picker row | Row 56dp min | 24dp | Icon in 40dp tonal badge. |
| Home card | Card min 96dp to 120dp | 28dp | Icon in top-left without text baseline shift. |
| Tips row | Compact bordered row | 18dp | Monochrome on-surface-variant. |
| History card badge | 44dp square badge | 20dp | Icon centered in tonal container. |
| Partner compact badge | 40dp square badge | 20dp | Same as history badge, tighter container. |
| Partner active sleep badge | 52dp badge | 28dp | Same sleep icon, active color treatment. |
| Empty state | Unframed centered content | 32dp | Larger vector, not illustration scale. |
| Glance widget small | Widget-size dependent | 16dp to 20dp | Prefer monochrome icon if bitmap/vector support is reliable; otherwise keep text-free summary. |
| Glance widget medium/large | Widget-size dependent | 20dp to 24dp | Tonal icon allowed. |
| Notification prefix/title | System notification row | 18dp equivalent | Prefer notification small icon resource, not text emoji. |

All tappable placements still need a minimum 48dp touch target. Icon size does not define hit area.

## Color Behavior

### Light Theme

| State | Container | Icon | Text pairing |
|---|---|---|---|
| Default tonal | Domain container (`primaryContainer`, `secondaryContainer`, `tertiaryContainer`) | Matching on-container role | Parent label uses normal text color. |
| Default neutral | `surfaceVariant` or `surface` with outline | `onSurfaceVariant` | Used for inventory, tips, and partner sharing when not active. |
| Active | Domain solid role (`primary`, `secondary`, `tertiary`) | Matching on-role color | Pair with existing active status badge or live copy. |
| Selected | Same as active or selected chip colors | Matching on-role or on-container role | Selection must also be expressed by control state, not icon color alone. |
| Disabled | Existing container at 38% alpha or disabled M3 state layer | `onSurface` at 38% alpha | Preserve selected-but-disabled diaper readability by keeping label contrast above icon contrast. |
| Hover | Default container plus 8% `onSurface` state layer | Same as default | Applies to pointer environments only. Android touch surfaces do not need a hover visual. |
| Focus | Existing component focus indication | Same as current state | Use component focus ring/indicator; do not invent icon-only focus effects. |

### Dark Theme

| State | Container | Icon | Notes |
|---|---|---|---|
| Default tonal | Dark domain container roles | Matching on-container roles | Avoid increasing chroma beyond current dark scheme. |
| Default neutral | `surfaceVariant` or `surface` plus 1dp `outlineVariant` where the component already uses borders | `onSurfaceVariant` | Mirrors existing dark-mode `HistoryCard` border behavior. |
| Active | Dark domain solid roles | Matching on-role colors | Active cards keep existing elevation/color animation. |
| Disabled | Current dark container at disabled alpha | `onSurface` at 38% alpha | Do not drop below readable silhouette contrast in dense rows. |
| Hover | Default dark container plus 8% `onSurface` state layer | Same as default | Keep hover subtle; no glow. |

Color must never be the only difference between two semantic meanings. For example, wet and dirty diaper types need different glyph structures, not just blue vs green.

## State And Motion

- Icons do not animate independently for active sessions. Existing card color, elevation, timer, and status badge animations remain the state signal.
- Cue chips may keep their existing tap scale and checkmark behavior. The cue icon itself should not bounce separately.
- Selected feature rows use the switch and row state as the selection signal. The icon follows the row's enabled/disabled color.
- Loading states use the parent component skeleton or progress treatment. Do not spin section icons.
- Motion timing follows the current design system: 150ms to 250ms, ease-out, state-driven only.

## Accessibility

### Decorative vs Meaningful

| Context | Semantics rule |
|---|---|
| Home cards | Icon is decorative. Parent card owns the section-level `contentDescription`. |
| Feature picker rows | Icon is decorative. Row text and switch state carry meaning. |
| History card badges | Decorative unless the parent row lacks a richer description. Prefer parent row descriptions. |
| Diaper type segmented controls | Icon may be meaningful only when paired with visible text. Never expose icon-only labels for wet/dirty/both. |
| Empty states | Icon is decorative. Empty-state title and body carry meaning. |
| Widgets | Icon is decorative if adjacent text names the section. If icon-only due to space, provide widget-level content description. |
| Notifications | Use title/body text for meaning. Small icons are decorative/status identifiers. |

### Contrast

- Icon-to-container contrast must meet at least 3:1 for meaningful icons.
- Text-to-container contrast remains the stronger requirement: 4.5:1 for normal text, 3:1 for large text.
- Disabled icons may use disabled alpha, but disabled selected controls must keep labels readable.
- Do not rely on red/green or blue/green distinctions without shape differences.

### Labels

- Every section icon needs a stable localized label for semantics and tests, even when the icon itself is hidden from accessibility.
- Labels use the domain noun: "Breastfeeding", "Bottle feed", "Sleep", "Diapers", "Growth", "Trends", "Milestones".
- Avoid status copy inside icon descriptions. Status belongs to parent content such as "Sleep active" or "Pumping paused".

## Implementation Requirements For Follow-Up Work

1. Add icons as Android vector drawables or Compose `ImageVector` definitions with a shared naming scheme: `ic_section_<name>`.
2. Keep section-icon assets inside the app module. Do not add a new icon font, SVG runtime, or external icon package.
3. Introduce a small `SectionIcon` model only if it removes repeated resource/color mapping. Do not create mapper classes.
4. Replace emoji from shared components first: `HistoryCard`, home cards, feature picker, partner badges, then widgets and notifications.
5. Preserve current parent `contentDescription` behavior and `clearAndSetSemantics` intent where icons are decorative.
6. Add screenshot or Compose tests only where existing tests assert emoji text directly or where accessibility descriptions change.

## Out Of Scope

- Replacing standard action icons such as back, settings, add, edit, pause, play, stop, and share.
- Redesigning the Material 3 color scheme.
- Introducing animated illustrations or celebratory empty-state art.
- Replacing warning tokens or notification warning icons.
- Adding a multi-module icon package.

## Acceptance Checklist

- A future designer or engineer can draw a new section icon using the 24dp grid, 20dp live area, 2dp rounded stroke, and detail budget.
- Every audited section has a decided icon concept and color role.
- Default, active, selected, disabled, hover, focus, and dark-mode behavior are defined.
- The smallest placements, including 16dp chips and 20dp history badges, are explicitly supported.
- Accessibility behavior distinguishes decorative icons from meaningful icons.
