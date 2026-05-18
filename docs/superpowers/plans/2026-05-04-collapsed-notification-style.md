# Collapsed Notification Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the default collapsed notification appearance with a custom layout that matches the design system — bold title, thin 2dp inline progress bar between title and subtitle, and muted subtitle text — for all four notification types.

**Architecture:** Each notification type that uses `richEnabled=true` gets a new `setCustomContentView()` call alongside the existing `setCustomBigContentView()`. Three new collapsed layout XMLs (feeding, warning, sleep) each reference their own themed progress drawable. The sleep notification adds a collapsed custom view while keeping the existing `BigTextStyle` for the expanded state.

**Tech Stack:** Android View XML (RemoteViews), NotificationCompat, existing color tokens in `res/values/colors.xml`

---

## Collapsed Notification Design Spec

From `docs/design-system/akachan-design-system/project/preview/notifications.html`:

```
[ System header: app icon | Akachan | ⏱ chronometer ]
  ┌──────────────────────────────────────────────────┐
  │  Title text (bold, 13sp)                         │
  │  ████████░░░░░░░░░░░  (2dp inline progress bar)  │
  │  Subtitle text (muted, 11.5sp)                   │
  └──────────────────────────────────────────────────┘
```

**Per-notification collapsed behavior:**

| Notification | Layout | Progress | Progress value |
|---|---|---|---|
| Feeding active | `notification_collapsed_feeding.xml` | Shown if maxTotalMinutes > 0, else GONE | elapsedSeconds / (maxTotalMinutes × 60) |
| Feeding limit reached | `notification_collapsed_warning.xml` | Always shown | 100% (progress == max) |
| Switch sides | `notification_collapsed_feeding.xml` | Always shown | 100% |
| Sleep active | `notification_collapsed_sleep.xml` | Always GONE | — |

**No background** on collapsed layouts — Android renders the system notification glass/tinted background.

---

## File Map

| File | Action |
|---|---|
| `res/layout/notification_collapsed_feeding.xml` | Create — collapsed layout, pink progress |
| `res/layout/notification_collapsed_warning.xml` | Create — collapsed layout, amber progress |
| `res/layout/notification_collapsed_sleep.xml` | Create — collapsed layout, blue progress (always GONE) |
| `res/drawable/notification_progress_sleep.xml` | Create — blue-themed progress bar drawable |
| `res/values/colors.xml` | Modify — add `notification_sleep_accent`, `notification_sleep_track` |
| `res/values-night/colors.xml` | Modify — add sleep dark color tokens |
| `app/src/main/java/com/babytracker/util/NotificationHelper.kt` | Modify — add `buildCollapsedView()`, wire `setCustomContentView()` in all `show*` |
| `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt` | Modify — add tests for collapsed view wiring |

---

## Task 1: Add sleep notification color tokens

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`

- [ ] **Step 1: Add sleep colors to light values**

Open `app/src/main/res/values/colors.xml` and add after the warning block:

```xml
<color name="notification_sleep_accent">#1976D2</color>
<color name="notification_sleep_track">#B3E5FC</color>
```

Result (`res/values/colors.xml` complete):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFFFFF</color>
    <color name="notification_surface">#FFFDE7</color>
    <color name="notification_on_surface">#1A1A1A</color>
    <color name="notification_on_surface_variant">#757575</color>
    <color name="notification_feeding_accent">#C2185B</color>
    <color name="notification_feeding_track">#F8BBD0</color>
    <color name="notification_warning_accent">#E65100</color>
    <color name="notification_warning_track">#FFE0B2</color>
    <color name="notification_sleep_accent">#1976D2</color>
    <color name="notification_sleep_track">#B3E5FC</color>
</resources>
```

- [ ] **Step 2: Add sleep colors to dark values**

Open `app/src/main/res/values-night/colors.xml` and add:

```xml
<color name="notification_sleep_accent">#90CAF9</color>
<color name="notification_sleep_track">#0D2440</color>
```

Result (`res/values-night/colors.xml` complete):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="notification_surface">#1C1B1F</color>
    <color name="notification_on_surface">#E6E1E5</color>
    <color name="notification_on_surface_variant">#BDB8C0</color>
    <color name="notification_feeding_accent">#F48FB1</color>
    <color name="notification_feeding_track">#4A2838</color>
    <color name="notification_warning_accent">#FFCC80</color>
    <color name="notification_warning_track">#4A2E14</color>
    <color name="notification_sleep_accent">#90CAF9</color>
    <color name="notification_sleep_track">#0D2440</color>
