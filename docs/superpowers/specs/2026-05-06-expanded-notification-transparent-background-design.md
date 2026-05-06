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
- All design-system color tokens, typography, progress bar drawables, and action button styles remain unchanged.
- No changes to `NotificationHelper.kt` or any Kotlin notification logic.
- Dark mode continues to work correctly via `values-night/colors.xml`.

## Non-Goals

- Changing notification content, wording, or behavior.
- Switching any notification to `BigTextStyle` or other native styles — all notifications keep `DecoratedCustomViewStyle` with custom layouts.
- Modifying collapsed view layouts (`notification_collapsed_feeding.xml`, `notification_collapsed_warning.xml`, `notification_collapsed_sleep.xml`).
- Modifying the sleep notification (`showSleepActive` already uses `BigTextStyle` for expanded and has no background issue).

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
- `DecoratedCustomViewStyle` usage — all notifications keep custom view rendering.

---

## Testing

- Trigger a breastfeeding session and expand the notification: content should sit flush in the tile with no inner card.
- Trigger a breastfeeding session with a max time set and let the limit alarm fire: expanded warning notification should similarly have no inner card.
- Trigger a side-switch alarm: expanded notification has no inner card.
- Toggle system dark mode: colors and contrast should remain correct (no regression).
- Verify no build warnings about unused resources.
