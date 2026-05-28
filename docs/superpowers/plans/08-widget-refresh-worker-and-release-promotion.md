# WidgetRefreshWorker + Release Promotion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-55](https://linear.app/akachan/issue/AKA-55/widgetrefreshworker-release-promotion)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Refresh Strategy section, item 2; Architecture: `WidgetRefreshWorker`)
**Suggested branch:** `feat/widget-refresh-worker-and-release`
**Depends on:** Plan 7 (`WidgetSyncManager` event push is the primary refresh path; the worker is the safety net)
**Blocks:** none

**Goal:** Add a `PeriodicWorkRequest`-backed safety net that fires `WidgetUpdater.updateAll()` every 15 minutes so elapsed text stays current when the app process is not alive to observe Room flows. Once both refresh paths are in place, **promote the receiver from `app/src/debug/AndroidManifest.xml` to `app/src/main/AndroidManifest.xml`** so release builds expose the widget. This is the final plan in the Home Widget project.

**Architecture:**
- `WidgetRefreshWorker` is a `HiltWorker` (constructor-injected via `androidx.hilt:hilt-work`); it depends on `WidgetUpdater` from Plan 7 and calls `updater.updateAll()` in `doWork()`. Returns `Result.success()` on success, `Result.retry()` on transient failures, `Result.failure()` only on exhaustion via WorkManager defaults.
- `BabyWidgetReceiver` overrides `onEnabled(context)` to enqueue a unique `PeriodicWorkRequest<WidgetRefreshWorker>(15.minutes, KEEP)`, and `onDisabled(context)` to cancel it. `onUpdate(...)` does not re-enqueue (KEEP prevents duplicates anyway, and `onUpdate` fires on the OS auto-refresh interval which we already set to 0 in `baby_widget_info.xml`).
- Hilt-Work integration: add `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` (KSP), and configure a custom `Configuration.Provider` on `BabyTrackerApp` so WorkManager can resolve the Hilt-injected worker. The app already extends `Application` + `@HiltAndroidApp`; we add `Configuration.Provider`.
- Release promotion: copy the `<receiver>` block from `app/src/debug/AndroidManifest.xml` into `app/src/main/AndroidManifest.xml`, then `git rm app/src/debug/AndroidManifest.xml`.

**Tech Stack:** AndroidX WorkManager 2.10.0 (from Plan 1), `androidx.hilt:hilt-work` 1.2.0, `HiltWorker`, `Configuration.Provider`, KSP.

---

## Files

- Modify: `gradle/libs.versions.toml` (add `androidx-hilt-work` + `androidx-hilt-compiler` aliases)
- Modify: `app/build.gradle.kts` (add implementation + KSP lines)
- Create: `app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt` (override `onEnabled` + `onDisabled`)
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt` (implement `Configuration.Provider`)
- Modify: `app/src/main/AndroidManifest.xml` (add receiver block — promotion)
- Delete: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt`

---

### Task 1: Add Hilt-Work dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add a version + libraries**

In `gradle/libs.versions.toml` `[versions]`:

```toml
hiltWork = "1.2.0"
```

In `[libraries]` (next to existing Hilt entries):

```toml
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
hilt-ext-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
```

- [ ] **Step 2: Wire into `:app`**

In `app/build.gradle.kts` `dependencies { ... }` next to the existing Hilt lines:

```kotlin
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)
```

- [ ] **Step 3: Verify Gradle sync**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q | grep "androidx.hilt:hilt-work" | head -3`
Expected: `androidx.hilt:hilt-work:1.2.0`.

---

### Task 2: Configure `BabyTrackerApp` as a WorkManager `Configuration.Provider`

Hilt-Work requires the application class to supply a `Configuration` with the Hilt-injected `WorkerFactory`. Without this, the OS instantiates workers via reflection and the Hilt-managed worker constructor fails.

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

- [ ] **Step 1: Implement `Configuration.Provider`**

```kotlin
package com.babytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.widget.WidgetSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder
    @Inject lateinit var widgetSyncManager: WidgetSyncManager
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
        widgetSyncManager.start()
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
    }
}
```

- [ ] **Step 2: Disable WorkManager's default auto-initializer**

`Configuration.Provider` alone is **not** sufficient when WorkManager's androidx-startup auto-initializer wins the race: it instantiates WorkManager with the default reflection-based factory, after which Hilt's `HiltWorkerFactory` never gets used. The fix is to remove the WorkManager startup initializer from the merged manifest, so WorkManager is constructed on-demand via `Configuration.Provider`.

Modify `app/src/main/AndroidManifest.xml`. Add `xmlns:tools` to the root `<manifest>` element if not already present, then add a `<provider>` node-merge block inside `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    ...
    <application ... >
        ...
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                tools:node="remove" />
        </provider>
        ...
    </application>
