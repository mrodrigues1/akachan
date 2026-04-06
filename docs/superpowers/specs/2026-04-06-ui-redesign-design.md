# UI Redesign — Akachan

**Date:** 2026-04-06
**Scope:** Full UI overhaul — theme, all 8 screens, 3 shared components
**Approach:** Full pass (Approach B) — consistent redesign in one branch

---

## 1. Design Principles

- **Soft & Playful:** Rounded corners, pastel BabyPink + BabyBlue palette, emoji accents
- **One-handed 3am usability:** Large touch targets, high contrast text, minimal cognitive load
- **Local-first:** No data leaves the device; no network UI states to design for
- **Functional polish:** Every visual change must preserve all existing functionality

---

## 2. Theme & Color System

### 2.1 Color tokens (`ui/theme/Color.kt`)

New semantic constants replace the orphaned `Purple*` defaults:

| Token | Light value | Source constant |
|-------|-------------|-----------------|
| `PrimaryDark` | `#C2185B` | Deep BabyPink |
| `PrimaryLight` | `#F8BBD0` | BabyPink container |
| `SecondaryDark` | `#1976D2` | Deep BabyBlue |
| `SecondaryLight` | `#B3E5FC` | BabyBlue container |
| `TertiaryDark` | `#388E3C` | Deep SoftGreen |
| `TertiaryLight` | `#C8E6C9` | SoftGreen container |
| `SurfaceTint` | `#FFFDE7` | SoftYellow tint |
| `OnPrimaryDark` | `#FFFFFF` | White on primary |
| `OnPrimaryLight` | `#880E4F` | Dark pink on container |

Existing `BabyBlue`, `BabyPink`, `SoftGreen`, `SoftYellow` constants are kept as-is; new semantic constants reference their derived values.

### 2.2 Color scheme (`ui/theme/Theme.kt`)

```
lightColorScheme(
    primary           = PrimaryDark,
    onPrimary         = OnPrimaryDark,
    primaryContainer  = PrimaryLight,
    onPrimaryContainer = OnPrimaryLight,
    secondary         = SecondaryDark,
    onSecondary       = Color.White,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary          = TertiaryDark,
    tertiaryContainer = TertiaryLight,
    surface           = SurfaceTint,
    background        = SurfaceTint,
)
```

`dynamicColor = false` — custom palette used on all Android versions, not overridden by wallpaper.

### 2.3 Shapes (`ui/theme/Shape.kt`) — new file

```
Shapes(
    small      = RoundedCornerShape(8.dp),    // chips, badges
    medium     = RoundedCornerShape(16.dp),   // cards
    large      = RoundedCornerShape(24.dp),   // sheets, dialogs
    extraLarge = RoundedCornerShape(50.dp),   // FAB, primary buttons
)
```

Wire into `MaterialTheme(shapes = AkachanShapes, ...)`.

### 2.4 Typography (`ui/theme/Type.kt`)

Add two missing styles to the existing `Typography` object:

- `displaySmall` — `FontWeight.ExtraBold`, 36sp, -1sp letterSpacing — used by ring timer number
- `labelMedium` — `FontWeight.Bold`, 12sp, 0.8sp letterSpacing, `UPPERCASE` — used by day section headers

All styles remain `FontFamily.Default` (no custom font import needed).

---

## 3. Shared Components

### 3.1 `TimerDisplay` (`ui/component/TimerDisplay.kt`) — rewritten

**Current:** Plain `headlineLarge` text clock.
**New:** Circular conic arc ring + timer number inside.

Behaviour:
- Ring is a `Canvas` drawing a conic arc: `progress = elapsedSeconds / (maxMinutes * 60f)`, clamped to 1f
- Arc colour = `MaterialTheme.colorScheme.primary` (pink for feeding, blue for sleep — set by caller via `LocalContentColor` or explicit parameter)
- Track (background arc) = `primaryContainer`
- When `progress >= 1f` (over max time): arc colour switches to `tertiaryContainer` (amber warning)
- Centre text: elapsed time in `displaySmall` + smaller label for `"XX% of Ym"` in `labelSmall`
- Gentle scale pulse animation (1.0→1.04→1.0, 2.5s loop) via `rememberInfiniteTransition`
- New parameter: `maxDurationSeconds: Int` (0 = no ring, render plain text only)

### 3.2 `SideSelector` (`ui/component/SideSelector.kt`) — rewritten

**Current:** Two `FilterChip`s in a row.
**New:** Two large square `Card`s in a row.

