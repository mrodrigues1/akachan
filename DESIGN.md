---
name: Akachan
description: A warm, calm baby tracker for new parents who need clarity at 3am
colors:
  carnation-pink: "#C2185B"
  carnation-pink-container: "#F8BBD0"
  on-carnation-pink-container: "#880E4F"
  pale-carnation: "#F4C2C2"
  sleep-blue: "#1976D2"
  sleep-blue-container: "#B3E5FC"
  on-sleep-blue-container: "#0D47A1"
  pale-blue: "#89CFF0"
  threshold-green: "#388E3C"
  threshold-green-container: "#C8E6C9"
  on-threshold-green-container: "#1B5E20"
  warning-amber: "#E65100"
  warning-amber-container: "#FFE0B2"
  warm-cream: "#FFFDE7"
  cream-variant: "#F0EDE0"
  near-black: "#1A1A1A"
  muted-grey: "#757575"
  outline: "#79747E"
  outline-variant: "#CAC4D0"
  error: "#B00020"
  dark-surface: "#1C1B1F"
  dark-primary: "#F48FB1"
  dark-sleep-blue: "#90CAF9"
  dark-threshold-green: "#A5D6A7"
typography:
  display:
    fontFamily: "system-ui, sans-serif"
    fontSize: "36sp"
    fontWeight: 800
    lineHeight: "44sp"
    letterSpacing: "-1sp"
  headline:
    fontFamily: "system-ui, sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: "40sp"
    letterSpacing: "0sp"
  title:
    fontFamily: "system-ui, sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "0sp"
  body:
    fontFamily: "system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.5sp"
  label:
    fontFamily: "system-ui, sans-serif"
    fontSize: "12sp"
    fontWeight: 700
    lineHeight: "16sp"
    letterSpacing: "0.8sp"
rounded:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  lg: "24dp"
  pill: "50dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.carnation-pink}"
    textColor: "#FFFFFF"
    rounded: "{rounded.pill}"
    padding: "16dp 24dp"
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.sleep-blue}"
    rounded: "{rounded.pill}"
    padding: "14dp 24dp"
  side-selector-selected:
    backgroundColor: "{colors.carnation-pink}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
    height: "88dp"
  side-selector-unselected:
    backgroundColor: "{colors.warm-cream}"
    textColor: "{colors.near-black}"
    rounded: "{rounded.md}"
    height: "88dp"
  card-history:
    backgroundColor: "{colors.warm-cream}"
    rounded: "{rounded.md}"
    padding: "12dp 14dp"
  chip-allergy:
    backgroundColor: "{colors.carnation-pink-container}"
    textColor: "{colors.on-carnation-pink-container}"
    rounded: "{rounded.sm}"
    padding: "4dp 12dp"
---

# Design System: Akachan

## 1. Overview

**Creative North Star: "The Midnight Companion"**

Akachan is a tool for care, not performance. Its design language answers one question: what does a parent need when one arm is occupied and exhaustion has blurred the edges of thought? Everything else is secondary. The visual system is warm and legible first; it carries personality through restraint, not decoration.

