# AKA-89: Predictive Sleep Notification Stack — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the notification infrastructure for sleep-window predictions, mirroring the feed-prediction stack, shipped behind `BuildConfig.DEBUG` flag until GA (issue 7).

**Architecture:** `PredictSleepWindowUseCase` returns `Flow<SleepPredictionState>`; only `Window` states schedule an alarm at `bestEstimate − leadMinutes`. The coordinator combines that flow with settings and debounces at 200 ms — same pattern as `PredictiveFeedNotificationCoordinator`. Quiet hours are shared between feed and sleep (reusing existing DataStore keys).

**Tech Stack:** Kotlin + Hilt + AlarmManager + DataStore + Room (via existing repo), JUnit 5 + MockK + Turbine (unit tests), AndroidJUnit4 (instrumentation tests).

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `app/src/main/java/com/babytracker/manager/PredictiveSleepScheduler.kt` | Interface + AlarmManager impl with exact/inexact fallback |
| Create | `app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt` | Combines `Flow<SleepPredictionState>` + settings; reconciles alarm |
| Create | `app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt` | `@AndroidEntryPoint` BroadcastReceiver that posts the sleep reminder |
| Create | `app/src/main/java/com/babytracker/util/PredictiveSleepNotificationHelper.kt` | Channel creation + `showPredictiveSleepReminder()` (Sleep Blue accent) |
| Create | `app/src/test/java/com/babytracker/manager/PredictiveSleepNotificationCoordinatorTest.kt` | Unit tests (8 cases) |
| Create | `app/src/androidTest/java/com/babytracker/receiver/PredictiveSleepReceiverTest.kt` | Instrumentation tests (4 cases) |
| Modify | `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` | Add 4 methods for predictive sleep enabled + lead minutes |
| Modify | `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt` | Add 2 DataStore keys + 4 method impls |
| Modify | `app/src/main/java/com/babytracker/util/NotificationHelper.kt` | Add `PREDICTIVE_SLEEP_CHANNEL_ID` and `PREDICTIVE_SLEEP_NOTIFICATION_ID` |
| Modify | `app/src/main/di/NotificationSchedulerModule.kt` | Add `@Provides @Singleton` for `PredictiveSleepScheduler` |
| Modify | `app/src/main/java/com/babytracker/BabyTrackerApp.kt` | Inject coordinator; start + create channel inside `BuildConfig.DEBUG` guard |
| Modify | `app/src/main/AndroidManifest.xml` | Declare `PredictiveSleepReceiver` |
| Modify | `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt` | Add 2 state fields + 2 event handlers; extend combine to 17 flows |
| Modify | `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` | Add `BuildConfig.DEBUG`-gated toggle + lead time + quiet hours in Sleep Reminders section |
| Modify | `app/src/main/res/values/strings.xml` | Add predictive sleep channel/notification/settings strings |
| Modify | `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt` | Add 4 stub methods to `FakeSettingsRepository` |
| Modify | `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt` | Add 4 stub methods to prediction fake |
| Modify | `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt` | Add 4 stub methods to `FakePartnerSettingsRepository` |
| Modify | `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt` | Add `getPredictiveSleepEnabled` + `getPredictiveSleepLeadMinutes` stubs + permission-warning test |
| Modify | `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt` | Add `getPredictiveSleepEnabled` + `getPredictiveSleepLeadMinutes` stubs |

---

## Task 1: Settings DataStore — predictiveSleepEnabled + predictiveSleepLeadMinutes

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`

- [ ] **Step 1.1: Add 4 methods to SettingsRepository interface**

In `SettingsRepository.kt`, add after line 49 (`fun setNapReminderDelayMinutes`):

```kotlin
fun getPredictiveSleepEnabled(): Flow<Boolean>
suspend fun setPredictiveSleepEnabled(enabled: Boolean)
fun getPredictiveSleepLeadMinutes(): Flow<Int>
suspend fun setPredictiveSleepLeadMinutes(minutes: Int)
```

- [ ] **Step 1.2: Add DataStore keys to SettingsRepositoryImpl companion object**

In `SettingsRepositoryImpl.kt`, add after line 43 (`val NAP_REMINDER_DELAY_MINUTES`):

```kotlin
val PREDICTIVE_SLEEP_ENABLED = booleanPreferencesKey("predictive_sleep_enabled")
val PREDICTIVE_SLEEP_LEAD_MINUTES = intPreferencesKey("predictive_sleep_lead_minutes")
```

- [ ] **Step 1.3: Add 4 method implementations to SettingsRepositoryImpl**

In `SettingsRepositoryImpl.kt`, add after the `setNapReminderDelayMinutes` implementation (after line 211):

```kotlin
override fun getPredictiveSleepEnabled(): Flow<Boolean> =
    dataStore.data.map { it[PREDICTIVE_SLEEP_ENABLED] ?: false }

