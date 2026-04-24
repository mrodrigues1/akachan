# Notification Visual Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each notification type a distinct small-icon + accent-color identity that matches the `notifications.html` mockup, while keeping the existing channel / ID / action / AlarmManager wiring untouched.

**Architecture:** Additive only. Extend `ui/theme/Color.kt` with an Amber raw scale and three warning semantic tokens (non-M3). Add three alpha-only 24dp vector drawables. Rewire `util/NotificationHelper.kt` so each `NotificationCompat.Builder` chain gets `setSmallIcon(<per-type glyph>)` + `setColor(<light-or-dark accent>)` via two tiny private helpers (`resolveAccent`, `applyDesignSystem`). No other Kotlin file changes.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.12.01 (`androidx.compose.ui.graphics.Color.toArgb()`), `androidx.core.app.NotificationCompat`, Android VectorDrawable, JUnit 5 + MockK.

**Source of truth:** `docs/superpowers/specs/2026-04-23-notification-visual-identity-design.md`

**Workflow preference:** Implement first, tests after, then docs/review. Not TDD.

---

## File Structure

### New files

```
app/src/main/res/drawable/
  ic_notif_breastfeeding.xml       # Task 2 — baby bottle outline
  ic_notif_limit.xml               # Task 2 — stopwatch
  ic_notif_sleep.xml               # Task 2 — crescent moon

app/src/test/java/com/babytracker/util/
  NotificationHelperTest.kt        # Task 4 — resolveAccent branch coverage
```

### Modified files

```
app/src/main/java/com/babytracker/ui/theme/Color.kt
  Task 1: add Amber100/200/700/800/900 raws + Warning* semantic tokens.

app/src/main/java/com/babytracker/util/NotificationHelper.kt
  Task 3: add resolveAccent() + Builder.applyDesignSystem() helpers;
          replace 4× setSmallIcon(ic_launcher_foreground) sites with
          applyDesignSystem(accent, icon).

CLAUDE.md
  Task 5: add Amber row to palette table; add Warning mini-table;
          add "What NOT to Do" note; update NotificationHelper
          description in Package Structure section.
```

Each task commits independently so the history tells the story: palette → drawables → wiring → test → docs → manual-verified.

---

## Task 1: Add Amber raws and Warning semantic tokens to `Color.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt` (append new blocks at the bottom)

- [ ] **Step 1: Append the Amber raw scale**

Open `app/src/main/java/com/babytracker/ui/theme/Color.kt`. After the existing `OnErrorContainerDark` declaration (currently the last line of the file), append:

```kotlin

// ─── Raw palette — Warning / Amber ───────────────────────────
// Non-M3 extended palette for limit / over-limit states.
// Scale semantics match Pink/Blue/Green (700 = primary, 200 = container,
// 900 = on-container text, 100 = softest tone).
// Amber800 is off-scale: only the dark-scheme warning container uses it.
val Amber900 = Color(0xFF7A3600)
val Amber800 = Color(0xFF7A4800)
val Amber700 = Color(0xFFE65100)
val Amber200 = Color(0xFFFFE0B2)
val Amber100 = Color(0xFFFFCC80)

// ─── Warning semantic tokens (extended, non-M3) ──────────────
// Accessed as top-level vals — NOT wired through MaterialTheme.colorScheme.
// Consumed directly by NotificationHelper for the Feeding Limit notification.

// Light scheme
val WarningAmber = Amber700
val WarningContainerAmber = Amber200
val OnWarningContainerAmber = Amber900

// Dark scheme
val WarningAmberDark = Amber100
val WarningContainerAmberDark = Amber800
val OnWarningContainerAmberDark = Amber200
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Any Kotlin compile error means a typo in the hex value or declaration.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/Color.kt
git commit -m "feat(ui): add Amber raw scale and Warning semantic tokens

Amber100/200/700/800/900 raws plus WarningAmber, WarningContainerAmber,
OnWarningContainerAmber for light and dark schemes. Not wired through
MaterialTheme.colorScheme — these are extended semantics consumed
directly by NotificationHelper."
```

