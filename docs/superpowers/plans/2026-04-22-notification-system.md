# Notification System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade breastfeeding notifications to rich (progress bar + action buttons) and add a live-chronometer sleep notification, with a per-user Settings toggle to revert all notifications to simple style.

**Architecture:** Session extras flow from `BreastfeedingNotificationManager` → alarm `PendingIntent` → `BreastfeedingNotificationReceiver` → `NotificationHelper`. Two new `@AndroidEntryPoint` receivers (`BreastfeedingActionReceiver`, `SleepActionReceiver`) handle button taps when the process is killed. `SleepNotificationScheduler` mirrors the existing `NotificationScheduler` pattern and is injected into `SleepViewModel`.

**Tech Stack:** Kotlin coroutines + Flow, Hilt (`@AndroidEntryPoint` on BroadcastReceiver), `NotificationCompat.Builder` (BigTextStyle, setProgress, setUsesChronometer), `DataStore<Preferences>`, MockK + JUnit 5.

---

### Task 1: Rich notifications preference — Settings repository layer

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Create: `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt`

- [ ] **Step 1: Add methods to the SettingsRepository interface**

In `SettingsRepository.kt`, add at the end of the interface body:

```kotlin
fun getRichNotificationsEnabled(): Flow<Boolean>
suspend fun setRichNotificationsEnabled(enabled: Boolean)
```

- [ ] **Step 2: Implement in SettingsRepositoryImpl**

In `SettingsRepositoryImpl.kt`, add to the `companion object`:
```kotlin
val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
```

Add the two override implementations after `setAutoUpdateEnabled`:
```kotlin
override fun getRichNotificationsEnabled(): Flow<Boolean> =
    dataStore.data.map { it[RICH_NOTIFICATIONS_ENABLED] ?: true }

override suspend fun setRichNotificationsEnabled(enabled: Boolean) {
    dataStore.edit { it[RICH_NOTIFICATIONS_ENABLED] = enabled }
}
```

- [ ] **Step 3: Write validation tests**

```kotlin
// app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = SettingsRepositoryImpl(dataStore)
    }

    @Test
    fun `getRichNotificationsEnabled returns true when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("rich_notifications_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertTrue(repository.getRichNotificationsEnabled().first())
    }

    @Test
    fun `getRichNotificationsEnabled returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("rich_notifications_enabled")] } returns false
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getRichNotificationsEnabled().first())
    }

    @Test
    fun `setRichNotificationsEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setRichNotificationsEnabled(false)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertFalse(prefs[booleanPreferencesKey("rich_notifications_enabled")]!!)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRepositoryImplTest"
```

Expected: PASS (3 tests).

- [ ] **Step 5: Spec review**

Check against spec section 6:
- ✅ Key name is `rich_notifications_enabled`
- ✅ Default is `true` (absent key returns true)
- ✅ `getRichNotificationsEnabled()` returns `Flow<Boolean>`
- ✅ `setRichNotificationsEnabled(enabled: Boolean)` is a `suspend fun`
- ✅ No migration needed — DataStore returns default for missing keys

- [ ] **Step 6: Code review**

Check:
- `companion object` key is `private` — ✅ follows existing pattern
- `map { it[KEY] ?: true }` — default matches spec (true)
- `dataStore.edit { }` — correct DataStore write pattern, matches all other keys in the file

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt \
        app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt
git commit -m "feat(settings): add rich_notifications_enabled DataStore preference"
```

---

### Task 2: Settings UI — ViewModel toggle + Screen section

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Update SettingsUiState**

Replace the `SettingsUiState` data class in `SettingsViewModel.kt`:
```kotlin
data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
)
```

- [ ] **Step 2: Update SettingsViewModel — combine 6 flows + new callback**

In `SettingsViewModel.kt`, replace the `combine(...)` block inside `init` (currently 5 flows) with one that combines 6:
```kotlin
combine(
    getBabyProfile(),
    settingsRepository.getMaxPerBreastMinutes(),
    settingsRepository.getMaxTotalFeedMinutes(),
    settingsRepository.getThemeConfig(),
    settingsRepository.getAutoUpdateEnabled(),
    settingsRepository.getRichNotificationsEnabled(),
) { values ->
    val baby = values[0] as Baby?
    val maxPerBreast = values[1] as Int
    val maxTotal = values[2] as Int
    val themeConfig = values[3] as ThemeConfig
    val autoUpdate = values[4] as Boolean
    val richNotifications = values[5] as Boolean
    SettingsUiState(
        baby = baby,
        maxPerBreastMinutes = maxPerBreast,
        maxTotalFeedMinutes = maxTotal,
        themeConfig = themeConfig,
        autoUpdateEnabled = autoUpdate,
        richNotificationsEnabled = richNotifications,
    )
}.collect { _uiState.value = it }
```

Add the new callback after `onAutoUpdateChanged`:
```kotlin
fun onRichNotificationsToggled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setRichNotificationsEnabled(enabled) }
}
```

- [ ] **Step 3: Add Notifications section to SettingsScreen**

In `SettingsScreen.kt`, find the `// Developer` section (or the last `HorizontalDivider` before the debug developer block) and insert before it:

```kotlin
HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
Text(
    text = "NOTIFICATIONS",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("Rich notifications", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Show progress bars and quick actions on notifications",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Switch(
        checked = uiState.richNotificationsEnabled,
        onCheckedChange = { viewModel.onRichNotificationsToggled(it) }
    )
}
```

- [ ] **Step 4: Write validation tests**

