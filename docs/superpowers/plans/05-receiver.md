# StashExpirationReceiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-80

**Goal:** Add the `StashExpirationReceiver` that the daily alarm fires into: it guards on both toggles, queries bags expiring **exactly today**, posts the notification when any qualify, **re-arms the next day's alarm in a guaranteed `finally`**, and adds an app-startup reconciliation so a dropped one-shot self-heals.

**Architecture:** `@AndroidEntryPoint` `BroadcastReceiver` using the established `goAsync()` + `CoroutineScope(Dispatchers.IO + SupervisorJob())` + `withTimeout` + `finally { result.finish() }` pattern (see `SleepActionReceiver`, `NapReminderReceiver`). Because AKA-79 uses a **one-shot** alarm, this receiver owns recurrence: it re-arms via `StashExpirationScheduler.scheduleDaily` in a `finally` so a failure while reading settings / querying bags / posting the notification cannot stop tomorrow's reminder. A `BabyTrackerApp` startup reconciliation re-arms after a dropped one-shot (process kill, lost alarm). This plan **satisfies the "Recurrence-durability contract"** defined in plan 04 (AKA-79).

**Tech Stack:** Kotlin, Android `BroadcastReceiver`, Hilt `@AndroidEntryPoint`, Coroutines, Room DAO, `java.time`.

**Dependencies:** AKA-79 (`StashExpirationScheduler`, `NotificationHelper.showStashExpiration`), AKA-75 (`InventorySettingsRepository`). Existing: `MilkBagDao.getAllBagsOnce()`, `MilkBagEntity`.

**Suggested implementation branch:** `feat/aka-80-receiver`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/receiver/StashExpirationReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml` — register the receiver (`exported="false"`)
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt` — startup reconciliation (re-arm if both toggles enabled)
- Test: `app/src/test/java/com/babytracker/receiver/StashExpirationReceiverTest.kt`

Pattern sources: `receiver/NapReminderReceiver.kt` (goAsync, withTimeout, `internal suspend fun handle`, `SecurityException` guard around the notification post), `receiver/SleepActionReceiver.kt` (injected use cases / repos).

---

## Query semantics (from spec)

- **Both guards required:** read `getExpirationEnabled().first()` and `getExpirationNotifEnabled().first()`; if **either** is false, bail (no notification, no re-arm — the alarm should remain cancelled in the disabled state).
- **Active bags only:** `usedAt == null`.
- **Exact date equality:** include a bag only when `expiryDate == today`, where `expiryDate = Instant.ofEpochMilli(collectionDate).atZone(systemZone).toLocalDate() + days`. Already-expired bags (`expiryDate < today`) are **excluded** — re-notifying about the same stale bags every day causes alarm fatigue (spec decision).
- **Fire only when count > 0:** `totalMl = sum of volumeMl`. Body via `NotificationHelper.showStashExpiration(context, count, totalMl)`.

---

## Task 1: Write the receiver test (TDD)

**Files:**
- Test: `app/src/test/java/com/babytracker/receiver/StashExpirationReceiverTest.kt`

Test the `internal suspend fun handle(context, ...)` directly with MockK (the same approach used for other receivers — bypasses the `goAsync` Android plumbing). Mock `MilkBagDao`, `InventorySettingsRepository`, `StashExpirationScheduler`, and `NotificationHelper` (object → `mockkObject`).

- [ ] **Step 1: Write failing tests**

