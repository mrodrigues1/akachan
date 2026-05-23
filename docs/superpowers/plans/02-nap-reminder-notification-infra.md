# Nap Reminder Notification Infrastructure — Implementation Plan

LINEAR_ISSUE: AKA-31

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the AlarmManager-based scheduling infrastructure, BroadcastReceiver with quiet-hours logic, and `NotificationHelper.showNapReminder()` that fire the nap reminder notification.

**Architecture:** `NapReminderScheduler` (interface) / `NapReminderManager` (impl) follow the same AlarmManager pattern as `PredictiveFeedSchedulerImpl`. `NapReminderReceiver` fires on the alarm, reads quiet hours from `SettingsRepository` at fire time, and either suppresses or posts via `NotificationHelper`. The receiver follows the `SleepActionReceiver` `goAsync()` + coroutine pattern with an `internal fun handle()` for unit testability.

**Tech Stack:** Kotlin, AlarmManager, `@AndroidEntryPoint` BroadcastReceiver, Hilt, NotificationCompat, JUnit 5, MockK

**Suggested branch:** `feat/nap-reminder-notification-infra`

**Dependencies:** Requires Plan 01 (AKA-30) merged first — `SettingsRepository.getNapReminderEnabled()` is read by `NapReminderReceiver.handle()` at alarm-fire time as a defense-in-depth check: if the user disabled the feature after the alarm was set, the receiver suppresses the notification. `getQuietHoursStartMinute()` and `getQuietHoursEndMinute()` already exist in the current codebase before Plan 01.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `manager/NapReminderScheduler.kt` | Create | Interface: `schedule(napEndTime, delayMinutes)`, `cancel()` |
| `manager/NapReminderManager.kt` | Create | AlarmManager impl: compute `triggerAt`, cancel-then-schedule with exact/inexact fallback |
| `receiver/NapReminderReceiver.kt` | Create | `@AndroidEntryPoint` receiver: quiet hours check, call `showNapReminder()` |
| `util/NotificationHelper.kt` | Modify | Add `NAP_REMINDER_NOTIFICATION_ID`, `showNapReminder()`, private tap pending intent |
| `app/src/main/res/values/strings.xml` | Modify | Add notification title and body strings |
| `di/NotificationSchedulerModule.kt` | Modify | Add abstract module to bind `NapReminderScheduler → NapReminderManager` |
| `app/src/main/AndroidManifest.xml` | Modify | Register `NapReminderReceiver` with an `android:name` intent-filter action |
| `src/test/.../NapReminderManagerTest.kt` | Create | Unit tests: `triggerAt` computation, cancel-before-schedule, `cancel()` |
| `src/test/.../NapReminderReceiverTest.kt` | Create | Unit tests: quiet window suppression (normal + overnight), posts outside window |

---

### Task 1: NapReminderScheduler interface

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/NapReminderScheduler.kt`

- [ ] **Step 1: Create the interface file**

```kotlin
package com.babytracker.manager

import java.time.Instant