Behaviour:
- Each card: 80dp × 80dp, `shape = MaterialTheme.shapes.medium`
- Selected: filled `primary` background, white label, elevated shadow
- Unselected: white background, `primary` border (2dp), muted label
- Arrow icon (← / →) above the label text
- Same API: `selectedSide: BreastSide?`, `onSideSelected: (BreastSide) -> Unit`

### 3.3 `HistoryCard` (`ui/component/HistoryCard.kt`) — updated

- Leading icon badge: 28dp circle/rounded-rect with emoji (🍼 for feeding, 😴 for nap, 🌙 for night sleep)
- Badge background: `primaryContainer` (pink) for LEFT side / nap, `secondaryContainer` (blue) for RIGHT side / night sleep
- Title: human-formatted side name ("Left side", "Right side", "Nap", "Night Sleep") — not raw `.name`
- Subtitle: time formatted as "08:42 AM" via existing `formatDateTime()` extension
- Trailing: duration string stays as-is
- New parameter: `badgeEmoji: String`, `badgeColor: Color`

---

## 4. Screen Designs

### 4.1 `HomeScreen`

**Layout:** `Scaffold` with `TopAppBar` + `NavigationBar` (bottom).

**TopAppBar:**
- Title: `"Hi, {babyName} 👋"` + subtitle `"Sunday, Apr 6"` (formatted from current date)
- Trailing: settings icon button navigating to Settings

**Body (idle — no active session):**
1. Two summary cards in a 2-column grid:
   - Feeding card: 🍼 emoji, `"Breastfeeding"` label, `"N sessions today"` count, `"Last: Xh Ym ago"` derived from most recent session
   - Sleep card: 🌙 emoji, `"Sleep"` label, `"Xh Ym last night"` total from previous night's records
   - Both cards: `fillMaxWidth`, `shape = MaterialTheme.shapes.medium`, white background, 16dp padding
   - Cards are `clickable` — navigate to respective tracking screen
2. Tip card (pink `primaryContainer` background): suggests which breast to use next based on last session's `startingSide`

**Body (live — active session present):**
1. Active session banner (full width, gradient `primary→secondary`):
   - Emoji + `"Feeding/Sleep in progress"` + elapsed time (`"07:42 elapsed"`)
   - `"STOP"` pill button: calls stop use case directly from `HomeViewModel`; navigates to tracking screen on tap of rest of banner
   - Pulsing box-shadow animation
2. Summary cards below (same as idle, `alpha = 0.7f`)

**NavigationBar fix:** Pass `selected = currentRoute == Routes.X` for each item. `HomeViewModel` or `AppNavGraph` tracks current route.

**`HomeUiState` additions needed:**
- `activeSession: BreastfeedingSession?` (already exists as `recentFeedings.firstOrNull { it.endTime == null }`)
- `activeRecord: SleepRecord?`
- `lastFeedSide: BreastSide?`
- `lastNightSleepDuration: Duration?`
- `sessionsTodayCount: Int`

### 4.2 `BreastfeedingScreen`

**Idle state:**
1. Centered heading: `"Start a feeding session"` (`titleLarge`)
2. `SideSelector` (new big-button version)
3. `"Start Session"` filled button (full width, `extraLarge` shape)
4. History link moved to `TopAppBar` trailing text button

**Active state:**
1. Status pill badge: `"● Session in progress"` on `primaryContainer` background
2. `TimerDisplay` with ring (passes `maxDurationSeconds` from settings)
3. Per-side breakdown row: two small `Card`s side by side showing elapsed per side
   - Active side: `primaryContainer` fill
   - Inactive side: surface fill, muted text
4. `"Switch to Right/Left"` outlined card (full width, border = `primary`, leading ⇄ icon)
5. `"Stop Session"` filled button (`primary`, full width)

### 4.3 `SleepTrackingScreen`

**Idle state:**
1. Heading: `"Track Sleep"` + subtitle `"Choose a sleep type to begin"`
2. Sleep type selector: two large square cards (same big-button pattern as `SideSelector`)
   - NAP: 😴 emoji, blue filled when selected
   - NIGHT SLEEP: 🌙 emoji, blue filled when selected
3. `"Start Tracking"` filled button (blue `secondary`)
4. `"View History"` + `"Schedule"` as outlined pill buttons in a row

**Active state:**
1. Status pill badge: `"● {type} in progress"`
2. `TimerDisplay` ring (blue arc, `secondaryContainer` track)
3. `"Stop"` filled button (`secondary`)