</resources>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml
git commit -m "feat(ui): add sleep notification color tokens for collapsed notification style"
```

---

## Task 2: Create sleep progress drawable

**Files:**
- Create: `app/src/main/res/drawable/notification_progress_sleep.xml`

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `sleep progress drawable file exists`() {
    val file = listOf(
        java.io.File("src/main/res/drawable/notification_progress_sleep.xml"),
        java.io.File("app/src/main/res/drawable/notification_progress_sleep.xml")
    ).firstOrNull { it.exists() }
    assertTrue(file != null, "notification_progress_sleep.xml must exist in drawable/")
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.sleep progress drawable file exists"
```

Expected: FAIL — file does not exist yet

- [ ] **Step 3: Create the drawable**

Create `app/src/main/res/drawable/notification_progress_sleep.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape>
            <solid android:color="@color/notification_sleep_track" />
            <corners android:radius="3dp" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape>
                <solid android:color="@color/notification_sleep_accent" />
                <corners android:radius="3dp" />
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.sleep progress drawable file exists"
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/notification_progress_sleep.xml app/src/test/java/com/babytracker/util/NotificationHelperTest.kt
git commit -m "feat(ui): add sleep notification progress bar drawable"
```

---

## Task 3: Create collapsed notification layout XMLs

**Files:**
- Create: `app/src/main/res/layout/notification_collapsed_feeding.xml`
- Create: `app/src/main/res/layout/notification_collapsed_warning.xml`
- Create: `app/src/main/res/layout/notification_collapsed_sleep.xml`