override suspend fun setPredictiveSleepEnabled(enabled: Boolean) {
    dataStore.edit { it[PREDICTIVE_SLEEP_ENABLED] = enabled }
}

override fun getPredictiveSleepLeadMinutes(): Flow<Int> =
    dataStore.data.map { prefs ->
        val stored = prefs[PREDICTIVE_SLEEP_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES
        if (stored in ALLOWED_LEAD_MINUTES) stored else DEFAULT_LEAD_MINUTES
    }

override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) {
    val sanitized = if (minutes in ALLOWED_LEAD_MINUTES) minutes else DEFAULT_LEAD_MINUTES
    dataStore.edit { it[PREDICTIVE_SLEEP_LEAD_MINUTES] = sanitized }
}
```

- [ ] **Step 1.4: Run unit tests to confirm nothing broke**

```
./gradlew :app:testDebugUnitTest -PfastTests
```
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 1.5: Commit**

```
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt
git commit -m "feat(sleep): add predictiveSleepEnabled and predictiveSleepLeadMinutes to SettingsRepository"
```

---

## Task 1b: Update androidTest SettingsRepository fakes

`SettingsRepository` is an interface. Every concrete fake in `androidTest/` that implements it must add the 4 new methods or `compileDebugAndroidTestKotlin` fails. Three fakes are affected.

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt`
- Modify: `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`

- [ ] **Step 1b.1: Add 4 stub methods to FakeSettingsRepository in SettingsScreenTest.kt**

Locate `class FakeSettingsRepository : SettingsRepository` (line ~143). Find its last two methods:
```kotlin
        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit
```
Add immediately after `setNapReminderDelayMinutes`:
```kotlin
        override fun getPredictiveSleepEnabled(): Flow<Boolean> = flowOf(false)
        override suspend fun setPredictiveSleepEnabled(enabled: Boolean) = Unit
        override fun getPredictiveSleepLeadMinutes(): Flow<Int> = flowOf(15)
        override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) = Unit
```

- [ ] **Step 1b.2: Add 4 stub methods to the prediction fake in SettingsScreenPredictionTest.kt**

Locate the class implementing `SettingsRepository` in that file (line ~401 region). Find:
```kotlin
        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit
```
Add immediately after `setNapReminderDelayMinutes`:
```kotlin
        override fun getPredictiveSleepEnabled(): Flow<Boolean> = flowOf(false)
        override suspend fun setPredictiveSleepEnabled(enabled: Boolean) = Unit
        override fun getPredictiveSleepLeadMinutes(): Flow<Int> = flowOf(15)
        override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) = Unit
```

- [ ] **Step 1b.3: Add 4 stub methods to FakePartnerSettingsRepository in PartnerDashboardScreenTest.kt**

Locate `class FakePartnerSettingsRepository : SettingsRepository` (line ~548). Find:
```kotlin
        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit
```
Add immediately after `setNapReminderDelayMinutes`:
```kotlin
        override fun getPredictiveSleepEnabled(): Flow<Boolean> = flowOf(false)
        override suspend fun setPredictiveSleepEnabled(enabled: Boolean) = Unit
        override fun getPredictiveSleepLeadMinutes(): Flow<Int> = flowOf(15)
        override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) = Unit
```

- [ ] **Step 1b.4: Verify androidTest compilation**

```
./gradlew :app:compileDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 1b.5: Commit**

```
git add app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt \
        app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt \
        app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt
git commit -m "test(sleep): update androidTest SettingsRepository fakes for predictive sleep methods"
```

---

## Task 2: PredictiveSleepScheduler (interface + AlarmManager impl)

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/PredictiveSleepScheduler.kt`

- [ ] **Step 2.1: Create PredictiveSleepScheduler.kt**

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.PredictiveSleepReceiver
import java.time.Instant

interface PredictiveSleepScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, bestEstimate: Instant)
    fun cancelPredictiveReminder()
}