---

## Task 2: Create three notification vector drawables

All three files share the same authoring rules (per spec §3):
- 24×24 dp viewport
- Single `<path>` with `android:fillColor="#FFFFFFFF"`
- No gradients, no background rectangles, no `?attr/...` theme references
- Any path data may be substituted with an equivalent Material Symbols path as long as it stays alpha-only and single-path

**Files:**
- Create: `app/src/main/res/drawable/ic_notif_breastfeeding.xml`
- Create: `app/src/main/res/drawable/ic_notif_limit.xml`
- Create: `app/src/main/res/drawable/ic_notif_sleep.xml`

- [ ] **Step 1: Create `ic_notif_breastfeeding.xml` (baby bottle)**

Path: `app/src/main/res/drawable/ic_notif_breastfeeding.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M9,2h6v2h-1v1.5l1.41,0.71C16.39,6.71 17,7.7 17,8.78V20c0,1.1 -0.9,2 -2,2H9c-1.1,0 -2,-0.9 -2,-2V8.78c0,-1.08 0.61,-2.07 1.59,-2.57L10,5.5V4H9V2zM9,10v2h6v-2H9zM9,14v5h6v-5H9z"/>
</vector>
```

- [ ] **Step 2: Create `ic_notif_limit.xml` (stopwatch)**

Path: `app/src/main/res/drawable/ic_notif_limit.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M15,1H9v2h6V1zm-4,13h2V8h-2v6zm8.03,-6.61l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"/>
</vector>
```

- [ ] **Step 3: Create `ic_notif_sleep.xml` (crescent moon)**

Path: `app/src/main/res/drawable/ic_notif_sleep.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z"/>
</vector>
```

- [ ] **Step 4: Verify the resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`. Any AAPT error means malformed XML or an invalid `pathData` glyph.

Optional sanity check: open each file in Android Studio and confirm the Preview pane renders a recognizable bottle / stopwatch / moon silhouette. If the bottle glyph looks off, substitute a preferred Material Symbols path — the plan is icon-agnostic as long as it's alpha-only single-path.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_notif_breastfeeding.xml app/src/main/res/drawable/ic_notif_limit.xml app/src/main/res/drawable/ic_notif_sleep.xml
git commit -m "feat(ui): add per-type notification small-icon drawables

Three alpha-only 24dp vector drawables — bottle, stopwatch, crescent
moon — authored as a single white path each so Android's small-icon
tinting (alpha-mask + setColor) renders them in the per-type accent."
```

---

