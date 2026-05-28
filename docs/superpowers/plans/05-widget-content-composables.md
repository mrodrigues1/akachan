# Widget Small + Medium Content Composables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-52](https://linear.app/akachan/issue/AKA-52/widget-small-medium-content-composables-and-release-promotion)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Content & Layout section)
**Suggested branch:** `feat/widget-content-composables`
**Depends on:** Plan 3 (`WidgetData`), Plan 4 (`BabyWidget` skeleton + `WidgetEntryPoint`)
**Blocks:** Plan 6 (tap routing modifies these composables); release-readiness is gated by Plan 8 (refresh promotion)

**Goal:** Replace `BabyWidget`'s placeholder with real, theme-aware content. The Small (2x1) layout renders the last feed; the Medium (2x2) layout adds the sleep row + baby name header. Empty states are explicit ("No feeds yet" / "No sleep logged"). The widget now reads `WidgetData` via `WidgetEntryPoint` repositories inside `provideGlance`, mapped through `toWidgetData(...)`, with a resilient fallback to `WidgetData.EMPTY` if any repository read fails.

> **Release promotion deferred:** The receiver stays in `app/src/debug/AndroidManifest.xml`. Without an event-push or periodic refresh, release users would see frozen elapsed text and stale state after the first render. Plan 8 (`WidgetRefreshWorker`) is the plan that finally promotes the receiver to `app/src/main/AndroidManifest.xml`, after both Plan 7 (`WidgetSyncManager` event push) and Plan 8 (periodic safety net) are wired and verified.

**Architecture:**
- `WidgetContent.kt` owns `SmallContent`, `MediumContent`, shared `FeedRow`, `SleepRow`, and `EmptyRow` composables. All composables take `WidgetData` (no Flows, no repositories) so they remain trivially renderable.
- `BabyWidget.provideGlance` uses `LocalSize.current` (Glance) to pick `SmallContent` vs `MediumContent` based on the active `SizeMode.Responsive` declared size, and reads data via `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)` once per Glance recomposition.
- Baby name is sourced from `BabyRepository.getBabyProfile().first()`. `Baby.name` is non-null on the model but may be blank during onboarding; `toWidgetData` already falls back to "Baby".
- Elapsed text reuses `Duration.formatElapsedAgo()` from `util/DateTimeExt.kt` (e.g. "2h 10m ago", "Just now"). Sleeping duration reuses `Duration.formatDuration()` ("1h 5m").
- Feed side icon: reuse `ic_notif_breastfeeding`. Sleep icon: reuse `ic_notif_sleep`. No new drawables are added.

**Tech Stack:** Jetpack Glance 1.1.x (`Column`, `Row`, `Text`, `Image`, `LocalSize`, `EntryPointAccessors`), Kotlin Coroutines `Flow.first()`, existing `DateTimeExt` formatters.

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/WidgetContent.kt`
- Modify: `app/src/main/java/com/babytracker/widget/BabyWidget.kt` (replace `PlaceholderContent` call site with real data wiring + size dispatch)
- Create: `app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt` (resilient repository read; fallback to `WidgetData.EMPTY` on any failure)
- Create: `app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt`
- Create: `app/src/test/java/com/babytracker/widget/WidgetContentHelpersTest.kt`

---

### Task 1: Write `WidgetContent.kt`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetContent.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import java.time.Duration
import java.time.Instant

@Composable
fun SmallContent(data: WidgetData, now: Instant) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedRowContent(data = data, now = now)
    }
}

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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            FeedRowContent(data = data, now = now)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            SleepRowContent(data = data, now = now)
        }
    }
}

@Composable
private fun FeedRowContent(data: WidgetData, now: Instant) {
    val side = data.lastFeedSide
    val start = data.lastFeedStart
    if (side == null || start == null) {
        EmptyText("No feeds yet")
        return
    }
    Image(
        provider = ImageProvider(R.drawable.ic_notif_breastfeeding),
        contentDescription = null,
        modifier = GlanceModifier.size(20.dp),
    )
    Spacer(modifier = GlanceModifier.width(8.dp))
    Text(
        text = "${side.label()} · ${Duration.between(start, now).formatElapsedAgo()}",
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
    )
}

@Composable
private fun SleepRowContent(data: WidgetData, now: Instant) {
    when (data.sleepState) {
        SleepState.NONE -> EmptyText("No sleep logged")
        SleepState.SLEEPING -> {
            Image(
                provider = ImageProvider(R.drawable.ic_notif_sleep),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            val since = data.sleepSince ?: return
            Text(
                text = "Sleeping · ${Duration.between(since, now).formatDuration()}",
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 14.sp),
            )
        }
        SleepState.AWAKE -> {
            Image(
                provider = ImageProvider(R.drawable.ic_notif_sleep),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            val since = data.sleepSince ?: return
            Text(
                text = "Awake since ${Duration.between(since, now).formatElapsedAgo()}",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 13.sp,
        ),
    )
}

private fun BreastSide.label(): String = when (this) {
    BreastSide.LEFT -> "Left"
    BreastSide.RIGHT -> "Right"
}
```

