# Predictive Feeding — Task 4: Notification channel + helper + `PredictiveFeedReceiver`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the new `PREDICTIVE_FEED_CHANNEL_ID` channel, a `NotificationHelper.showPredictiveReminder()` builder (BigTextStyle, Pink700 accent, Start-feeding + Snooze-15m actions), and the `PredictiveFeedReceiver` that posts the notification on `ACTION_FIRE` (with stale-prediction suppression and lead-minutes recomputed from current time) and reschedules a 15-minute alarm on `ACTION_SNOOZE`. Register the receiver in the manifest. Wire channel creation in `BabyTrackerApp.onCreate`. Add an explicit notification-deep-link contract: Start-feeding routes through `MainActivity` to `Routes.BREASTFEEDING` via an intent extra consumed by the Compose host.

**Architecture:** Mirrors the existing breastfeeding/sleep notification pattern. New constants live alongside `BREASTFEEDING_CHANNEL_ID` in `NotificationHelper`. Receiver is `@AndroidEntryPoint` so Task 5 can inject the scheduler — for this PR, the snooze path uses a local AlarmManager call; Task 5 refactors it to go through the scheduler interface. Receiver recomputes `leadMinutes` from `Instant.now()` at every fire so a delayed alarm or stale snooze never posts misleading "in N min" copy; if the predicted time has already passed beyond `MAX_STALE_MINUTES`, the receiver drops the notification entirely. Start-feeding's `PendingIntent` carries `EXTRA_NAV_ROUTE = Routes.BREASTFEEDING` (no autostart); `MainActivity` consumes the extra and navigates the existing `NavHostController` to that route after composition.

**Tech Stack:** Kotlin · AndroidX `NotificationCompat` · AlarmManager · Hilt · Compose UI Test / instrumentation.

**Branch:** `feat/predictive-feeding-4-notification`
**Linear issue:** Predictive feeding — channel, helper, receiver
**Depends on:** Task 1
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/PredictiveFeedReceiver.kt`
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`
- Modify: `app/src/main/java/com/babytracker/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/babytracker/receiver/PredictiveFeedReceiverTest.kt`

## Steps

- [ ] **Step 1: Add strings**

Append to `app/src/main/res/values/strings.xml`:

```xml
<string name="notif_channel_predictive_feed_name">Feeding reminders</string>
<string name="notif_channel_predictive_feed_description">Soft reminders before a likely feeding time</string>
<string name="notif_title_predictive_feed">Feeding likely soon</string>
<string name="notif_body_predictive_feed">Around %1$s (in %2$d min). Estimated from recent feeds.</string>
<string name="notif_body_predictive_feed_now">Around %1$s. Estimated from recent feeds.</string>
<string name="notif_action_start_feeding">Start feeding</string>
<string name="notif_action_snooze_15">Snooze 15m</string>
```

- [ ] **Step 2: Add channel + builder to `NotificationHelper.kt`**

Append at the bottom of `object NotificationHelper`:

```kotlin
const val PREDICTIVE_FEED_CHANNEL_ID = "predictive_feed_notifications"
const val PREDICTIVE_FEED_NOTIFICATION_ID = 1006
const val EXTRA_NAV_ROUTE = "com.babytracker.nav.route"
private const val RC_PREDICTIVE_START = 2010

fun createPredictiveFeedNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            PREDICTIVE_FEED_CHANNEL_ID,
            context.getString(R.string.notif_channel_predictive_feed_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_predictive_feed_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

/**
 * Posts the predictive reminder. The caller passes only `predictedAtEpochMs`; this builder
 * recomputes the displayed minutes-until from `Instant.now()` so a late-delivered alarm or
 * stale snooze cannot show an obsolete "in N min" copy. If the prediction is already in the
 * past (>= 0 min remaining), the body falls back to "now".
 */
fun showPredictiveReminder(
    context: Context,
    predictedAtEpochMs: Long,
    snoozePendingIntent: PendingIntent,
) {
    val nowMs = System.currentTimeMillis()
    val minutesUntil = ((predictedAtEpochMs - nowMs).coerceAtLeast(0L) / 60_000L).toInt()
    val timeLabel = java.time.Instant.ofEpochMilli(predictedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
    val title = context.getString(R.string.notif_title_predictive_feed)
    val body = if (minutesUntil > 0) {
        context.getString(R.string.notif_body_predictive_feed, timeLabel, minutesUntil)
    } else {
        context.getString(R.string.notif_body_predictive_feed_now, timeLabel)
    }
    val accent = resolveAccent(context, Pink700, PrimaryPinkDark)

    // Notification-to-screen contract: MainActivity consumes EXTRA_NAV_ROUTE and navigates
    // the existing NavHostController to that route. SINGLE_TOP keeps the existing task so
    // we don't blow away the back stack or any in-progress session.
    val startIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_NAV_ROUTE, Routes.BREASTFEEDING)
    }
    val startPi = PendingIntent.getActivity(
        context, RC_PREDICTIVE_START, startIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val builder = NotificationCompat.Builder(context, PREDICTIVE_FEED_CHANNEL_ID)
        .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(startPi)
        .addAction(0, context.getString(R.string.notif_action_start_feeding), startPi)
        .addAction(0, context.getString(R.string.notif_action_snooze_15), snoozePendingIntent)

    context.getSystemService(NotificationManager::class.java)
        .notify(PREDICTIVE_FEED_NOTIFICATION_ID, builder.build())
}
```

- [ ] **Step 3: Wire channel creation**

In `app/src/main/java/com/babytracker/BabyTrackerApp.kt`, append inside `onCreate()`:

```kotlin
NotificationHelper.createPredictiveFeedNotificationChannel(this)
```

- [ ] **Step 3a: Notification → screen contract in `MainActivity.kt`**

`MainActivity` currently builds `AppNavGraph` purely from `isOnboardingComplete` + `appMode` and ignores its launch `Intent`. Without changes, tapping Start-feeding only resumes `MainActivity` and lands on the default start destination (Home for a normal onboarded user). Add explicit intent → route handling:

1. Add `onNewIntent` to capture later notification taps (the activity is `launchMode="standard"` by default but `FLAG_ACTIVITY_SINGLE_TOP` from the helper means we can be re-entered without a fresh `setContent`):

```kotlin
private val pendingNavRoute = mutableStateOf<String?>(null)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingNavRoute.value = intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE)
    enableEdgeToEdge()
    setContent { /* existing content */ }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingNavRoute.value = intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE)
}
```

2. Inside `setContent`, after `rememberNavController()`, consume the route exactly once. Only allow whitelisted routes so a crafted intent can't deep-link to arbitrary destinations. Do not clear the pending route until after a successful navigation attempt (or if the route is explicitly invalid/disallowed):

```kotlin
val navController = rememberNavController()
val pending by remember { pendingNavRoute }
LaunchedEffect(pending, isOnboardingComplete, appMode) {
    val route = pending ?: return@LaunchedEffect
    if (route !in ALLOWED_NAV_ROUTES) {
        pendingNavRoute.value = null
        return@LaunchedEffect
    }
    if (appMode == AppMode.PARTNER) {
        pendingNavRoute.value = null
        return@LaunchedEffect          // partner mode never feeds
    }
    if (isOnboardingComplete != true) return@LaunchedEffect        // don't skip onboarding; keep pending
    navController.navigate(route) {
        launchSingleTop = true
        // Keep Home in the back stack so system Back returns to it after Start-feeding lands.
        popUpTo(Routes.HOME) { inclusive = false; saveState = true }
    }
    pendingNavRoute.value = null  // clear only after successful navigation
}
```

3. Add the whitelist as a top-level `private val` in `MainActivity.kt` (Task 4 only needs Breastfeeding, but expose the set so Task 6/future deep links extend it):

```kotlin
private val ALLOWED_NAV_ROUTES = setOf(Routes.BREASTFEEDING)
```

