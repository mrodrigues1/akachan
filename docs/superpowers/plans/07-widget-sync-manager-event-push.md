# WidgetSyncManager Event Push Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-54](https://linear.app/akachan/issue/AKA-54/widgetsyncmanager-event-push-refresh)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Refresh Strategy section, item 1)
**Suggested branch:** `feat/widget-sync-manager`
**Depends on:** Plan 4 (`BabyWidget`)
**Blocks:** Plan 8 (release promotion is gated on **both** sync manager + refresh worker)

**Goal:** Push widget updates within ~500ms of any feed or sleep data change so a parent who logs an event in the app sees the home-screen widget update immediately, without waiting for the periodic refresh in Plan 8. Achieved by observing `BreastfeedingRepository.getAllSessions()` and `SleepRepository.getAllRecords()` flows from an application-scoped coroutine and calling `BabyWidget().updateAll(appContext)` on debounced + distinct emissions.

**Architecture:**
- `WidgetSyncManager` is a `@Singleton` `@Inject`-constructed class. It receives `@ApplicationContext context`, `BreastfeedingRepository`, `SleepRepository`, and a `WidgetUpdater` (a thin abstraction over `BabyWidget().updateAll(context)` so the manager is JVM-unit-testable without an Android device).
- `WidgetUpdater` is an interface; its production implementation `GlanceWidgetUpdater` calls `BabyWidget().updateAll(context)`. The manager depends on the interface only.
- `start()` launches a single collector on the existing `@ApplicationScope CoroutineScope` (already provided by `ApplicationScopeModule.provideApplicationScope()` — `SupervisorJob() + Dispatchers.Default`). The flow is `merge(feedFlow.map { Unit }, sleepFlow.map { Unit }).debounce(500)`. Inside the collector, `updater.updateAll()` is wrapped in a per-emission `try/catch`; a single Glance update failure logs and continues, so future events still refresh the widget.
- `BabyTrackerApp.onCreate()` injects + calls `widgetSyncManager.start()`. No other touch points.
- DI: a new `@InstallIn(SingletonComponent::class) WidgetModule` binds `WidgetUpdater → GlanceWidgetUpdater`.

**Tech Stack:** Hilt `@Inject`, Coroutines Flow (`merge`, `debounce`, `distinctUntilChanged`), AndroidX Glance `GlanceAppWidget.updateAll`, MockK + Turbine for tests.

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/WidgetUpdater.kt`
- Create: `app/src/main/java/com/babytracker/widget/GlanceWidgetUpdater.kt`
- Create: `app/src/main/java/com/babytracker/widget/WidgetSyncManager.kt`
- Create: `app/src/main/java/com/babytracker/di/WidgetModule.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`
- Create: `app/src/test/java/com/babytracker/widget/WidgetSyncManagerTest.kt`

---

### Task 1: Define the `WidgetUpdater` abstraction

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetUpdater.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.babytracker.widget

interface WidgetUpdater {
    suspend fun updateAll()
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Implement `GlanceWidgetUpdater`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/GlanceWidgetUpdater.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdater {
    override suspend fun updateAll() {
        BabyWidget().updateAll(context)
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Wire the binding via Hilt

**Files:**
- Create: `app/src/main/java/com/babytracker/di/WidgetModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.babytracker.di

import com.babytracker.widget.GlanceWidgetUpdater
import com.babytracker.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}
```

- [ ] **Step 2: Compile check (KSP must regenerate Hilt graph)**

Run: `./gradlew :app:kspDebugKotlin :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Write the failing `WidgetSyncManagerTest`

**Files:**
- Create: `app/src/test/java/com/babytracker/widget/WidgetSyncManagerTest.kt`

Manager does not exist yet; this test fails at compile. That is the desired state.

- [ ] **Step 1: Write the test**