## Task 3: Rewire `NotificationHelper` with per-type accent and small icon

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`

- [ ] **Step 1: Add two new imports**

At the top of `NotificationHelper.kt`, add:

```kotlin
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.babytracker.ui.theme.Blue700
import com.babytracker.ui.theme.Pink700
import com.babytracker.ui.theme.PrimaryPinkDark
import com.babytracker.ui.theme.SecondaryBlueDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
```

Keep existing imports; the alphabetical order above matches the file's existing style.

- [ ] **Step 2: Add the two private helpers**

Inside `object NotificationHelper`, immediately after the `TAG` constant (line 32 in the current file), insert:

```kotlin
    private fun resolveAccent(context: Context, light: Color, dark: Color): Int {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMask == Configuration.UI_MODE_NIGHT_YES
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

- [ ] **Step 3: Rewire `showSwitchSide`**

In `showSwitchSide`, replace the current `builder` declaration:

```kotlin
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🍼 Time to switch sides")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)
```

with:

```kotlin
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle("🍼 Time to switch sides")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)
```

- [ ] **Step 4: Rewire `showFeedingLimit`**

In `showFeedingLimit`, replace the `builder` declaration with:

```kotlin
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle("⏱ Feeding limit reached")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)
```

- [ ] **Step 5: Rewire `showBreastfeedingActive`**

In `showBreastfeedingActive`, replace the `builder` declaration with:

```kotlin
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle("🍼 Feeding session active")
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(tapPi)
```

- [ ] **Step 6: Rewire `showSleepActive`**

In `showSleepActive`, replace the `builder` declaration with:

```kotlin
        val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
        val builder = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
            .setContentTitle("🌙 Sleep session active")
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(tapPi)
```

- [ ] **Step 7: Verify `ic_launcher_foreground` is no longer referenced in `NotificationHelper`**

Run: `grep -n "ic_launcher_foreground" app/src/main/java/com/babytracker/util/NotificationHelper.kt`
Expected: no output. If any line matches, that call site was missed — re-apply the rewiring.

Also confirm `R.drawable.ic_launcher_foreground` is still referenced elsewhere (launcher icon metadata) so we haven't broken the launcher:
Run: `grep -rn "ic_launcher_foreground" app/src/main/`
Expected: matches in `AndroidManifest.xml` / `res/mipmap-anydpi-v26/*.xml`, but **none** in `NotificationHelper.kt`.

- [ ] **Step 8: Build and run existing tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. No existing notification-related tests should break — their assertions cover scheduling, action routing, and persistence, none of which this change touches.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/babytracker/util/NotificationHelper.kt
git commit -m "feat(ui): apply per-type accent and small icon to notifications

Add resolveAccent() and Builder.applyDesignSystem() helpers.
Rewire showSwitchSide / showBreastfeedingActive to Pink + bottle,
showFeedingLimit to Amber + stopwatch, showSleepActive to Blue + moon.
Channel IDs, notification IDs, action wiring, and AlarmManager
scheduling are unchanged."
```

---

## Task 4: Unit-test `resolveAccent`

**Files:**
- Create: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

Note: `resolveAccent` is `private` inside `object NotificationHelper`. To test it without loosening visibility, the test invokes it via Kotlin reflection. This keeps production surface area minimal (per spec §6.1 and the precedent set by the 2026-04-22 spec §9, which rejects mocking `NotificationManager` to assert builder chains).

- [ ] **Step 1: Create the test file**

Path: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

```kotlin
package com.babytracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationHelperTest {

    private val lightColor = Color(0xFFC2185B) // Pink700
    private val darkColor = Color(0xFFF48FB1)  // PrimaryPinkDark

    private fun invokeResolveAccent(context: Context, light: Color, dark: Color): Int {
        val method = NotificationHelper::class.java.getDeclaredMethod(
            "resolveAccent",
            Context::class.java,
            Color::class.java,
            Color::class.java
        )
        method.isAccessible = true
        return method.invoke(NotificationHelper, context, light, dark) as Int
    }

    private fun contextWithUiMode(uiMode: Int): Context {
        val config = Configuration().apply { this.uiMode = uiMode }
        val resources = mockk<Resources>()
        every { resources.configuration } returns config
        val context = mockk<Context>()
        every { context.resources } returns resources
        return context
    }

    @Test
    fun `resolveAccent returns dark color when uiMode is night`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_YES)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(darkColor.toArgb(), result)
    }

    @Test
    fun `resolveAccent returns light color when uiMode is not night`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_NO)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(lightColor.toArgb(), result)
    }

    @Test
    fun `resolveAccent returns light color when uiMode is undefined`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(lightColor.toArgb(), result)
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.util.NotificationHelperTest"`
Expected: 3 tests pass. If reflection fails with `NoSuchMethodException`, the method signature drifted — confirm `resolveAccent(Context, Color, Color): Int` still exists in `NotificationHelper`.

- [ ] **Step 3: Run the full unit-test suite to confirm no regression**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/babytracker/util/NotificationHelperTest.kt
git commit -m "test(util): cover resolveAccent night/not-night/undefined branches"
```

---

## Task 5: Update `CLAUDE.md`

**Files:**
- Modify: `CLAUDE.md`

Three narrowly-scoped edits. Keep the surrounding table/section styles.

- [ ] **Step 1: Add Amber row to the palette-scale table**

In `CLAUDE.md`, find the palette-scale table under "Theme & UI Tokens → Palette Scale (`Color.kt`)". After the `Green` row, insert:

```markdown
| **Amber** (Warning) | `#FFCC80` | `#FFE0B2` | `#E65100` | `#7A3600` |
```

Immediately under the table, append a bullet to the "Surface grays and yellows have no scale equivalent" list:

```markdown
- `Amber800` `#7A4800` — off-scale raw used only by the dark-scheme warning container
```

- [ ] **Step 2: Add the Warning semantic mini-table**

Still in the "Theme & UI Tokens" section, after the "Dark scheme" semantic color table, add:

```markdown
### Warning semantic tokens (extended, non-M3)