class PredictiveSleepSchedulerImpl(private val context: Context) : PredictiveSleepScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedulePredictiveReminderAt(
        triggerTime: Instant,
        bestEstimate: Instant,
    ) {
        val pi = buildPendingIntent(bestEstimate)
        alarmManager.cancel(pi)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.toEpochMilli(),
                    pi,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.toEpochMilli(),
                    pi,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
        }
    }

    override fun cancelPredictiveReminder() {
        alarmManager.cancel(buildPendingIntent(Instant.EPOCH))
    }

    private fun buildPendingIntent(bestEstimate: Instant): PendingIntent {
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, bestEstimate.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context,
            PredictiveSleepReceiver.REQUEST_CODE_PREDICTIVE_SLEEP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "PredictiveSleepScheduler"
    }
}
```

Note: `PredictiveSleepReceiver` doesn't exist yet — this file will show a compile error until Task 4. That's fine; both are wired up before the first build.

- [ ] **Step 2.2: Commit (after Task 4 compiles cleanly)**

Hold commit — commit together with receiver in Task 4, Step 4.4.

---

## Task 3: NotificationHelper constants + PredictiveSleepNotificationHelper

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Create: `app/src/main/java/com/babytracker/util/PredictiveSleepNotificationHelper.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 3.1: Add constants to NotificationHelper.kt**

In `NotificationHelper.kt`, add after the existing `PREDICTIVE_FEED_NOTIFICATION_ID = 1006` and `EXTRA_NAV_ROUTE` constants (around line 611–613):

```kotlin
const val PREDICTIVE_SLEEP_CHANNEL_ID = "predictive_sleep_notifications"
const val PREDICTIVE_SLEEP_NOTIFICATION_ID = 1008  // 1007 is NAP_REMINDER_NOTIFICATION_ID
```

- [ ] **Step 3.2: Add string resources to strings.xml**

In `app/src/main/res/values/strings.xml`, add after the last nap reminder string (after `<string name="notif_body_nap_reminder">`):

```xml
<!-- Predictive sleep reminder channel -->
<string name="notif_channel_predictive_sleep_name">Sleep reminders</string>
<string name="notif_channel_predictive_sleep_description">Soft reminders before a likely sleep window</string>

<!-- Predictive sleep reminder notification -->
<string name="notif_title_predictive_sleep">Sleep window likely soon</string>
<!-- Args: 1=formatted time, 2=minutes until -->
<string name="notif_body_predictive_sleep">Around %1$s (in %2$d min). Estimated from recent sleep.</string>
<string name="notif_body_predictive_sleep_now">Around %1$s. Estimated from recent sleep.</string>
<string name="notif_action_start_sleep">Start sleep</string>

<!-- Settings — predictive sleep reminders -->
<string name="settings_predictive_sleep_toggle_title">Predictive sleep reminder</string>
<string name="settings_predictive_sleep_toggle_subtitle">Notify before next likely sleep window</string>
```

- [ ] **Step 3.3: Create PredictiveSleepNotificationHelper.kt**

```kotlin
package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.Blue700
import com.babytracker.ui.theme.SecondaryBlueDark
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RC_PREDICTIVE_SLEEP_START = 2001

fun createPredictiveSleepNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID,
            context.getString(R.string.notif_channel_predictive_sleep_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_predictive_sleep_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

fun showPredictiveSleepReminder(
    context: Context,
    bestEstimateMs: Long,
) {
    val nowMs = System.currentTimeMillis()
    val minutesUntil = ((bestEstimateMs - nowMs).coerceAtLeast(0L) / 60_000L).toInt()
    val timeLabel = java.time.Instant.ofEpochMilli(bestEstimateMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
    val title = context.getString(R.string.notif_title_predictive_sleep)
    val body = if (minutesUntil > 0) {
        context.getString(R.string.notif_body_predictive_sleep, timeLabel, minutesUntil)
    } else {
        context.getString(R.string.notif_body_predictive_sleep_now, timeLabel)
    }
    val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val accent = (if (nightMask == Configuration.UI_MODE_NIGHT_YES) SecondaryBlueDark else Blue700).toArgb()

    val startIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
    }
    val startPi = PendingIntent.getActivity(
        context,
        RC_PREDICTIVE_SLEEP_START,
        startIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val builder = NotificationCompat.Builder(context, NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notif_sleep)
        .setColor(accent)
        .setColorized(false)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(startPi)
        .addAction(0, context.getString(R.string.notif_action_start_sleep), startPi)

    context.getSystemService(NotificationManager::class.java)
        .notify(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID, builder.build())
}
```

- [ ] **Step 3.4: Commit (hold — commit together with Task 4)**