```kotlin
// app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
package com.babytracker.ui.settings

import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk()
        val getBabyProfile = mockk<GetBabyProfileUseCase>()
        every { getBabyProfile() } returns flowOf(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { settingsRepository.getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
        every { settingsRepository.getAutoUpdateEnabled() } returns flowOf(true)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)

        viewModel = SettingsViewModel(getBabyProfile, settingsRepository, mockk())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has richNotificationsEnabled true`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.richNotificationsEnabled)
    }

    @Test
    fun `onRichNotificationsToggled false calls repository and updates state`() = runTest {
        coEvery { settingsRepository.setRichNotificationsEnabled(false) } returns Unit
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)

        viewModel.onRichNotificationsToggled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setRichNotificationsEnabled(false) }
        assertFalse(viewModel.uiState.value.richNotificationsEnabled)
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelTest"
```

Expected: PASS (2 tests).

- [ ] **Step 6: Spec review**

Check against spec section 6:
- ✅ `SettingsUiState.richNotificationsEnabled: Boolean = true`
- ✅ `onRichNotificationsToggled(enabled: Boolean)` exists
- ✅ New "Notifications" section in SettingsScreen with one toggle row
- ✅ Toggle label: "Rich notifications", subtitle: "Show progress bars and quick actions on notifications"
- ✅ Default ON matches spec

- [ ] **Step 7: Code review**

Check:
- `combine()` with 6 flows uses the array-lambda form (`values[i]`) — correct for >5 flows in Kotlin coroutines
- `Switch` uses `uiState.richNotificationsEnabled` as source of truth — reactive, no local state
- Section uses `labelMedium` typography with `UPPERCASE` text — matches CLAUDE.md convention

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): add rich notifications toggle to SettingsScreen"
```

---

