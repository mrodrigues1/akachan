# Predictive Feeding — Task 5: Scheduler API + `PredictiveFeedNotificationCoordinator`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated `PredictiveFeedScheduler` interface with `schedulePredictiveReminderAt(triggerTime, predictedAt)` / `cancelPredictiveReminder`. Build a `PredictiveFeedNotificationCoordinator` that subscribes to `combine(PredictNextFeedUseCase, predictiveEnabled, predictiveLeadMinutes)` with a 200 ms debounce and reconciles the AlarmManager state. Refactor `PredictiveFeedReceiver.snooze()` to go through the scheduler. Wire `coordinator.start()` from `BabyTrackerApp.onCreate`.

> **Receiver contract (locked by Task 4):** The `ACTION_FIRE` PendingIntent carries only `EXTRA_PREDICTED_AT_MS`. `EXTRA_LEAD_MINUTES` does not exist — the receiver recomputes minutes-until from `System.currentTimeMillis()` at fire time. Task 5's scheduler MUST NOT put a lead-minutes extra on the intent.

**Architecture:** Keeps the predictive feature's scheduler separate from the per-session `NotificationScheduler` because the contract differs — one-shot, prediction-anchored, not session-bound. Hilt provides the implementation as a singleton. The coordinator owns the lifecycle subscription via a Hilt-provided `@ApplicationScope CoroutineScope` qualifier — **no default-arg fallback** (Kotlin defaults are invisible to Dagger; a default-only `CoroutineScope` would leave the parameter unbound at component generation).

**Tech Stack:** Kotlin · Coroutines/Flow · Hilt · AlarmManager · JUnit 5 · MockK.

**Branch:** `feat/predictive-feeding-5-coordinator`
**Linear issue:** Predictive feeding — scheduler + coordinator
**Depends on:** Tasks 1, 2, 4
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/PredictiveFeedScheduler.kt`
- Create: `app/src/main/java/com/babytracker/manager/PredictiveFeedNotificationCoordinator.kt`
- Create: `app/src/main/java/com/babytracker/di/ApplicationScope.kt` (qualifier annotation)
- Create: `app/src/main/java/com/babytracker/di/ApplicationScopeModule.kt`
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Modify: `app/src/main/java/com/babytracker/receiver/PredictiveFeedReceiver.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`
- Test: `app/src/test/java/com/babytracker/manager/PredictiveFeedNotificationCoordinatorTest.kt`

## Steps

- [ ] **Step 1: Define the scheduler interface and implementation**

Create `app/src/main/java/com/babytracker/manager/PredictiveFeedScheduler.kt`:

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.PredictiveFeedReceiver
import java.time.Instant

interface PredictiveFeedScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, predictedAt: Instant)
    fun cancelPredictiveReminder()
}

class PredictiveFeedSchedulerImpl(private val context: Context) : PredictiveFeedScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedulePredictiveReminderAt(
        triggerTime: Instant,
        predictedAt: Instant,
    ) {
        val pi = buildPendingIntent(predictedAt)
        alarmManager.cancel(pi)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
        }
    }

    override fun cancelPredictiveReminder() {
        alarmManager.cancel(buildPendingIntent(Instant.EPOCH))
    }

    private fun buildPendingIntent(predictedAt: Instant): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context, PredictiveFeedReceiver.REQUEST_CODE_PREDICTIVE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "PredictiveFeedScheduler"
    }
}
```

- [ ] **Step 2: Bind the scheduler in `NotificationSchedulerModule`**

Add to `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`:

```kotlin
@Provides
@Singleton
fun providePredictiveFeedScheduler(@ApplicationContext context: Context): PredictiveFeedScheduler =
    PredictiveFeedSchedulerImpl(context)
```

- [ ] **Step 2.1: Add `@ApplicationScope` qualifier**

Create `app/src/main/java/com/babytracker/di/ApplicationScope.kt`:

```kotlin
package com.babytracker.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

- [ ] **Step 2.2: Provide the application-scoped `CoroutineScope`**

Create `app/src/main/java/com/babytracker/di/ApplicationScopeModule.kt`:

```kotlin
package com.babytracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

Rationale: the coordinator outlives any UI scope; using a process-lifetime `CoroutineScope` keeps the prediction/settings subscription alive for the app's lifetime. A Hilt-provided binding (not a constructor default) ensures the component graph is verifiable at build time and tests can pass a `TestScope` explicitly.

- [ ] **Step 3: Write the failing coordinator test**