Spec invariant: Start-feeding does **not** autostart a session — the user lands on the Breastfeeding screen and presses Start themselves (matches `2026-05-19-predictive-feeding-reminders-design.md` § notification actions).

- [ ] **Step 4: Create `PredictiveFeedReceiver.kt`**

```kotlin
package com.babytracker.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant

@AndroidEntryPoint
class PredictiveFeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            ACTION_SNOOZE -> snooze(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    /**
     * Revalidates the prediction at fire time. Drops the notification if:
     *  - the predicted timestamp is missing/zero, or
     *  - "now" is already past `predictedAtEpochMs + MAX_STALE_MINUTES` (alarm delivered late,
     *    snoozed past the window, or a feed has happened since scheduling).
     *
     * Note: deeper "active session / latest prediction" checks live in Task 5's scheduler,
     * which owns persisted state and use cases. This receiver does the time-only guard.
     */
    private fun postReminder(context: Context, intent: Intent) {
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        if (predictedAtEpochMs <= 0L) {
            Log.w(TAG, "Missing predictedAt; dropping fire")
            return
        }
        val nowMs = System.currentTimeMillis()
        val staleAfterMs = predictedAtEpochMs + MAX_STALE_MINUTES * 60_000L
        if (nowMs > staleAfterMs) {
            Log.i(TAG, "Prediction stale by ${(nowMs - predictedAtEpochMs) / 60_000L}m; dropping fire")
            return
        }
        try {
            NotificationHelper.showPredictiveReminder(
                context = context,
                predictedAtEpochMs = predictedAtEpochMs,
                snoozePendingIntent = buildSnoozePendingIntent(context, predictedAtEpochMs),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
        }
    }

    /**
     * Reschedules a fresh `ACTION_FIRE` 15 minutes from now. The original `predictedAtEpochMs`
     * is carried forward unchanged so the displayed time stays anchored to the actual
     * prediction; the receiver recomputes "in N min" against `Instant.now()` when it fires,
     * so a user who snoozes past the predicted time gets coherent copy (or the stale-guard
     * above drops the post entirely).
     */
    private fun snooze(context: Context, intent: Intent) {
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        val nextTrigger = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L)
        val fireIntent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_PREDICTED_AT_MS, predictedAtEpochMs)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE_PREDICTIVE, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger.toEpochMilli(), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger.toEpochMilli(), pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger.toEpochMilli(), pi)
        }
        NotificationHelper.cancelNotification(context, NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    }

    private fun buildSnoozePendingIntent(
        context: Context,
        predictedAtEpochMs: Long,
    ): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_PREDICTED_AT_MS, predictedAtEpochMs)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_SNOOZE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_FEED_FIRE"
        const val ACTION_SNOOZE = "com.babytracker.PREDICTIVE_FEED_SNOOZE"
        const val EXTRA_PREDICTED_AT_MS = "predicted_at_ms"
        const val REQUEST_CODE_PREDICTIVE = 1003
        const val REQUEST_CODE_SNOOZE = 1004
        private const val SNOOZE_MINUTES = 15L
        /** Drop notifications whose prediction is older than this when the alarm finally fires. */
        private const val MAX_STALE_MINUTES = 20L
        private const val TAG = "PredictiveFeedRx"
    }
}
```

> `EXTRA_LEAD_MINUTES` is intentionally gone — every fire recomputes minutes-until from `System.currentTimeMillis()` against `predictedAtEpochMs` so snooze + late delivery never yield stale "in N min" copy. Task 5's scheduler should match: pass only `predictedAtEpochMs` when constructing the initial `ACTION_FIRE` PendingIntent.

- [ ] **Step 5: Register the receiver in `AndroidManifest.xml`**

Inside `<application>`, add:

```xml
<receiver
    android:name=".receiver.PredictiveFeedReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.PREDICTIVE_FEED_FIRE" />
        <action android:name="com.babytracker.PREDICTIVE_FEED_SNOOZE" />
    </intent-filter>
</receiver>
```

- [ ] **Step 6: Write the instrumentation test**