---

## Task 4: PredictiveSleepReceiver + AndroidManifest

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 4.1: Create PredictiveSleepReceiver.kt**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.BuildConfig
import com.babytracker.util.showPredictiveSleepReminder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PredictiveSleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Guard against a debug-scheduled alarm firing on a release build after upgrade.
        // The manifest declares the receiver for all build types (required for AlarmManager
        // to resolve the PendingIntent), so this is the last line of defence.
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "Dropping alarm in release build")
            return
        }
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val bestEstimateMs = intent.getLongExtra(EXTRA_BEST_ESTIMATE_MS, 0L)
        if (bestEstimateMs <= 0L) {
            Log.w(TAG, "Missing bestEstimate; dropping fire")
            return
        }
        val nowMs = System.currentTimeMillis()
        val staleAfterMs = bestEstimateMs + MAX_STALE_MINUTES * 60_000L
        if (nowMs > staleAfterMs) {
            Log.i(TAG, "Prediction stale by ${(nowMs - bestEstimateMs) / 60_000L}m; dropping fire")
            return
        }
        try {
            showPredictiveSleepReminder(
                context = context,
                bestEstimateMs = bestEstimateMs,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_SLEEP_FIRE"
        const val EXTRA_BEST_ESTIMATE_MS = "best_estimate_ms"
        const val REQUEST_CODE_PREDICTIVE_SLEEP = 1005
        private const val MAX_STALE_MINUTES = 20L
        private const val TAG = "PredictiveSleepRx"
    }
}
```

- [ ] **Step 4.2: Declare PredictiveSleepReceiver in AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`, add after the `PredictiveFeedReceiver` block (after line 87):

```xml
<receiver
    android:name=".receiver.PredictiveSleepReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.PREDICTIVE_SLEEP_FIRE" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4.3: Run build to confirm Tasks 2–4 compile cleanly**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 4.4: Commit Tasks 2–4**

```
git add app/src/main/java/com/babytracker/manager/PredictiveSleepScheduler.kt \
        app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt \
        app/src/main/java/com/babytracker/util/PredictiveSleepNotificationHelper.kt \
        app/src/main/java/com/babytracker/util/NotificationHelper.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat(notification): add PredictiveSleepScheduler, receiver, and notification helper"
```

---

## Task 5: PredictiveSleepNotificationCoordinator

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt`

The coordinator reads `Flow<SleepPredictionState>` from `PredictSleepWindowUseCase`. Only `SleepPredictionState.Window` schedules an alarm; every other subtype cancels. `isInQuietHours` is already defined as `internal` in `PredictiveFeedNotificationCoordinator.kt` in the same module and is directly callable.

- [ ] **Step 5.1: Create PredictiveSleepNotificationCoordinator.kt**

```kotlin
package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveSleepNotificationCoordinator @Inject constructor(
    private val predictSleepWindow: PredictSleepWindowUseCase,
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveSleepScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                predictSleepWindow(),
                settingsRepository.getPredictiveSleepEnabled(),
                settingsRepository.getPredictiveSleepLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
            ) { state, enabled, lead, quietStart, quietEnd ->
                ReconcileParams(state, enabled, lead, quietStart, quietEnd)
            }
                .debounce(DEBOUNCE_MS)
                .collect { params ->
                    reconcile(
                        params.state,
                        params.enabled,
                        params.leadMinutes,
                        params.quietStartMinute,
                        params.quietEndMinute,
                    )
                }
        }
    }

    private fun reconcile(
        state: SleepPredictionState,
        enabled: Boolean,
        leadMinutes: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ) {
        val window = (state as? SleepPredictionState.Window)?.window
        if (!enabled || window == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        if (isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, ZoneId.systemDefault())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        scheduler.schedulePredictiveReminderAt(triggerAt, window.bestEstimate)
    }

    private data class ReconcileParams(
        val state: SleepPredictionState,
        val enabled: Boolean,
        val leadMinutes: Int,
        val quietStartMinute: Int,
        val quietEndMinute: Int,
    )

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
```

- [ ] **Step 5.2: Build check**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.3: Commit**

```
git add app/src/main/java/com/babytracker/manager/PredictiveSleepNotificationCoordinator.kt
git commit -m "feat(notification): add PredictiveSleepNotificationCoordinator"
```

---

## Task 6: DI wiring + BabyTrackerApp startup

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

- [ ] **Step 6.1: Add PredictiveSleepScheduler provider to NotificationSchedulerModule.kt**

