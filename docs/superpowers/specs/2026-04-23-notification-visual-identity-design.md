# Notification Visual Identity — Design Spec

**Date:** 2026-04-23
**Status:** Draft
**Reference design:** `design-System-handoff/akachan-design-system/project/preview/notifications.html`
**Builds on:** `docs/superpowers/specs/2026-04-22-notification-system-design.md`

---

## 1. Overview

The 2026-04-22 spec landed the structural notification system: channels, action receivers, the rich/simple toggle, and three notification flows (Switch Sides, Feeding Limit, Sleep Active). A subsequent commit added a fourth — Breastfeeding Active, the persistent ongoing-session notification. It did not address visual identity — every notification today posts with `R.drawable.ic_launcher_foreground` and no `setColor()`, so Android renders them with the system default accent regardless of which kind of alert fired.

This spec brings the notifications in line with the design-system mockup (`notifications.html`):

| Type | Accent (light) | Accent (dark) | Small icon |
|---|---|---|---|
| Switch Sides | Pink700 `#C2185B` | `#F48FB1` | bottle |
| Feeding Limit | **Amber700 `#E65100`** *(new)* | **Amber100 `#FFCC80`** *(new)* | stopwatch |
| Breastfeeding Active | Pink700 `#C2185B` | `#F48FB1` | bottle |
| Sleep Active | Blue700 `#1976D2` | `#90CAF9` | crescent moon |

### Constraints

The mockup is a browser preview with full layout freedom. Android (API 26+, target 35) owns the notification card chrome — rounded corners, background fill, and the app-name header row are drawn by the OS. We deliberately stay within the system-native notification idiom: no `DecoratedCustomViewStyle`, no RemoteViews. The visible mockup elements that we **can** honor on real devices are the small icon glyph and the `setColor()` accent, which Android tints into the small icon and (on most OEMs) the action-button labels and progress bar. The cream card background, the colored top accent bar, and the colored pill action buttons remain system-styled.

### Out of scope

- New notification channels (the existing two-channel structure stays)
- Custom RemoteViews / `DecoratedCustomViewStyle`
- Body-text rewording (current copy stays — see §4)
- Large icons (`setLargeIcon`)
- Changes to `BreastfeedingNotificationManager`, `SleepNotificationManager`, action receivers, or any AlarmManager logic
- Adding a notification mock to `DesignSystemPreviewScreen.kt`

---

## 2. Color Tokens (design system additions)

Add a full Amber raw scale to `ui/theme/Color.kt`, mirroring the existing Pink/Blue/Green pattern. Expose three semantic warning tokens. Do **not** remap M3 `tertiary` — Green stays the M3 tertiary slot.

### 2.1 Raw palette additions

```kotlin
// Warning / Amber (no M3 slot — used for limit / over-limit states)
val Amber900 = Color(0xFF7A3600)
val Amber800 = Color(0xFF7A4800)
val Amber700 = Color(0xFFE65100)
val Amber200 = Color(0xFFFFE0B2)
val Amber100 = Color(0xFFFFCC80)
```

`Amber800` exists because the dark-scheme `WarningContainerAmber` is a darker amber than `Amber700`; it has no light-scheme counterpart but earns a named raw for consistency with the "every notification color is a named raw" convention.

### 2.2 Semantic warning tokens (extended, non-M3)

```kotlin
// Light scheme
val WarningAmber              = Amber700                 // accent
val WarningContainerAmber     = Amber200                 // progress track / soft fill
val OnWarningContainerAmber   = Amber900                 // text on container

// Dark scheme
val WarningAmberDark          = Amber100
val WarningContainerAmberDark = Amber800
val OnWarningContainerAmberDark = Amber200
```

These ship as top-level `val`s in `Color.kt` and are referenced directly by name. They are **not** wired through `MaterialTheme.colorScheme` because they are not part of M3's slot system. `NotificationHelper` reads them directly via Kotlin import.

### 2.3 Light-scheme color scheme (`Theme.kt`) — no change

`Theme.kt`'s `LightColorScheme` and `DarkColorScheme` are not modified. Warning tokens are extension semantics, not M3 slots.

---

## 3. Drawable Resources

Three new monochrome `VectorDrawable` files in `app/src/main/res/drawable/`. Each file is a single white path on a 24×24 dp viewport — alpha-only so Android's small-icon tinting (which keys on alpha and ignores RGB) renders them in the accent color set via `setColor()`.

