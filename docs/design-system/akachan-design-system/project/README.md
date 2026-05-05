# Akachan Design System

**Akachan** (赤ちゃん, "baby" in Japanese) is a native Android baby tracker app for parents of infants aged 0–12 months. It focuses on two core pillars: **breastfeeding tracking** and **sleep tracking**, with secondary features for allergy monitoring and growth logging.

## Sources

- **Codebase**: [github.com/mrodrigues1/akachan](https://github.com/mrodrigues1/akachan) — Android, Kotlin, Jetpack Compose + Material 3
- **Design spec**: `specs/SPEC-001-APP-STRUCTURE.md` (within repo)
- **No Figma link provided** — design system derived entirely from codebase analysis

## Product Overview

Akachan is a **local-only, privacy-first** app — no cloud sync, no analytics. Parents can:
- Track breastfeeding sessions (start/stop/pause, left/right side, per-side timers, switch reminders)
- Log and review sleep records (naps + night sleep, wake-time entry, schedule generation)
- Manage baby profile (name, birth date, allergies)
- Receive local push notifications for feeding limit alerts (max time per breast, max total feed)
- Switch between Light, Dark, and System themes

**Navigation**: Home → Breastfeeding (with History) | Sleep (with History + Schedule) | Settings  
**Onboarding**: 3-step flow — Welcome → Baby Info → Allergies

---

## File Index

| File/Folder | Purpose |
|---|---|
| `README.md` | This file — product context + design principles |
| `colors_and_type.css` | CSS custom properties for colors, typography, shapes, spacing |
| `assets/` | Logo SVG, icon reference SVG |
| `preview/` | Design System tab cards (colors, type, shapes, components) |
| `ui_kits/android/` | Hi-fi Android UI kit — core screens as interactive HTML |
| `SKILL.md` | Agent skill manifest |

---

## CONTENT FUNDAMENTALS

### Voice & Tone
- **Warm, supportive, non-clinical.** This app is used in exhausting early parenthood moments — copy is always calm and encouraging, never alarming.
- **Second person ("you / your baby")** — the app speaks directly to the parent.
- **Sentence case** throughout UI labels (except section headers which are ALL CAPS).
- **Concise and action-oriented** — buttons say exactly what they do: "Start Session", "Stop Session", "Save Nap", "Switch to Right".
- **Emoji used sparingly** for warmth: 🍼 (feeding), 🌙 (sleep), 🌅 (wake time), ✨ (tips), 👋 (greeting). Never decorative — always functional or contextual.
- **No jargon** — "breastfeeding" not "lactation"; "nap" not "daytime sleep period".
- **Gentle suggestions**: tip cards say "Try Left breast next — used less last session." — never imperative commands.
- **Numbers and durations**: formatted human-friendly — "2h 15m", "12:30 PM", "3 days old".

### Copy Examples
- `"Hi, Mia 👋"` — home screen greeting (baby's name)
- `"Start a feeding session"` — instruction above side selector
- `"Try Left breast next — used less last session."` — recommendation tip
- `"No sleep entries yet"` / `"Tap below to add one"` — empty state
- `"Tap to set today's wake time"` — call to action
- `"LAST FEEDING"` — uppercase section label
- `"Session paused"` / `"● Session in progress"` — status pill

### Section Headers
Section labels (e.g. "BABY PROFILE", "FEEDING LIMITS", "TODAY") are rendered in `labelMedium` (Bold 12sp, 0.8sp letter-spacing) in ALL CAPS, colored in the relevant theme color (pink for feeding-related, blue for sleep-related).

---

## VISUAL FOUNDATIONS

### Color System

Akachan uses a **dual-theme semantic color system** (Material 3) with strong thematic color coding:

| Role | Light | Dark | Meaning |
|---|---|---|---|
| **Primary** | `#C2185B` | `#F48FB1` | Breastfeeding / core actions |
| **Primary Container** | `#F8BBD0` | `#880E4F` | Feeding highlights, tip cards |
| **Secondary** | `#1976D2` | `#90CAF9` | Sleep / secondary actions |
| **Secondary Container** | `#B3E5FC` | `#0D47A1` | Sleep highlights, nap chips |
| **Tertiary** | `#388E3C` | `#A5D6A7` | Success / overtime alert |
| **Tertiary Container** | `#C8E6C9` | `#1B5E20` | Duration preview chips |
| **Surface / Background** | `#FFFDE7` | `#1C1B1F` | App background, card base |
| **On Surface** | `#1A1A1A` | `#E6E1E5` | Primary text |
| **On Surface Variant** | `#757575` | `#757575` | Secondary text, hints |

**Raw palette** (reference tones, not used directly in UI):
- Baby Blue: `#89CFF0`
- Baby Pink: `#F4C2C2`
- Soft Green: `#90EE90`
- Soft Yellow: `#FFF9C4`

**Color semantics rule**: Pink = feeding/breastfeeding. Blue = sleep. Green = success/done/overtime. The warm yellow surface (`#FFFDE7`) gives the whole app a soft, nursery-like warmth.

### Typography

**Font**: Roboto (Android system default). No custom typeface — keeps the app feeling native and familiar.

| Style | Weight | Size | Tracking | Usage |
|---|---|---|---|---|
| `displaySmall` | ExtraBold (800) | 36sp | −1sp | Timer clock display |
| `headlineLarge` | Bold | 32sp | 0 | Large headings |
| `titleLarge` | SemiBold | 22sp | 0 | Screen titles, top bar |
| `titleMedium` | SemiBold | ~18sp | 0 | Card titles |
| `bodyLarge` | Regular | 16sp | +0.5sp | Primary body text, settings rows |
| `bodyMedium` | Regular | 14sp | 0 | Supporting body text |
| `labelMedium` | Bold | 12sp | +0.8sp | Section headers (ALL CAPS) |
| `labelSmall` | Medium | 11sp | +0.5sp | Sub-labels, stat descriptions |

### Shapes (Corner Radii)

Akachan uses **rounded corners throughout** — nothing is sharp:

| Token | Radius | Usage |
|---|---|---|
| `extraSmall` | 4dp | Error chips, small inline elements |
| `small` | 8dp | History cards, small containers |
| `medium` | 16dp | Main cards, side breakdown cards |
| `large` | 24dp | Home summary cards, banners |
| `extraLarge` | 50dp (pill) | Primary buttons, FABs, status chips, wake time chip |

### Spacing & Density

- Base padding: `16dp` horizontal screen margins
- Card internal padding: `12–20dp`
- Inter-element spacing: `8–12dp` (consistent `Arrangement.spacedBy`)
- Top bar / scaffold padding respected via `Scaffold` + `padding` params
- Bottom nav bar always surface-colored (no elevation color tint)

### Backgrounds & Surfaces

- **No gradients** — flat color surfaces only
- Surface color is the warm yellow `#FFFDE7` (light) or near-black `#1C1B1F` (dark)
- Cards use surface color with `2dp elevation` (subtle shadow)
- No full-bleed imagery or illustrations in the current codebase
- No texture or pattern overlays

### Elevation & Shadow

- Cards: `defaultElevation = 2dp` — very subtle shadow
- Active banners: colored background (primary/secondary) instead of elevation
- Bottom sheets: Modal, system-default sheet elevation

### Animation

- **Fade in/out**: `fadeIn()` / `fadeOut()` for banner visibility
- **Pulse scale**: Timer ring scales 1.0→1.04 on 1250ms tween (RepeatMode.Reverse) while running — gentle heartbeat feel
- **AnimatedContent**: Onboarding steps use `AnimatedContent` for step transitions
- No spring bounces, no complex enter/exit specs beyond fades
- Easing: Material default (no custom easing curves)

### Interactive States

- **Press**: Material ripple (system default)
- **Disabled**: Reduced alpha (Material default)
- **Outlined cards**: used as secondary action buttons (Switch Side, Pause, Stop) — `OutlinedCard(onClick = ...)`
- **Filter chips**: used for sleep type selection (Nap / Night Sleep)
- **Suggestion chips**: used for allergy tags in settings

### Iconography

See ICONOGRAPHY section below.

### Cards

Cards are the primary container primitive:
- Shape: `large` (24dp) for home/banner cards, `medium` (16dp) for detail cards
- Background: `surface` color or semantic container color (primaryContainer, secondaryContainer)
- Elevation: 1–2dp
- No border strokes on regular cards (OutlinedCard used only for action buttons)
- Internal layout: `Column`, `padding(12–20dp)`

### Bottom Navigation

3 tabs: Feeding (Restaurant icon), Sleep (Bedtime icon), Settings (Settings icon). Container color = surface. No elevation tint.

### Top App Bar

Surface-colored, no scroll behavior. Left: back arrow or title. Right: text action buttons ("History", "Edit").

---

## ICONOGRAPHY

Akachan uses **Material Icons (filled style)** exclusively — no custom icon set, no SVG sprite, no icon font beyond Material Icons which is bundled with Compose.

**Icons used:**
- `Icons.Default.Restaurant` — Feeding / breastfeeding
- `Icons.Default.Bedtime` — Sleep
- `Icons.Default.Settings` — Settings
- `Icons.Default.PlayArrow` — Start / resume
- `Icons.Default.Pause` — Pause
- `Icons.Default.Stop` — Stop session
- `Icons.AutoMirrored.Filled.ArrowBack` — Back navigation
- `Icons.Default.Edit` — Edit (wake time)
- `Icons.Default.Add` — Add entry
- `Icons.Default.Check` — Selected state (theme picker)

**Emoji used as icons** (in-text, not as icon components):
- 🍼 Feeding card, session banners
- 🌙 Sleep card, empty state
- 🌅 Wake time chip
- ✨ Tip card

**App icon**: Pink circle with a minimalist baby face (two white dot eyes + white smile arc on `#C2185B` background). See `assets/logo.svg`.

**No third-party icon library** — web implementations should use Material Symbols or equivalent.