In the first `object NotificationSchedulerModule` block, add after the existing `providePredictiveFeedScheduler` function (after line 44):

```kotlin
@Provides
@Singleton
fun providePredictiveSleepScheduler(
    @ApplicationContext context: Context,
): PredictiveSleepScheduler = PredictiveSleepSchedulerImpl(context)
```

Also add the import at the top of the file:
```kotlin
import com.babytracker.manager.PredictiveSleepScheduler
import com.babytracker.manager.PredictiveSleepSchedulerImpl
```

- [ ] **Step 6.2: Wire coordinator and channel in BabyTrackerApp.kt**

Add injection field after `predictiveCoordinator` (line 25):
```kotlin
@Inject lateinit var predictiveSleepCoordinator: PredictiveSleepNotificationCoordinator
```

Add imports at the top:
```kotlin
import com.babytracker.manager.PredictiveSleepNotificationCoordinator
import com.babytracker.util.createPredictiveSleepNotificationChannel
```

In `onCreate()`, add after line 46 (`createPredictiveFeedNotificationChannel(this)`) **inside a `BuildConfig.DEBUG` guard**:
```kotlin
if (BuildConfig.DEBUG) {
    createPredictiveSleepNotificationChannel(this)
    predictiveSleepCoordinator.start()
}
```

The DEBUG guard is required here, not just in the Settings UI. DataStore values persist across builds — a user who enabled the flag in a debug install and then upgrades to a release APK would otherwise have the coordinator running silently in production. The UI gate alone does not prevent this.

- [ ] **Step 6.3: Build check**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.4: Commit**

```
git add app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt
git commit -m "feat(di): wire PredictiveSleepScheduler and start coordinator in BabyTrackerApp"
```

---

## Task 7: Settings UI — SettingsViewModel + SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

### Part A: SettingsViewModel

The `SettingsUiState` needs 2 new fields. The `combine` call currently takes 15 flows (indices 0–14); we add 2 more at indices 15–16. The `showPermissionWarning` condition must include `predictiveSleepEnabled`.

- [ ] **Step 7.1: Add 2 fields to SettingsUiState**

In `SettingsViewModel.kt`, add after line 40 (`val napReminderDelayMinutes: Int = 60,`):

```kotlin
val predictiveSleepEnabled: Boolean = false,
val predictiveSleepLeadMinutes: Int = 15,
```

- [ ] **Step 7.2: Extend the combine to 17 flows**

In `SettingsViewModel.kt`, add 2 flows at the end of the `combine(...)` call (after line 77, `settingsRepository.getNapReminderDelayMinutes(),`):

```kotlin
settingsRepository.getPredictiveSleepEnabled(),
settingsRepository.getPredictiveSleepLeadMinutes(),
```

Inside the `combine` lambda, add after line 93 (`val napReminderDelayMinutes = values[14] as Int`):

```kotlin
val predictiveSleepEnabled = values[15] as Boolean
val predictiveSleepLeadMinutes = values[16] as Int
```

Update the `SettingsUiState(...)` constructor call to include the new fields. Change line 107 (`showPermissionWarning = (predictiveEnabled || napReminderEnabled) && !permissionGranted,`):

```kotlin
showPermissionWarning = (predictiveEnabled || napReminderEnabled || predictiveSleepEnabled) && !permissionGranted,
```

And add to the state constructor:
```kotlin
predictiveSleepEnabled = predictiveSleepEnabled,
predictiveSleepLeadMinutes = predictiveSleepLeadMinutes,
```

- [ ] **Step 7.3: Add 2 event handlers to SettingsViewModel**

Add after `onQuietHoursEndChanged` (after line 158):

```kotlin
fun onPredictiveSleepToggleChanged(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setPredictiveSleepEnabled(enabled) }
}

fun onSleepLeadMinutesChanged(minutes: Int) {
    viewModelScope.launch { settingsRepository.setPredictiveSleepLeadMinutes(minutes) }
}
```

### Part B: SettingsScreen

The Sleep Reminders section currently contains the nap reminder toggle + delay field (lines 477–543). We add the predictive sleep subsection inside the same column, after the `OutlinedTextField` (after line 542), gated by `BuildConfig.DEBUG`.

- [ ] **Step 7.4: Add DEBUG-gated predictive sleep UI to SettingsScreen.kt**

In `SettingsScreen.kt`, add after line 542 (`                )`) and before line 543 (`            }`):