interface NapReminderScheduler {
    fun schedule(napEndTime: Instant, delayMinutes: Int)
    fun cancel()
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -10
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/NapReminderScheduler.kt
git commit -m "feat(notification): add NapReminderScheduler interface"
```

---

### Task 2: NapReminderManager implementation (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/NapReminderManager.kt`
- Create: `app/src/test/java/com/babytracker/manager/NapReminderManagerTest.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class NapReminderManagerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var manager: NapReminderManager
    private lateinit var mockPi: PendingIntent

    @BeforeEach
    fun setup() {
        alarmManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.babytracker"

        mockkStatic(PendingIntent::class)
        mockPi = mockk(relaxed = true)
        every {
            PendingIntent.getBroadcast(any(), eq(NapReminderManager.RC_NAP_REMINDER), any(), any())
        } returns mockPi

        manager = NapReminderManager(context)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `schedule computes triggerAt as napEndTime plus delayMinutes`() {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")
        val delayMinutes = 60
        val triggerSlot = slot<Long>()
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), capture(triggerSlot), any())
        } just runs

        manager.schedule(napEnd, delayMinutes)

        val expected = napEnd.plusSeconds(60 * 60L).toEpochMilli()
        assertEquals(expected, triggerSlot.captured)
    }

    @Test
    fun `schedule cancels existing alarm before setting new one`() {
        val napEnd = Instant.parse("2026-01-01T10:00:00Z")

        manager.schedule(napEnd, 30)

        verifyOrder {
            alarmManager.cancel(mockPi)
            alarmManager.setExactAndAllowWhileIdle(any(), any(), mockPi)
        }
    }

    @Test
    fun `schedule passes RTC_WAKEUP as alarm type`() {
        manager.schedule(Instant.now(), 60)

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `cancel calls alarmManager cancel with the same PendingIntent`() {
        manager.cancel()

        verify { alarmManager.cancel(mockPi) }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.NapReminderManagerTest" 2>&1 | tail -20
```

Expected: compile error — `NapReminderManager` does not exist yet. If tests pass, stop — the class already exists.

- [ ] **Step 3: Create NapReminderManager**

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.NapReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NapReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : NapReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(napEndTime: Instant, delayMinutes: Int) {
        val pi = buildPendingIntent()
        alarmManager.cancel(pi)
        val triggerAtMs = napEndTime.plusSeconds(delayMinutes.toLong() * 60).toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM permission revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    override fun cancel() {
        alarmManager.cancel(buildPendingIntent())
    }

    private fun buildPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_NAP_REMINDER,
        Intent(context, NapReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val RC_NAP_REMINDER = 3001
        private const val TAG = "NapReminderManager"
    }
}
```

Note: `NapReminderReceiver` does not exist yet. The compiler will error on the import — that's expected. The test still compiles because it only references `NapReminderManager`, not the receiver. The full build will be fixed in Task 4.

- [ ] **Step 4: Run NapReminderManagerTest specifically**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.NapReminderManagerTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 4 tests pass. (The test classpath does not need `NapReminderReceiver` to exist since it's only referenced in the `Intent` constructor at runtime, not at compile-time of the test itself.)

If compile still fails due to missing `NapReminderReceiver`, create a minimal stub first:
```kotlin
// app/src/main/java/com/babytracker/receiver/NapReminderReceiver.kt
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NapReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
```

Then re-run the manager tests before replacing with the real implementation in Task 4.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/NapReminderManager.kt
git add app/src/test/java/com/babytracker/manager/NapReminderManagerTest.kt
git commit -m "feat(notification): add NapReminderManager and unit tests"
```

---

### Task 3: NotificationHelper.showNapReminder and string resources

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/main/res/values/strings.xml`

This task has no new test file — `showNapReminder` is tested indirectly through `NapReminderReceiverTest` in Task 4 via `mockkObject(NotificationHelper)`.

- [ ] **Step 1: Add notification string resources to strings.xml**

Add before `</resources>` (after the last `settings_quiet_*` entry or any other block already present):

```xml
<!-- Nap reminder notification -->
<string name="notif_title_nap_reminder">Next nap window approaching</string>
<string name="notif_body_nap_reminder">Consider starting the next nap soon</string>
```

- [ ] **Step 2: Add NAP_REMINDER_NOTIFICATION_ID constant to NotificationHelper**

In `NotificationHelper.kt`, add after `BREASTFEEDING_GROUP_SUMMARY_NOTIFICATION_ID = 1005`:

```kotlin
const val NAP_REMINDER_NOTIFICATION_ID = 1007
```

(1006 is already used by `PREDICTIVE_FEED_NOTIFICATION_ID` at the bottom of the file.)

- [ ] **Step 3: Add RC_NAP_REMINDER_TAP private constant**

In `NotificationHelper.kt`, in the private constants block (after `RC_RESUME_BF_ACTIVE = 2009`), add:

```kotlin
private const val RC_NAP_REMINDER_TAP = 3002
```

- [ ] **Step 4: Add Routes import to NotificationHelper.kt**

Add this import at the top of `NotificationHelper.kt` (with existing imports):

```kotlin
import com.babytracker.navigation.Routes
```

- [ ] **Step 5: Add showNapReminder() method and its tap PendingIntent helper**

Add these two functions after `cancelNotification()` and before `mainActivityPendingIntent()` (or after `mainActivityPendingIntent()` — anywhere in the `object` body is fine):

```kotlin
fun showNapReminder(context: Context) {
    val title = context.getString(R.string.notif_title_nap_reminder)
    val body = context.getString(R.string.notif_body_nap_reminder)
    val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
    val tapPi = napReminderTapPendingIntent(context)
    val notification = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
        .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
        .setContentTitle(title)
        .setContentText(body)
        .setTicker(title)
        .setAutoCancel(true)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setGroup(SLEEP_GROUP_KEY)
        .setContentIntent(tapPi)
        .build()
    context.getSystemService(NotificationManager::class.java)
        .notify(NAP_REMINDER_NOTIFICATION_ID, notification)
    Log.d(TAG, "showNapReminder posted")
}

private fun napReminderTapPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        RC_NAP_REMINDER_TAP,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
```

- [ ] **Step 6: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -10
```

Expected: no errors (or only the pre-existing missing `NapReminderReceiver` if the stub from Task 2 was not yet replaced).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/util/NotificationHelper.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(notification): add showNapReminder to NotificationHelper"
```

---

### Task 4: NapReminderReceiver (TDD)

**Files:**
- Create (or replace stub): `app/src/main/java/com/babytracker/receiver/NapReminderReceiver.kt`
- Create: `app/src/test/java/com/babytracker/receiver/NapReminderReceiverTest.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class NapReminderReceiverTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var receiver: NapReminderReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        receiver = NapReminderReceiver()
        receiver.settingsRepository = settingsRepository
        mockkObject(NotificationHelper)
        mockkStatic(LocalTime::class)
        every { NotificationHelper.showNapReminder(any()) } returns Unit
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun stubQuietHours(startMinute: Int, endMinute: Int) {
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(startMinute)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(endMinute)
    }