| File | Glyph | Used by |
|---|---|---|
| `ic_notif_breastfeeding.xml` | Baby bottle outline | `showSwitchSide`, `showBreastfeedingActive` |
| `ic_notif_limit.xml` | Stopwatch / clock-with-bell | `showFeedingLimit` |
| `ic_notif_sleep.xml` | Crescent moon | `showSleepActive` |

### Authoring rules

- 24×24 dp viewport (`android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"`)
- Single `<path>` with `android:fillColor="#FFFFFFFF"`
- No gradients, no filled background rectangles — those would block tinting
- No theme references (`?attr/...`) — small icons render in the system status bar where the app theme is unavailable

The existing `R.drawable.ic_launcher_foreground` is left alone (still used by the launcher).

---

## 4. NotificationHelper Rewiring

`util/NotificationHelper.kt` is the only Kotlin file rewired. Behavior, channel IDs, notification IDs, action wiring, and PendingIntent request codes are unchanged. Only styling changes.

### 4.1 New private helpers

```kotlin
private fun resolveAccent(context: Context, light: Color, dark: Color): Int {
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return (if (isDark) dark else light).toArgb()
}

private fun NotificationCompat.Builder.applyDesignSystem(
    accentColor: Int,
    smallIconRes: Int
): NotificationCompat.Builder = this
    .setSmallIcon(smallIconRes)
    .setColor(accentColor)
    .setColorized(false)
```

`setColorized(false)` is explicit because Android only honors `setColorized(true)` for foreground-service media notifications. Calling it with `false` keeps intent obvious to readers.

### 4.2 Per-call-site wiring

Each `NotificationCompat.Builder` chain in `NotificationHelper` swaps its `setSmallIcon(R.drawable.ic_launcher_foreground)` call for `applyDesignSystem(accent, icon)`:

| Function | accent (light) | accent (dark) | smallIconRes |
|---|---|---|---|
| `showSwitchSide` | `Pink700` | `PrimaryPinkDark` | `ic_notif_breastfeeding` |
| `showFeedingLimit` | `WarningAmber` | `WarningAmberDark` | `ic_notif_limit` |
| `showBreastfeedingActive` | `Pink700` | `PrimaryPinkDark` | `ic_notif_breastfeeding` |
| `showSleepActive` | `Blue700` | `SecondaryBlueDark` | `ic_notif_sleep` |

### 4.3 Body-text alignment with mockup

The mockup wording differs slightly from the current code in two cases. Both are **kept as currently coded**:

| Type | Mockup | Current code | Decision |
|---|---|---|---|
| Switch Sides | `Left breast: 10 min. Try switching to the right.` | `Left breast: 10 min · Switch to the right` | Keep current — tighter, matches app copy style |
| Sleep Active | `Night sleep started at 9:00 PM · 32 min elapsed` | `Night sleep · started at 9:00 PM` (+ chronometer) | Keep current — chronometer renders live elapsed in the header on most Android versions; baking elapsed minutes into a static string would be wrong-by-time |
| Feeding Limit | `Session has reached 20 minutes. Consider wrapping up.` | identical | no change |

### 4.4 What stays the same

- Channel IDs (`breastfeeding_notifications`, `sleep_notifications`)
- Channel importance (HIGH for breastfeeding, DEFAULT for sleep)
- Notification IDs (`SWITCH_SIDE_NOTIFICATION_ID = 1002`, etc.)
- PendingIntent request codes
- Action wiring (`BreastfeedingActionReceiver` / `SleepActionReceiver` routes)
- The `richEnabled` toggle behavior — design-system styling applies unconditionally; rich gating still controls progress bar + actions
- AlarmManager scheduling (`BreastfeedingNotificationManager` is untouched)

---

## 5. Settings interaction (no change)

The `richNotificationsEnabled` toggle from the 2026-04-22 spec keeps its current contract. Per-type icon and `setColor()` accent are baseline identity (not "rich"); they apply in both rich and simple modes. Toggling rich off still removes progress bar + actions.

---

## 6. Testing Strategy

### 6.1 Unit tests

- **`NotificationHelperTest.kt`** *(new)* — covers the only branchable piece of `NotificationHelper`: `resolveAccent`. Builds a fake `Configuration` with `uiMode` set to night vs. not-night vs. undefined and asserts the returned ARGB matches the light/dark input.

That is the only addition. The rest of `NotificationHelper` remains pure `NotificationCompat.Builder` API calls — mocking `NotificationManager` to assert builder chains adds noise without catching real bugs (per the precedent set in §9 of the 2026-04-22 spec).

### 6.2 Existing tests