```kotlin
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_predictive_sleep_toggle_title),
                        description = stringResource(R.string.settings_predictive_sleep_toggle_subtitle),
                        checked = uiState.predictiveSleepEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = NotificationManagerCompat.from(context)
                                    .areNotificationsEnabled()
                                viewModel.onPredictiveSleepToggleChanged(true)
                                if (!granted) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.onPredictiveSleepToggleChanged(newValue)
                            }
                        },
                        modifier = Modifier.testTag("predictive_sleep_switch"),
                        leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
                    )
                    LeadTimeSegmentedRow(
                        enabled = uiState.predictiveSleepEnabled,
                        selectedMinutes = uiState.predictiveSleepLeadMinutes,
                        onSelect = viewModel::onSleepLeadMinutesChanged,
                    )
                    val is24Hour = DateFormat.is24HourFormat(context)
                    QuietHoursRow(
                        enabled = uiState.predictiveSleepEnabled,
                        startMinute = uiState.quietHoursStartMinute,
                        endMinute = uiState.quietHoursEndMinute,
                        is24Hour = is24Hour,
                        onStartPicked = viewModel::onQuietHoursStartChanged,
                        onEndPicked = viewModel::onQuietHoursEndChanged,
                    )
                }
```

Add `import com.babytracker.BuildConfig` to the imports of `SettingsScreen.kt` if not already present.

- [ ] **Step 7.5: Update SettingsViewModelTest.kt mock setup**

In `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt`, locate the `@BeforeEach` setup block (around line 68–69, after `getNapReminderDelayMinutes` stub). Add immediately after:

```kotlin
every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
every { settingsRepository.getPredictiveSleepLeadMinutes() } returns flowOf(15)
```

Also find the `showPermissionWarning` test at line ~198. If it covers nap-reminder + denied, add a parallel test:

```kotlin
@Test
fun `showPermissionWarning true when predictive sleep enabled and notifications denied`() = runTest {
    every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
    val deniedChecker = mockk<NotificationPermissionChecker> {
        every { areNotificationsEnabled() } returns false
    }
    // rebuild viewModel with deniedChecker — follow the same pattern as the nap-reminder variant
    // in this file to construct the ViewModel and assert uiState.showPermissionWarning == true
}
```

Note: mirror the exact ViewModel construction pattern used by the existing nap-reminder `showPermissionWarning` test in this file.

- [ ] **Step 7.6: Update SettingsViewModelPredictiveTest.kt mock setup**

In `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt`, locate the `@BeforeEach` setup block (around line 63–64, after `getNapReminderDelayMinutes` stub). Add immediately after:

```kotlin
every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
every { settingsRepository.getPredictiveSleepLeadMinutes() } returns flowOf(15)
```

- [ ] **Step 7.7: Build check**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.8: Run fast unit tests**

```
./gradlew :app:testDebugUnitTest -PfastTests
```
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7.9: Commit**

```
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt
git commit -m "feat(sleep): add predictive sleep settings to SettingsViewModel and SettingsScreen (DEBUG-gated)"
```

---

## Task 8: Unit Tests — PredictiveSleepNotificationCoordinatorTest

**Files:**
- Create: `app/src/test/java/com/babytracker/manager/PredictiveSleepNotificationCoordinatorTest.kt`

The test mirrors `PredictiveFeedNotificationCoordinatorTest` exactly, substituting:
- `PredictNextFeedUseCase` → `PredictSleepWindowUseCase`
- `FeedPrediction?` flow → `SleepPredictionState` flow
- `prediction(at)` helper → `windowState(at)` returning `SleepPredictionState.Window`
- "null prediction" → non-Window state (e.g. `SleepPredictionState.NeedMoreData(...)`)