```kotlin
package com.babytracker.widget

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class WidgetSyncManagerTest {

    private val feed = BreastfeedingSession(
        id = 1, startTime = Instant.parse("2026-05-27T10:00:00Z"),
        startingSide = BreastSide.LEFT,
    )
    private val sleep = SleepRecord(
        id = 1, startTime = Instant.parse("2026-05-27T11:00:00Z"),
        sleepType = SleepType.NAP,
    )

    @Test
    fun `emits updateAll after debounce on single feed change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
        val sleepFlow = MutableSharedFlow<List<SleepRecord>>(replay = 1)
        val feedRepo: BreastfeedingRepository = mockk { every { getAllSessions() } returns feedFlow }
        val sleepRepo: SleepRepository = mockk { every { getAllRecords() } returns sleepFlow }
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        val manager = WidgetSyncManager(feedRepo, sleepRepo, updater, scope)
        manager.start()
        feedFlow.emit(listOf(feed))
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `debounce coalesces back-to-back emissions to one updateAll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
        val sleepFlow = MutableSharedFlow<List<SleepRecord>>(replay = 1)
        val feedRepo: BreastfeedingRepository = mockk { every { getAllSessions() } returns feedFlow }
        val sleepRepo: SleepRepository = mockk { every { getAllRecords() } returns sleepFlow }
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        val manager = WidgetSyncManager(feedRepo, sleepRepo, updater, scope)
        manager.start()
        feedFlow.emit(emptyList())
        advanceTimeBy(100)
        sleepFlow.emit(emptyList())
        advanceTimeBy(100)
        feedFlow.emit(listOf(feed))
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `first updateAll exception does not stop later emissions from refreshing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
        val sleepFlow = MutableSharedFlow<List<SleepRecord>>(replay = 1)
        val feedRepo: BreastfeedingRepository = mockk { every { getAllSessions() } returns feedFlow }
        val sleepRepo: SleepRepository = mockk { every { getAllRecords() } returns sleepFlow }
        val updater: WidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("glance host gone") andThen Unit
        }

        val manager = WidgetSyncManager(feedRepo, sleepRepo, updater, scope)
        manager.start()
        feedFlow.emit(listOf(feed))
        advanceTimeBy(600)   // first emission, updateAll throws — must be absorbed
        feedFlow.emit(emptyList())
        advanceTimeBy(600)   // second emission, updateAll must still be invoked

        coVerify(exactly = 2) { updater.updateAll() }
    }

    @Test
    fun `sleep changes also trigger updateAll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<List<BreastfeedingSession>>(replay = 1)
        val sleepFlow = MutableSharedFlow<List<SleepRecord>>(replay = 1)
        val feedRepo: BreastfeedingRepository = mockk { every { getAllSessions() } returns feedFlow }
        val sleepRepo: SleepRepository = mockk { every { getAllRecords() } returns sleepFlow }
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        val manager = WidgetSyncManager(feedRepo, sleepRepo, updater, scope)
        manager.start()
        sleepFlow.emit(listOf(sleep))
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }
}
```

- [ ] **Step 2: Verify the test does not compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin -q`
Expected: FAIL — `Unresolved reference: WidgetSyncManager`. Continue to Task 5.

> Note: 4 tests total. The failure-recovery test (third in the list above) is **mandatory** — it locks in the requirement that one Glance update exception does not stop the event-push flow.

---

### Task 5: Implement `WidgetSyncManager`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetSyncManager.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget

import android.util.Log
import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBOUNCE_MS = 500L
private const val TAG = "WidgetSyncManager"

@OptIn(FlowPreview::class)
@Singleton
class WidgetSyncManager @Inject constructor(
    private val feedRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val updater: WidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun start() {
        val feedTrigger = feedRepository.getAllSessions().map { Unit }
        val sleepTrigger = sleepRepository.getAllRecords().map { Unit }

        merge(feedTrigger, sleepTrigger)
            .debounce(DEBOUNCE_MS)
            .onEach { safeUpdate() }
            .catch { e -> Log.w(TAG, "Widget sync flow terminated", e) }
            .launchIn(scope)
    }

    private suspend fun safeUpdate() {
        try {
            updater.updateAll()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "updateAll() failed; will retry on next emission", t)
        }
    }
}
```