```kotlin
package com.babytracker.receiver

import android.content.Context
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StashExpirationReceiverTest {

    private lateinit var dao: MilkBagDao
    private lateinit var settings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler
    private lateinit var context: Context
    private lateinit var receiver: StashExpirationReceiver

    private val zone: ZoneId = ZoneId.systemDefault()
    private val notifTime = 480

    @BeforeEach
    fun setup() {
        dao = mockk()
        settings = mockk()
        scheduler = mockk(relaxed = true)
        context = mockk(relaxed = true)
        receiver = StashExpirationReceiver().apply {
            milkBagDao = dao
            inventorySettings = settings
            scheduler = this@StashExpirationReceiverTest.scheduler
        }
        mockkObject(NotificationHelper)
        every { NotificationHelper.showStashExpiration(any(), any(), any()) } returns Unit
        every { settings.getExpirationNotifTimeMinutes() } returns flowOf(notifTime)
        every { settings.getExpirationDays() } returns flowOf(4)
    }

    @AfterEach
    fun tearDown() = unmockkObject(NotificationHelper)

    /** Helper: a bag whose expiry (collectionDate + days) lands on [expiryDate]. */
    private fun bagExpiringOn(expiryDate: LocalDate, days: Int, volumeMl: Int, usedAt: Long? = null): MilkBagEntity {
        val collection = expiryDate.minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        return MilkBagEntity(
            id = 0,
            collectionDate = collection.toEpochMilli(),
            volumeMl = volumeMl,
            usedAt = usedAt,
            createdAt = Instant.now().toEpochMilli(),
        )
    }

    @Test
    fun `posts notification for bags expiring exactly today and re-arms`() = runTest {
        val today = LocalDate.now(zone)
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { dao.getAllBagsOnce() } returns listOf(
            bagExpiringOn(today, days = 4, volumeMl = 100),
            bagExpiringOn(today, days = 4, volumeMl = 50),
            bagExpiringOn(today.plusDays(1), days = 4, volumeMl = 999), // tomorrow → excluded
            bagExpiringOn(today.minusDays(1), days = 4, volumeMl = 999), // expired → excluded
            bagExpiringOn(today, days = 4, volumeMl = 999, usedAt = 1L), // used → excluded
        )

        receiver.handle(context)

        verify(exactly = 1) { NotificationHelper.showStashExpiration(context, 2, 150) }
        verify(exactly = 1) { scheduler.scheduleDaily(notifTime) }
    }

    @Test
    fun `does not post when no bag expires today but still re-arms`() = runTest {
        val today = LocalDate.now(zone)
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { dao.getAllBagsOnce() } returns listOf(bagExpiringOn(today.plusDays(2), days = 4, volumeMl = 100))

        receiver.handle(context)

        verify(exactly = 0) { NotificationHelper.showStashExpiration(any(), any(), any()) }
        verify(exactly = 1) { scheduler.scheduleDaily(notifTime) } // re-arm independent of count
    }

    @Test
    fun `bails and does not re-arm when master toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(false)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)

        receiver.handle(context)

        coVerify(exactly = 0) { dao.getAllBagsOnce() }
        verify(exactly = 0) { NotificationHelper.showStashExpiration(any(), any(), any()) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) } // disabled => stays cancelled
    }

    @Test
    fun `bails and does not re-arm when notif toggle disabled`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(false)

        receiver.handle(context)

        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `re-arms even when bag query throws`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { dao.getAllBagsOnce() } throws RuntimeException("db error")

        runCatching { receiver.handle(context) }

        verify(exactly = 1) { scheduler.scheduleDaily(notifTime) } // finally re-arm survives failure
    }

    @Test
    fun `re-arms with default time when toggle read throws`() = runTest {
        // Settings store unavailable: cannot determine enabled state -> fail open with default time.
        every { settings.getExpirationEnabled() } throws RuntimeException("datastore error")

        runCatching { receiver.handle(context) }

        // notifTime was never read; finally re-arms with the 480 default so the reminder survives.
        verify(exactly = 1) { scheduler.scheduleDaily(480) }
        verify(exactly = 0) { NotificationHelper.showStashExpiration(any(), any(), any()) }
    }

    @Test
    fun `re-arms with default time when notif-time read throws`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifTimeMinutes() } throws RuntimeException("datastore error")

        runCatching { receiver.handle(context) }

        verify(exactly = 1) { scheduler.scheduleDaily(480) }
    }

    @Test
    fun `disabled master toggle short-circuits before later read can throw`() = runTest {
        // Clean false on master toggle; notif-enabled read would throw if reached — it must not be.
        every { settings.getExpirationEnabled() } returns flowOf(false)
        every { settings.getExpirationNotifEnabled() } throws RuntimeException("must not be read")

        receiver.handle(context)

        verify(exactly = 0) { settings.getExpirationNotifEnabled() }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) } // disabled stays cancelled
    }

    @Test
    fun `disabled notif toggle short-circuits before notif-time read can throw`() = runTest {
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(false)
        every { settings.getExpirationNotifTimeMinutes() } throws RuntimeException("must not be read")

        receiver.handle(context)

        verify(exactly = 0) { settings.getExpirationNotifTimeMinutes() }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `does not throw when scheduler re-arm fails`() = runTest {
        val today = LocalDate.now(zone)
        every { settings.getExpirationEnabled() } returns flowOf(true)
        every { settings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { dao.getAllBagsOnce() } returns listOf(bagExpiringOn(today, days = 4, volumeMl = 100))
        every { scheduler.scheduleDaily(any()) } throws RuntimeException("AlarmManager failure")

        // handle() must complete normally — the finally re-arm failure is swallowed and logged;
        // recovery is delegated to startup reconciliation / boot receiver.
        receiver.handle(context)

        verify(exactly = 1) { scheduler.scheduleDaily(notifTime) }
        verify(exactly = 1) { NotificationHelper.showStashExpiration(context, 1, 100) }
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (class doesn't exist yet)

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationReceiverTest" -PfastTests`
Expected: compile failure / FAIL

---

## Task 2: Implement the receiver

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/StashExpirationReceiver.kt`

- [ ] **Step 1: Write the receiver**

```kotlin
package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class StashExpirationReceiver : BroadcastReceiver() {

    @Inject lateinit var milkBagDao: MilkBagDao
    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var scheduler: StashExpirationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(TIMEOUT_MS) { handle(context) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } catch (e: Exception) {
                // Never let a receiver-side failure escape as an uncaught coroutine exception
                // (would crash the process at alarm-delivery time). handle() already re-arms
                // in its own finally; here we just log and finish cleanly.
                Log.e(TAG, "onReceive failed", e)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context) {
        // Fail-open recurrence: re-arm unless the feature is *authoritatively* disabled.
        // notifTime starts at the default so the finally can always re-arm even if the
        // settings reads below throw. A spurious daily alarm that posts nothing is harmless;
        // a permanently dead reminder is the failure mode we are eliminating.
        var notifTime = DEFAULT_NOTIF_TIME_MINUTES
        var shouldRearm = true
        try {
            // Evaluate each toggle the instant it is read and short-circuit on a CLEAN false,
            // BEFORE any later read. Otherwise a throw in a subsequent read would jump to the
            // catch with shouldRearm still true and resurrect a user-disabled reminder.
            if (!inventorySettings.getExpirationEnabled().first()) {
                shouldRearm = false
                Log.d(TAG, "Master toggle disabled; not posting and not re-arming")
                return
            }
            if (!inventorySettings.getExpirationNotifEnabled().first()) {
                shouldRearm = false
                Log.d(TAG, "Notif toggle disabled; not posting and not re-arming")
                return
            }
            notifTime = inventorySettings.getExpirationNotifTimeMinutes().first()
            val days = inventorySettings.getExpirationDays().first()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val expiringToday = milkBagDao.getAllBagsOnce()
                .filter { it.usedAt == null }
                .filter {
                    val expiry = Instant.ofEpochMilli(it.collectionDate)
                        .atZone(zone).toLocalDate().plusDays(days.toLong())
                    expiry == today
                }
            if (expiringToday.isNotEmpty()) {
                val totalMl = expiringToday.sumOf { it.volumeMl }
                try {
                    NotificationHelper.showStashExpiration(context, expiringToday.size, totalMl)
                } catch (e: SecurityException) {
                    Log.w(TAG, "POST_NOTIFICATIONS denied; skipping stash notification", e)
                }
            }
        } catch (e: CancellationException) {
            // Preserve structured cancellation (e.g. withTimeout) — but still re-arm in finally.
            throw e
        } catch (e: Exception) {
            // Any settings/DAO failure: keep shouldRearm = true so the reminder survives.
            Log.w(TAG, "handle failed; re-arming defensively with notifTime=$notifTime", e)
        } finally {
            // One-shot alarm is consumed; re-arm tomorrow unless authoritatively disabled.
            // Guard the re-arm itself: a throw from finally would mask the original exception,
            // and an AlarmManager failure here must not crash delivery. If this fails, the
            // backstops are BabyTrackerApp startup reconciliation (Task 4) and the boot/TIME_SET
            // receiver (AKA-83), both of which re-arm independently.
            if (shouldRearm) {
                runCatching { scheduler.scheduleDaily(notifTime) }
                    .onFailure { Log.e(TAG, "Re-arm failed; startup/boot reconciliation will recover", it) }
            }
        }
    }

    companion object {
        private const val TAG = "StashExpirationReceiver"
        private const val TIMEOUT_MS = 10_000L
        private const val DEFAULT_NOTIF_TIME_MINUTES = 480 // 08:00 — matches settings default
    }
}
```

> Note: the `finally` re-arm runs only after the both-toggles guard passes, so a disabled feature does not get re-armed. The `SecurityException` catch is **inside** the try so a denied POST_NOTIFICATIONS still re-arms.

- [ ] **Step 2: Run tests, expect PASS**

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationReceiverTest" -PfastTests`
Expected: PASS (10 tests)

---

## Task 3: Register the receiver in the manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the receiver** (alongside the other `<receiver>` entries, e.g. after `NapReminderReceiver`)

```xml
<receiver
    android:name=".receiver.StashExpirationReceiver"
    android:exported="false" />
```

- [ ] **Step 2: Verify build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

---

## Task 4: App-startup reconciliation (recovery path)

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

Recovers from a dropped one-shot (process killed between fires, lost alarm) without waiting for a reboot. Re-arming an already-armed alarm is idempotent (same `PendingIntent` + `FLAG_UPDATE_CURRENT`).

- [ ] **Step 1: Inject deps and reconcile in onCreate**

Add fields:

```kotlin
    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var stashExpirationScheduler: StashExpirationScheduler
```

In `onCreate()`, after the existing channel/coordinator setup, inside the existing `appScope`:

```kotlin
        appScope.launch {
            // Recovery only; never crash startup if DataStore read fails.
            runCatching {
                if (inventorySettings.getExpirationEnabled().first() &&
                    inventorySettings.getExpirationNotifEnabled().first()
                ) {
                    stashExpirationScheduler.scheduleDaily(
                        inventorySettings.getExpirationNotifTimeMinutes().first(),
                    )
                }
            }
        }
```

Add imports: `com.babytracker.domain.repository.InventorySettingsRepository`, `com.babytracker.manager.StashExpirationScheduler`, `kotlinx.coroutines.flow.first`.

> Both toggles default to `false`, so for users who never enabled the feature this reconciliation does nothing (dormant). It only re-arms for users who opted in.

- [ ] **Step 2: Verify build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

---

## Task 5: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Full receiver tests** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.receiver.StashExpirationReceiverTest" -PfastTests` → PASS
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/receiver/StashExpirationReceiver.kt \
        app/src/test/java/com/babytracker/receiver/StashExpirationReceiverTest.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/babytracker/BabyTrackerApp.kt
git commit -m "feat(receiver): add StashExpirationReceiver with durable re-arm [AKA-80]"
```

---

## Acceptance Criteria

- Receiver guards on **both** toggles; bails (no post, no re-arm) if either is off.
- Filters active bags (`usedAt == null`) to those whose `expiryDate == today` (exact equality; expired bags excluded).
- Posts `showStashExpiration(context, count, totalMl)` only when count > 0.
- **Re-arms the next alarm in a `finally`** — recurrence survives a settings-read / DAO / notification failure. Tests cover: DAO throws, toggle read throws (re-arms with 480 default), notif-time read throws (re-arms with default). POST_NOTIFICATIONS denial is caught and still re-arms.
- **Fail-open:** if settings reads throw (store unavailable), the receiver re-arms defensively with the default time rather than letting the reminder die. Re-arm does **not** happen only when the feature is read cleanly as disabled.
- `onReceive` catches non-cancellation exceptions so a receiver failure never escapes as an uncaught coroutine exception (no process crash at alarm delivery); `result.finish()` always runs.
- The `finally` re-arm itself is wrapped in `runCatching` — a `scheduleDaily`/AlarmManager failure is logged, not thrown out of `finally` (test: "does not throw when scheduler re-arm fails"). Backstops for that case are startup reconciliation (Task 4) and boot/`TIME_SET` re-arm (AKA-83).
- `BabyTrackerApp` startup reconciliation re-arms when both toggles are enabled; no-ops otherwise.
- Receiver registered `exported="false"` in the manifest.
- Tests pass.