- [ ] **Step 8.1: Create the test file**

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveSleepNotificationCoordinatorTest {

    private fun windowState(bestEstimate: Instant): SleepPredictionState.Window =
        SleepPredictionState.Window(
            window = SleepWindow(
                windowStart = bestEstimate.minusSeconds(1800),
                windowEnd = bestEstimate.plusSeconds(1800),
                bestEstimate = bestEstimate,
                confidence = Confidence.MEDIUM,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "Safe to sleep",
            ),
        )

    private fun needMoreData(): SleepPredictionState.NeedMoreData =
        SleepPredictionState.NeedMoreData(
            progress = EvidenceProgress(
                completedIntervals = 1,
                requiredIntervals = 3,
                localDays = 1,
                requiredLocalDays = 2,
                hint = "Need more data",
            ),
        )

    @Test
    fun `schedules alarm when enabled and state is Window in the future`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val state = windowState(bestEstimate)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(state),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when toggle is off`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val enabled = MutableStateFlow(true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(Instant.now().plusSeconds(3600))),
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
    fun `cancels alarm when state is NeedMoreData (not Window)`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val stateFlow = MutableStateFlow<SleepPredictionState>(windowState(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = stateFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        stateFlow.value = needMoreData()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when state is CurrentlySleeping`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(SleepPredictionState.CurrentlySleeping),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `skips scheduling and cancels when trigger is in the past`() = runTest {
        // bestEstimate = now + 5m, leadMinutes = 15 → triggerAt = now − 10m → cancel
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(Instant.now().plusSeconds(300))),
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
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val lead = MutableStateFlow(15)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = lead,
        )
        coordinator.start()
        advanceTimeBy(300)
        lead.value = 30
        advanceTimeBy(300)
        verify(exactly = 2) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
    }

    @Test
    fun `debounces rapid emissions inside 200ms`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val stateFlow = MutableStateFlow<SleepPredictionState>(windowState(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = stateFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(50)
        stateFlow.value = windowState(Instant.now().plusSeconds(3700))
        advanceTimeBy(50)
        stateFlow.value = windowState(Instant.now().plusSeconds(3800))
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), any()) }
    }

    @Test
    fun `cancels alarm when trigger falls inside quiet hours`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val leadMinutes = 15
        val triggerAt = bestEstimate.minusSeconds(leadMinutes * 60L)
        val triggerMinuteOfDay = triggerAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .let { it.hour * 60 + it.minute }
        val quietStart = triggerMinuteOfDay
        val quietEnd = (triggerMinuteOfDay + 60) % 1440
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(leadMinutes),
            quietStartFlow = MutableStateFlow(quietStart),
            quietEndFlow = MutableStateFlow(quietEnd),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `schedules alarm when quiet hours are disabled (start equals end)`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(0),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    private fun TestScope.buildCoordinator(
        scheduler: PredictiveSleepScheduler,
        stateFlow: MutableStateFlow<SleepPredictionState>,
        enabledFlow: MutableStateFlow<Boolean>,
        leadFlow: MutableStateFlow<Int>,
        quietStartFlow: MutableStateFlow<Int> = MutableStateFlow(0),
        quietEndFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    ): PredictiveSleepNotificationCoordinator {
        val useCase = mockk<PredictSleepWindowUseCase>().also {
            every { it.invoke() } returns stateFlow
        }
        val settings = mockk<SettingsRepository>().also {
            every { it.getPredictiveSleepEnabled() } returns enabledFlow
            every { it.getPredictiveSleepLeadMinutes() } returns leadFlow
            every { it.getQuietHoursStartMinute() } returns quietStartFlow
            every { it.getQuietHoursEndMinute() } returns quietEndFlow
        }
        return PredictiveSleepNotificationCoordinator(
            predictSleepWindow = useCase,
            settingsRepository = settings,
            scheduler = scheduler,
            applicationScope = backgroundScope,
        )
    }
}
```

Note: `SleepWindow.Confidence` is referenced above. If the actual sealed class or enum in `SleepWindow.kt` uses a different name, adjust to match. Run the build once to confirm the import, then fix if needed.

- [ ] **Step 8.2: Run the coordinator unit tests**

```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.PredictiveSleepNotificationCoordinatorTest" -PfastTests
```
Expected: 8 tests PASSED.

- [ ] **Step 8.3: Commit**

```
git add app/src/test/java/com/babytracker/manager/PredictiveSleepNotificationCoordinatorTest.kt
git commit -m "test(notification): add PredictiveSleepNotificationCoordinatorTest"
```

---

## Task 9: Instrumentation Tests — PredictiveSleepReceiverTest

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/receiver/PredictiveSleepReceiverTest.kt`

No snooze action for sleep — only `ACTION_FIRE`. 4 test cases mirror `PredictiveFeedReceiverTest`.

- [ ] **Step 9.1: Create PredictiveSleepReceiverTest.kt**