Notes for the worker:
- `now: Instant` is passed in (not captured inside the composable) so tests can stub it.
- `FeedRowContent` and `SleepRowContent` are private to the file. `SmallContent` and `MediumContent` are the only public composables and both take the same `(WidgetData, Instant)` signature for symmetry.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Add `WidgetDataLoader` (resilient repository read)

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt`

The loader is a `suspend` top-level function. Any repository throwing — DataStore IOException, Room SQLite error, baby profile not yet seeded — must not blank the widget. Instead, the loader catches, logs at `Log.w`, and returns `WidgetData.EMPTY` (preserving the empty-state UX). This is the only place repository failures are silenced; the rest of the app keeps surfacing them.

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.data.toWidgetData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "BabyWidget"

suspend fun loadWidgetData(context: Context): WidgetData = try {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    val baby = entryPoint.babyRepository().getBabyProfile().first()
    val lastFeed = entryPoint.breastfeedingRepository().getLastSession()
    val latestSleep = entryPoint.sleepRepository().getLatestRecord()

    toWidgetData(
        babyName = baby?.name,
        lastFeed = lastFeed,
        latestSleep = latestSleep,
    )
} catch (ce: CancellationException) {
    throw ce
} catch (t: Throwable) {
    Log.w(TAG, "Widget data load failed; rendering EMPTY", t)
    WidgetData.EMPTY
}
```

Re-throw on `CancellationException` so coroutine cancellation is not swallowed.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Test `WidgetDataLoader` failure paths

**Files:**
- Create: `app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt`

The loader uses `EntryPointAccessors`, which is hard to mock cleanly. Refactor the file before testing: split the function in two — a `loadWidgetData(context)` shim that resolves the entry point, and an `internal suspend fun loadWidgetData(baby, feed, sleep)` overload that takes the three repositories directly. Test the inner overload.

- [ ] **Step 1: Update `WidgetDataLoader.kt`**

Replace the file with:

```kotlin
package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.data.toWidgetData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "BabyWidget"

suspend fun loadWidgetData(context: Context): WidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    return loadWidgetData(
        baby = entryPoint.babyRepository(),
        feed = entryPoint.breastfeedingRepository(),
        sleep = entryPoint.sleepRepository(),
    )
}

internal suspend fun loadWidgetData(
    baby: BabyRepository,
    feed: BreastfeedingRepository,
    sleep: SleepRepository,
): WidgetData = try {
    val babyProfile = baby.getBabyProfile().first()
    val lastFeed = feed.getLastSession()
    val latestSleep = sleep.getLatestRecord()

    toWidgetData(
        babyName = babyProfile?.name,
        lastFeed = lastFeed,
        latestSleep = latestSleep,
    )
} catch (ce: CancellationException) {
    throw ce
} catch (t: Throwable) {
    Log.w(TAG, "Widget data load failed; rendering EMPTY", t)
    WidgetData.EMPTY
}
```

- [ ] **Step 2: Write the test**

```kotlin
package com.babytracker.widget

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WidgetDataLoaderTest {

    private val happyBaby = Baby(name = "Akira", birthDate = LocalDate.of(2025, 12, 1))
    private val happyFeed = BreastfeedingSession(
        id = 1, startTime = Instant.parse("2026-05-27T10:00:00Z"),
        endTime = Instant.parse("2026-05-27T10:15:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @Test
    fun `happy path maps repositories to WidgetData`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk { coEvery { getLatestRecord() } returns null }

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals("Akira", result.babyName)
        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(SleepState.NONE, result.sleepState)
    }

    @Test
    fun `baby repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk {
            every { getBabyProfile() } returns flow { throw IllegalStateException("datastore busted") }
        }
        val feed: BreastfeedingRepository = mockk()
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `feed repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk {
            coEvery { getLastSession() } throws IllegalStateException("room busted")
        }
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `sleep repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk {
            coEvery { getLatestRecord() } throws IllegalStateException("room busted")
        }

        val result = loadWidgetData(baby = baby, feed = feed, sleep = sleep)

        assertEquals(WidgetData.EMPTY, result)
    }
}
```

- [ ] **Step 3: Run**

Run:
```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetDataLoaderTest" -PfastTests
```
Expected: 4 tests PASS.

---

### Task 4: Wire real data in `BabyWidget`

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/BabyWidget.kt`

Replace the placeholder body of `provideGlance` with a `loadWidgetData(context)` call, plus size-based dispatch between `SmallContent` and `MediumContent`.

- [ ] **Step 1: Replace `provideGlance` and remove `PlaceholderContent`**