The palette anchors on a soft cream surface (#FFFDE7) that reads like a familiar page, not a product interface. Carnation Pink identifies feeding. Sleep Blue identifies rest. These are deliberate semantic assignments, not aesthetic preferences. The system's warmth lives in the surface and the containment colors; the action colors are decisive, dark enough to read instantly against any background the eye lands on.

The system explicitly rejects the aesthetics it competes against: clinical white-and-teal apps that feel like patient intake forms, loud baby-brand UIs saturated with primary colors and bubble fonts, and fitness-tracker dashboards that treat care as a performance metric. Akachan does not count reps. It records moments.

**Key Characteristics:**
- Warm cream (#FFFDE7) as the single app surface — no alternating backgrounds
- Semantic color assignment: Pink = Feeding, Blue = Sleep, Green = over-limit threshold
- Generous touch targets (88dp interactive height for primary selectors; 48dp for buttons)
- System font (Roboto on Android) set across all weights; personality through weight contrast
- Elevation communicates state, not hierarchy; surfaces are flat at rest
- Dark mode is a first-class citizen, tuned for 3am low-light legibility

## 2. Colors: The Carnation Palette

Three hue families with fixed semantic roles, plus a warm cream surface that is non-negotiable.

### Primary
- **Carnation Pink** (#C2185B): The feeding role. Primary actions, active session ring arc, selected SideSelector tile fill. Never used decoratively.
- **Carnation Pink Container** (#F8BBD0): Feeding-domain container backgrounds, history badge fill for breastfeeding sessions, inactive SideSelector border tint.
- **Deep Carnation** (#880E4F): Text on primary containers; on-container text within the pink family.
- **Pale Carnation** (#F4C2C2): The softest pink tone. Used as badge backgrounds where the lightest indication is sufficient.

### Secondary
- **Sleep Blue** (#1976D2): The sleep role. Mirrors Carnation Pink in structure but is confined to the sleep tracking domain: active sleep session ring, sleep type selector fill, SleepTrackingScreen primary actions.
- **Sleep Blue Container** (#B3E5FC): Sleep-domain container backgrounds and history badge fills for sleep records.
- **Deep Sleep Blue** (#0D47A1): Text on sleep containers.

### Tertiary
- **Threshold Green** (#388E3C): Signals limit-exceeded moments. When a feeding session passes its max time limit, the ring arc transitions to this color. Paired with alarm and success states only; not a general affirmative color.
- **Threshold Green Container** (#C8E6C9): Background for threshold-state contexts.

### Extended (Non-M3 Warning Tokens)
- **Warning Amber** (#E65100): Feeding-limit notification accent. Not wired through `MaterialTheme.colorScheme`; accessed directly as `WarningAmber` from `Color.kt`. Dark scheme uses `#FFCC80`.
- **Warning Amber Container** (#FFE0B2): Background for warning notification states. Dark scheme uses `#7A4800`.
- **On Warning Amber Container** (#7A3600): Text on warning containers. Dark scheme uses `#FFE0B2`.

### Neutral
- **Warm Cream** (#FFFDE7): The app surface and background. A yellow-tinted white that reads as domestic and familiar. Never replaced with pure white.
- **Cream Variant** (#F0EDE0): Inactive containers, grouping zones. The slightly cooler companion to Warm Cream for unselected or secondary surfaces.
- **Near-Black** (#1A1A1A): Primary text on light surfaces. Warm-tinted dark, not pure black.
- **Muted Grey** (#757575): Secondary text, captions, on-surface-variant in light mode.
- **Outline** (#79747E): Dividers and inactive card borders.
- **Outline Variant** (#CAC4D0): Softer separators; dark-mode HistoryCard border stroke.

**The Semantic Assignment Rule.** Carnation Pink is the feeding color. Sleep Blue is the sleep color. They never appear as decorative interchangeables. If a screen handles both domains, the active domain's hue dominates; the other domain's color appears only in summarizing contexts.

**The No Pure White Rule.** The app surface is always Warm Cream (#FFFDE7). Pure white (#FFFFFF) appears only as text on filled Carnation Pink or Sleep Blue buttons; it is not a background value.

**The Direct Import Rule.** Warning tokens (WarningAmber, WarningContainerAmber, OnWarningContainerAmber and their dark variants) must be imported by name from `Color.kt`. Never access them through `MaterialTheme.colorScheme`, which does not carry them.

## 3. Typography

**Display Font:** System UI (resolves to Roboto on Android)
**Body Font:** System UI — same family across all weights and sizes
**Label Font:** System UI — distinguished by weight (700), size (12sp), and letter-spacing (0.8sp)

**Character:** The type system reads as functional, not fashionable. The system font was a deliberate choice: zero loading cost, full system hinting, familiar to Android users, legible at any size. Personality comes from weight contrast; the jump from ExtraBold 800 (timer) to Normal 400 (body) is the hierarchy doing the work.

### Hierarchy
- **Display** (ExtraBold 800, 36sp, -1sp tracking, 44sp line-height): The live timer readout inside TimerDisplay. Used nowhere else. At this size and weight, it reads from arm's length with one eye half-open.
- **Headline Large** (Bold 700, 32sp, 0 tracking): Screen-level titles. Appears once per screen, never inside a card.
- **Headline Medium** (Bold 700, 28sp): Major section headings within a screen.
- **Headline Small** (SemiBold 600, 24sp): Card-level titles and subsection headings.
- **Title Large** (SemiBold 600, 22sp): TopAppBar titles; the label a user reads to orient themselves on a new screen.
- **Title Medium** (SemiBold 600, 18sp, +0.15sp tracking): Primary list item text.
- **Title Small** (SemiBold 600, 14sp, +0.1sp tracking): HistoryCard primary line; compact interactive labels.
- **Body Large** (Normal 400, 16sp, +0.5sp tracking): Main body copy and description text.
- **Body Medium** (Normal 400, 14sp, +0.25sp tracking): Supporting information, settings row values.
- **Body Small** (Normal 400, 12sp, +0.4sp tracking): Captions; the HistoryCard subtitle line.
- **Label Large** (SemiBold 600, 14sp, +0.1sp tracking): Button labels; text on interactive controls.
- **Label Medium** (Bold 700, 12sp, +0.8sp tracking): Section headers in history lists, rendered UPPERCASE. The 0.8sp tracking is what makes 12sp caps legible.
- **Label Small** (Medium 500, 11sp, +0.5sp tracking): Chip and swatch labels; the smallest text the system renders.

**The ExtraBold Timer Rule.** `displaySmall` (36sp ExtraBold) is used exclusively for the live elapsed-time readout inside TimerDisplay. Assigning it to any other text element collapses the hierarchy at the moment a parent glances at an active session.

**The UPPERCASE Header Rule.** Day-group headers in history lists use `labelMedium` with `uppercase()` text transformation. This is the only place uppercase text appears in the system. Applying uppercase to body copy or titles undermines its function as a sectioning device.

## 4. Elevation

Elevation communicates state, not hierarchy. At rest, Akachan is nearly flat: surfaces separate visually through their tonal relationship (Warm Cream base, color-filled active states) rather than through stacked shadows.

Shadows appear in two specific conditions: a small ambient lift for cards at rest, and a jump to an active selection elevation when the user commits a choice.

### Shadow Vocabulary
- **Ambient card shadow** (1dp elevation): Applied to HistoryCard and passive content cards. Gives physical presence without visual drama. In dark mode, this shadow is invisible; replaced by a 1dp `outlineVariant` border stroke (#CAC4D0) applied explicitly when `LocalDarkTheme.current` is true.
- **Active selection shadow** (6dp elevation): Applied to selected SideSelector tiles. The step from 0dp to 6dp communicates that a choice has been made and committed.
- **Flat default**: Surfaces with no interactive state carry no shadow. The app background, settings rows, and section containers are entirely flat.

**The Flat-By-Default Rule.** Surfaces are flat at rest. Shadows appear only in response to user-initiated state change. A card that appears elevated before the user has touched it has confused decoration with state.

**The Dark Mode Swap Rule.** Ambient shadows (1dp) are not visible on dark surfaces. Swap them explicitly for an `outlineVariant` border stroke (1dp) in dark mode rather than increasing shadow opacity. High shadow values on dark backgrounds look garish, not deep.

## 5. Components

### Buttons
The pill shape (50dp radius, `AkachanShapes.extraLarge`) is the primary action affordance. Large, obvious, reachable with a thumb.
- **Primary:** Carnation Pink fill (#C2185B), white label (LabelLarge, 14sp SemiBold), full-width on tracking screens. Minimum 48dp tap target.
- **Outline:** Transparent fill, 2dp border matching context (Carnation Pink for feeding, Sleep Blue for sleep), matching text color. Used for "View History" and "Schedule" secondary actions.
- **Text:** No border or fill; text color only. Used for "Edit" actions within settings rows.
- **Disabled:** Reduced opacity on fill and label. No structural change.

### SideSelector (Signature Component)
The left/right breast chooser. Two equal-width cards in a row, each 88dp tall, separated by 12dp.
- **Unselected:** Warm Cream fill, 2dp Carnation Pink Container border (#F8BBD0), muted icon at 40% opacity, medium radius (16dp). Zero elevation.
- **Selected:** Carnation Pink fill (#C2185B), white icon and label, 6dp elevation shadow. The elevation jump is the state signal.
- **Icon:** 28dp Material arrow. Directional: left-pointing for LEFT breast, right-pointing for RIGHT.
- **Label:** LabelLarge (14sp SemiBold).
- **Sleep variant:** Same structure; replace all Carnation Pink references with Sleep Blue (#1976D2, #B3E5FC).

### TimerDisplay (Signature Component)
The live session clock. A 208dp circular Canvas ring wrapping a centered text readout. Pulses at 1.0→1.04→1.0 scale (2.5s, ease-in-out) while the session is running; stops when paused.
- **Track arc:** Full 360° ring, 16dp round-capped stroke. Pink Container (#F8BBD0) for feeding; Blue Container (#B3E5FC) for sleep.
- **Progress arc:** Fills clockwise from top. Carnation Pink (#C2185B) for feeding; Sleep Blue (#1976D2) for sleep. Transitions to Threshold Green (#388E3C) when elapsed time exceeds the configured maximum.
- **Center text:** Elapsed time in Display style (36sp ExtraBold, -1sp tracking); percentage-of-limit label below in LabelMedium (on-surface-variant tint).
- **No-ring variant:** When `maxDurationSeconds` is 0, renders only the display text; no Canvas, no ring, no pulse.

### HistoryCard
A row-layout list item for session history entries.
- **Shape:** Medium radius (16dp), 1dp ambient shadow, Warm Cream surface fill. In dark mode: no shadow, 1dp outlineVariant border (#CAC4D0).
- **Badge:** 44dp × 44dp box with small radius (8dp). Fill is the domain's container color (Carnation Pink Container for feeding, Sleep Blue Container for sleep). Contains an emoji at 20sp.
- **Row layout:** Badge, 12dp gap, text column (title + subtitle), 8dp gap, trailing duration.
- **Title:** TitleSmall (14sp SemiBold), near-black (#1A1A1A).
- **Subtitle:** BodySmall (12sp Normal), muted grey (#757575).
- **Trailing:** BodyMedium (14sp Normal), Carnation Pink (#C2185B).
- **Outer padding:** 14dp horizontal, 12dp vertical. 4dp vertical margin between cards.

### Chips
Allergy tags in the Settings Baby Profile section.
- **Style:** Carnation Pink Container fill (#F8BBD0), Deep Carnation text (#880E4F), small radius (8dp), 4dp × 12dp padding.
- **Non-interactive:** Display-only in Settings; not used for filtering.

### Navigation Bar
Bottom NavigationBar spanning the full screen width.
- **Active item:** Carnation Pink indicator pill behind icon; icon and label at full opacity.
- **Inactive item:** Muted grey (#757575) icon and label at reduced opacity.
- **Surface:** Warm Cream background; no elevated container, no shadow. The bar sits flush on the same surface as the screen body.

## 6. Do's and Don'ts

### Do:
- **Do** use Warm Cream (#FFFDE7) for all app backgrounds. It is the identity of the surface; pure white removes the warmth that distinguishes Akachan from a clinical or generic interface.
- **Do** assign color by domain: Carnation Pink for feeding screens and feeding states, Sleep Blue for sleep screens and sleep states. Keep the semantic pairing intact at every component level.
- **Do** use the pill shape (50dp radius) for primary action buttons and the medium radius (16dp) for cards, selector tiles, and interactive containers.
- **Do** give all primary interactive elements a minimum 48dp tap height. SideSelector-style toggle tiles should be 88dp. Parents use this one-handed.
- **Do** replace ambient card shadows with `outlineVariant` border strokes (1dp, #CAC4D0) in dark mode. Swap at the component level using `LocalDarkTheme.current`.
- **Do** use LabelMedium (12sp Bold, 0.8sp tracking) with uppercase text for day-group headers in history lists. This is the only place UPPERCASE appears.
- **Do** communicate an active session visually at the screen level: a banner, a status pill, or a TimerDisplay must indicate "something is running" at a glance without reading.
- **Do** access Warning tokens (WarningAmber, WarningContainerAmber, OnWarningContainerAmber) by importing them directly from `Color.kt`. Do not route them through `MaterialTheme.colorScheme`.
- **Do** treat dark mode as a first-class requirement. Every new component needs explicit dark-mode color assignments, not inherited defaults.

### Don't:
- **Don't** build clinical white or sterile layouts. Akachan is a family tool, not a hospital intake form. If the surface is pure white and the palette is neutral, the design has missed the product.
- **Don't** use oversaturated cartoon palettes, bubble fonts, or primary-color noise. The parents using this app are adults already overwhelmed by baby-product branding; don't replicate it.
- **Don't** treat tracking as a performance metric. No concentric stat rings, no "you hit your goal" dashboard patterns, no SaaS-style hero metrics. Akachan tracks care, not performance.
- **Don't** use gradient text or `background-clip: text` decorative effects. All text is a single solid color.
- **Don't** use side-stripe borders (a colored `border-left` or `border-right` wider than 1dp as an accent on cards or list items). Rewrite with a full border, a tinted background, or a leading badge.
- **Don't** reach for a modal dialog as the first editing solution. Settings uses bottom sheets that reuse onboarding step composables; keep that pattern.
- **Don't** introduce shadows deeper than 6dp or multi-layer shadow compositions. The system's elevation vocabulary has two active steps (1dp ambient, 6dp selected); a third step breaks the grammar.
- **Don't** use Carnation Pink and Sleep Blue interchangeably or on the same surface as stylistic choices. They carry semantic meaning that users learn by repetition; mixing them erodes trust.
- **Don't** use `FontFamily.Default` at `displaySmall` (36sp ExtraBold) for anything other than the live timer readout. Its role is established; reusing it collapses the hierarchy.