```kotlin
package com.babytracker.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveSleepNotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PredictiveSleepReceiverTest {

    private lateinit var context: Context
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(NotificationManager::class.java)
        createPredictiveSleepNotificationChannel(context)
        nm.cancel(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        awaitNotificationAbsent(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
    }

    @Test
    fun postsNotificationOnFireAction() {
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(
                PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS,
                Instant.now().plusSeconds(15 * 60L).toEpochMilli(),
            )
        }
        PredictiveSleepReceiver().onReceive(context, intent)

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals(NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID, posted!!.notification.channelId)
    }

    @Test
    fun dropsFireWhenPredictionAlreadyStale() {
        val stale = Instant.now().minusSeconds(30 * 60L).toEpochMilli()
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, stale)
        }
        PredictiveSleepReceiver().onReceive(context, intent)

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID })
    }

    @Test
    fun fireCopyRecomputesMinutesFromNow() {
        val bestEstimateMs = Instant.now().plusSeconds(5 * 60L).toEpochMilli()
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, bestEstimateMs)
        }
        PredictiveSleepReceiver().onReceive(context, intent)

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        assertNotNull("Notification not posted", posted)
        val body = posted!!.notification.extras.getCharSequence("android.text").toString()
        assertTrue("Body did not recompute minutes: $body", body.contains("in 4 min") || body.contains("in 5 min"))
    }

    @Test
    fun dropsFireWhenBestEstimateIsZero() {
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
        }
        PredictiveSleepReceiver().onReceive(context, intent)

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID })
    }

    private fun awaitNotification(id: Int, timeoutMs: Long = 2_000): android.service.notification.StatusBarNotification? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val n = nm.activeNotifications.firstOrNull { it.id == id }
            if (n != null) return n
            Thread.sleep(100)
        }
        return null
    }

    private fun awaitNotificationAbsent(id: Int, timeoutMs: Long = 2_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (nm.activeNotifications.none { it.id == id }) return
            Thread.sleep(100)
        }
    }
}
```

- [ ] **Step 9.2: Run full test suite (unit + architecture)**

```
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, 0 failures (instrumentation tests require a device — run separately).

- [ ] **Step 9.3: Commit**

```
git add app/src/androidTest/java/com/babytracker/receiver/PredictiveSleepReceiverTest.kt
git commit -m "test(notification): add PredictiveSleepReceiverTest instrumentation test"
```

---

## Self-Review Checklist

- [x] `PredictiveSleepSchedulerImpl` handles `SecurityException` gracefully (inexact fallback) — Task 2
- [x] Coordinator: enabled/disabled — Task 8 tests 1–2
- [x] Coordinator: lead minutes — Task 8 test 6
- [x] Coordinator: quiet hours — Task 8 tests 7–8
- [x] Coordinator: only-Window-schedules — Tasks 5 + 8 tests 3–4
- [x] Coordinator: overdue-cancels — `SleepPredictionState.Overdue` is not `Window`, covered by Task 8 test 4 (`CurrentlySleeping`) pattern; add `Overdue` variant if desired
- [x] Coordinator: past-trigger cancel — Task 8 test 5
- [x] `predictiveSleepEnabled` not visible in release build — `BuildConfig.DEBUG` gate in SettingsScreen Task 7
- [x] **Coordinator + channel creation gated by `BuildConfig.DEBUG` in `BabyTrackerApp`** — Task 6 Step 6.2; prevents coordinator running in release when DataStore flag persists from debug install
- [x] **`PredictiveSleepReceiver.onReceive` guarded by `BuildConfig.DEBUG`** — Task 4 Step 4.1; receiver must be declared in manifest for all builds so the OS can resolve the PendingIntent, but early-return drops any alarm that fires post-upgrade to release
- [x] **All three androidTest `SettingsRepository` fakes updated** — Task 1b; `compileDebugAndroidTestKotlin` verified before commit
- [x] **`SettingsViewModelTest` + `SettingsViewModelPredictiveTest` stubs updated** — Task 7 Steps 7.5–7.6; prevents MockK "no answer" crash on ViewModel init in unit tests
- [x] `PredictiveSleepReceiver` instrumentation test — Task 9
- [x] Coordinator unit tests mirror `PredictiveFeedNotificationCoordinatorTest` — Task 8
- [x] String resources for all new UI text — Task 3
- [x] AndroidManifest receiver declaration — Task 4
- [x] Notification ID/channel ID constants collision-free (1008 / `predictive_sleep_notifications`) — Task 3; 1007 is already `NAP_REMINDER_NOTIFICATION_ID`

All types, method names, and import paths match observed code. `Confidence` is a standalone `enum class { LOW, MEDIUM, HIGH }` at `domain/model/Confidence.kt` — imported directly, not qualified as `SleepWindow.Confidence`.