### Task 3: NotificationHelper overhaul + BabyTrackerApp sleep channel + stub receivers

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`
- Create: `app/src/main/java/com/babytracker/receiver/BreastfeedingActionReceiver.kt` (stub — replaced in Task 9)
- Create: `app/src/main/java/com/babytracker/receiver/SleepActionReceiver.kt` (stub — replaced in Task 9)

Note: `NotificationHelper` imports the receiver classes for action `PendingIntent` construction. The stubs define the companion object constants so compilation succeeds before the full receivers are written in Task 9.

- [ ] **Step 1: Create stub BreastfeedingActionReceiver**

```kotlin
// app/src/main/java/com/babytracker/receiver/BreastfeedingActionReceiver.kt
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreastfeedingActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.babytracker.BREASTFEEDING_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_SWITCH = "switch"
        const val ACTION_STOP = "stop"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KEEP_GOING = "keep_going"
    }
    override fun onReceive(context: Context, intent: Intent) = Unit
}
```

- [ ] **Step 2: Create stub SleepActionReceiver**

```kotlin
// app/src/main/java/com/babytracker/receiver/SleepActionReceiver.kt
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
    }
    override fun onReceive(context: Context, intent: Intent) = Unit
}
```

- [ ] **Step 3: Rewrite NotificationHelper with all new methods**

Replace the full contents of `NotificationHelper.kt`:

```kotlin
package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.receiver.BreastfeedingActionReceiver
import com.babytracker.receiver.SleepActionReceiver
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
    const val BREASTFEEDING_CHANNEL_ID = "breastfeeding_notifications"
    const val SLEEP_CHANNEL_ID = "sleep_notifications"
    const val BREASTFEEDING_NOTIFICATION_ID = 1001
    const val SWITCH_SIDE_NOTIFICATION_ID = 1002
    const val SLEEP_NOTIFICATION_ID = 1003
    private const val RC_MAIN_TAP = 0
    private const val RC_SWITCH_NOW = 2001
    private const val RC_BF_DISMISS = 2002
    private const val RC_STOP_SESSION = 2003
    private const val RC_KEEP_GOING = 2004
    private const val RC_STOP_SLEEP = 2005
    private const val TAG = "NotificationHelper"

    fun createBreastfeedingNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BREASTFEEDING_CHANNEL_ID,
                "Breastfeeding Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for breastfeeding session timers" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $BREASTFEEDING_CHANNEL_ID")
        }
    }

    fun createSleepNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLEEP_CHANNEL_ID,
                "Sleep Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for active sleep sessions" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $SLEEP_CHANNEL_ID")
        }
    }

    fun showSwitchSide(
        context: Context,
        sessionId: Long,
        currentSide: String,
        elapsedMinutes: Int,
        maxPerBreastMinutes: Int,
        maxTotalMinutes: Int,
        richEnabled: Boolean
    ) {
        val otherSide = if (currentSide == "LEFT") "right" else "left"
        val sideLabel = if (currentSide == "LEFT") "Left" else "Right"
        val body = "$sideLabel breast: $elapsedMinutes min · Switch to the $otherSide"
        val tapPi = mainActivityPendingIntent(context)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🍼 Time to switch sides")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setProgress(maxPerBreastMinutes, elapsedMinutes, false)
                .setSubText("$elapsedMinutes / $maxPerBreastMinutes min")
                .addAction(0, "Switch Now", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_SWITCH, RC_SWITCH_NOW))
                .addAction(0, "Dismiss", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_DISMISS, RC_BF_DISMISS))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(SWITCH_SIDE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showSwitchSide posted (rich=$richEnabled)")
    }

    fun showFeedingLimit(
        context: Context,
        sessionId: Long,
        maxTotalMinutes: Int,
        richEnabled: Boolean
    ) {
        val body = "Session has reached $maxTotalMinutes minutes. Consider wrapping up."
        val tapPi = mainActivityPendingIntent(context)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏱ Feeding limit reached")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setProgress(maxTotalMinutes, maxTotalMinutes, false)
                .setSubText("$maxTotalMinutes / $maxTotalMinutes min")
                .addAction(0, "Stop Session", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_SESSION))
                .addAction(0, "Keep Going", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_KEEP_GOING, RC_KEEP_GOING))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(BREASTFEEDING_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showFeedingLimit posted (rich=$richEnabled)")
    }

    fun showSleepActive(
        context: Context,
        sessionId: Long,
        sleepType: String,
        startTimeEpochMs: Long,
        richEnabled: Boolean
    ) {
        val typeLabel = if (sleepType == "NIGHT_SLEEP") "Night sleep" else "Nap"
        val startFormatted = java.time.Instant.ofEpochMilli(startTimeEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        val body = "$typeLabel · started at $startFormatted"
        val tapPi = mainActivityPendingIntent(context)
        val builder = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🌙 Sleep session active")
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setUsesChronometer(true)
                .setWhen(startTimeEpochMs)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .addAction(0, "Stop Sleep", sleepActionPi(context, sessionId, SleepActionReceiver.ACTION_STOP, RC_STOP_SLEEP))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(SLEEP_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showSleepActive posted (rich=$richEnabled)")
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        Log.d(TAG, "Notification cancelled (ID: $notificationId)")
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, RC_MAIN_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun breastfeedingActionPi(
        context: Context, sessionId: Long, action: String, requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(BreastfeedingActionReceiver.ACTION).apply {
            setClass(context, BreastfeedingActionReceiver::class.java)
            putExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(BreastfeedingActionReceiver.EXTRA_ACTION, action)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun sleepActionPi(
        context: Context, sessionId: Long, action: String, requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(SleepActionReceiver.ACTION).apply {
            setClass(context, SleepActionReceiver::class.java)
            putExtra(SleepActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepActionReceiver.EXTRA_ACTION, action)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

- [ ] **Step 4: Update BabyTrackerApp to create both channels**

In `BabyTrackerApp.kt`, replace the `onCreate()` body:

```kotlin
override fun onCreate() {
    super.onCreate()
    NotificationHelper.createBreastfeedingNotificationChannel(this)
    NotificationHelper.createSleepNotificationChannel(this)
}
```

- [ ] **Step 5: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Spec review**

Check against spec sections 3 and 4:
- ✅ `breastfeeding_notifications` channel — HIGH importance
- ✅ `sleep_notifications` channel — DEFAULT importance
- ✅ Both created in `BabyTrackerApp.onCreate()`
- ✅ `showSwitchSide` — title "🍼 Time to switch sides", body with side + elapsed, progress bar, Switch Now + Dismiss actions
- ✅ `showFeedingLimit` — title "⏱ Feeding limit reached", progress full, Stop Session + Keep Going actions
- ✅ `showSleepActive` — title "🌙 Sleep session active", `setUsesChronometer(true)`, `setWhen(startTimeEpochMs)`, Stop Sleep action
- ✅ `setOngoing(false)` on all types (swipeable)
- ✅ Simple fallback is title + body only, no actions
- ✅ `cancelNotification` retained

- [ ] **Step 7: Code review**

Check:
- `PendingIntent.FLAG_IMMUTABLE` on all PendingIntents — required for Android 12+ ✅
- `FLAG_UPDATE_CURRENT` on action PendingIntents — ensures extras are refreshed ✅
- Each action button has a distinct request code (RC_SWITCH_NOW, RC_BF_DISMISS, etc.) so Android doesn't collapse them ✅
- `setClass()` on broadcast intents — ensures Hilt can inject the receiver on Android 8+ ✅
- Old `showBreastfeedingTimeNotification` and `showSwitchSideNotification` methods are removed — no dead code ✅
- `createNotificationChannel` is renamed to `createBreastfeedingNotificationChannel` — callers must be updated (BabyTrackerApp is updated here)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/util/NotificationHelper.kt \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt \
        app/src/main/java/com/babytracker/receiver/BreastfeedingActionReceiver.kt \
        app/src/main/java/com/babytracker/receiver/SleepActionReceiver.kt
git commit -m "feat(notifications): overhaul NotificationHelper with rich/simple variants and sleep channel"
```

---

### Task 4: SleepNotificationScheduler + SleepNotificationManager + DI

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/SleepNotificationScheduler.kt`
- Create: `app/src/main/java/com/babytracker/manager/SleepNotificationManager.kt`
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Create: `app/src/test/java/com/babytracker/manager/SleepNotificationManagerTest.kt`

- [ ] **Step 1: Create SleepNotificationScheduler interface**

```kotlin
// app/src/main/java/com/babytracker/manager/SleepNotificationScheduler.kt
package com.babytracker.manager

import com.babytracker.domain.model.SleepType
import java.time.Instant

interface SleepNotificationScheduler {
    suspend fun show(sessionId: Long, sleepType: SleepType, startTime: Instant)
    fun cancel()
}
```

- [ ] **Step 2: Create SleepNotificationManager**

```kotlin
// app/src/main/java/com/babytracker/manager/SleepNotificationManager.kt
package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SleepNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : SleepNotificationScheduler {

    override suspend fun show(sessionId: Long, sleepType: SleepType, startTime: Instant) {
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        NotificationHelper.showSleepActive(
            context, sessionId, sleepType.name, startTime.toEpochMilli(), richEnabled
        )
    }

    override fun cancel() {
        NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID)
    }
}
```

- [ ] **Step 3: Add SleepNotificationScheduler binding to NotificationSchedulerModule**

Replace the full contents of `NotificationSchedulerModule.kt`:

```kotlin
package com.babytracker.di

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NotificationScheduler
import com.babytracker.manager.SleepNotificationManager
import com.babytracker.manager.SleepNotificationScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object NotificationSchedulerModule {

    @Provides
    @ViewModelScoped
    fun provideNotificationScheduler(
        @ApplicationContext context: Context
    ): NotificationScheduler = BreastfeedingNotificationManager(context)

    @Provides
    @ViewModelScoped
    fun provideSleepNotificationScheduler(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): SleepNotificationScheduler = SleepNotificationManager(context, settingsRepository)
}
```

- [ ] **Step 4: Write validation tests**

```kotlin
// app/src/test/java/com/babytracker/manager/SleepNotificationManagerTest.kt
package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var manager: SleepNotificationManager

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk()
        manager = SleepNotificationManager(context, settingsRepository)
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSleepActive(any(), any(), any(), any(), any()) } returns Unit
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `show delegates to NotificationHelper with rich enabled`() = runTest {
        coEvery { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)
        val start = Instant.ofEpochMilli(1_000_000L)

        manager.show(7L, SleepType.NAP, start)

        verify { NotificationHelper.showSleepActive(context, 7L, "NAP", 1_000_000L, true) }
    }

    @Test
    fun `show delegates to NotificationHelper with rich disabled`() = runTest {
        coEvery { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)

        manager.show(3L, SleepType.NIGHT_SLEEP, Instant.now())

        verify { NotificationHelper.showSleepActive(context, 3L, "NIGHT_SLEEP", any(), false) }
    }

    @Test
    fun `cancel removes sleep notification`() {
        manager.cancel()

        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID) }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.SleepNotificationManagerTest"
```

Expected: PASS (3 tests).

- [ ] **Step 6: Spec review**

Check against spec sections 2 and 8:
- ✅ `SleepNotificationScheduler` is an interface — mirrors `NotificationScheduler` pattern
- ✅ `show(sessionId, sleepType, startTime)` reads rich pref, calls `NotificationHelper.showSleepActive()`
- ✅ `cancel()` calls `NotificationHelper.cancelNotification(SLEEP_NOTIFICATION_ID)`
- ✅ Provided in `NotificationSchedulerModule` as `@ViewModelScoped` in `ViewModelComponent` — same scope as `NotificationScheduler`

- [ ] **Step 7: Code review**

Check:
- `SleepNotificationManager` injects `SettingsRepository` directly (not via use case) — ✅ correct, it's a single DataStore read, not business logic
- `suspend fun show()` — correct because `settingsRepository.getRichNotificationsEnabled().first()` is a suspend call
- `fun cancel()` — non-suspend is fine, `cancelNotification` is a synchronous `NotificationManager.cancel()` call
- `@ApplicationContext` qualifier — correct, prevents ViewModel-scope memory leak

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/SleepNotificationScheduler.kt \
        app/src/main/java/com/babytracker/manager/SleepNotificationManager.kt \
        app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt \
        app/src/test/java/com/babytracker/manager/SleepNotificationManagerTest.kt
git commit -m "feat(sleep): add SleepNotificationScheduler interface and SleepNotificationManager"
```

---

### Task 5: Sleep live tracking use cases

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt`

- [ ] **Step 1: Create StartSleepRecordUseCase**

```kotlin
// app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class StartSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sleepType: SleepType): SleepRecord {
        val record = SleepRecord(startTime = Instant.now(), sleepType = sleepType)
        val id = repository.insertRecord(record)
        return record.copy(id = id)
    }
}
```

- [ ] **Step 2: Create StopSleepRecordUseCase**

```kotlin
// app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class StopSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sessionId: Long) {
        val record = repository.getAllRecords().first()
            .find { it.id == sessionId && it.isInProgress } ?: return
        repository.updateRecord(record.copy(endTime = Instant.now()))
    }
}
```

- [ ] **Step 3: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Spec review**

Check against spec section 2 data flow:
- ✅ `StartSleepRecordUseCase.invoke(sleepType)` — creates record with null endTime, inserts via repository, returns the full record with generated id
- ✅ `StopSleepRecordUseCase.invoke(sessionId)` — looks up in-progress record by id, sets endTime = now (called by `SleepActionReceiver`)
- ✅ No new DAO queries added — `getAllRecords()` and `updateRecord()` already exist on `SleepRepository`

- [ ] **Step 5: Code review**

Check:
- `record.copy(id = id)` — correct; Room returns the generated id from `@Insert`, we copy it back
- `getAllRecords().first().find { it.id == sessionId && it.isInProgress }` — safe: returns null silently if session not found or already stopped, preventing double-stop
- Both use cases follow the project's single-method `invoke` pattern with `@Inject constructor`
- No `Mapper` class — domain model assembled inline ✅

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/sleep/StartSleepRecordUseCase.kt \
        app/src/main/java/com/babytracker/domain/usecase/sleep/StopSleepRecordUseCase.kt
git commit -m "feat(sleep): add StartSleepRecordUseCase and StopSleepRecordUseCase for live tracking"
```

---

### Task 6: SleepViewModel — live tracking methods

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`

- [ ] **Step 1: Add new dependencies and methods to SleepViewModel**

Replace the full contents of `SleepViewModel.kt`:

```kotlin
package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.SleepNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null,
    val entryDurationPreview: Duration? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val startRecord: StartSleepRecordUseCase,
    private val stopRecord: StopSleepRecordUseCase,
    private val sleepNotificationScheduler: SleepNotificationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = getSleepHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSleepSession: StateFlow<SleepRecord?> = history
        .map { records -> records.find { it.isInProgress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settingsRepository.getWakeTime().collect { wakeTime ->
                _uiState.value = _uiState.value.copy(wakeTime = wakeTime)
            }
        }
        loadSchedule()
    }

    fun onStartRecord(sleepType: SleepType) {
        viewModelScope.launch {
            val record = startRecord(sleepType)
            sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime)
        }
    }

    fun onStopRecord() {
        val session = activeSleepSession.value ?: return
        viewModelScope.launch {
            stopRecord(session.id)
            sleepNotificationScheduler.cancel()
        }
    }

    fun onAddEntryClick() {
        val now = LocalTime.now()
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            entryStartTime = now,
            entryEndTime = now,
            entryError = null,
            entryDurationPreview = null
        )
    }

    fun onDismissSheet() {
        _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
    }

    fun onEntryTypeChanged(type: SleepType) {
        _uiState.value = _uiState.value.copy(entryType = type)
    }

    fun onEntryStartTimeChanged(time: LocalTime) {
        val newState = _uiState.value.copy(entryStartTime = time, entryError = null)
        _uiState.value = newState.copy(entryDurationPreview = computeDurationPreview(time, newState.entryEndTime))
    }

    fun onEntryEndTimeChanged(time: LocalTime) {
        val newState = _uiState.value.copy(entryEndTime = time, entryError = null)
        _uiState.value = newState.copy(entryDurationPreview = computeDurationPreview(newState.entryStartTime, time))
    }

    fun onSaveEntry() {
        val state = _uiState.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        var startInstant = state.entryStartTime.atDate(today).atZone(zone).toInstant()
        val endInstant = state.entryEndTime.atDate(today).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = state.entryStartTime.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }
        if (endInstant <= startInstant) {
            _uiState.value = state.copy(entryError = "End time must be after start time")
            return
        }
        viewModelScope.launch {
            saveSleepEntry(startInstant, endInstant, state.entryType)
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null)
        }
    }

    fun onSetWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
            loadSchedule()
        }
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    private fun computeDurationPreview(start: LocalTime, end: LocalTime): Duration? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        var startInstant = start.atDate(today).atZone(zone).toInstant()
        val endInstant = end.atDate(today).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = start.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }
        val d = Duration.between(startInstant, endInstant)
        return if (d.isNegative || d.isZero) null else d
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spec review**

Check against spec section 2 data flow:
- ✅ `onStartRecord()` calls `startRecord(sleepType)` then `sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime)`
- ✅ `onStopRecord()` calls `stopRecord(session.id)` then `sleepNotificationScheduler.cancel()` (belt-and-suspenders)
- ✅ `activeSleepSession` is derived from the DB-backed `history` flow — automatically reflects stops done by `SleepActionReceiver`
- ✅ Existing `onAddEntryClick` / `onSaveEntry` retrospective flow is untouched

- [ ] **Step 4: Code review**

Check:
- `activeSleepSession.value` in `onStopRecord()` — safe, uses StateFlow's current value; null-guarded with `?: return`
- New constructor params added to `@HiltViewModel` — Hilt will auto-inject `StartSleepRecordUseCase`, `StopSleepRecordUseCase`, and `SleepNotificationScheduler` (provided in Task 4's module)
- `history.map { ... }.stateIn(...)` — efficient: no extra DB query, derived from the flow that is already subscribed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt
git commit -m "feat(sleep): add onStartRecord/onStopRecord with live notification to SleepViewModel"
```

---

### Task 7: NotificationScheduler interface + BreastfeedingNotificationManager extras + BreastfeedingViewModel update

**Files:**
- Modify: `app/src/main/java/com/babytracker/manager/NotificationScheduler.kt`
- Modify: `app/src/main/java/com/babytracker/manager/BreastfeedingNotificationManager.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`
- Extend: `app/src/test/java/com/babytracker/manager/BreastfeedingNotificationManagerTest.kt`

- [ ] **Step 1: Update NotificationScheduler interface**

Replace the full contents of `NotificationScheduler.kt`:

```kotlin
package com.babytracker.manager

import java.time.Instant

interface NotificationScheduler {
    fun scheduleMaxTotalTimeNotification(
        sessionStartTime: Instant,
        maxTotalMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxPerBreastMinutes: Int
    )
    fun scheduleMaxPerBreastNotification(
        sessionStartTime: Instant,
        maxPerBreastMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxTotalMinutes: Int
    )
    fun scheduleMaxTotalTimeNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxTotalMinutes: Int,
        currentSide: String,
        maxPerBreastMinutes: Int
    )
    fun scheduleMaxPerBreastNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxPerBreastMinutes: Int,
        currentSide: String,
        maxTotalMinutes: Int
    )
    fun cancelAllScheduledNotifications()
}
```

- [ ] **Step 2: Update BreastfeedingNotificationManager to implement new interface and bundle extras**

Replace the full contents of `BreastfeedingNotificationManager.kt`:

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.BreastfeedingNotificationReceiver
import java.time.Instant

class BreastfeedingNotificationManager(private val context: Context) : NotificationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val TAG = "NotificationManager"

    override fun scheduleMaxTotalTimeNotification(
        sessionStartTime: Instant,
        maxTotalMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxPerBreastMinutes: Int
    ) {
        if (maxTotalMinutes <= 0) return
        scheduleAlarm(
            triggerTime = sessionStartTime.plusSeconds(maxTotalMinutes * 60L),
            requestCode = REQUEST_CODE_MAX_TOTAL,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxTotalMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxPerBreastNotification(
        sessionStartTime: Instant,
        maxPerBreastMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxTotalMinutes: Int
    ) {
        if (maxPerBreastMinutes <= 0) return
        scheduleAlarm(
            triggerTime = sessionStartTime.plusSeconds(maxPerBreastMinutes * 60L),
            requestCode = REQUEST_CODE_MAX_PER_BREAST,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxPerBreastMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxTotalTimeNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxTotalMinutes: Int,
        currentSide: String,
        maxPerBreastMinutes: Int
    ) {
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_TOTAL,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxTotalMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxPerBreastNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxPerBreastMinutes: Int,
        currentSide: String,
        maxTotalMinutes: Int
    ) {
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_PER_BREAST,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxPerBreastMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun cancelAllScheduledNotifications() {
        cancelAlarm(REQUEST_CODE_MAX_TOTAL)
        cancelAlarm(REQUEST_CODE_MAX_PER_BREAST)
    }

    private fun scheduleAlarm(
        triggerTime: Instant,
        requestCode: Int,
        notificationType: String,
        sessionId: Long,
        currentSide: String,
        elapsedMinutes: Int,
        maxPerBreastMinutes: Int,
        maxTotalMinutes: Int
    ) {
        Log.d(TAG, "Scheduling alarm type=$notificationType at $triggerTime (rc=$requestCode)")
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
            putExtra("notification_type", notificationType)
            putExtra("session_id", sessionId)
            putExtra("current_side", currentSide)
            putExtra("elapsed_minutes", elapsedMinutes)
            putExtra("max_per_breast_minutes", maxPerBreastMinutes)
            putExtra("max_total_minutes", maxTotalMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pendingIntent)
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val NOTIFICATION_ACTION = "com.babytracker.BREASTFEEDING_NOTIFICATION"
        private const val REQUEST_CODE_MAX_TOTAL = 1001
        private const val REQUEST_CODE_MAX_PER_BREAST = 1002
    }
}
```

- [ ] **Step 3: Update BreastfeedingViewModel callers**

In `BreastfeedingViewModel.kt`, replace `scheduleNotifications()`:

```kotlin
private fun scheduleNotifications(session: BreastfeedingSession) {
    val maxPerBreastMinutes = _uiState.value.maxPerBreastMinutes
    val maxTotalFeedMinutes = _uiState.value.maxTotalFeedMinutes

    if (maxPerBreastMinutes > 0) {
        notificationScheduler.scheduleMaxPerBreastNotification(
            sessionStartTime = session.startTime,
            maxPerBreastMinutes = maxPerBreastMinutes,
            sessionId = session.id,
            currentSide = session.startingSide.name,
            maxTotalMinutes = maxTotalFeedMinutes
        )
    }

    if (maxTotalFeedMinutes > 0) {
        notificationScheduler.scheduleMaxTotalTimeNotification(
            sessionStartTime = session.startTime,
            maxTotalMinutes = maxTotalFeedMinutes,
            sessionId = session.id,
            currentSide = session.startingSide.name,
            maxPerBreastMinutes = maxPerBreastMinutes
        )
    }
}
```

Replace `rescheduleNotificationsAfterResume()`:

```kotlin
private fun rescheduleNotificationsAfterResume(session: BreastfeedingSession) {
    val pausedAt = session.pausedAt ?: return
    val maxPerBreast = _uiState.value.maxPerBreastMinutes
    val maxTotal = _uiState.value.maxTotalFeedMinutes

    if (maxPerBreast > 0) {
        val adjustedTrigger = session.startTime
            .plusSeconds(maxPerBreast * 60L)
            .plusMillis(session.pausedDurationMs)
        val remaining = Duration.between(pausedAt, adjustedTrigger)
        if (!remaining.isNegative && !remaining.isZero) {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.now().plus(remaining),
                sessionId = session.id,
                maxPerBreastMinutes = maxPerBreast,
                currentSide = session.startingSide.name,
                maxTotalMinutes = maxTotal
            )
        }
    }

    if (maxTotal > 0) {
        val adjustedTrigger = session.startTime
            .plusSeconds(maxTotal * 60L)
            .plusMillis(session.pausedDurationMs)
        val remaining = Duration.between(pausedAt, adjustedTrigger)
        if (!remaining.isNegative && !remaining.isZero) {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = Instant.now().plus(remaining),
                sessionId = session.id,
                maxTotalMinutes = maxTotal,
                currentSide = session.startingSide.name,
                maxPerBreastMinutes = maxPerBreast
            )
        }
    }
}
```

- [ ] **Step 4: Extend BreastfeedingNotificationManagerTest with extras and regression tests**

Add these test methods to the existing `BreastfeedingNotificationManagerTest` class:

```kotlin
@Test
fun `scheduleMaxPerBreastNotification bundles session extras in alarm intent`() {
    val intentSlot = slot<Intent>()
    every { PendingIntent.getBroadcast(any(), any(), capture(intentSlot), any()) } returns mockk()

    notificationManager.scheduleMaxPerBreastNotification(
        sessionStartTime = Instant.now(),
        maxPerBreastMinutes = 15,
        sessionId = 42L,
        currentSide = "LEFT",
        maxTotalMinutes = 30
    )

    val captured = intentSlot.captured
    assertEquals(42L, captured.getLongExtra("session_id", -1L))
    assertEquals("LEFT", captured.getStringExtra("current_side"))
    assertEquals(15, captured.getIntExtra("elapsed_minutes", 0))
    assertEquals(15, captured.getIntExtra("max_per_breast_minutes", 0))
    assertEquals(30, captured.getIntExtra("max_total_minutes", 0))
}

@Test
fun `scheduleMaxTotalTimeNotification bundles session extras in alarm intent`() {
    val intentSlot = slot<Intent>()
    every { PendingIntent.getBroadcast(any(), any(), capture(intentSlot), any()) } returns mockk()

    notificationManager.scheduleMaxTotalTimeNotification(
        sessionStartTime = Instant.now(),
        maxTotalMinutes = 30,
        sessionId = 7L,
        currentSide = "RIGHT",
        maxPerBreastMinutes = 15
    )

    val captured = intentSlot.captured
    assertEquals(7L, captured.getLongExtra("session_id", -1L))
    assertEquals("RIGHT", captured.getStringExtra("current_side"))
    assertEquals(30, captured.getIntExtra("max_total_minutes", 0))
}

@Test
fun `cancelAllScheduledNotifications regression - still cancels both request codes`() {
    notificationManager.cancelAllScheduledNotifications()
    verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
}
```

Add the missing import at the top of the test file:
```kotlin
import android.content.Intent
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
```

- [ ] **Step 5: Run all manager tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.manager.BreastfeedingNotificationManagerTest"
```

Expected: PASS (all tests including 3 new ones).

- [ ] **Step 6: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all existing tests pass (no regressions from interface change).

- [ ] **Step 7: Spec review**

Check against spec section 5 (Intent Extras) and section 7 (Modified files):
- ✅ `session_id` (Long), `current_side` (String), `elapsed_minutes` (Int), `max_per_breast_minutes` (Int), `max_total_minutes` (Int) bundled in breastfeeding alarm PendingIntents
- ✅ `BreastfeedingNotificationManager.kt` bundles dynamic extras
- ✅ `BreastfeedingViewModel.kt` passes `session.id` and `session.startingSide.name` to scheduler calls

- [ ] **Step 8: Code review**

Check:
- `NOTIFICATION_ACTION` constant is unchanged — `cancelAlarm()` still uses it, so existing `filterEquals()` behavior is preserved ✅
- `cancelAlarm()` does NOT include extras in the cancel intent — correct, extras are not part of `filterEquals()` ✅
- Named parameters in `scheduleAlarm()` call sites — reduces positional mistake risk ✅
- `BreastfeedingViewModel` uses `session.startingSide.name` for `currentSide` — correct for initial scheduling; side at alarm-fire time = starting side since alarm fires before first switch

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/NotificationScheduler.kt \
        app/src/main/java/com/babytracker/manager/BreastfeedingNotificationManager.kt \
        app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt \
        app/src/test/java/com/babytracker/manager/BreastfeedingNotificationManagerTest.kt
git commit -m "feat(breastfeeding): thread session extras through alarm PendingIntents"
```

---

### Task 8: BreastfeedingNotificationReceiver — @AndroidEntryPoint + rich pref + new NotificationHelper methods

**Files:**
- Modify: `app/src/main/java/com/babytracker/receiver/BreastfeedingNotificationReceiver.kt`

- [ ] **Step 1: Replace BreastfeedingNotificationReceiver**

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    companion object {
        const val NOTIFICATION_TYPE_MAX_TOTAL = "max_total"
        const val NOTIFICATION_TYPE_SWITCH_SIDE = "switch_side"
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationType = intent.getStringExtra("notification_type")
        Log.d(TAG, "Broadcast received: type=$notificationType")
        if (notificationType == null) {
            Log.w(TAG, "No notification_type extra found")
            return
        }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
                val sessionId = intent.getLongExtra("session_id", -1L)
                val currentSide = intent.getStringExtra("current_side") ?: "LEFT"
                val elapsedMinutes = intent.getIntExtra("elapsed_minutes", 0)
                val maxPerBreastMinutes = intent.getIntExtra("max_per_breast_minutes", 0)
                val maxTotalMinutes = intent.getIntExtra("max_total_minutes", 0)

                when (notificationType) {
                    NOTIFICATION_TYPE_SWITCH_SIDE -> {
                        Log.i(TAG, "Triggering switch side notification (rich=$richEnabled)")
                        NotificationHelper.showSwitchSide(
                            context, sessionId, currentSide, elapsedMinutes,
                            maxPerBreastMinutes, maxTotalMinutes, richEnabled
                        )
                    }
                    NOTIFICATION_TYPE_MAX_TOTAL -> {
                        Log.i(TAG, "Triggering feeding limit notification (rich=$richEnabled)")
                        NotificationHelper.showFeedingLimit(
                            context, sessionId, maxTotalMinutes, richEnabled
                        )
                    }
                    else -> Log.w(TAG, "Unknown notification type: $notificationType")
                }
            } finally {
                result.finish()
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spec review**

Check against spec section 2 data flow:
- ✅ `BreastfeedingNotificationReceiver` reads `rich_notifications_enabled` from DataStore
- ✅ Calls `NotificationHelper.showSwitchSide()` or `showFeedingLimit()` depending on notification type
- ✅ Passes all required extras to the helper methods
- ✅ Existing alarm-only responsibility retained — receiver still only responds to alarm broadcasts

- [ ] **Step 4: Code review**

Check:
- `@AndroidEntryPoint` — required for Hilt field injection on BroadcastReceiver ✅
- `goAsync()` + `result.finish()` in `finally` — BroadcastReceiver gets a 10-second budget; DataStore read is fast but still async; `finally` ensures the broadcast slot is always released ✅
- `CoroutineScope(Dispatchers.IO + SupervisorJob())` — IO dispatcher is appropriate for DataStore; SupervisorJob prevents one failure from cascading ✅
- Default `"LEFT"` for missing `current_side` — safe fallback, won't cause a crash if extras are somehow missing

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/BreastfeedingNotificationReceiver.kt
git commit -m "feat(breastfeeding): BreastfeedingNotificationReceiver reads rich pref and shows rich/simple notifications"
```

---

### Task 9: Action receivers (full impl) + Manifest + Tests

**Files:**
- Modify: `app/src/main/java/com/babytracker/receiver/BreastfeedingActionReceiver.kt` (replace stub)
- Modify: `app/src/main/java/com/babytracker/receiver/SleepActionReceiver.kt` (replace stub)
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/babytracker/receiver/BreastfeedingActionReceiverTest.kt`
- Create: `app/src/test/java/com/babytracker/receiver/SleepActionReceiverTest.kt`
- Extend: `app/src/test/java/com/babytracker/receiver/BreastfeedingNotificationReceiverConstantsTest.kt`

- [ ] **Step 1: Replace BreastfeedingActionReceiver stub with full implementation**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BreastfeedingRepository
    @Inject lateinit var switchSide: SwitchBreastfeedingSideUseCase
    @Inject lateinit var stopSession: StopBreastfeedingSessionUseCase

    companion object {
        const val ACTION = "com.babytracker.BREASTFEEDING_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_SWITCH = "switch"
        const val ACTION_STOP = "stop"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KEEP_GOING = "keep_going"
        private const val TAG = "BreastfeedingActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                handle(context, intent)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        when (action) {
            ACTION_SWITCH -> {
                val session = repository.getActiveSession().first()
                if (session?.id == sessionId) switchSide(session)
            }
            ACTION_STOP -> {
                val session = repository.getActiveSession().first()
                if (session?.id == sessionId) stopSession(session)
            }
        }
        NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
        NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
    }
}
```

- [ ] **Step 2: Replace SleepActionReceiver stub with full implementation**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepActionReceiver : BroadcastReceiver() {

    @Inject lateinit var stopRecord: StopSleepRecordUseCase

    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
        private const val TAG = "SleepActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                handle(context, intent)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        if (action == ACTION_STOP) {
            stopRecord(sessionId)
        }
        NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID)
    }
}
```

- [ ] **Step 3: Add both receivers to AndroidManifest.xml**

In `AndroidManifest.xml`, after the `BreastfeedingNotificationReceiver` block, before `</application>`:

```xml
<receiver android:name=".receiver.BreastfeedingActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.BREASTFEEDING_ACTION" />
    </intent-filter>
</receiver>

<receiver android:name=".receiver.SleepActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.babytracker.SLEEP_ACTION" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4: Write validation tests for BreastfeedingActionReceiver**

```kotlin
// app/src/test/java/com/babytracker/receiver/BreastfeedingActionReceiverTest.kt
package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingActionReceiverTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var stopSession: StopBreastfeedingSessionUseCase
    private lateinit var receiver: BreastfeedingActionReceiver
    private val context = mockk<Context>(relaxed = true)

    private val activeSession = BreastfeedingSession(
        id = 42L,
        startTime = Instant.now(),
        startingSide = BreastSide.LEFT
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        switchSide = mockk()
        stopSession = mockk()
        receiver = BreastfeedingActionReceiver()
        receiver.repository = repository
        receiver.switchSide = switchSide
        receiver.stopSession = stopSession

        coEvery { repository.getActiveSession() } returns flowOf(activeSession)
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_SWITCH calls switchSide for matching session`() = runTest {
        coEvery { switchSide(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        coVerify { switchSide(activeSession) }
    }

    @Test
    fun `ACTION_STOP calls stopSession for matching session`() = runTest {
        coEvery { stopSession(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 42L))

        coVerify { stopSession(activeSession) }
    }

    @Test
    fun `ACTION_DISMISS cancels notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_DISMISS, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(any(), any()) }
    }

    @Test
    fun `ACTION_KEEP_GOING cancels notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(any(), any()) }
    }

    private fun intent(action: String, sessionId: Long) = Intent().apply {
        putExtra(BreastfeedingActionReceiver.EXTRA_ACTION, action)
        putExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, sessionId)
    }
}
```

- [ ] **Step 5: Write validation tests for SleepActionReceiver**

```kotlin
// app/src/test/java/com/babytracker/receiver/SleepActionReceiverTest.kt
package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SleepActionReceiverTest {

    private lateinit var stopRecord: StopSleepRecordUseCase
    private lateinit var receiver: SleepActionReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        stopRecord = mockk()
        receiver = SleepActionReceiver()
        receiver.stopRecord = stopRecord

        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_STOP calls StopSleepRecordUseCase and cancels notification`() = runTest {
        coEvery { stopRecord(99L) } returns Unit

        receiver.handle(context, Intent().apply {
            putExtra(SleepActionReceiver.EXTRA_ACTION, SleepActionReceiver.ACTION_STOP)
            putExtra(SleepActionReceiver.EXTRA_SESSION_ID, 99L)
        })

        coVerify { stopRecord(99L) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID) }
    }
}
```

- [ ] **Step 6: Extend BreastfeedingNotificationReceiverConstantsTest**

Add to the existing test class (add imports as needed):

```kotlin
// Add imports:
import com.babytracker.receiver.BreastfeedingActionReceiver
import com.babytracker.receiver.SleepActionReceiver
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

// Add test method:
@Test
fun `action string constants are unique and non-empty`() {
    val breastfeedingAction = BreastfeedingActionReceiver.ACTION
    val sleepAction = SleepActionReceiver.ACTION

    assertTrue(breastfeedingAction.isNotEmpty())
    assertTrue(sleepAction.isNotEmpty())
    assertNotEquals(breastfeedingAction, sleepAction)
}
```

- [ ] **Step 7: Run all receiver tests**

```bash
./gradlew :app:testDebugUnitTest \
    --tests "com.babytracker.receiver.BreastfeedingActionReceiverTest" \
    --tests "com.babytracker.receiver.SleepActionReceiverTest" \
    --tests "com.babytracker.receiver.BreastfeedingNotificationReceiverConstantsTest"
```

Expected: PASS (4 + 1 + 3 = 8 tests).

- [ ] **Step 8: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 9: Build debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Spec review**

Check against spec section 7 (New files) and section 10 (Acceptance Criteria):
- ✅ `BreastfeedingActionReceiver` — `@AndroidEntryPoint`, handles Switch/Stop/Dismiss/Keep Going
- ✅ `SleepActionReceiver` — `@AndroidEntryPoint`, handles Stop Sleep
- ✅ Both declared in manifest with `android:exported="false"`
- ✅ Action strings match manifest `<intent-filter>` action names
- ✅ `ACTION_SWITCH` → `switchSide()` called; `ACTION_STOP` → `stopSession()` called; Dismiss/KeepGoing → repo untouched
- ✅ Sleep stop → `StopSleepRecordUseCase` called; notification cancelled

- [ ] **Step 11: Code review**

Check:
- `android:exported="false"` on both receivers — prevents external apps from triggering them ✅
- `internal suspend fun handle()` — testable without Hilt; `onReceive()` is the Hilt entry point, `handle()` contains the logic ✅
- `session?.id == sessionId` guard in `BreastfeedingActionReceiver` — prevents stale notifications from acting on a different session ✅
- `SleepActionReceiver` injects only `StopSleepRecordUseCase` (not the full repository) — follows spec which says "calls StopSleepRecordUseCase" ✅

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/BreastfeedingActionReceiver.kt \
        app/src/main/java/com/babytracker/receiver/SleepActionReceiver.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/babytracker/receiver/BreastfeedingActionReceiverTest.kt \
        app/src/test/java/com/babytracker/receiver/SleepActionReceiverTest.kt \
        app/src/test/java/com/babytracker/receiver/BreastfeedingNotificationReceiverConstantsTest.kt
git commit -m "feat(notifications): add BreastfeedingActionReceiver and SleepActionReceiver with action handling"
```
