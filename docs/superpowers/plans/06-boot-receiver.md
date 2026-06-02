# StashExpirationBootReceiver (Boot Resilience) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-83

**Goal:** Restore the daily stash-expiration alarm after a device reboot or clock/timezone change. `AlarmManager` alarms do not survive reboot — without this the reminder silently stops after a restart.

**Architecture:** A dedicated `@AndroidEntryPoint` `BroadcastReceiver` listening for `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED`. On trigger it reads both toggles; only if **both** are enabled does it call `StashExpirationScheduler.scheduleDaily(notifTime)`. `TIME_SET` covers manual clock changes and `TIMEZONE_CHANGED` covers timezone moves (e.g. travel) — both re-anchor the alarm to the correct next **local** occurrence because the scheduler recomputes from the current `ZoneId` (see AKA-79). A timezone change is a separate broadcast from a clock change, so both actions are required. This is one of the three recurrence backstops named in the AKA-79 durability contract.

**Tech Stack:** Kotlin, Android `BroadcastReceiver`, Hilt, Coroutines, `RECEIVE_BOOT_COMPLETED` permission.

**Dependencies:** AKA-79 (`StashExpirationScheduler`), AKA-75 (`InventorySettingsRepository`).

**Suggested implementation branch:** `feat/aka-83-boot-receiver`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/receiver/StashExpirationBootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add `RECEIVE_BOOT_COMPLETED` permission + receiver with intent-filter
- Test: `app/src/test/java/com/babytracker/receiver/StashExpirationBootReceiverTest.kt`

Pattern sources: `receiver/NapReminderReceiver.kt` / `receiver/SleepActionReceiver.kt` (goAsync + coroutine + `internal suspend fun handle`).

---

## Task 1: Write the boot-receiver test (TDD)

**Files:**
- Test: `app/src/test/java/com/babytracker/receiver/StashExpirationBootReceiverTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.babytracker.receiver

import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StashExpirationBootReceiverTest {

    private lateinit var settings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler
    private lateinit var receiver: StashExpirationBootReceiver

    @BeforeEach
    fun setup() {
        settings = mockk()
        scheduler = mockk(relaxed = true)
        receiver = StashExpirationBootReceiver().apply {
            inventorySettings = settings
            scheduler = this@StashExpirationBootReceiverTest.scheduler
        }
        every { settings.getExpirationNotifTimeMinutes() } returns flowOf(480)
    }

    @Test
    fun `reschedules when both toggles enabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)

        receiver.handle()

        verify(exactly = 1) { scheduler.scheduleDaily(480) }
    }

    @Test
    fun `does not reschedule when master toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(false)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)

        receiver.handle()

        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `does not reschedule when notif toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(false)

        receiver.handle()

        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `shouldHandle accepts boot, time-set and timezone-change actions`() {
        assert(receiver.shouldHandle("android.intent.action.BOOT_COMPLETED"))
        assert(receiver.shouldHandle("android.intent.action.TIME_SET"))
        assert(receiver.shouldHandle("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `shouldHandle ignores unrelated and null actions`() {
        assert(!receiver.shouldHandle("android.intent.action.AIRPLANE_MODE"))
        assert(!receiver.shouldHandle(null))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (class doesn't exist)

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationBootReceiverTest" -PfastTests`
Expected: compile failure / FAIL

---

## Task 2: Implement the boot receiver

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/StashExpirationBootReceiver.kt`

- [ ] **Step 1: Write the receiver**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class StashExpirationBootReceiver : BroadcastReceiver() {

    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var scheduler: StashExpirationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(TIMEOUT_MS) { handle() }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } catch (e: Exception) {
                Log.e(TAG, "onReceive failed", e)
            } finally {
                result.finish()
            }
        }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle() {
        val enabled = inventorySettings.getExpirationEnabled().first()
        if (!enabled) return
        val notifEnabled = inventorySettings.getExpirationNotifEnabled().first()
        if (!notifEnabled) return
        val notifTime = inventorySettings.getExpirationNotifTimeMinutes().first()
        scheduler.scheduleDaily(notifTime)
        Log.d(TAG, "Restored stash expiration alarm at minute=$notifTime")
    }

    companion object {
        private const val TAG = "StashExpirationBoot"
        private const val TIMEOUT_MS = 10_000L
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,   // android.intent.action.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED,     // android.intent.action.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED, // android.intent.action.TIMEZONE_CHANGED
        )
    }
}
```

> `Intent.ACTION_TIME_CHANGED` maps to `android.intent.action.TIME_SET`; `Intent.ACTION_TIMEZONE_CHANGED` maps to `android.intent.action.TIMEZONE_CHANGED`. A timezone change is a distinct broadcast from a clock change, so both are needed for travel/DST resilience. Verify both toggles before scheduling so a disabled feature is never resurrected on boot. Reading is short-circuited per-toggle (same disable-invariant discipline as `StashExpirationReceiver`). `onReceive` catches non-cancellation exceptions so a failure here never crashes at boot.

- [ ] **Step 2: Run tests, expect PASS**

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationBootReceiverTest" -PfastTests`
Expected: PASS (5 tests)

---

## Task 3: Manifest — permission + receiver

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the permission** (with the other `<uses-permission>` entries near the top)

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

- [ ] **Step 2: Register the receiver** (with the other `<receiver>` entries)

```xml
<receiver
    android:name=".receiver.StashExpirationBootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: Verify build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

---

## Task 4: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Tests** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationBootReceiverTest" -PfastTests` → PASS
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/StashExpirationBootReceiver.kt \
        app/src/test/java/com/babytracker/receiver/StashExpirationBootReceiverTest.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(receiver): restore stash expiration alarm on boot and time change [AKA-83]"
```

---

## Acceptance Criteria

- Receiver fires on `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED` (ignores other/null actions — verified by `shouldHandle` tests).
- Reschedules **only** when both `getExpirationEnabled()` and `getExpirationNotifEnabled()` are true; per-toggle short-circuit so a disabled feature is never re-armed.
- `RECEIVE_BOOT_COMPLETED` permission declared; receiver `exported="false"` with the three-action intent-filter.
- Timezone change (travel) re-anchors the alarm to the new local zone via the scheduler's `ZoneId`-based recompute.
- `onReceive` cannot crash at boot/time-change (non-cancellation exceptions caught; `result.finish()` always runs).
- Tests pass.