```kotlin
package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.theme.BabyWidgetColors
import java.time.Instant

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val now = Instant.now()

        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                WidgetBody(data = data, now = now)
            }
        }
    }

    @Composable
    private fun WidgetBody(data: WidgetData, now: Instant) {
        val size = LocalSize.current
        if (size.width >= MEDIUM_SIZE.width && size.height >= MEDIUM_SIZE.height) {
            MediumContent(data = data, now = now)
        } else {
            SmallContent(data = data, now = now)
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 40.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 110.dp)
    }
}
```

- [ ] **Step 2: Extend `WidgetEntryPoint` to include `BabyRepository`**

Modify: `app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt`

Add `babyRepository()`:

```kotlin
package com.babytracker.widget

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun babyRepository(): BabyRepository
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 5: Smoke-build the APK

- [ ] **Step 1: Assemble**

Run:
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2 (manual, recommended): Install and verify on device**

If Pixel_10_Pro is reachable (see CLAUDE.md "Linux / WSL — UI / Instrumentation Tests"):

```
adb devices
./gradlew :app:installDebug
```

Test plan:
1. Fresh install — onboarding incomplete: widget shows "No feeds yet" + "No sleep logged" + header "Baby".
2. Complete onboarding with name "Akira", no sessions yet: header shows "Akira", both rows empty.
3. Log a left feed: small widget shows "Left · Just now" → "Left · Xm ago" as time passes.
4. Start a sleep session: medium widget shows "Sleeping · Xm" (does not tick live — expected; refresh worker lands in Plan 8).
5. Stop the sleep session: shows "Awake since Xm ago".
6. Toggle system dark mode: colors flip via `BabyWidgetColors`.

If no device is available, **document this in the PR description** and defer manual verification to a follow-up.

---

### Task 6: Unit tests for `WidgetContent`

Glance composables cannot be hosted in Compose UI tests directly (no Robolectric Glance host as of 1.1.x). The spec explicitly accepts manual verification for layout. **Do not block on a host that doesn't exist.**

- [ ] **Step 1: Confirm no JVM Glance test API is available**

Run:
```
./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath -q | grep -i "glance.*test\|glance.*preview" || echo "no glance test artifact present"
```
Expected: `no glance test artifact present`. Proceed to Step 2 without authoring composable tests.

- [ ] **Step 2: Cover the only pure helper introduced — `BreastSide.label()`**

`BreastSide.label()` is the one piece of testable pure logic in `WidgetContent.kt`. Add a tiny unit test:

Create: `app/src/test/java/com/babytracker/widget/WidgetContentHelpersTest.kt`

```kotlin
package com.babytracker.widget

// `BreastSide.label()` is file-private in WidgetContent.kt; expose it via an
// `@VisibleForTesting internal` extension in WidgetContent.kt if needed.
// Cleaner alternative: move the extension to a top-level `internal` fn at file
// scope so the test below can import it.

import com.babytracker.domain.model.BreastSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetContentHelpersTest {

    @Test
    fun `label maps LEFT to "Left"`() {
        assertEquals("Left", BreastSide.LEFT.label())
    }

    @Test
    fun `label maps RIGHT to "Right"`() {
        assertEquals("Right", BreastSide.RIGHT.label())
    }
}
```

Update `WidgetContent.kt`: change `private fun BreastSide.label()` → `internal fun BreastSide.label()` so the test can import it.

- [ ] **Step 3: Run the new test**

Run:
```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetContentHelpersTest" -PfastTests
```
Expected: 2 tests PASS.

---

### Task 7: Lint, static analysis, full unit suite

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on both. If detekt warns about the new composables' parameter count or magic numbers, prefer extracting named constants in `WidgetContent.kt` (e.g. `private val ICON_SIZE = 20.dp`) over `@Suppress`.

---

### Task 8: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetContent.kt \
        app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt \
        app/src/main/java/com/babytracker/widget/BabyWidget.kt \
        app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt \
        app/src/test/java/com/babytracker/widget/WidgetContentHelpersTest.kt \
        app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt

git commit -m "feat(widget): render last feed + sleep status in debug-gated widget"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 6 files changed; the debug manifest from Plan 4 remains intact (no manifest changes in this commit).

---

## Acceptance Criteria

- `SmallContent` and `MediumContent` render real `WidgetData`, with explicit empty states for missing feed and missing sleep.
- `BabyWidget.provideGlance` delegates to `loadWidgetData(context)`, which catches any repository failure and falls back to `WidgetData.EMPTY` (covered by 4 unit tests).
- `LocalSize.current` selects between `SmallContent` and `MediumContent`.
- The receiver remains debug-only (`app/src/debug/AndroidManifest.xml` from Plan 4). Release promotion is owned by Plan 8 once refresh is in place.
- Light/dark themes both apply via `GlanceTheme(colors = BabyWidgetColors)`.
- `./gradlew :app:assembleDebug ktlintFormat detekt :app:testDebugUnitTest` all succeed.
- One commit on `feat/plans-home-screen-widget`.