Accessed as top-level `val`s from `ui/theme/Color.kt` — **not** wired through `MaterialTheme.colorScheme`. Consumed directly by `NotificationHelper` for the Feeding Limit notification.

| Token | Light | Dark |
|------|-------|------|
| `WarningAmber` | `Amber700` `#E65100` | `Amber100` `#FFCC80` |
| `WarningContainerAmber` | `Amber200` `#FFE0B2` | `Amber800` `#7A4800` |
| `OnWarningContainerAmber` | `Amber900` `#7A3600` | `Amber200` `#FFE0B2` |
```

- [ ] **Step 3: Add a note under "What NOT to Do"**

Append the following bullet to the "What NOT to Do" section (after the "Do not use KAPT" bullet):

```markdown
- Do not access warning tokens (`WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` and their `*Dark` pairs) through `MaterialTheme.colorScheme` — they are extended, non-M3 semantics and ship as top-level `val`s in `ui/theme/Color.kt`. Import them by name.
```

- [ ] **Step 4: Update the `util/NotificationHelper.kt` description**

In the "Package Structure" tree, find the line:

```
    ├── NotificationHelper.kt  # Cancel/show notification helpers
```

Replace with:

```
    ├── NotificationHelper.kt  # Cancel/show notification helpers — builds design-system-themed
    │                          #   notifications (per-type small icon + accent color via setColor())
```

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document Amber palette and Warning semantic tokens

Add Amber row to the palette-scale table, Warning semantic mini-table,
'What NOT to Do' guidance, and update the NotificationHelper.kt
description in the Package Structure section."
```

---

## Task 6: Manual device verification

The only behavior this plan changes is visual. Unit tests cover `resolveAccent`; everything else has to be seen on a device or emulator. Run through the full checklist on a single API-26+ device (target SDK 35 is fine on any emulator image you already have).

**Preconditions:**
- Fresh install or cleared notifications, so stale test notifications don't confuse the view.
- Both breastfeeding and sleep notification channels are enabled in system settings.
- `richNotificationsEnabled = true` in app Settings for steps 1–4; toggle for step 5.

- [ ] **Step 1: Switch-side notification (light mode)**