All three share identical structure — only the `progressDrawable` attribute differs. No `android:background` is set on any of them (system renders the notification background).

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `all three collapsed notification layout files exist`() {
    fun layoutExists(name: String): Boolean =
        listOf(
            java.io.File("src/main/res/layout/$name"),
            java.io.File("app/src/main/res/layout/$name")
        ).any { it.exists() }

    assertTrue(layoutExists("notification_collapsed_feeding.xml"), "feeding collapsed layout missing")
    assertTrue(layoutExists("notification_collapsed_warning.xml"), "warning collapsed layout missing")
    assertTrue(layoutExists("notification_collapsed_sleep.xml"), "sleep collapsed layout missing")
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.all three collapsed notification layout files exist"
```

Expected: FAIL

- [ ] **Step 3: Create feeding collapsed layout**

Create `app/src/main/res/layout/notification_collapsed_feeding.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="2dp"
    android:paddingEnd="2dp"
    android:paddingBottom="2dp">

    <TextView
        android:id="@+id/notification_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface"
        android:textSize="13sp"
        android:textStyle="bold" />

    <ProgressBar
        android:id="@+id/notification_progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="2dp"
        android:layout_marginTop="4dp"
        android:indeterminate="false"
        android:progressDrawable="@drawable/notification_progress_breastfeeding" />

    <TextView
        android:id="@+id/notification_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface_variant"
        android:textSize="11.5sp" />
</LinearLayout>
```

- [ ] **Step 4: Create warning collapsed layout**

Create `app/src/main/res/layout/notification_collapsed_warning.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="2dp"
    android:paddingEnd="2dp"
    android:paddingBottom="2dp">

    <TextView
        android:id="@+id/notification_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface"
        android:textSize="13sp"
        android:textStyle="bold" />

    <ProgressBar
        android:id="@+id/notification_progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="2dp"
        android:layout_marginTop="4dp"
        android:indeterminate="false"
        android:progressDrawable="@drawable/notification_progress_warning" />

    <TextView
        android:id="@+id/notification_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface_variant"
        android:textSize="11.5sp" />
</LinearLayout>
```

- [ ] **Step 5: Create sleep collapsed layout**

The sleep notification has no meaningful max, so `notification_progress` is always GONE. The drawable reference is still required for XML validity.

Create `app/src/main/res/layout/notification_collapsed_sleep.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="2dp"
    android:paddingEnd="2dp"
    android:paddingBottom="2dp">

    <TextView
        android:id="@+id/notification_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface"
        android:textSize="13sp"
        android:textStyle="bold" />

    <ProgressBar
        android:id="@+id/notification_progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="2dp"
        android:layout_marginTop="4dp"
        android:indeterminate="false"
        android:progressDrawable="@drawable/notification_progress_sleep"
        android:visibility="gone" />

    <TextView
        android:id="@+id/notification_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:maxLines="1"
        android:textColor="@color/notification_on_surface_variant"
        android:textSize="11.5sp" />
</LinearLayout>
```

- [ ] **Step 6: Run test to verify all three layouts exist**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.all three collapsed notification layout files exist"
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/notification_collapsed_feeding.xml \
        app/src/main/res/layout/notification_collapsed_warning.xml \
        app/src/main/res/layout/notification_collapsed_sleep.xml \
        app/src/test/java/com/babytracker/util/NotificationHelperTest.kt
git commit -m "feat(ui): add collapsed notification layout files for feeding, warning, and sleep"
```

---

## Task 4: Add `buildCollapsedView()` to NotificationHelper

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `buildCollapsedView function exists in NotificationHelper`() {
    val source = notificationHelperSource()
    assertTrue(
        source.contains("fun buildCollapsedView("),
        "buildCollapsedView must be defined in NotificationHelper"
    )
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.buildCollapsedView function exists in NotificationHelper"
```

Expected: FAIL — `buildCollapsedView` is not yet present

- [ ] **Step 3: Add `buildCollapsedView()` to `NotificationHelper`**

In `NotificationHelper.kt`, add this private function immediately after `buildProgressBigView()` (after line 100):

```kotlin
private fun buildCollapsedView(
    context: Context,
    layoutRes: Int,
    title: String,
    body: String,
    progress: Int,
    maxProgress: Int,
    showProgress: Boolean
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    val safeMax = maxProgress.coerceAtLeast(1)
    setTextViewText(R.id.notification_title, title)
    setTextViewText(R.id.notification_body, body)
    setViewVisibility(R.id.notification_progress, if (showProgress) View.VISIBLE else View.GONE)
    if (showProgress) {
        setProgressBar(
            R.id.notification_progress,
            safeMax,
            progress.coerceIn(0, safeMax),
            false
        )
    }
}
```

- [ ] **Step 4: Run full test suite to confirm no breakage**

```bash
./gradlew :app:test
```

Expected: All existing tests PASS including the new `buildCollapsedView function exists` test

---

## Task 5: Wire collapsed view into `showBreastfeedingActive`

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

The `applyBreastfeedingActiveRichContent` extension function builds the expanded view. We add the collapsed view alongside it.

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `showBreastfeedingActive rich path wires setCustomContentView`() {
    val source = notificationHelperSource()
    val body = Regex("fun applyBreastfeedingActiveRichContent[\\s\\S]*?private fun breastfeedingActiveProgress")
        .find(source)?.value ?: error("applyBreastfeedingActiveRichContent body not found")
    assertTrue(body.contains("setCustomContentView("),
        "applyBreastfeedingActiveRichContent must call setCustomContentView for collapsed layout")
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.showBreastfeedingActive rich path wires setCustomContentView"
```

Expected: FAIL

- [ ] **Step 3: Update `applyBreastfeedingActiveRichContent`**

In `NotificationHelper.kt`, find `applyBreastfeedingActiveRichContent` (around line 303). Add `setCustomContentView(...)` after `setCustomBigContentView(...)`:

```kotlin
private fun NotificationCompat.Builder.applyBreastfeedingActiveRichContent(content: ActiveNotificationContent) {
    val isPaused = content.pausedAtEpochMs != null
    val elapsedSeconds = content.pausedAtEpochMs
        ?.let { pausedElapsedSeconds(content.sessionStartEpochMs, content.pausedDurationMs, it) }
        ?: activeElapsedSeconds(content.sessionStartEpochMs, content.pausedDurationMs)
    val progress = breastfeedingActiveProgress(elapsedSeconds, content.maxTotalMinutes)

    setUsesChronometer(!isPaused)
        .setShowWhen(!isPaused)
        .setWhen(content.sessionStartEpochMs + content.pausedDurationMs)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(
            buildCollapsedView(
                context = content.context,
                layoutRes = R.layout.notification_collapsed_feeding,
                title = content.title,
                body = content.body,
                progress = progress.current,
                maxProgress = progress.max,
                showProgress = progress.isEnabled
            )
        )
        .setCustomBigContentView(
            buildProgressBigView(
                context = content.context,
                layoutRes = R.layout.notification_breastfeeding_progress,
                title = content.title,
                body = content.body,
                progress = progress.current,
                maxProgress = progress.max,
                progressText = progress.label,
                chronometerBaseElapsedMs = SystemClock.elapsedRealtime() - (elapsedSeconds * MILLIS_PER_SECOND),
                chronometerRunning = !isPaused,
                showProgress = progress.isEnabled
            )
        )
        .addAction(0, pauseResumeLabel(isPaused), pauseResumePendingIntent(content.context, content.sessionId, isPaused))
        .addAction(
            0,
            "Stop Session",
            breastfeedingActionPi(
                content.context,
                content.sessionId,
                BreastfeedingActionReceiver.ACTION_STOP,
                RC_STOP_BF_ACTIVE
            )
        )

    if (progress.isEnabled && !isPaused) {
        scheduleBreastfeedingActiveRefresh(content.context, content.sessionId)
    }
}
```

- [ ] **Step 4: Run tests — new test must pass, existing tests must not regress**

```bash
./gradlew :app:test
```

Expected: All tests PASS

---

## Task 6: Wire collapsed view into `showFeedingLimit`

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `showFeedingLimit rich path wires setCustomContentView`() {
    val source = notificationHelperSource()
    val body = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
        .find(source)?.value ?: error("showFeedingLimit body not found")
    assertTrue(body.contains("setCustomContentView("),
        "showFeedingLimit must call setCustomContentView for collapsed layout")
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.showFeedingLimit rich path wires setCustomContentView"
```

Expected: FAIL

- [ ] **Step 3: Update the rich branch of `showFeedingLimit`**

In `NotificationHelper.kt`, find the `if (richEnabled)` block inside `showFeedingLimit` (around line 229). Replace with:

```kotlin
if (richEnabled) {
    val progressText = limitProgressText(maxTotalMinutes)
    builder
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(
            buildCollapsedView(
                context = context,
                layoutRes = R.layout.notification_collapsed_warning,
                title = title,
                body = body,
                progress = maxTotalMinutes,
                maxProgress = maxTotalMinutes,
                showProgress = true
            )
        )
        .setCustomBigContentView(
            buildProgressBigView(
                context = context,
                layoutRes = R.layout.notification_warning_progress,
                title = title,
                body = body,
                progress = maxTotalMinutes,
                maxProgress = maxTotalMinutes,
                progressText = progressText
            )
        )
        .addAction(0, "Stop Session", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_SESSION))
        .addAction(0, "Keep Going", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_KEEP_GOING, RC_KEEP_GOING))
} else {
    builder.setContentText(body)
}
```

- [ ] **Step 4: Run tests — new test must pass**

```bash
./gradlew :app:test
```

Expected: All tests PASS

---

## Task 7: Wire collapsed view into `showSwitchSide`

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

Switch sides collapsed: progress bar shown at 100% (the side timer expired). Note: the EXISTING test `switch side rich notification hides custom progress row` checks the EXPANDED view — that test must still pass (it checks `showProgress = false` in `showSwitchSide`, which is for the expanded view only).

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `showSwitchSide rich path wires setCustomContentView`() {
    val source = notificationHelperSource()
    val body = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
        .find(source)?.value ?: error("showSwitchSide body not found")
    assertTrue(body.contains("setCustomContentView("),
        "showSwitchSide must call setCustomContentView for collapsed layout")
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.showSwitchSide rich path wires setCustomContentView"
```

Expected: FAIL

- [ ] **Step 3: Update the rich branch of `showSwitchSide`**

In `NotificationHelper.kt`, find the `if (richEnabled)` block inside `showSwitchSide` (around line 185). Replace with:

```kotlin
if (richEnabled) {
    builder
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(
            buildCollapsedView(
                context = context,
                layoutRes = R.layout.notification_collapsed_feeding,
                title = title,
                body = body,
                progress = 1,
                maxProgress = 1,
                showProgress = true
            )
        )
        .setCustomBigContentView(
            buildProgressBigView(
                context = context,
                layoutRes = R.layout.notification_breastfeeding_progress,
                title = title,
                body = body,
                progress = 0,
                maxProgress = 1,
                progressText = "",
                showProgress = false
            )
        )
        .addAction(0, "Switch Now", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_SWITCH, RC_SWITCH_NOW))
        .addAction(0, "Dismiss", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_DISMISS, RC_BF_DISMISS))
} else {
    builder.setContentText(body)
}
```

- [ ] **Step 4: Run tests — confirm existing switch-side test still passes and new test passes**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.switch side rich notification hides custom progress row"
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.showSwitchSide rich path wires setCustomContentView"
```

Expected: Both PASS

- [ ] **Step 5: Run full test suite**

```bash
./gradlew :app:test
```

Expected: All tests PASS

---

## Task 8: Wire collapsed view into `showSleepActive`

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/test/java/com/babytracker/util/NotificationHelperTest.kt`

Sleep keeps `BigTextStyle` for expanded — adding `setCustomContentView()` alongside it controls the collapsed state. No progress bar is shown (GONE in `notification_collapsed_sleep.xml`).

- [ ] **Step 1: Write the failing test**

In `NotificationHelperTest.kt`, add:

```kotlin
@Test
fun `showSleepActive rich path wires setCustomContentView`() {
    val source = notificationHelperSource()
    val body = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
        .find(source)?.value ?: error("showSleepActive body not found")
    assertTrue(body.contains("setCustomContentView("),
        "showSleepActive must call setCustomContentView for collapsed layout")
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.babytracker.util.NotificationHelperTest.showSleepActive rich path wires setCustomContentView"
```

Expected: FAIL

- [ ] **Step 3: Update the rich branch of `showSleepActive`**

In `NotificationHelper.kt`, find the `if (richEnabled)` block inside `showSleepActive` (around line 408). Replace with:

```kotlin
if (richEnabled) {
    builder
        .setUsesChronometer(true)
        .setWhen(startTimeEpochMs)
        .setCustomContentView(
            buildCollapsedView(
                context = context,
                layoutRes = R.layout.notification_collapsed_sleep,
                title = title,
                body = body,
                progress = 0,
                maxProgress = 1,
                showProgress = false
            )
        )
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .addAction(0, "Stop Sleep", sleepActionPi(context, sessionId, SleepActionReceiver.ACTION_STOP, RC_STOP_SLEEP))
} else {
    builder.setContentText(body)
}
```

- [ ] **Step 4: Run full test suite — all four per-notification tests must now pass**

```bash
./gradlew :app:test
```

Expected: All tests PASS — all four `*rich path wires setCustomContentView` tests pass

---

## Task 9: Format, lint, and final commit

**Files:**
- Modified: `NotificationHelper.kt` and all XML files

- [ ] **Step 1: Run ktlint formatter**

```bash
./gradlew ktlintFormat
```

- [ ] **Step 2: Run detekt**

```bash
./gradlew detekt
```

If detekt reports violations, fix each one with its own `fix(detekt): fix <RuleName> violations` commit. Never use `@Suppress`.

- [ ] **Step 3: Run full test suite one final time**

```bash
./gradlew :app:test
```

Expected: All tests PASS

- [ ] **Step 4: Commit all remaining changes**

```bash
git add app/src/main/java/com/babytracker/util/NotificationHelper.kt \
        app/src/test/java/com/babytracker/util/NotificationHelperTest.kt
git commit -m "feat(ui): implement collapsed notification style with inline progress bar

Wire custom collapsed layout (setCustomContentView) for all four
notification types — feeding active, feeding limit, switch sides, and
sleep active. Each collapsed view shows bold title, thin 2dp progress
bar, and muted subtitle text in line with the design system spec."
```

---

## Acceptance Criteria (verify after full implementation)

- [ ] Collapsed feeding active — custom collapsed view with progress bar (shown if max > 0, GONE otherwise)
- [ ] Collapsed feeding limit — amber progress bar shown at 100%
- [ ] Collapsed switch sides — pink progress bar shown at 100%
- [ ] Collapsed sleep — no progress bar (GONE), title + subtitle visible
- [ ] Sleep dark color tokens present in both `values/colors.xml` and `values-night/colors.xml`
- [ ] Sleep progress drawable exists at `res/drawable/notification_progress_sleep.xml`
- [ ] All four `*rich path wires setCustomContentView` tests pass
- [ ] `./gradlew ktlintCheck` reports no violations
- [ ] `./gradlew detekt` reports no violations

### No placeholder scan
- No TBD, TODO, or "add error handling" patterns found.

### Type consistency
- `buildCollapsedView` parameters (`layoutRes: Int`, `title: String`, `body: String`, `progress: Int`, `maxProgress: Int`, `showProgress: Boolean`) match call sites in Tasks 5–8.
- View IDs (`R.id.notification_title`, `R.id.notification_body`, `R.id.notification_progress`) are defined in all three collapsed XMLs.