    private fun stubCurrentTime(hourOfDay: Int, minute: Int) {
        val localTime = mockk<LocalTime>()
        every { localTime.hour } returns hourOfDay
        every { localTime.minute } returns minute
        every { LocalTime.now() } returns localTime
    }

    @Test
    fun `posts notification when current time is outside normal quiet window`() = runTest {
        stubQuietHours(startMinute = 0, endMinute = 480)   // 00:00–08:00
        stubCurrentTime(hourOfDay = 10, minute = 0)        // 10:00 → minute 600

        receiver.handle(context)

        verify(exactly = 1) { NotificationHelper.showNapReminder(context) }
    }

    @Test
    fun `suppresses notification when current time is inside normal quiet window`() = runTest {
        stubQuietHours(startMinute = 0, endMinute = 480)   // 00:00–08:00
        stubCurrentTime(hourOfDay = 1, minute = 0)         // 01:00 → minute 60

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `suppresses notification when inside overnight quiet window (wraps midnight)`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00 overnight
        stubCurrentTime(hourOfDay = 0, minute = 30)         // 00:30 → minute 30

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `posts notification when outside overnight quiet window`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00 overnight
        stubCurrentTime(hourOfDay = 5, minute = 0)          // 05:00 → minute 300

        receiver.handle(context)

        verify(exactly = 1) { NotificationHelper.showNapReminder(context) }
    }

    @Test
    fun `suppresses notification when current time equals start of overnight window`() = runTest {
        stubQuietHours(startMinute = 1380, endMinute = 120) // 23:00–02:00
        stubCurrentTime(hourOfDay = 23, minute = 0)         // 23:00 → minute 1380

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }

