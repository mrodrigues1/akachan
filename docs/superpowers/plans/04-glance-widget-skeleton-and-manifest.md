# GlanceAppWidget Skeleton + Manifest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-51](https://linear.app/akachan/issue/AKA-51/glance-widget-skeleton-manifest-registration)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Architecture section)
**Suggested branch:** `feat/glance-widget-skeleton`
**Depends on:** Plan 1 (Glance dependencies + `BabyWidgetColors`)
**Blocks:** Plan 5 (content composables), Plan 6 (tap routing), Plan 7 (sync manager), Plan 8 (refresh worker)

**Goal:** Stand up the Glance widget infrastructure end-to-end so that, **in debug builds only**, placing the widget on the home screen renders a static placeholder ("Baby Tracker") sourced from `WidgetData.EMPTY`. No live data, no taps, no refresh worker — those land in subsequent plans. This plan is the smallest possible vertically-integrated step.

> **Visibility gate:** The widget receiver is registered in `app/src/debug/AndroidManifest.xml`, NOT the main manifest. A skeleton widget that never updates and renders no real data must not ship to release users. Plan 5 (content composables) promotes the receiver to `app/src/main/AndroidManifest.xml` once real data + meaningful UI exist; Plan 8 (refresh worker) ensures it stays current after promotion.

**Architecture:**
- `BabyWidget` extends `GlanceAppWidget`, declares `SizeMode.Responsive` with two sizes (Small 110×40, Medium 180×110 dp per spec), wraps content in `GlanceTheme(colors = BabyWidgetColors)`, and renders `WidgetData.EMPTY` via a single `PlaceholderContent` composable.
- `BabyWidgetReceiver` extends `GlanceAppWidgetReceiver`; assigns `BabyWidget()` to `glanceAppWidget`. Registered in `AndroidManifest.xml` with the `APPWIDGET_UPDATE` intent filter + `appwidget-provider` meta-data.
- `WidgetEntryPoint` (Hilt `@EntryPoint`, `@InstallIn(SingletonComponent::class)`) exposes `BreastfeedingRepository` and `SleepRepository`. Not consumed yet — declared now so Plan 5 can wire it without touching this file again.
- `res/xml/baby_widget_info.xml` declares minWidth/minHeight, target cells, `widgetCategory="home_screen"`, `resizeMode="horizontal|vertical"`, `previewLayout` (preview-only `@layout/widget_preview`), `initialLayout=@layout/glance_default_loading_layout` (provided by glance-appwidget).

**Tech Stack:** Jetpack Glance 1.1.x (`androidx.glance.appwidget.GlanceAppWidget` / `GlanceAppWidgetReceiver` / `provideContent` / `SizeMode.Responsive`), Hilt EntryPoint, Android `AppWidgetProvider` manifest registration.

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/BabyWidget.kt`
- Create: `app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt`
- Create: `app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt`
- Create: `app/src/main/res/xml/baby_widget_info.xml`
- Create: `app/src/main/res/layout/widget_preview.xml`
- Create: `app/src/main/res/drawable/ic_widget_preview.xml` (lightweight preview icon — reuse `ic_notif_breastfeeding` vector path if convenient)
- Create: `app/src/debug/AndroidManifest.xml` (debug-only receiver registration — gate Plan 4's skeleton)
- Modify: `app/src/test/java/com/babytracker/architecture/` (if a Konsist rule requires the new package to have at least one production scope tagged test — only touch if `./gradlew :app:testDebugUnitTest` fails on the new package; otherwise leave alone)

---

### Task 1: Create `WidgetEntryPoint`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt`

The `EntryPoint` is declared now so later plans (5, 7) can read repositories from inside `provideGlance` without re-touching DI wiring.

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Create the Glance widget with placeholder content

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/BabyWidget.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.babytracker.widget.theme.BabyWidgetColors

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                PlaceholderContent()
            }
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 40.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 110.dp)
    }
}