Create `app/src/androidTest/java/com/babytracker/receiver/PredictiveFeedReceiverTest.kt`:

```kotlin
@Test
fun postsNotificationOnFireAction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
        action = PredictiveFeedReceiver.ACTION_FIRE
        putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS,
            Instant.now().plusSeconds(15 * 60L).toEpochMilli())
    }
    PredictiveFeedReceiver().onReceive(context, intent)
    val nm = context.getSystemService(NotificationManager::class.java)
    val posted = nm.activeNotifications.firstOrNull {
        it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
    }
    assertNotNull(posted)
    assertEquals(NotificationHelper.PREDICTIVE_FEED_CHANNEL_ID, posted!!.notification.channelId)
}

@Test
fun dropsFireWhenPredictionAlreadyStale() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.cancel(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    val stale = Instant.now().minusSeconds(30 * 60L).toEpochMilli() // > MAX_STALE_MINUTES (20)
    val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
        action = PredictiveFeedReceiver.ACTION_FIRE
        putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, stale)
    }
    PredictiveFeedReceiver().onReceive(context, intent)
    val posted = nm.activeNotifications.firstOrNull {
        it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
    }
    assertNull(posted)
}

@Test
fun fireCopyRecomputesMinutesFromNowNotFromExtras() {
    // Predicted in 5 minutes; we should see "in 5 min", regardless of any historical lead.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.cancel(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    val predictedAt = Instant.now().plusSeconds(5 * 60L).toEpochMilli()
    val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
        action = PredictiveFeedReceiver.ACTION_FIRE
        putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
    }
    PredictiveFeedReceiver().onReceive(context, intent)
    val posted = nm.activeNotifications.first {
        it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
    }
    val body = posted.notification.extras.getCharSequence("android.text").toString()
    // Body contains "in 4 min" or "in 5 min" (allow 1m slop for execution time).
    assertTrue("Body did not recompute minutes: $body", body.contains("in 4 min") || body.contains("in 5 min"))
}

@Test
fun snoozeReschedulesAlarmFifteenMinutesOut() {
    // Use Shadow AlarmManager via Robolectric (testImplementation already covers this)
    // OR a Hilt test rule injecting a fake AlarmManager.
    // Assert: after onReceive(ACTION_SNOOZE), a new alarm exists at triggerAtMillis
    // within [now + 14m, now + 16m] window. Verify the alarm intent has ACTION_FIRE and
    // carries the ORIGINAL predictedAtEpochMs (so the next fire revalidates against the
    // anchor, not against "now + 15m").
}

@Test
fun startFeedingActionNavigatesToBreastfeedingRoute() {
    // Tap Start-feeding action → MainActivity launches with EXTRA_NAV_ROUTE = Routes.BREASTFEEDING,
    // and AppNavGraph's NavHostController ends up on Routes.BREASTFEEDING (NOT Routes.HOME).
    // Use ActivityScenario.launch(Intent(...).putExtra(EXTRA_NAV_ROUTE, Routes.BREASTFEEDING))
    // and assert the visible composable via composeTestRule.onNodeWithTag("breastfeeding_screen").assertExists().
    // Guards against the regression where the action only resumed MainActivity and stayed on Home.
}
```

- [ ] **Step 7: Run tests**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.babytracker.receiver.PredictiveFeedReceiverTest"
```

Expected: PASS.

- [ ] **Step 8: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/PredictiveFeedReceiver.kt \
        app/src/main/java/com/babytracker/util/NotificationHelper.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/main/java/com/babytracker/MainActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml \
        app/src/androidTest/java/com/babytracker/receiver/PredictiveFeedReceiverTest.kt
git commit -m "feat(notification): add predictive feeding channel, helper, and receiver"
```

- [ ] **Step 10: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-4-notification
gh pr create --title "feat(notification): predictive feeding channel + receiver" \
  --body "Adds PREDICTIVE_FEED_CHANNEL_ID, NotificationHelper.showPredictiveReminder builder, and PredictiveFeedReceiver with Start/Snooze actions. Task 4 of 6."
```