    @Test
    fun `suppresses notification when nap reminder feature is disabled`() = runTest {
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
        stubQuietHours(startMinute = 0, endMinute = 480)
        stubCurrentTime(hourOfDay = 10, minute = 0)         // outside quiet window

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showNapReminder(any()) }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.NapReminderReceiverTest" 2>&1 | tail -30
```

Expected: compile error — `NapReminderReceiver.handle` does not exist, or the stub from Task 2 lacks the `settingsRepository` field and `handle` function. Also expected: `getNapReminderEnabled` does not exist on `SettingsRepository` (since Plan 01 is not yet implemented). That is fine — confirm the error and proceed.

- [ ] **Step 3: Create (or replace) NapReminderReceiver with full implementation**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class NapReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(10_000L) {
                    handle(context)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context) {
        val enabled = settingsRepository.getNapReminderEnabled().first()
        if (!enabled) {
            Log.d(TAG, "Nap reminder disabled; suppressing stale alarm")
            return
        }

        val startMinute = settingsRepository.getQuietHoursStartMinute().first()
        val endMinute = settingsRepository.getQuietHoursEndMinute().first()
        val now = LocalTime.now()
        val currentMinute = now.hour * 60 + now.minute

        val inQuietWindow = if (startMinute <= endMinute) {
            currentMinute in startMinute until endMinute
        } else {
            currentMinute >= startMinute || currentMinute < endMinute
        }

        if (inQuietWindow) {
            Log.d(TAG, "In quiet window ($startMinute–$endMinute min); suppressing nap reminder")
            return
        }

        try {
            NotificationHelper.showNapReminder(context)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping nap reminder", e)
        }
    }

    companion object {
        private const val TAG = "NapReminderReceiver"
    }
}
```

- [ ] **Step 4: Run receiver tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.NapReminderReceiverTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/NapReminderReceiver.kt
git add app/src/test/java/com/babytracker/receiver/NapReminderReceiverTest.kt
git commit -m "feat(notification): add NapReminderReceiver with quiet-hours check and unit tests"
```

---

### Task 5: DI binding and AndroidManifest.xml registration

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add NapReminderScheduler binding to NotificationSchedulerModule.kt**

Add a new abstract Hilt module class at the end of the file (after `NotificationPermissionCheckerModule`):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class NapReminderModule {
    @Binds
    @Singleton
    abstract fun bindNapReminderScheduler(impl: NapReminderManager): NapReminderScheduler
}
```

The full updated file will have these imports at the top (add any missing):

```kotlin
import com.babytracker.manager.NapReminderManager
import com.babytracker.manager.NapReminderScheduler
```

Full updated `NotificationSchedulerModule.kt`:

```kotlin
package com.babytracker.di

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NapReminderManager
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.manager.NotificationPermissionCheckerImpl
import com.babytracker.manager.NotificationScheduler
import com.babytracker.manager.PredictiveFeedScheduler
import com.babytracker.manager.PredictiveFeedSchedulerImpl
import com.babytracker.manager.SleepNotificationManager
import com.babytracker.manager.SleepNotificationScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationSchedulerModule {

    @Provides
    @Singleton
    fun provideNotificationScheduler(
        @ApplicationContext context: Context
    ): NotificationScheduler = BreastfeedingNotificationManager(context)

    @Provides
    @Singleton
    fun provideSleepNotificationScheduler(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): SleepNotificationScheduler = SleepNotificationManager(context, settingsRepository)

    @Provides
    @Singleton
    fun providePredictiveFeedScheduler(
        @ApplicationContext context: Context
    ): PredictiveFeedScheduler = PredictiveFeedSchedulerImpl(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPermissionCheckerModule {

    @Binds
    @Singleton
    abstract fun bindNotificationPermissionChecker(
        impl: NotificationPermissionCheckerImpl
    ): NotificationPermissionChecker
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NapReminderModule {

    @Binds
    @Singleton
    abstract fun bindNapReminderScheduler(impl: NapReminderManager): NapReminderScheduler
}
```

- [ ] **Step 2: Register NapReminderReceiver in AndroidManifest.xml**

In `AndroidManifest.xml`, add after the `PredictiveFeedReceiver` `</receiver>` block (before `</application>`):

```xml
<receiver
    android:name=".receiver.NapReminderReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.NAP_REMINDER_FIRE" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: Run full unit test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, zero failures.

- [ ] **Step 4: Format and lint**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations. Fix any that appear (never use `@Suppress`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat(notification): register NapReminderReceiver and add DI binding"
```

---

## Acceptance Criteria

- [ ] `NapReminderScheduler` interface exists with `schedule(Instant, Int)` and `cancel()`
- [ ] `NapReminderManager.schedule()` computes `triggerAt = napEndTime + delayMinutes * 60s`, cancels existing alarm before scheduling, calls `setExactAndAllowWhileIdle` (or `setAndAllowWhileIdle` on fallback)
- [ ] `NapReminderManager.cancel()` cancels the same `PendingIntent` (request code `RC_NAP_REMINDER = 3001`)
- [ ] `NotificationHelper.NAP_REMINDER_NOTIFICATION_ID == 1007`
- [ ] `NotificationHelper.showNapReminder()` builds on `SLEEP_CHANNEL_ID`, blue accent, `PRIORITY_DEFAULT`, `CATEGORY_REMINDER`, tap opens `MainActivity` with `EXTRA_NAV_ROUTE = Routes.SLEEP_TRACKING`, no action buttons
- [ ] `NapReminderReceiver.handle()` reads `getNapReminderEnabled()` first; suppresses and returns immediately when false
- [ ] `NapReminderReceiver.handle()` suppresses if current minute is inside quiet window (checked only when enabled); suppression handles overnight wrap-around (start > end)
- [ ] `NapReminderReceiver` registered in `AndroidManifest.xml`
- [ ] `NapReminderScheduler` bound to `NapReminderManager` via `@Binds @Singleton` in `NapReminderModule`
- [ ] 4 `NapReminderManagerTest` tests pass
- [ ] 6 `NapReminderReceiverTest` tests pass (5 quiet-hours + 1 disabled)
- [ ] `./gradlew ktlintFormat && ./gradlew detekt` clean
- [ ] `./gradlew :app:testDebugUnitTest` — zero failures including pre-existing tests
