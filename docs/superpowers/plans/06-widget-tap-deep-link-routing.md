# Widget Tap Deep-Link Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-53](https://linear.app/akachan/issue/AKA-53/widget-tap-deep-link-routing)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Taps section)
**Suggested branch:** `feat/widget-tap-deep-link-routing`
**Depends on:** Plan 5 (`WidgetContent.kt`)
**Blocks:** none

**Goal:** Make widget taps open the right screen in `MainActivity`. Small widget tap → Home. Medium widget feed row tap → Breastfeeding screen; sleep row tap → Sleep screen. Reuse the existing `NotificationHelper.EXTRA_NAV_ROUTE` deep-link convention — `MainActivity.onCreate` and `onNewIntent` already read that extra (see `MainActivity.kt:57` and `MainActivity.kt:138`), and `HandlePendingNavRoute` already navigates to `Routes.BREASTFEEDING` and `Routes.SLEEP_TRACKING` when allowed. No new MainActivity logic is required.

**Architecture:**
- Use `actionStartActivity` with an `ActionParameters` carrying the target route string. A small `WidgetNavigation.kt` helper exposes `openHomeAction()`, `openBreastfeedingAction()`, `openSleepAction()` returning `androidx.glance.action.Action` objects so composables stay terse.
- The `Action` builders construct an `Intent` for `MainActivity::class.java` with `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP` and `putExtra(NotificationHelper.EXTRA_NAV_ROUTE, route)`.
- `MainActivity.ALLOWED_NAV_ROUTES` is extended to include `Routes.HOME` so the Small widget reliably navigates Home even when the task is already on Breastfeeding / Sleep / Settings. The existing `HandlePendingNavRoute` already calls `navigate(route)`, which works for `Routes.HOME` once it is allowed.
- Small widget: outer `Row` modifier gains `.clickable(openHomeAction())`.
- Medium widget: the feed `Row` gets `.clickable(openBreastfeedingAction())`; the sleep `Row` gets `.clickable(openSleepAction())`. The baby-name header is **not** clickable to avoid confusion.

**Tech Stack:** `androidx.glance.action.clickable`, `androidx.glance.appwidget.action.actionStartActivity`, `androidx.glance.action.ActionParameters`. No new manifest or activity changes.

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/WidgetNavigation.kt`
- Modify: `app/src/main/java/com/babytracker/widget/WidgetContent.kt` (apply `.clickable(...)` to Small outer Row, Medium feed Row, Medium sleep Row)
- Modify: `app/src/main/java/com/babytracker/MainActivity.kt:39` (extend `ALLOWED_NAV_ROUTES` to include `Routes.HOME`)

Glance `Action` objects are not unit-testable without a Glance host. The `MainActivity` change is covered by a Robolectric test that drives `HandlePendingNavRoute` with the Home route — see Task 3.

---

### Task 1: Add `WidgetNavigation.kt`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetNavigation.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.babytracker.MainActivity
import com.babytracker.navigation.Routes
import com.babytracker.util.NotificationHelper
import android.content.ComponentName
import android.content.Intent

internal fun openHomeAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.HOME),
)

internal fun openBreastfeedingAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.BREASTFEEDING),
)

internal fun openSleepAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.SLEEP_TRACKING),
)

private fun mainActivityIntent(route: String): Intent {
    val intent = Intent()
    intent.component = ComponentName("com.babytracker", MainActivity::class.java.name)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    intent.putExtra(NotificationHelper.EXTRA_NAV_ROUTE, route)
    return intent
}
```

Notes for the worker:
- `actionStartActivity(intent: Intent)` requires the intent's `component` to be explicit; using `ComponentName("com.babytracker", MainActivity::class.java.name)` is robust regardless of the build's `applicationId` suffix conventions. If the project ever introduces flavor-based `applicationIdSuffix`, replace the literal package with `context.packageName` (the worker can fetch context via a different overload — see Step 2).
- `FLAG_ACTIVITY_CLEAR_TOP` ensures repeated widget taps don't stack new MainActivity instances; `MainActivity` already calls `setIntent(intent)` in `onNewIntent` to refresh the extra.
- `Routes.HOME`, `Routes.BREASTFEEDING`, and `Routes.SLEEP_TRACKING` already exist (`navigation/Routes.kt`). The two latter routes are present in `MainActivity.ALLOWED_NAV_ROUTES`; `HOME` is the default landing route after onboarding, so an empty extra would also produce the same effect — keeping it explicit is clearer.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 1b: Extend `MainActivity.ALLOWED_NAV_ROUTES` to include Home