`BreastfeedingNotificationManagerTest`, `SleepNotificationManagerTest`, `BreastfeedingActionReceiverTest`, `SleepActionReceiverTest`, `SettingsRepositoryImplTest`, `SettingsViewModelTest` — all unchanged, all expected to keep passing. Their assertions cover scheduling, action routing, and persistence — none of which this spec touches.

### 6.3 Manual verification (in implementation plan, not spec)

A device/emulator checklist in the implementation plan covers:

1. Switch-side alarm fires → pink small icon, pink-tinted action labels, pink progress bar, expected body
2. Feeding-limit alarm fires → amber accent + amber progress bar at 100%
3. Active session shows persistent pink notification with chronometer
4. Sleep session start → blue accent + chronometer
5. Toggle `richEnabled` OFF → accent + small icon still apply, progress bar + actions disappear (no regression)
6. System dark mode → all four notifications switch to their dark accent values

---

## 7. Documentation Updates

### 7.1 `CLAUDE.md` — "Theme & UI Tokens" section

Add a new "Amber" row to the palette-scale table:

| Family | 100 | 200 | 700 | 900 |
|---|---|---|---|---|
| Amber (Warning) | `#FFCC80` | `#FFE0B2` | `#E65100` | `#7A3600` |

Add a note that `Amber800` (`#7A4800`) is a single off-scale raw used by the dark-scheme warning container.

Add a new mini-table: "Warning semantic tokens (extended, non-M3)":

| Token | Light | Dark |
|---|---|---|
| `WarningAmber` | `Amber700` | `Amber100` |
| `WarningContainerAmber` | `Amber200` | `Amber800` |
| `OnWarningContainerAmber` | `Amber900` | `Amber200` |

### 7.2 `CLAUDE.md` — "What NOT to Do" section

Add: "Warning tokens (`WarningAmber*`) are extended semantics — access them as top-level `val`s from `ui/theme/Color.kt`, not via `MaterialTheme.colorScheme`."

### 7.3 `CLAUDE.md` — "Package Structure" section

Update `util/NotificationHelper.kt` description to mention "Builds design-system-themed notifications (per-type small icon + accent color)".

### 7.4 `notifications.html`

Untouched — it is the source-of-truth design intent, not a generated artifact.

---

## 8. New & Modified Files

### New files

```
app/src/main/res/drawable/
  ic_notif_breastfeeding.xml
  ic_notif_limit.xml
  ic_notif_sleep.xml

app/src/test/java/com/babytracker/util/
  NotificationHelperTest.kt
```

### Modified files

```
app/src/main/java/com/babytracker/ui/theme/Color.kt
  Add Amber100/200/700/800/900 raws
  Add WarningAmber, WarningContainerAmber, OnWarningContainerAmber (light)
  Add WarningAmberDark, WarningContainerAmberDark, OnWarningContainerAmberDark (dark)

app/src/main/java/com/babytracker/util/NotificationHelper.kt
  Add private resolveAccent() and Builder.applyDesignSystem()
  Replace setSmallIcon(ic_launcher_foreground) call sites with applyDesignSystem(accent, icon)

CLAUDE.md
  Add Amber row to palette table
  Add Warning semantic mini-table
  Add note under "What NOT to Do"
  Update util/NotificationHelper.kt description
```

---

## 9. Acceptance Criteria

- [ ] `Color.kt` exports `Amber100/200/700/800/900` raws and `WarningAmber*` semantic tokens for light + dark
- [ ] Three new vector drawables exist in `res/drawable/` and are alpha-only single-path 24×24 dp
- [ ] `NotificationHelper.showSwitchSide` posts with Pink accent + bottle icon (light) / `#F48FB1` accent (dark)
- [ ] `NotificationHelper.showFeedingLimit` posts with Amber accent + stopwatch icon (light) / `#FFCC80` accent (dark)
- [ ] `NotificationHelper.showBreastfeedingActive` posts with Pink accent + bottle icon (light/dark per system)
- [ ] `NotificationHelper.showSleepActive` posts with Blue accent + moon icon (light) / `#90CAF9` accent (dark)
- [ ] `NotificationHelperTest` covers `resolveAccent` for night, not-night, and undefined uiMode
- [ ] `richEnabled = false` still posts the design-system styling but omits progress bar and actions
- [ ] All existing notification-related tests pass with no regression
- [ ] Manual device verification (§6.3) passes for all six items
- [ ] `CLAUDE.md` updated with Amber palette row, Warning semantic mini-table, and "What NOT to Do" note