@Composable
private fun PlaceholderContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Baby Tracker",
            style = TextStyle(color = ColorProvider(GlanceTheme.colors.onSurface.getColor(LocalContext.current))),
        )
    }
}
```

> **Note:** Do **not** read `LocalContext.current` for text color. Use `GlanceTheme.colors.onSurface` directly — Glance `Text`'s `TextStyle.color` accepts a `ColorProvider`, and `GlanceTheme.colors.onSurface` already is one. Replace the `Text(...)` style with:
>
> ```kotlin
> style = TextStyle(color = GlanceTheme.colors.onSurface),
> ```
>
> Drop the unused `LocalContext` / `ColorProvider` imports. The note above is intentionally left in the plan as a self-correction step — if the worker copies blindly, the compile check in Step 2 will fail and the worker will fix it before moving on.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL after applying the self-correction in the note (drop `LocalContext` usage, use `GlanceTheme.colors.onSurface` as the `ColorProvider`).

---

### Task 3: Create `BabyWidgetReceiver`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BabyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BabyWidget()
}
```

> **Hilt note:** The receiver does not yet inject anything (no `@AndroidEntryPoint`). Plan 7 (`WidgetSyncManager`) and Plan 8 (`WidgetRefreshWorker`) will add `@AndroidEntryPoint` only if they introduce injected fields on the receiver; until then keep it plain to minimize surface area.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Create `baby_widget_info.xml`

**Files:**
- Create: `app/src/main/res/xml/baby_widget_info.xml`

- [ ] **Step 1: Write the file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="110dp"
    android:minHeight="40dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:previewLayout="@layout/widget_preview"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:description="@string/widget_description" />
```

- `glance_default_loading_layout` is shipped by `androidx.glance:glance-appwidget` and is the canonical loading layout for Glance widgets.
- `updatePeriodMillis="0"` disables the OS auto-update; refresh is driven explicitly by Plan 7 (event push) + Plan 8 (15-min WorkManager safety net).

- [ ] **Step 2: Add the description string**

Modify: `app/src/main/res/values/strings.xml`

Append (or insert near other widget-related strings if any exist):

```xml
    <string name="widget_description">Last feed and current sleep status</string>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:processDebugResources -q`
Expected: BUILD SUCCESSFUL.

---

### Task 5: Create the static preview layout + drawable

The widget picker shows `previewLayout`; Glance-rendered widgets cannot supply a live preview, so we ship a static XML layout that mimics the empty state.

**Files:**
- Create: `app/src/main/res/layout/widget_preview.xml`
- Create: `app/src/main/res/drawable/ic_widget_preview.xml` (only if no suitable existing drawable — `ic_notif_breastfeeding` is acceptable to reuse; in that case skip this drawable and reference the existing one)

- [ ] **Step 1: Write `widget_preview.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="8dp">

    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:contentDescription="@null"
        android:src="@drawable/ic_notif_breastfeeding" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:text="@string/widget_preview_label"
        android:textColor="?android:attr/textColorPrimary"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 2: Add the preview label string**

Modify: `app/src/main/res/values/strings.xml`

```xml
    <string name="widget_preview_label">Last feed · just now</string>
```

- [ ] **Step 3: Verify**

Run: `./gradlew :app:processDebugResources -q`
Expected: BUILD SUCCESSFUL.

---

### Task 6: Register the receiver in the **debug-only** manifest

The receiver registration lives in `app/src/debug/AndroidManifest.xml` so release builds do **not** ship a non-functional widget to users. Plan 5 promotes the same `<receiver>` block to `app/src/main/AndroidManifest.xml` once the widget renders real data.

**Files:**
- Create: `app/src/debug/AndroidManifest.xml`

- [ ] **Step 1: Write the debug manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <receiver
            android:name="com.babytracker.widget.BabyWidgetReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/baby_widget_info" />
        </receiver>
    </application>

