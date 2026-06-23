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
  muted-grey: "#6D6A64"
  outline: "#79747E"
  outline-variant: "#CAC4D0"
  error: "#B00020"
  error-container: "#FFDAD6"
  on-error-container: "#410002"
  dark-surface: "#1C1B1F"
  dark-primary: "#F48FB1"
  dark-sleep-blue: "#90CAF9"
  dark-threshold-green: "#A5D6A7"
  milestone-purple: "#7B1FA2"
  milestone-purple-container: "#E1BEE7"
  on-milestone-purple-container: "#4A148C"
  growth-teal: "#00796B"
  growth-teal-container: "#B2DFDB"
  on-growth-teal-container: "#004D40"
  diaper-yellow: "#F9A825"
  diaper-yellow-container: "#FFE9A8"
  on-diaper-yellow-container: "#5F4B00"
  vaccine-indigo: "#303F9F"
  vaccine-indigo-container: "#C5CAE9"
  on-vaccine-indigo-container: "#1A237E"
  doctor-slate: "#455A64"
  doctor-slate-container: "#CFD8DC"
  on-doctor-slate-container: "#263238"
  bottle-feed-red: "#D32F2F"
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
  card-summary-idle:
    backgroundColor: "{colors.carnation-pink-container}"
    rounded: "{rounded.lg}"
    padding: "20dp"
  card-summary-active:
    backgroundColor: "{colors.carnation-pink}"
    textColor: "#FFFFFF"
    rounded: "{rounded.lg}"
    padding: "20dp"
  sleep-prediction-card:
    backgroundColor: "{colors.sleep-blue-container}"
    textColor: "{colors.on-sleep-blue-container}"
    rounded: "{rounded.lg}"
    padding: "16dp"
  cue-chip:
    backgroundColor: "transparent"
    textColor: "{colors.near-black}"
    rounded: "{rounded.pill}"
    padding: "4dp 12dp"
  cue-chip-selected:
    backgroundColor: "{colors.carnation-pink-container}"
    textColor: "{colors.on-carnation-pink-container}"
    rounded: "{rounded.pill}"
    padding: "4dp 12dp"
  domain-hero-card:
    backgroundColor: "{colors.vaccine-indigo}"
    textColor: "#FFFFFF"
    rounded: "{rounded.lg}"
    padding: "20dp"
  domain-hero-card-overdue:
    backgroundColor: "{colors.warning-amber-container}"
    textColor: "{colors.warning-amber}"
    rounded: "{rounded.lg}"
    padding: "20dp"
  persistent-bottom-bar:
    backgroundColor: "{colors.vaccine-indigo}"
    textColor: "#FFFFFF"
    rounded: "{rounded.pill}"
    height: "52dp"
---

# Design System: Akachan

## 1. Overview

**Creative North Star: "The Midnight Companion"**

Akachan is a tool for care, not performance. Its design language answers one question: what does a parent need when one arm is occupied and exhaustion has blurred the edges of thought? Everything else is secondary. The visual system is warm and legible first; it carries personality through restraint, not decoration.