Start a breastfeeding session, let the per-breast timer elapse (or shorten `maxPerBreastMinutes` via Settings if it's tunable; otherwise wait), and verify when the notification fires:
- Small icon in status bar is the **bottle glyph**, tinted **Pink `#C2185B`**
- Notification card: action-button labels rendered in the pink accent on most OEMs
- Progress bar tinted pink (when supported by the OEM)
- Body text: `<Left|Right> breast: <N> min · Switch to the <other>`

- [ ] **Step 2: Feeding-limit notification (light mode)**

Continue the same session past `maxTotalMinutes`. Verify:
- Small icon is the **stopwatch glyph**, tinted **Amber `#E65100`**
- Progress bar full (100%) and tinted amber on supported OEMs
- Body text: `Session has reached <N> minutes. Consider wrapping up.`

- [ ] **Step 3: Breastfeeding-active (persistent) notification (light mode)**

From the home screen, start a feeding session. Verify the ongoing notification:
- Small icon: **bottle, Pink `#C2185B`**
- Chronometer ticks live in the header
- Title: `🍼 Feeding session active`; body reflects current side
- The notification persists until session stop

- [ ] **Step 4: Sleep-active notification (light mode)**

Start a sleep (nap or night) session. Verify:
- Small icon: **crescent moon**, tinted **Blue `#1976D2`**
- Chronometer ticks live from the session `startTime`
- Body text: `<Night sleep|Nap> · started at <h:mm a>`

- [ ] **Step 5: Rich-toggle off regression**

Open app Settings → toggle `richNotificationsEnabled` OFF. Repeat steps 1, 2, 4 and verify:
- Small icon and accent color **still apply** (design-system identity is baseline, not rich)
- Progress bar and action buttons **disappear**

- [ ] **Step 6: System dark mode sweep**

Enable system-wide dark mode (Settings → Display → Dark theme). Repeat steps 1–4 and verify each small icon tint switches to its dark counterpart:
- Switch Sides / Breastfeeding Active: `#F48FB1` (PrimaryPinkDark)
- Feeding Limit: `#FFCC80` (WarningAmberDark)
- Sleep Active: `#90CAF9` (SecondaryBlueDark)

- [ ] **Step 7: Tick all six manual checkboxes in the plan**

If any step fails, stop and root-cause before continuing. Do not paper over a miss by editing the plan — visual bugs are why we're doing this.

- [ ] **Step 8: No commit needed**

Manual verification is documented in the spec's Acceptance Criteria (§9, items 8 and 9). No file change to commit.

---

## Task 7: Final self-review and PR

- [ ] **Step 1: Re-read the spec's Acceptance Criteria (§9)**

Open `docs/superpowers/specs/2026-04-23-notification-visual-identity-design.md` and walk through each bullet. For every `- [ ]`, confirm the corresponding code change or manual-verification step was completed in Tasks 1–6 of this plan. If anything is unchecked, do not open the PR — go back and finish.

- [ ] **Step 2: Run the full unit-test suite one more time**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, zero regressions.

- [ ] **Step 3: Confirm no other Kotlin file was touched**

Run: `git diff main --stat`
Expected: only `Color.kt`, `NotificationHelper.kt`, `NotificationHelperTest.kt`, `CLAUDE.md`, and three new `ic_notif_*.xml` drawables. Anything else means scope creep — revert or explain in the PR.

- [ ] **Step 4: Push and open a PR against `main`**

Branch the work off a branch named like `feat/notification-visual-identity` (follow CLAUDE.md branching model). PR title:

```
feat(ui): per-type notification small icon and accent color
```

PR body: reference the spec (`docs/superpowers/specs/2026-04-23-notification-visual-identity-design.md`), list the six acceptance-criteria bullets, and paste a before/after screenshot of one notification from light mode and dark mode.

---

## Self-Review Checklist

Before handing off for execution, I verified:

- **Spec coverage:** Every `- [ ]` in the spec §9 Acceptance Criteria maps to a step:
  - Amber raws + Warning semantic tokens → Task 1
  - Three drawables 24dp alpha-only single-path → Task 2
  - `showSwitchSide` Pink + bottle (light/dark) → Task 3 step 3 + Task 6 steps 1, 6
  - `showFeedingLimit` Amber + stopwatch → Task 3 step 4 + Task 6 steps 2, 6
  - `showBreastfeedingActive` Pink + bottle → Task 3 step 5 + Task 6 steps 3, 6
  - `showSleepActive` Blue + moon → Task 3 step 6 + Task 6 steps 4, 6
  - `NotificationHelperTest` 3 branches → Task 4
  - `richEnabled = false` regression → Task 6 step 5
  - Existing tests pass → Task 3 step 8 + Task 4 step 3 + Task 7 step 2
  - Manual checklist → Task 6
  - `CLAUDE.md` updates → Task 5

- **Placeholder scan:** No "TBD", "implement later", "handle edge cases", or "similar to above". Every code step contains the actual code; every command step contains the actual command and the expected output.

- **Type consistency:** `resolveAccent(Context, Color, Color): Int` signature is identical across Task 3 (implementation), Task 4 (test reflection lookup), and the spec §4.1. All six `applyDesignSystem(accent, icon)` call sites pass the correct (light, dark) pair per the §4.2 table — re-verified against the spec table when writing Tasks 3.3–3.6.