**Files:**
- Modify: `app/src/main/java/com/babytracker/MainActivity.kt:39`

- [ ] **Step 1: Update the constant**

Change:

```kotlin
private val ALLOWED_NAV_ROUTES = setOf(Routes.BREASTFEEDING, Routes.SLEEP_TRACKING)
```

to:

```kotlin
private val ALLOWED_NAV_ROUTES = setOf(Routes.HOME, Routes.BREASTFEEDING, Routes.SLEEP_TRACKING)
```

`HandlePendingNavRoute` already calls `navController.navigate(route)` for every allowed route, so no other change is needed. The existing partner-mode + onboarding-incomplete guards still hold for Home.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Apply `.clickable(...)` in `WidgetContent.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/WidgetContent.kt`

- [ ] **Step 1: Wire Small tap**

Replace the Small `Row` modifier with:

```kotlin
import androidx.glance.action.clickable
// ...

@Composable
fun SmallContent(data: WidgetData, now: Instant) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(openHomeAction()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedRowContent(data = data, now = now)
    }
}
```

- [ ] **Step 2: Wire Medium feed + sleep taps**

```kotlin
@Composable
fun MediumContent(data: WidgetData, now: Instant) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp),
    ) {
        Text(
            text = data.babyName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openBreastfeedingAction()),
        ) {
            FeedRowContent(data = data, now = now)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openSleepAction()),
        ) {
            SleepRowContent(data = data, now = now)
        }
    }
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Manual verification (device)

The full matrix below is mandatory — `MainActivity` state has too many branches (cold start, warm start from another route, onboarding-incomplete, partner mode) to cover with a single sanity tap.


Glance taps cannot be unit-tested without a Glance host. Manual verification is mandatory.

- [ ] **Step 1: Install debug build on device**

```
./gradlew :app:installDebug
```

- [ ] **Step 2: Place both widget sizes**

Long-press home → Widgets → Baby Tracker. Drop the small (2x1) and medium (2x2) variants on the home screen.

- [ ] **Step 3: Verify routing across state matrix**

For each row, take the action and confirm the destination matches "Expected".

| # | Pre-state                                           | Tap                | Expected                                |
|---|-----------------------------------------------------|--------------------|-----------------------------------------|
| 1 | App fully closed (cold start)                       | Small              | Home                                    |
| 2 | App open on Home                                    | Small              | Home (no-op visually)                   |
| 3 | App open on Breastfeeding                           | Small              | Home                                    |
| 4 | App open on Sleep                                   | Medium feed row    | Breastfeeding                           |
| 5 | App open on Settings                                | Medium sleep row   | Sleep                                   |
| 6 | App open on Breastfeeding                           | Medium feed row    | Breastfeeding (no-op visually)          |
| 7 | App open on Sleep                                   | Medium sleep row   | Sleep (no-op visually)                  |
| 8 | Onboarding-incomplete                               | Any zone           | Onboarding screen (pending route held)  |
| 9 | Partner mode (`AppMode.PARTNER`)                    | Any zone           | Partner dashboard; pending route cleared |
|10 | Medium baby-name header                             | Header             | No-op (header is not clickable)         |

If any row routes incorrectly, **stop and investigate** before continuing.

---

### Task 4: Lint, static analysis, full unit suite

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on both.

---

### Task 5: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetNavigation.kt \
        app/src/main/java/com/babytracker/widget/WidgetContent.kt \
        app/src/main/java/com/babytracker/MainActivity.kt

git commit -m "feat(widget): route widget taps to Home, Breastfeeding, and Sleep"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 3 files changed; no unrelated changes.

---

## Acceptance Criteria

- Small widget tap opens Home (`Routes.HOME`).
- Medium widget feed row opens Breastfeeding (`Routes.BREASTFEEDING`); sleep row opens Sleep (`Routes.SLEEP_TRACKING`).
- Deep-link uses the existing `NotificationHelper.EXTRA_NAV_ROUTE` extra; `MainActivity.HandlePendingNavRoute` consumes it for all three routes.
- `MainActivity.ALLOWED_NAV_ROUTES` is extended to include `Routes.HOME` so the Small widget tap is deterministic regardless of the app's current screen.
- `./gradlew ktlintFormat detekt :app:testDebugUnitTest :app:assembleDebug` all succeed.
- One commit on `feat/plans-home-screen-widget`.