</manifest>
```

Notes:
- The fully-qualified class name avoids `package`-attribute issues — `manifest` has no `package=` in this file (AGP namespace handles it).
- `android:exported="false"` is required for `targetSdk >= 31`.
- This file is auto-merged with the main manifest only for the `debug` variant.

- [ ] **Step 2: Verify debug-variant manifest merging includes the receiver**

Run: `./gradlew :app:processDebugMainManifest -q`
Then:

```
grep -A2 "BabyWidgetReceiver" app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
```
Expected: contains the receiver block.

- [ ] **Step 3: Verify release-variant manifest merging excludes the receiver**

Run: `./gradlew :app:processReleaseMainManifest -q`
Then:

```
grep -A2 "BabyWidgetReceiver" app/build/intermediates/merged_manifests/release/AndroidManifest.xml || echo "NOT PRESENT (expected)"
```
Expected: prints `NOT PRESENT (expected)`. If the receiver appears in the release manifest, the debug source set is misconfigured — stop and investigate before continuing.

---

### Task 7: Smoke-build the APK and confirm receiver is present

- [ ] **Step 1: Assemble**

Run:
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify receiver in the merged manifest**

Run:
```
grep -A2 "BabyWidgetReceiver" app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
```
Expected: output contains the receiver with action `android.appwidget.action.APPWIDGET_UPDATE` and meta-data `android.appwidget.provider`.

- [ ] **Step 3: (Manual, optional) Install and place widget**

If a connected device is available:

```
./gradlew :app:installDebug
```

On the device, long-press home → Widgets → Baby Tracker → drag. Confirm the widget appears with the "Baby Tracker" placeholder text and that the picker shows the preview from Task 5.

If no device is available, **stop and note that manual verification is deferred to Plan 5**, when the visual content lands and is worth verifying together.

---

### Task 8: Lint, static analysis, tests

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on both.

If a Konsist architecture test fails because the new `widget/` package has no test under `productionScope`, add a single `@Tag("architecture")` test in `app/src/test/java/com/babytracker/architecture/WidgetPackageArchitectureTest.kt` that asserts every class in `com.babytracker.widget` is annotated as the rules require (or trivially asserts the package exists). Reuse `productionScope` from `KonsistScopes.kt`. Do **not** disable the rule.

---

### Task 9: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/widget/BabyWidget.kt \
        app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt \
        app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt \
        app/src/main/res/xml/baby_widget_info.xml \
        app/src/main/res/layout/widget_preview.xml \
        app/src/main/res/values/strings.xml \
        app/src/debug/AndroidManifest.xml

# If Task 8 required an architecture test:
git add app/src/test/java/com/babytracker/architecture/WidgetPackageArchitectureTest.kt

git commit -m "feat(widget): add Glance widget skeleton with manifest registration"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 7-8 files changed; no unrelated changes.

---

## Acceptance Criteria

- `BabyWidget`, `BabyWidgetReceiver`, and `WidgetEntryPoint` exist in `com.babytracker.widget`.
- `baby_widget_info.xml` declares `widgetCategory="home_screen"`, `resizeMode="horizontal|vertical"`, `updatePeriodMillis="0"`, and references a static preview layout.
- Receiver is declared **only** in `app/src/debug/AndroidManifest.xml` with `APPWIDGET_UPDATE` action + provider meta-data. Release builds do not expose the skeleton.
- `./gradlew :app:assembleDebug` succeeds; debug merged manifest contains the receiver; release merged manifest does not.
- The widget shows a "Baby Tracker" placeholder when placed on the home screen (manual; optional if no device available).
- `./gradlew ktlintFormat detekt :app:testDebugUnitTest` all succeed.
- No code outside `widget/`, `res/xml/`, `res/layout/`, `res/values/strings.xml`, or `AndroidManifest.xml` is modified.
- One commit on `feat/plans-home-screen-widget`.