Notes for the worker:
- `@ApplicationScope CoroutineScope` is the existing scope qualifier provided by `app/src/main/java/com/babytracker/di/ApplicationScopeModule.kt` (`SupervisorJob() + Dispatchers.Default`). Reusing it avoids creating an unscoped coroutine scope and satisfies Hilt without a new binding.
- `safeUpdate` re-throws `CancellationException` so coroutine cancellation is not swallowed; all other throwables are logged and absorbed. One failed Glance update must not stop future event pushes.
- Tests construct `WidgetSyncManager` directly (not via Hilt) and pass a `CoroutineScope(StandardTestDispatcher(testScheduler))` as the last argument.
- `getAllSessions()` and `getAllRecords()` already emit on subscription (they wrap Room `Flow<List<...>>`); the initial emission replays current state, which causes one `updateAll` ~500ms after `start()`. This is **correct** — it ensures the widget syncs with current DB state when the app process is created.
- `distinctUntilChanged` is not applied — every Room change must trigger a refresh because elapsed text differs across consecutive emissions even when the data content matches (e.g. one feed session with the same ID is "5m ago" then "6m ago"). Coalescing is handled solely by the 500ms debounce.

- [ ] **Step 2: Re-run the unit test**

Run:
```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetSyncManagerTest" -PfastTests
```
Expected: 4 tests PASS.

---

### Task 6: Wire from `BabyTrackerApp.onCreate()`

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

- [ ] **Step 1: Inject + call `start()`**

```kotlin
package com.babytracker

import android.app.Application
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
class BabyTrackerApp : Application() {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder
    @Inject lateinit var widgetSyncManager: WidgetSyncManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 7: Lint, static analysis, full unit suite

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on both. If a Konsist architecture rule flags the new `widget/` files, follow the same approach as Plan 4 Task 8 — add a tagged arch test if needed, never suppress.

---

### Task 8: Manual verification (device, debug build)

Because the receiver is still in `app/src/debug/AndroidManifest.xml` until Plan 8 promotes it, manual verification runs against a debug install.

- [ ] **Step 1: Install + place widget**

```
./gradlew :app:installDebug
```

Place the Medium widget on the home screen.

- [ ] **Step 2: Verify event-push**

1. Open the app, start a feed session — widget should refresh within ~1s ("Left · Just now").
2. Stop the feed — widget refreshes ("Left · Xm ago" / appropriate side).
3. Start a sleep session — widget shows "Sleeping · 0m" (or whatever current minute).
4. Stop the sleep — widget shows "Awake since …".
5. Edit the most recent feed in history — widget refreshes.
6. Delete the most recent feed — widget refreshes (now shows the previous one, or "No feeds yet" if none remain).

If any of (1)–(6) does not refresh within ~2s, **stop and investigate**. The most common cause is the dispatcher used by the scope; the production scope uses `Dispatchers.Default`, which must observe Room flows correctly (Room emits on `Dispatchers.IO` via its own coroutine; `merge` and `debounce` should still observe regardless).

---

### Task 9: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetUpdater.kt \
        app/src/main/java/com/babytracker/widget/GlanceWidgetUpdater.kt \
        app/src/main/java/com/babytracker/widget/WidgetSyncManager.kt \
        app/src/main/java/com/babytracker/di/WidgetModule.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/test/java/com/babytracker/widget/WidgetSyncManagerTest.kt

git commit -m "feat(widget): push widget updates on feed and sleep data changes"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 6 files changed; no unrelated changes.

---

## Acceptance Criteria

- `WidgetSyncManager` is `@Singleton`, observes `BreastfeedingRepository.getAllSessions()` + `SleepRepository.getAllRecords()` via `merge`, and calls `WidgetUpdater.updateAll()` after a 500ms debounce.
- `WidgetUpdater` is an interface; `GlanceWidgetUpdater` is its `@Singleton` impl wrapping `BabyWidget().updateAll(context)`. Bound in `WidgetModule`.
- `BabyTrackerApp.onCreate()` injects + calls `widgetSyncManager.start()`.
- Manager is fully JVM-unit-tested (4 cases) via injected `WidgetUpdater` mock + `StandardTestDispatcher`, including a failure-recovery test that asserts a thrown `updateAll()` does not stop subsequent event pushes.
- Constructor uses `@ApplicationScope CoroutineScope` from `ApplicationScopeModule` (no new scope binding introduced).
- `./gradlew ktlintFormat detekt :app:testDebugUnitTest :app:assembleDebug` all succeed.
- One commit on `feat/plans-home-screen-widget`.