### 4.4 `BreastfeedingHistoryScreen` & `SleepHistoryScreen`

Both screens share the same grouped-by-day structure:

**`LazyColumn` structure:**
```
stickyHeader { DayHeader("Today · 3 sessions · 36m total") }
items(todaySessions) { HistoryCard(...) }
stickyHeader { DayHeader("Yesterday · 5 sessions · 58m total") }
items(yesterdaySessions) { HistoryCard(...) }
```

**Day grouping logic:** Group `List<T>` by `startTime.atZone(ZoneId.systemDefault()).toLocalDate()`. Compute daily totals from completed sessions only.

**`DayHeader` composable:** New private composable — `labelMedium` text, `primary` color, 8dp top padding, 6dp bottom padding.

**Empty state:** When list is empty, centered column with large emoji (🍼 or 🌙), `"No sessions yet"` in `titleMedium`, muted subtitle `"Sessions you track will appear here"`.

### 4.5 `SleepScheduleScreen`

No structural changes. Apply theme tokens (rounded cards, `primaryContainer` section headers). No new functionality in scope.

### 4.6 `SettingsScreen`

**Layout:** Grouped list sections, no `Card` wrappers. Uses `HorizontalDivider` between rows.

**Section: Baby Profile**
- Name row: label / value / `"Edit"` text button
- Date of birth row: label / `"Mar 15, 2026 · 3 weeks old"` / `"Edit"` text button (age derived from `birthDate`)
- Allergies row: label / `FlowRow` of `SuggestionChip`s (pink) / `"Edit"` text button

**Section: Feeding Limits**
- Max per breast row: label / value / `"Edit"` text button
- Max total feed row: label / value or `"Disabled"` / `"Edit"` text button

**Edit actions:** Each `"Edit"` opens a `ModalBottomSheet` reusing the relevant `OnboardingScreen` step composable (`NameStepContent`, `BirthDateStepContent`, `AllergiesStepContent`). No new screen added.

**Footer:** `"Akachan v{BuildConfig.VERSION_NAME}"` centered, `labelSmall`, muted color.

### 4.7 `OnboardingScreen`

No redesign needed — already the most polished screen. Apply shape tokens (button becomes `extraLarge` rounded). `StepIndicator` dot color updated to `primary`.

---

## 5. Files to Create / Modify

| File | Action |
|------|--------|
| `ui/theme/Color.kt` | Add semantic color constants; keep existing ones |
| `ui/theme/Theme.kt` | Wire new scheme; `dynamicColor = false`; add `shapes` |
| `ui/theme/Shape.kt` | **New** — `AkachanShapes` |
| `ui/theme/Type.kt` | Add `displaySmall`, `labelMedium` |
| `ui/component/TimerDisplay.kt` | Rewrite with Canvas ring + pulse |
| `ui/component/SideSelector.kt` | Rewrite with big square card buttons |
| `ui/component/HistoryCard.kt` | Add badge emoji/color params, fix name formatting |
| `ui/home/HomeScreen.kt` | Full redesign — hybrid layout, banner, fix nav selection |
| `ui/home/HomeViewModel.kt` | Add `activeSession`, `activeRecord`, `lastFeedSide`, `sessionsTodayCount`, `lastNightSleepDuration` to `HomeUiState` |
| `ui/breastfeeding/BreastfeedingScreen.kt` | Full redesign — ring timer, big side buttons, per-side breakdown |
| `ui/sleep/SleepTrackingScreen.kt` | Full redesign — ring timer, big sleep-type buttons |
| `ui/breastfeeding/BreastfeedingHistoryScreen.kt` | Grouped-by-day list, empty state |
| `ui/sleep/SleepHistoryScreen.kt` | Grouped-by-day list, empty state |
| `ui/sleep/SleepScheduleScreen.kt` | Theme tokens only, no structural change |
| `ui/settings/SettingsScreen.kt` | Grouped rows with Edit actions, allergy chips, version footer |
| `ui/onboarding/OnboardingScreen.kt` | Shape tokens, `StepIndicator` color only |

---

## 6. Non-Goals

- Custom font import (Rounded system font sufficient)
- Dark theme custom palette (dark scheme inherits Material defaults; out of scope for this pass)
- Animation beyond ring pulse and existing `AnimatedContent` in onboarding
- New features (editing baby profile is UI-only, uses existing use cases)
- Multi-module restructuring