The palette anchors on a soft cream surface (#FFFDE7) that reads like a familiar page, not a product interface. Carnation Pink identifies feeding. Sleep Blue identifies rest. These are deliberate semantic assignments, not aesthetic preferences. The system's warmth lives in the surface and the containment colors; the action colors are decisive, dark enough to read instantly against any background the eye lands on.

As the app has grown to cover Vaccines, Doctor Visits, Diapers, Milestones, and Growth, a second tier of extended palette families has been introduced — one distinct hue per domain, each isolated from the M3 color scheme and accessed only through a scheme-resolved helper function. These domain colors are semantic instruments, not decoration: Indigo is vaccine records, Slate is doctor visits, Yellow is diaper changes, Purple is developmental milestones, Teal is growth measurements.

The system explicitly rejects the aesthetics it competes against: clinical white-and-teal apps that feel like patient intake forms, loud baby-brand UIs saturated with primary colors and bubble fonts, and fitness-tracker dashboards that treat care as a performance metric. Akachan does not count reps. It records moments.

**Key Characteristics:**
- Warm cream (#FFFDE7) as the single app surface — no alternating backgrounds
- Semantic color assignment: Pink = Feeding, Blue = Sleep, Green = over-limit threshold and Pumping; one distinct accent per every other domain (see Extended Domain Palettes)
- Generous touch targets (88dp interactive height for primary selectors; 48dp for buttons)
- System font (Roboto on Android) set across all weights; personality through weight contrast
- Elevation communicates state, not hierarchy; surfaces are flat at rest
- Dark mode is a first-class citizen, tuned for 3am low-light legibility
- Home screen uses a 2×2 summary card grid where active sessions animate color and elevation in real time
- Feature domains with dedicated dashboards (Vaccine, Doctor Visit) use a Domain Hero Card pattern: a filled-accent card owning the most urgent item, a persistent pill CTA bar pinned at the bottom

## 2. Colors: The Carnation Palette

Three M3-wired hue families plus a warm cream surface and five extended domain palettes, all with fixed semantic roles.

### Primary
- **Carnation Pink** (#C2185B): The feeding role. Primary actions, active session ring arc, selected SideSelector tile fill, active breastfeeding home card background. Never used decoratively.
- **Carnation Pink Container** (#F8BBD0): Feeding-domain container backgrounds, history badge fill for breastfeeding sessions, inactive SideSelector border tint, idle breastfeeding home card background.
- **Deep Carnation** (#880E4F): Text on primary containers; on-container text within the pink family.
- **Pale Carnation** (#F4C2C2): The softest pink tone. Used as badge backgrounds where the lightest indication is sufficient.

### Secondary
- **Sleep Blue** (#1976D2): The sleep role. Mirrors Carnation Pink in structure but is confined to the sleep tracking domain: active sleep session ring, sleep type selector fill, SleepTrackingScreen primary actions, active sleep home card background.
- **Sleep Blue Container** (#B3E5FC): Sleep-domain container backgrounds, history badge fills for sleep records, idle sleep home card background, SleepPredictionCard background.
- **Deep Sleep Blue** (#0D47A1): Text on sleep containers.

### Tertiary
- **Threshold Green** (#388E3C): Signals limit-exceeded moments and the Pumping domain. When a feeding session passes its max time limit, the ring arc transitions to this color. Also used as the active pumping home card color. Not a general affirmative color.
- **Threshold Green Container** (#C8E6C9): Background for threshold-state contexts and idle pumping home card.

### Extended (Non-M3 Warning Tokens)
- **Warning Amber** (#E65100): Feeding-limit notification accent and the OverdueHero vaccine card background (via WarningContainerAmber, #FFE0B2). Not wired through `MaterialTheme.colorScheme`; accessed directly as `WarningAmber` from `Color.kt`. Dark scheme uses `#FFCC80`.
- **Warning Amber Container** (#FFE0B2): Background for warning notification states and overdue vaccine hero. Dark scheme uses `#7A4800`.
- **On Warning Amber Container** (#7A3600): Text on warning containers. Dark scheme uses `#FFE0B2`.

### Extended Domain Palettes

Each feature domain beyond the M3 core gets one hue family: a primary accent, a container, an on-container text, and an on-accent text. Both light and dark values are defined. Access exclusively through the scheme-resolved helper function, never by importing individual `*Dark` token variants at call sites.

- **Milestone Purple** (`milestoneColors()`) — accent: #7B1FA2, container: #E1BEE7, on-container: #4A148C, on-accent: white (light) / dark purple (dark). Developmental milestone tracking. The only purple in the system.
- **Growth Teal** (`growthColors()`) — accent: #00796B, container: #B2DFDB, on-container: #004D40, on-accent: white (light) / dark teal (dark). Growth measurements and WHO charts. Distinct from Threshold Green by saturation and hue family.
- **Diaper Yellow** (`diaperColors()`) — accent: #F9A825, container: #FFE9A8, on-container: #5F4B00, on-accent: dark in both schemes (yellow is intrinsically light; white never reaches AA contrast on it).
- **Vaccine Indigo** (`vaccineColors()`) — accent: #303F9F, container: #C5CAE9, on-container: #1A237E, on-accent: white (light) / dark indigo (dark). Vaccine records and immunization schedule.
- **Doctor Visit Slate** (`doctorVisitColors()`) — accent: #455A64, container: #CFD8DC, on-container: #263238, on-accent: white (light) / dark slate (dark). Doctor visits, appointment countdown, question inbox.
- **Bottle-Feed Red** (#D32F2F / dark: #FF8A80): Rhythm-strip distinction token. Breast-feed dots render in M3 primary (Carnation Pink); bottle-feed dots use this redder tone so the two event kinds separate at ~5px dot size. Used only in the rhythm strip visualization, not in any card, button, or label.

**The Semantic Assignment Rule.** Carnation Pink is the feeding color. Sleep Blue is the sleep color. Threshold Green is the over-limit and pumping color. Domain palette colors are further isolated per section. They never appear as decorative interchangeables. If a screen handles both domains, the active domain's hue dominates; the other domain's color appears only in summarizing contexts.

**The No Pure White Rule.** The app surface is always Warm Cream (#FFFDE7). Pure white (#FFFFFF) appears only as text on filled dark-accent buttons (Carnation Pink, Sleep Blue, Vaccine Indigo, Doctor Slate, Growth Teal); it is not a background value.

**The Direct Import Rule.** Warning tokens (WarningAmber, WarningContainerAmber, OnWarningContainerAmber and their dark variants) must be imported by name from `Color.kt`. Never access them through `MaterialTheme.colorScheme`, which does not carry them.

**The Domain Palette Helper Rule.** Extended domain colors (Milestone Purple, Growth Teal, Diaper Yellow, Vaccine Indigo, Doctor Visit Slate) must be accessed through their scheme-resolved helper function (`milestoneColors()`, `growthColors()`, `diaperColors()`, `vaccineColors()`, `doctorVisitColors()`). Never import `*Dark` token variants directly at call sites. The helper is the single branching point for light/dark; duplicating the conditional elsewhere produces divergence.

### Neutral
- **Warm Cream** (#FFFDE7): The app surface and background. A yellow-tinted white that reads as domestic and familiar. Never replaced with pure white.
- **Cream Variant** (#F0EDE0): Inactive containers, grouping zones. The slightly cooler companion to Warm Cream for unselected or secondary surfaces. Also the Inventory home card background.
- **Near-Black** (#1A1A1A): Primary text on light surfaces. Warm-tinted dark, not pure black.
- **Muted Grey** (#6D6A64): Secondary text, captions, on-surface-variant in light mode. Tuned to keep AA contrast on warm cream surfaces.
- **Outline** (#79747E): Dividers and inactive card borders.
- **Outline Variant** (#CAC4D0): Softer separators; dark-mode HistoryCard border stroke; Tip card border.

## 3. Typography

**Display Font:** System UI (resolves to Roboto on Android)
**Body Font:** System UI — same family across all weights and sizes
**Label Font:** System UI — distinguished by weight (700), size (12sp), and letter-spacing (0.8sp)

**Character:** The type system reads as functional, not fashionable. The system font was a deliberate choice: zero loading cost, full system hinting, familiar to Android users, legible at any size. Personality comes from weight contrast; the jump from ExtraBold 800 (timer) to Normal 400 (body) is the hierarchy doing the work.

### Hierarchy
- **Display** (ExtraBold 800, 36sp, -1sp tracking, 44sp line-height): The live timer readout inside TimerDisplay. Used nowhere else. At this size and weight, it reads from arm's length with one eye half-open.
- **Headline Large** (Bold 700, 32sp, 0 tracking): Screen-level titles. Appears once per screen, never inside a card. Also used for the primary countdown value inside Domain Hero Cards and SleepPredictionCard.
- **Headline Medium** (Bold 700, 28sp): Major section headings within a screen. Used as the emoji icon scale in home summary cards.
- **Headline Small** (SemiBold 600, 24sp): Card-level titles and subsection headings.
- **Title Large** (SemiBold 600, 22sp): TopAppBar titles; the label a user reads to orient themselves on a new screen. Also used for overdue-days text in the OverdueHero variant — deliberately smaller than `headlineLarge` so the number informs without shouting at an anxious parent.
- **Title Medium** (SemiBold 600, 18sp, +0.15sp tracking): Primary list item text. Also used for active session elapsed time on home summary cards.
- **Title Small** (SemiBold 600, 14sp, +0.1sp tracking): HistoryCard primary line; compact interactive labels; vaccine and doctor visit row primary names.
- **Body Large** (Normal 400, 16sp, +0.5sp tracking): Main body copy and description text. Also used for dose/date primary lines in Domain Hero Cards.
- **Body Medium** (Normal 400, 14sp, +0.25sp tracking): Supporting information, settings row values, SleepPredictionCard window ranges, NeedMoreData hints, secondary date lines in hero cards.
- **Body Small** (Normal 400, 12sp, +0.4sp tracking): Captions; the HistoryCard subtitle line; vaccine and visit row date subtitles.
- **Label Large** (SemiBold 600, 14sp, +0.1sp tracking): Button labels; text on interactive controls.
- **Label Medium** (Bold 700, 12sp, +0.8sp tracking): Section headers in history lists (UPPERCASE). Also used for the "SLEEP PREDICTION" / "NEXT UP" / "NEXT VISIT" header labels inside cards. The 0.8sp tracking is what makes 12sp caps legible.
- **Label Small** (Medium 500, 11sp, +0.5sp tracking): Chip and swatch labels; the smallest text the system renders. Used for "Live"/"Paused" text in ActiveStatusBadge and the progress count in NeedMoreData.

**The ExtraBold Timer Rule.** `displaySmall` (36sp ExtraBold) is used exclusively for the live elapsed-time readout inside TimerDisplay. Assigning it to any other text element collapses the hierarchy at the moment a parent glances at an active session.

**The UPPERCASE Header Rule.** Day-group headers in history lists and section labels inside cards (SleepPredictionCard, Domain Hero Cards) use `labelMedium` with uppercase text. This is the only context where uppercase text appears in the system. Applying uppercase to body copy or titles undermines its function as a sectioning device.

## 4. Elevation

Elevation communicates state, not hierarchy. At rest, Akachan is nearly flat: surfaces separate visually through their tonal relationship (Warm Cream base, color-filled active states) rather than through stacked shadows.

Shadows appear in two specific conditions: a small ambient lift for cards at rest, and a jump to an active selection elevation when the user commits a choice. This same 1dp→6dp animated step is the system's universal active-state signal, used on SideSelector tiles and on every home summary card. Domain Hero Cards and feature-dashboard cards use 0dp elevation — they communicate importance through filled color, not shadow depth.

### Shadow Vocabulary
- **Ambient card shadow** (1dp elevation): Applied to HistoryCard, home summary cards at rest, and passive content cards. Gives physical presence without visual drama. In dark mode, this shadow is invisible; replaced by a 1dp `outlineVariant` border stroke (#CAC4D0) applied explicitly when `LocalDarkTheme.current` is true.
- **Active selection shadow** (6dp elevation): Applied to selected SideSelector tiles and home summary cards with an active session. The step from 1dp to 6dp communicates that a state has changed and is running. The transition is animated with `tween(240ms, EaseOutQuart)`.
- **Flat default**: Surfaces with no interactive state carry no shadow. Domain Hero Cards, feature dashboard cards, SleepPredictionCard, settings rows, section containers, and the app background are entirely flat (0 elevation). They communicate status through color fill, not depth.

**The Flat-By-Default Rule.** Surfaces are flat at rest. Shadows appear only in response to user-initiated state change. A card that appears elevated before the user has touched it has confused decoration with state.

**The Dark Mode Swap Rule.** Ambient shadows (1dp) are not visible on dark surfaces. Swap them explicitly for an `outlineVariant` border stroke (1dp) in dark mode rather than increasing shadow opacity. High shadow values on dark backgrounds look garish, not deep.

**The Two-Step Vocabulary Rule.** The system has exactly two active elevation steps: 1dp ambient and 6dp selected. A third step breaks the grammar. Never introduce a mid-elevation value (e.g., 3dp or 4dp) as a distinct state.

## 5. Components

### Buttons
The pill shape (50dp radius, `AkachanShapes.extraLarge`) is the primary action affordance. Large, obvious, reachable with a thumb.
- **Primary:** Carnation Pink fill (#C2185B), white label (LabelLarge, 14sp SemiBold), full-width on tracking screens. Minimum 48dp tap target.
- **Domain Primary:** Same pill shape and min-height, but uses the feature domain's `colors.accent` + `colors.onAccent` from the scheme-resolved helper. The Persistent Bottom Bar uses this variant for Vaccine and Doctor Visit dashboards.
- **Outline:** Transparent fill, 2dp border matching context (Carnation Pink for feeding, Sleep Blue for sleep), matching text color. Used for "View History" and secondary actions. On Domain Hero Cards, the outlined pill button inherits `contentColor` from the hero background so it remains legible on any domain accent.
- **Text:** No border or fill; text color only. Used for "Edit" actions within settings rows and "View All" links.
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
- **Subtitle:** BodySmall (12sp Normal), muted grey (#6D6A64).
- **Trailing:** BodyMedium (14sp Normal), Carnation Pink (#C2185B).
- **Outer padding:** 14dp horizontal, 12dp vertical. 4dp vertical margin between cards.

### Home Summary Cards (Signature Component)
The 2×2 grid on HomeScreen. Each card navigates to its domain screen and reflects live session state.
- **Shape:** Large radius (24dp), minimum height 120dp–140dp depending on content.
- **Idle state:** Domain container color fill, 1dp ambient shadow. Breastfeeding = primaryContainer, Sleep = secondaryContainer, Pumping = tertiaryContainer, Inventory = surfaceVariant.
- **Active state:** Full role color fill, 6dp shadow. Breastfeeding = primary, Sleep = secondary, Pumping = tertiary. Color and elevation animate with `tween(220–240ms, EaseOutQuart)` to signal a running session without a secondary indicator.
- **Content:** 20dp internal padding. Emoji icon at `headlineMedium` scale (top-left), `titleMedium` domain name, and context text below (elapsed time, last session time, or prediction subtitle). `animateContentSize` on the column avoids layout jumps when content changes.
- **ActiveStatusBadge:** A pill surface (extraLarge radius) shown top-right when a session is active. Contains a pulsing dot (scale 0.82→1.18 at 900ms, EaseOutQuart) and a "Live"/"Paused" label in `labelSmall`. Enters/exits with `fadeIn + scaleIn(0.82)` / `fadeOut + scaleOut(0.82)` at 120–180ms.

### Domain Hero Card (Signature Component)
A large filled card occupying the top of dedicated feature dashboards (Vaccine, Doctor Visit). Communicates urgency hierarchy through color fill; elevation is always 0.
- **Shape:** Large radius (24dp), 0 elevation. Importance comes from color fill, not shadow.
- **Normal variant:** Domain accent fill (e.g., Vaccine Indigo #303F9F), `onAccent` text (white in light mode; scheme-appropriate dark in dark mode where the accent inverts to a light tone). Contains: UPPERCASE `labelMedium` section label (e.g., "NEXT UP" / "NEXT VISIT") + trailing edit icon, `headlineLarge` countdown ("Tomorrow" / "In 5 days"), `bodyLarge` record name, `bodyMedium` date, full-width outlined pill button ("Mark as given").
- **Overdue variant:** WarningAmber container fill (#FFE0B2), `OnWarningContainerAmber` text (#7A3600). Uses `titleLarge` (not `headlineLarge`) for the overdue day count — deliberately smaller so the number informs without shouting at an anxious parent. Followed by reassurance copy in `bodyMedium`. In dark mode, swaps to `WarningContainerAmberDark` and `OnWarningContainerAmberDark`.
- **Quiet variant (CaughtUp / NoUpcomingVisit):** `OutlinedCard` with no fill, large radius, 20dp padding. Icon (28dp, `onSurfaceVariant` tint) + `titleMedium` title + `bodyMedium` subtitle. Used when there is nothing urgent to surface; the Persistent Bottom Bar remains the only CTA.
- **Group variant (NextUpHeroGroup):** Multiple records share one countdown header; each record gets its own row at 48dp min-height with name + check icon button + edit icon button.
- **Section labels within hero cards:** Always `labelMedium` uppercase via a shared `SectionLabel` composable. The same pattern reuses on history screens for "UPCOMING" / "RECENTLY GIVEN" / "TO SCHEDULE" / "RECENT" section dividers.

### SleepPredictionCard (Signature Component)
Appears on HomeScreen below the summary grid. Surfaces the ML-driven next-sleep estimate.
- **Shape:** Large radius (24dp), flat (no shadow). `secondaryContainer` background.
- **Header row:** 16dp Bedtime icon + "SLEEP PREDICTION" label in `labelMedium` (secondary tint, uppercase). ConfidenceDots aligned trailing.
- **ConfidenceDots:** Three 8dp filled circles spaced 4dp. Filled = secondary color; unfilled = secondary at 22% alpha. Represents LOW (1), MEDIUM (2), HIGH (3) confidence. Merged into a single accessibility node with a descriptive label.
- **State variants:** Window / NeedMoreData / Overdue / CueLed / CurrentlySleeping / AfterActiveFeed.
- **Hidden when Unavailable:** The card renders nothing and takes no space; callers are not responsible for gating it.

### CueQuickTapRow (Signature Component)
A horizontally scrollable row of six `FilterChip`s for one-tap baby event logging.
- **Position:** Below SleepPredictionCard on SleepTrackingScreen only — not present on HomeScreen.
- **Chips:** Emoji + label in `labelMedium`. Event types: Sleepy, Hungry, Fussy, Sick, Teething, Travel. Spaced 8dp apart.
- **Tap sequence:** Scale 0.80 → 1.25 → 0.94 → 1.0 across four keyframes (65ms, 190ms, 110ms, 130ms). Communicates confirmation without a separate success state.
- **Selection state:** Chip shows a check icon (16dp, animated scale + alpha) and a filter-chip selected background. Auto-deselects after 1.5s. Multiple chips can be independently selected.
- **Haptic:** `LongPress` haptic on every tap.
- **Style note:** FilterChips pick up the M3 color scheme; no custom color overrides.

### StatusDot (Vaccine Component)
A 10dp atomic indicator used in vaccine schedule rows to communicate record lifecycle state. Three variants cover the full lifecycle.
- **Filled** (`StatusDot(filled = true)`): Solid domain-accent color. Administered / confirmed records.
- **Hollow** (`StatusDot(filled = false)`): Domain-accent at 18% alpha. Upcoming scheduled records.
- **Ring** (`StatusRingDot`): 1.5dp border ring, no fill. To-schedule records (exist as records but have no confirmed date yet). Visually distinct from both hollow and filled; the ring conveys "needs action" without urgency color.

### ToScheduleRow (Vaccine Component)
A container-tinted row for vaccines that exist as records but have no scheduled date yet.
- **Shape:** 12dp radius. Intentionally asymmetric to the standard `HistoryCard` (16dp) to signal a different lifecycle state; both are less than the hero card (24dp).
- **Background:** Domain container color (e.g., Vaccine Indigo Container #C5CAE9). Text in `onContainer` color. The tinted background makes the "needs scheduling" state read at a glance without any badge or icon.
- **Content:** Ring dot + name (TitleSmall) + subtitle (BodySmall) + "Schedule" filled-accent mini-button + check icon + delete icon. The "Schedule" button uses a filled domain-accent color so it reads as the primary prompt against the container-tinted row.

### Persistent Bottom Bar
A full-width action bar pinned to the bottom of feature dashboards (Vaccine, Doctor Visit). Separates the primary add action from the scrollable content without competing with it.
- **Structure:** `Surface` (surface color background, respects `navigationBarsPadding`) + `HorizontalDivider` (outlineVariant, hairline 1dp) + full-width pill button (52dp min-height, domain accent + onAccent).
- **Button:** `MaterialTheme.shapes.extraLarge` (50dp), `Icon` (20dp) + `Spacer(8dp)` + `Text(labelLarge)`.
- **Purpose:** Keeps the single CTA always reachable without scrolling, regardless of list length. The hairline divider anchors the bar as a distinct layer without elevation.
- **Dark mode:** Surface background naturally picks up `SurfaceDark` (#1C1B1F); no override needed.

### Dashboard Skeleton
A static placeholder rendered between screen mount and the first data emission from Room. No shimmer — motion is reserved for state changes, not loading.
- **Blocks:** `surfaceVariant` color at 50% alpha, clipped to the shape matching the content they stand in for (large radius for hero placeholder, small radius for row placeholders).
- **Hero placeholder:** Full-width, 132–150dp tall, large radius — footprint matches the domain hero card.
- **Row placeholders:** Repeat 3 times; first block at 40% width (simulates a short section header), remainder full-width at 20dp height.
- **Accessibility:** The entire skeleton column carries `contentDescription = stringResource(R.string.loading)` and `liveRegion = LiveRegionMode.Polite` so TalkBack announces the loading state on transition rather than silently showing a blank screen.

### Chips
Allergy tags in the Settings Baby Profile section.
- **Style:** Carnation Pink Container fill (#F8BBD0), Deep Carnation text (#880E4F), small radius (8dp), 4dp × 12dp padding.
- **Non-interactive:** Display-only in Settings; not used for filtering.

### Tip Card
Informational inline card on HomeScreen suggesting the next breast to try.
- **Shape:** Large radius (24dp). Surface color fill with a 1dp outlineVariant border (#CAC4D0). No elevation shadow.
- **The Outline-Border Pattern.** When a card is informational (not actionable, not domain-colored), use a 1dp outlineVariant border on the surface color instead of a container fill. This distinguishes informational content from interactive domain cards without adding visual weight.

### Navigation Bar
Bottom NavigationBar spanning the full screen width.
- **Active item:** Carnation Pink indicator pill behind icon; icon and label at full opacity.
- **Inactive item:** Muted grey (#6D6A64) icon and label at reduced opacity.
- **Surface:** Warm Cream background; no elevated container, no shadow. The bar sits flush on the same surface as the screen body.

## 6. Do's and Don'ts

### Do:
- **Do** use Warm Cream (#FFFDE7) for all app backgrounds. It is the identity of the surface; pure white removes the warmth that distinguishes Akachan from a clinical or generic interface.
- **Do** assign color by domain: Carnation Pink for feeding screens and feeding states, Sleep Blue for sleep screens and sleep states, Threshold Green for pumping and limit-exceeded states. For all other domains, use the scheme-resolved helper function (`vaccineColors()`, `diaperColors()`, etc.) and keep the assignment isolated to that feature's screens and components.
- **Do** access extended domain palettes (Milestone Purple, Growth Teal, Diaper Yellow, Vaccine Indigo, Doctor Visit Slate) exclusively through their scheme-resolved helper function. Never import `*Dark` token variants directly at call sites.
- **Do** use the pill shape (50dp radius) for primary action buttons and the large radius (24dp) for home summary cards and domain hero cards; medium radius (16dp) for history cards and selector tiles.
- **Do** give all primary interactive elements a minimum 48dp tap height. SideSelector-style toggle tiles should be 88dp. Parents use this one-handed.
- **Do** replace ambient card shadows with `outlineVariant` border strokes (1dp, #CAC4D0) in dark mode. Swap at the component level using `LocalDarkTheme.current`.
- **Do** use LabelMedium (12sp Bold, 0.8sp tracking) with uppercase text for day-group headers in history lists and for section labels inside cards. This is the only place UPPERCASE appears.
- **Do** communicate an active session visually at the screen level: a banner, a status pill (ActiveStatusBadge), or a TimerDisplay must indicate "something is running" at a glance without reading.
- **Do** access Warning tokens (WarningAmber, WarningContainerAmber, OnWarningContainerAmber) by importing them directly from `Color.kt`. Do not route them through `MaterialTheme.colorScheme`.
- **Do** treat dark mode as a first-class requirement. Every new component needs explicit dark-mode color assignments, not inherited defaults.
- **Do** use `tween(200–240ms, EaseOutQuart / CubicBezierEasing(0.25, 1, 0.5, 1))` for all state-driven color and elevation transitions. Exponential ease-out is the system's motion signature.
- **Do** use 0 elevation (flat) for Domain Hero Cards. Their urgency hierarchy comes from color fill, not depth.
- **Do** pin a Persistent Bottom Bar (hairline divider + full-width pill button) as the primary CTA on any dedicated feature dashboard. The CTA must not scroll off screen.
- **Do** follow the OverdueHero copy pattern: use `titleLarge` (not `headlineLarge`) for overdue day counts, and always follow with reassurance copy in `bodyMedium`. The anxious moment is not the moment for the biggest text on screen.

### Don't:
- **Don't** build clinical white or sterile layouts. Akachan is a family tool, not a hospital intake form. If the surface is pure white and the palette is neutral, the design has missed the product.
- **Don't** use oversaturated cartoon palettes, bubble fonts, or primary-color noise. The parents using this app are adults already overwhelmed by baby-product branding; don't replicate it.
- **Don't** treat tracking as a performance metric. No concentric stat rings, no "you hit your goal" dashboard patterns, no SaaS-style hero metrics. Akachan tracks care, not performance.
- **Don't** use gradient text or `background-clip: text` decorative effects. All text is a single solid color.
- **Don't** use side-stripe borders (a colored `border-left` or `border-right` wider than 1dp as an accent on cards or list items). Rewrite with a full border, a tinted background, or a leading badge.
- **Don't** reach for a modal dialog as the first editing solution. Settings uses bottom sheets that reuse onboarding step composables; keep that pattern.
- **Don't** introduce shadows deeper than 6dp or multi-layer shadow compositions. The system's elevation vocabulary has two active steps (1dp ambient, 6dp selected); a third step breaks the grammar.
- **Don't** use Carnation Pink and Sleep Blue interchangeably or on the same surface as stylistic choices. They carry semantic meaning that users learn by repetition; mixing them erodes trust.
- **Don't** mix domain accent colors across feature sections. Vaccine Indigo must not appear on a Doctor Visit screen, and vice versa. The isolation is the point.
- **Don't** use `FontFamily.Default` at `displaySmall` (36sp ExtraBold) for anything other than the live timer readout. Its role is established; reusing it collapses the hierarchy.
- **Don't** animate layout properties or use bounce/elastic easing. State transitions must ease out; spring animations are reserved for tap confirmation microinteractions (CueChip scale) and never for layout shifts.
- **Don't** add shimmer animations to loading skeletons. Static low-alpha surfaceVariant blocks are the loading pattern; motion is reserved for state changes, not indeterminate wait states.
- **Don't** elevate Domain Hero Cards. They are flat by design; elevation would compete with the home summary card grammar (1dp idle → 6dp active) that parents learn by repetition.