</manifest>
```

Notes:
- `${applicationId}.androidx-startup` matches the canonical authority that AndroidX Startup advertises.
- `tools:node="merge"` on the provider means we add to whatever the library declares rather than replace it; `tools:node="remove"` strips the WorkManagerInitializer meta-data so it does not auto-initialize WorkManager.
- After this change, the only WorkManager instance is the one built from `BabyTrackerApp.workManagerConfiguration`, which uses `HiltWorkerFactory`.

- [ ] **Step 2a: Verify the merged manifest no longer initializes WorkManager**

```
./gradlew :app:processDebugMainManifest -q
grep -A4 "androidx.startup.InitializationProvider" app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
```
Expected: the provider block is present, but `WorkManagerInitializer` meta-data is absent.

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:kspDebugKotlin :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Write the failing worker test

**Files:**
- Create: `app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt`

WorkManager ships a JVM-runnable test harness in `androidx.work:work-testing`, but adding that dependency just for one test is over-kill. Instead, test the worker's `doWork()` body via direct instantiation with a fake `WidgetUpdater`. The worker constructor takes `Context`, `WorkerParameters`, and `WidgetUpdater`; the first two come from `TestListenableWorkerBuilder`.

Add `work-testing` to the test classpath: in `gradle/libs.versions.toml` `[libraries]`:

```toml
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

In `app/build.gradle.kts` `dependencies { ... }`:

```kotlin
    testImplementation(libs.work.testing)
```

- [ ] **Step 1: Write the test**

```kotlin
package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetRefreshWorkerTest {

    @Test
    fun `doWork delegates to WidgetUpdater and returns success`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(appContext, workerParameters, updater)
                },
            )
            .build()

        val result = worker.doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns retry when updater throws`() = runTest {
        val updater: WidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("transient")
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(appContext, workerParameters, updater)
                },
            )
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
```

- [ ] **Step 2: Verify the test does not compile (worker class missing)**

Run: `./gradlew :app:compileDebugUnitTestKotlin -q`
Expected: FAIL — `Unresolved reference: WidgetRefreshWorker`. Continue to Task 4.

---

### Task 4: Implement `WidgetRefreshWorker`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "WidgetRefreshWorker"

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        updater.updateAll()
        Result.success()
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Log.w(TAG, "Periodic widget refresh failed; will retry", t)
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "baby_widget_refresh"
    }
}
```

- [ ] **Step 2: Re-run the worker test**

Run:
```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetRefreshWorkerTest" -PfastTests
```
Expected: 2 tests PASS.

---

### Task 5: Enqueue + cancel the worker from `BabyWidgetReceiver`

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt`

- [ ] **Step 1: Override `onEnabled` + `onDisabled`**

```kotlin
package com.babytracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BabyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BabyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context)
            .cancelUniqueWork(WidgetRefreshWorker.UNIQUE_NAME)
    }
}
```

`ExistingPeriodicWorkPolicy.KEEP` is critical: if the user removes one widget but still has another, `onDisabled` does not fire and the worker keeps running; if the user adds a second widget, `onEnabled` fires again and KEEP ensures we do not stack duplicate periodic requests.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 6: Promote receiver to the main manifest, delete the debug stub

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/debug/AndroidManifest.xml`

- [ ] **Step 1: Add receiver block to main manifest**

Insert after the last existing `<receiver>` (after `NapReminderReceiver`), before `<provider>`:

```xml
        <receiver
            android:name=".widget.BabyWidgetReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/baby_widget_info" />
        </receiver>
```

- [ ] **Step 2: Delete the debug stub**

```bash
git rm app/src/debug/AndroidManifest.xml
```

- [ ] **Step 3: Verify both variants now include the receiver**

```
./gradlew :app:processDebugMainManifest :app:processReleaseMainManifest -q
grep "BabyWidgetReceiver" app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
grep "BabyWidgetReceiver" app/build/intermediates/merged_manifests/release/AndroidManifest.xml
```
Expected: both `grep`s print a matching line.

---

### Task 7: Smoke-build the APK

- [ ] **Step 1: Assemble both variants**

```
./gradlew :app:assembleDebug :app:assembleRelease
```
Expected: BUILD SUCCESSFUL (release requires `SIGNING_*` env vars; if absent, AGP falls back to debug-signed release per existing config — confirm with `git status app/build.gradle.kts:48-51`).

If release assembly fails purely on signing (e.g. missing keystore), document this in the PR description rather than expanding scope.

---

### Task 8: Manual verification (device)

The worker fires once per 15 minutes, so end-to-end periodic verification requires patience.

- [ ] **Step 1: Install + place Medium widget**

```
./gradlew :app:installDebug
```

- [ ] **Step 2: Confirm the worker is scheduled**

```
adb shell dumpsys jobscheduler | grep -i "baby_widget_refresh\|com.babytracker"
```
Expected: an entry for the periodic worker, repeat interval ~15min.

- [ ] **Step 3 (additional): Verify the Hilt-injected factory is actually used**

Direct `TestListenableWorkerBuilder` instantiation does not exercise the WorkManager → HiltWorkerFactory path. To prove that path on a device, enqueue the worker through WorkManager directly and check Logcat:

```
adb logcat -c
# In the app, place a widget so onEnabled fires (or call enqueue via a temporary debug button)
adb logcat | grep -E "WM-WorkerFactory|HiltWorkerFactory|WidgetRefreshWorker"
```
Expected: the log shows `HiltWorkerFactory` being used (not the default reflection factory). If the default factory appears, Task 2 Step 2 was skipped or merged manifest still includes the WorkManagerInitializer.

- [ ] **Step 4: Force the worker to run**

```
adb shell cmd jobscheduler run -f com.babytracker $(adb shell dumpsys jobscheduler | grep com.babytracker | grep "JOB #" | head -1 | awk '{print $4}' | tr -d ':')
```

If the above is fragile, simply wait the first 15 minutes after install and observe the widget update.

- [ ] **Step 5: Remove all widgets**

Confirm via `adb shell dumpsys jobscheduler | grep baby_widget_refresh || echo "no job (expected)"` that the worker is cancelled.

If any step fails, **stop and investigate** before committing.

---

### Task 9: Lint, static analysis, full unit suite

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on both.

---

### Task 10: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add gradle/libs.versions.toml \
        app/build.gradle.kts \
        app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt \
        app/src/main/java/com/babytracker/widget/BabyWidgetReceiver.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt

git rm app/src/debug/AndroidManifest.xml

git commit -m "feat(widget): add 15-min refresh worker and promote widget to release"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 8 files changed (7 modified/added + 1 deletion); no unrelated changes.

---

## Acceptance Criteria

- `WidgetRefreshWorker` is a `HiltWorker` `CoroutineWorker` that calls `WidgetUpdater.updateAll()` and returns `success()` / `retry()` on failure.
- `BabyWidgetReceiver.onEnabled` enqueues a unique (KEEP) periodic 15-min `WorkRequest`; `onDisabled` cancels it.
- `BabyTrackerApp` implements `Configuration.Provider` with the Hilt `WorkerFactory`.
- `BabyWidgetReceiver` is registered in `app/src/main/AndroidManifest.xml` (debug stub deleted).
- Both `:app:assembleDebug` and `:app:assembleRelease` succeed; release merged manifest contains the receiver.
- `WidgetRefreshWorker` is JVM-unit-tested (Robolectric + work-testing) for the success and retry paths.
- One commit on `feat/plans-home-screen-widget`.