Create `app/src/test/java/com/babytracker/manager/PredictiveFeedNotificationCoordinatorTest.kt`:

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveFeedNotificationCoordinatorTest {

    private fun prediction(at: Instant) = FeedPrediction(
        predictedAt = at,
        averageIntervalMinutes = 180,
        sampleSize = 5,
        isOverdue = false,
        minutesUntil = 60,
    )

    @Test
    fun `schedules alarm when enabled and prediction is in the future`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val p = prediction(Instant.now().plusSeconds(3600))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(p),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), p.predictedAt) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when toggle is off`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val enabled = MutableStateFlow(true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(prediction(Instant.now().plusSeconds(3600))),
            enabledFlow = enabled,
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        enabled.value = false
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when prediction becomes null`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val predictionFlow = MutableStateFlow<FeedPrediction?>(prediction(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = predictionFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        predictionFlow.value = null
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `skips scheduling and cancels when trigger is in the past`() = runTest {
        // prediction.predictedAt = now + 5m, leadMinutes = 15 -> trigger = now - 10m -> cancel.
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(prediction(Instant.now().plusSeconds(300))),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `reschedules when lead minutes change`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val p = prediction(Instant.now().plusSeconds(3600))
        val lead = MutableStateFlow(15)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(p),
            enabledFlow = MutableStateFlow(true),
            leadFlow = lead,
        )
        coordinator.start()
        advanceTimeBy(300)
        lead.value = 30
        advanceTimeBy(300)
        verify(exactly = 2) { scheduler.schedulePredictiveReminderAt(any(), p.predictedAt) }
    }

    @Test
    fun `debounces rapid emissions inside 200ms`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val predictionFlow = MutableStateFlow(prediction(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = predictionFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(50)
        predictionFlow.value = prediction(Instant.now().plusSeconds(3700))
        advanceTimeBy(50)
        predictionFlow.value = prediction(Instant.now().plusSeconds(3800))
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), any()) }
    }

    // Helper builder. Pass `backgroundScope` explicitly — the production constructor
    // has no default; the @ApplicationScope binding is wired via Hilt in app code.
    private fun TestScope.buildCoordinator(
        scheduler: PredictiveFeedScheduler,
        predictionFlow: MutableStateFlow<FeedPrediction?>,
        enabledFlow: MutableStateFlow<Boolean>,
        leadFlow: MutableStateFlow<Int>,
    ): PredictiveFeedNotificationCoordinator {
        val useCase = mockk<PredictNextFeedUseCase>().also {
            every { it.invoke() } returns predictionFlow
        }
        val settings = mockk<SettingsRepository>().also {
            every { it.getPredictiveEnabled() } returns enabledFlow
            every { it.getPredictiveLeadMinutes() } returns leadFlow
        }
        return PredictiveFeedNotificationCoordinator(
            predictNextFeed = useCase,
            settingsRepository = settings,
            scheduler = scheduler,
            applicationScope = backgroundScope,
        )
    }
}
```

- [ ] **Step 4: Run test, verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.PredictiveFeedNotificationCoordinatorTest"
```

Expected: FAIL with unresolved reference for `PredictiveFeedNotificationCoordinator`.

- [ ] **Step 5: Implement the coordinator**

Create `app/src/main/java/com/babytracker/manager/PredictiveFeedNotificationCoordinator.kt`:

```kotlin
package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveFeedNotificationCoordinator @Inject constructor(
    private val predictNextFeed: PredictNextFeedUseCase,
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveFeedScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                predictNextFeed(),
                settingsRepository.getPredictiveEnabled(),
                settingsRepository.getPredictiveLeadMinutes(),
            ) { prediction, enabled, lead -> Triple(prediction, enabled, lead) }
                .debounce(DEBOUNCE_MS)
                .collect { (prediction, enabled, lead) -> reconcile(prediction, enabled, lead) }
        }
    }

    private fun reconcile(prediction: FeedPrediction?, enabled: Boolean, leadMinutes: Int) {
        if (!enabled || prediction == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = prediction.predictedAt.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        scheduler.schedulePredictiveReminderAt(triggerAt, prediction.predictedAt)
    }

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
```

- [ ] **Step 6: Wire `start()` in `BabyTrackerApp.onCreate`**

```kotlin
@Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
// ...
override fun onCreate() {
    super.onCreate()
    NotificationHelper.createBreastfeedingNotificationChannel(this)
    NotificationHelper.createSleepNotificationChannel(this)
    NotificationHelper.createPredictiveFeedNotificationChannel(this)
    predictiveCoordinator.start()
}
```

- [ ] **Step 7: Refactor `PredictiveFeedReceiver.snooze()` to use the scheduler**

In `PredictiveFeedReceiver`, add:

```kotlin
@Inject lateinit var scheduler: PredictiveFeedScheduler
```

Replace the inline AlarmManager block in `snooze()` with:

```kotlin
scheduler.schedulePredictiveReminderAt(
    triggerTime = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L),
    predictedAt = Instant.ofEpochMilli(predictedAtEpochMs),
)
NotificationHelper.cancelNotification(context, NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
```

The Task 4 snooze intent carries only `EXTRA_PREDICTED_AT_MS`; no `leadMinutes` value is read or passed.

Remove `buildSnoozePendingIntent`'s direct AlarmManager dependency. Keep the snooze PendingIntent builder used by `showPredictiveReminder`, since the action still routes through the receiver.

- [ ] **Step 8: Run tests, verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.PredictiveFeedNotificationCoordinatorTest"
```

Expected: PASS — all six tests (schedule happy path, toggle-off cancel, null-prediction cancel, past-trigger skip, lead-minute reschedule, 200 ms debounce).

Also verify the app compiles end-to-end so Hilt's `@ApplicationScope` graph generates cleanly and the receiver's snooze refactor resolves:

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 9: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/PredictiveFeedScheduler.kt \
        app/src/main/java/com/babytracker/manager/PredictiveFeedNotificationCoordinator.kt \
        app/src/main/java/com/babytracker/di/ApplicationScope.kt \
        app/src/main/java/com/babytracker/di/ApplicationScopeModule.kt \
        app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt \
        app/src/main/java/com/babytracker/receiver/PredictiveFeedReceiver.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/test/java/com/babytracker/manager/PredictiveFeedNotificationCoordinatorTest.kt
git commit -m "feat(notification): add predictive-feed scheduler and reconcile coordinator"
```

- [ ] **Step 11: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-5-coordinator
gh pr create --title "feat(notification): predictive-feed scheduler + coordinator" \
  --body "Adds PredictiveFeedScheduler interface, PredictiveFeedNotificationCoordinator with 200 ms debounce reconcile, and routes the receiver's snooze path through the scheduler. Task 5 of 6."
```
