# Expanded Notification Transparent Background

**Date:** 2026-05-06
**Status:** Approved

---

## Problem

The expanded (big content view) notification layouts for breastfeeding and feeding-limit notifications render a second rounded card *inside* the system notification tile. This is caused by `android:background="@drawable/notification_surface"` on the root `LinearLayout` of both expanded layouts. The result is a "card-within-a-card" visual that conflicts with the system tile chrome and does not match the app's design system intent of surface-level content filling its container.

Collapsed layouts and the sleep notification are unaffected — they already have no custom background.

---

## Goals

- Expanded notification content fills the system notification tile naturally (transparent background).
- Text and progress bar colors meet WCAG AA contrast (4.5:1 for normal text, 3:1 for large text/UI components) against the system-supplied tile surface in both light and dark notification shades.
- All design-system color tokens, typography, progress bar drawables, and action button styles remain unchanged.
- No changes to `NotificationHelper.kt` or any Kotlin notification logic.
- Dark mode continues to work correctly via `values-night/colors.xml`.

## Non-Goals

- Changing notification content, wording, or behavior.
- Switching breastfeeding or warning notifications to `BigTextStyle` or other native styles — they keep `DecoratedCustomViewStyle` with custom layouts. Sleep already uses `BigTextStyle` for its expanded view and is out of scope.
- Modifying collapsed view layouts (`notification_collapsed_feeding.xml`, `notification_collapsed_warning.xml`, `notification_collapsed_sleep.xml`).
- Adjusting padding or spacing on the expanded layouts — internal padding (12dp start/end, 10dp top/bottom) is retained as-is. "Fills the tile" means no inner card background, not zero padding.

---

## Changes

### 1. `app/src/main/res/layout/notification_breastfeeding_progress.xml`

Remove the `android:background` attribute from the root `LinearLayout`:

```xml
<!-- Remove this line -->
android:background="@drawable/notification_surface"
```

All other attributes (padding, orientation, child views) stay identical.

### 2. `app/src/main/res/layout/notification_warning_progress.xml`

Same removal — remove `android:background="@drawable/notification_surface"` from the root `LinearLayout`.

### 3. `app/src/main/res/drawable/notification_surface.xml`

Delete this file entirely. It will have no remaining references after the above two changes.

### 4. `app/src/main/res/values/colors.xml`

Remove the `notification_surface` color entry — it is only consumed by `notification_surface.xml`.

### 5. `app/src/main/res/values-night/colors.xml`

Remove the `notification_surface` color entry.

---

## Affected Notifications

| Notification | Expanded layout | Change |
|---|---|---|
| Breastfeeding active (running + paused) | `notification_breastfeeding_progress.xml` | Remove background |
| Feeding limit reached | `notification_warning_progress.xml` | Remove background |
| Time to switch sides | `notification_breastfeeding_progress.xml` (progress hidden) | Remove background |
| Sleep active | `BigTextStyle` (system-rendered) | No change |

---

## What Does NOT Change

- `NotificationHelper.kt` — no Kotlin changes required.
- `notification_collapsed_feeding.xml`, `notification_collapsed_warning.xml`, `notification_collapsed_sleep.xml` — already transparent, no changes.
- All `notification_progress_*.xml` drawables — progress bar colors stay as-is.
- All `values/colors.xml` entries other than `notification_surface`.
- `DecoratedCustomViewStyle` usage on breastfeeding and warning notifications.

---

## Testing

### Visual verification (on device or emulator, Android 12+)

For each notification below, verify in **both system light mode and system dark mode** that:
- No inner rounded card is visible inside the tile — content sits directly on the system tile surface.
- Title text (`#1A1A1A` light / `#E6E1E5` dark) is clearly legible against the OEM tile background.
- Body text (`#757575` light / `#CAC4D0` dark) is clearly legible.
- Progress bar fill and track colors are clearly distinguishable.

| Notification to trigger | How |
|---|---|
| Breastfeeding active — running | Start a feeding session |
| Breastfeeding active — paused | Start then pause a feeding session |
| Feeding limit reached | Set a short max time and wait for the alarm |
| Time to switch sides | Set a short per-breast time and wait for the alarm |

### Resource cleanup verification

After the changes, run:

```
rg notification_surface app/src/main/res
```

Expected: zero matches. Any remaining match indicates an incomplete removal.

### Regression check

- Sleep active notification must be unaffected (expanded is system-rendered, collapsed has no background).
- Collapsed breastfeeding, warning, and sleep views must be unaffected.
