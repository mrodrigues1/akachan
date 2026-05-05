# Akachan Android UI Kit

Hi-fidelity interactive recreation of the Akachan Android app screens. Built with React + Babel.

## Screens Covered

| Screen | File | Notes |
|---|---|---|
| Onboarding (3 steps) | `Screens.jsx` | Welcome → Baby Info → Allergies |
| Home | `Screens.jsx` | Summary cards, tip card, bottom nav |
| Breastfeeding | `Screens.jsx` | Side selector, ring timer, pause/stop/switch |
| Sleep Tracking | `Screens.jsx` | Wake time chip, stats row, entry list |
| Settings | `Screens.jsx` | Baby profile, feeding limits, theme toggle |

## Usage

Open `index.html` to see 4 device frames side-by-side:
- **Device 1**: Onboarding flow (click through steps)
- **Device 2**: Home screen (light)
- **Device 3**: Breastfeeding screen (tap Start → see live timer)
- **Device 4**: Sleep + Settings (dark mode, toggle in Settings)

## Component Exports

All components and tokens are exported to `window` from `Screens.jsx`:

```js
// Theme tokens
AK, AKDark, R (radii), T (type styles)

// Primitives
Card, OutlinedCardBtn, PillBtn, SectionLabel, Divider, TopBar
HistoryCard, RingTimer, SideSelector, BottomNav

// Screens
HomeScreen, BreastfeedingScreen, SleepScreen, SettingsScreen, OnboardingScreen
```

## Design Tokens

Colors: `#C2185B` primary (feeding), `#1976D2` secondary (sleep), `#FFFDE7` surface
Shapes: 4 / 8 / 16 / 24 / 50dp radius scale
Font: Roboto (system default on Android)
