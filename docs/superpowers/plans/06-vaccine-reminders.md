# Vaccine Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-198

**Goal:** Notify the parent a configurable number of days before a scheduled vaccine. Add the reminder-settings repository (on/off + days-before), the real `VaccineReminderManager` (replacing plan 2's no-op), the notification channel + builder, the fire + boot receivers, the manifest entries, and the settings UI.

**Architecture:** Mirrors the milk-stash expiration scheduler (`StashExpirationScheduler` / `StashExpirationNotificationManager` / `StashExpirationReceiver` / `StashExpirationBootReceiver`) and the sleep nap-reminder settings (`SleepSettingsRepositoryImpl` allowlist pattern). Differences from stash: a vaccine schedules **many one-shot** alarms (one per scheduled future record, each with a unique `PendingIntent`) rather than a single daily alarm, and the fire receiver does **not** re-arm itself (one-shot). Alarms are **inexact** (`setAndAllowWhileIdle`, `RTC_WAKEUP`) — "a few days before, around 09:00" tolerates inexactness, so no `SCHEDULE_EXACT_ALARM` permission is needed. `RECEIVE_BOOT_COMPLETED` is already declared (predictive sleep / stash use it) — verify, don't re-add.

**Tech Stack:** Kotlin, Hilt, AlarmManager, DataStore, Coroutines/Flow; JUnit 5 + MockK + Turbine.

**Dependencies:** Plan 1 (`VaccineRepository`, `VaccineRecord`), Plan 2 (`VaccineReminderScheduler` interface + the no-op binding this plan replaces). Settings UI also depends on Plan 4's strings file (extend it).

**Suggested implementation branch:** `feat/vaccine-reminders`

**Project convention:** Implement first, then tests. Commit after each task. Strings in both locales. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `VaccineSettingsRepository` + impl + DI

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/VaccineSettingsRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/VaccineSettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/VaccineSettingsRepositoryImplTest.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface VaccineSettingsRepository {
    fun getReminderEnabled(): Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
    fun getReminderLeadDays(): Flow<Int>
    suspend fun setReminderLeadDays(days: Int)
}
```

- [ ] **Step 2: Impl** (mirrors `SleepSettingsRepositoryImpl` allowlist sanitization)

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.VaccineSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : VaccineSettingsRepository {

    private companion object {
        val REMINDER_ENABLED = booleanPreferencesKey("vaccine_reminder_enabled")
        val REMINDER_LEAD_DAYS = intPreferencesKey("vaccine_reminder_lead_days")
        const val DEFAULT_LEAD_DAYS = 7
        val ALLOWED_LEAD_DAYS = setOf(1, 3, 7, 14)
    }

    override fun getReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[REMINDER_ENABLED] ?: false }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    override fun getReminderLeadDays(): Flow<Int> =
        dataStore.data.map { prefs ->
            val stored = prefs[REMINDER_LEAD_DAYS] ?: DEFAULT_LEAD_DAYS
            if (stored in ALLOWED_LEAD_DAYS) stored else DEFAULT_LEAD_DAYS
        }

    override suspend fun setReminderLeadDays(days: Int) {
        val sanitized = if (days in ALLOWED_LEAD_DAYS) days else DEFAULT_LEAD_DAYS
        dataStore.edit { it[REMINDER_LEAD_DAYS] = sanitized }
    }
}
```

- [ ] **Step 3:** Bind in `RepositoryModule`: `@Binds @Singleton abstract fun bindVaccineSettingsRepository(impl: VaccineSettingsRepositoryImpl): VaccineSettingsRepository`.
- [ ] **Step 4: Test** — default enabled=false, default lead=7, out-of-allowlist write sanitizes to 7, allowed values persist. Follow the existing settings-repo test idiom (in-memory/test DataStore or a faked `DataStore<Preferences>`).
- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.data.repository.VaccineSettingsRepositoryImplTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(vaccine): add VaccineSettingsRepository with reminder lead-time allowlist`

---

### Task 2: `VaccineReminderManager` (real scheduler) + DI swap

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/VaccineReminderManager.kt`
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Delete: `app/src/main/java/com/babytracker/manager/NoOpVaccineReminderScheduler.kt` (introduced in plan 2)

- [ ] **Step 1: Implement** the manager (mirrors `StashExpirationNotificationManager`; one-shot inexact alarm per record; PendingIntent made unique per id via a `vaccine://reminder/<id>` data URI **and** a per-id request code; targets the receiver by class-name string to avoid a manager↔receiver compile cycle).

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VaccineSettingsRepository,
    private val repository: VaccineRepository,
) : VaccineReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(record: VaccineRecord) {
        // Always clear any prior alarm for this id (idempotent re-arm).
        cancel(record.id)
        if (record.status != VaccineStatus.SCHEDULED) return
        val scheduled = record.scheduledDate ?: return
        if (!settings.getReminderEnabled().first()) return
        val leadDays = settings.getReminderLeadDays().first()
        val triggerAtMs = computeTriggerAtMs(
            scheduledMs = scheduled.toEpochMilli(),
            leadDays = leadDays,
            nowMs = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return // no sensible reminder window before the shot

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            buildPendingIntent(record.id),
        )
        Log.d(TAG, "Scheduled vaccine reminder id=${record.id} at $triggerAtMs")
    }

    /**
     * Reminder trigger: ~09:00 local, [leadDays] before the scheduled date. If that lead window
     * has already passed, fall back to the NEXT 09:00 strictly after now — but never at/after the
     * scheduled instant (no immediate late-night ping, no post-shot ping). Returns null when no
     * window remains. Pure + testable without AlarmManager.
     */
    internal fun computeTriggerAtMs(scheduledMs: Long, leadDays: Int, nowMs: Long, zone: ZoneId): Long? {
        if (scheduledMs <= nowMs) return null
        fun nineAmOn(date: LocalDate): Long =
            date.atTime(REMINDER_HOUR_OF_DAY, 0).atZone(zone).toInstant().toEpochMilli()
        val scheduledDate = Instant.ofEpochMilli(scheduledMs).atZone(zone).toLocalDate()
        val leadTrigger = nineAmOn(scheduledDate.minusDays(leadDays.toLong()))
        val candidate = if (leadTrigger > nowMs) {
            leadTrigger
        } else {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val today9 = nineAmOn(today)
            if (today9 > nowMs) today9 else nineAmOn(today.plusDays(1))
        }
        return candidate.takeIf { it < scheduledMs }
    }

    override fun cancel(recordId: Long) {
        alarmManager.cancel(buildPendingIntent(recordId))
    }

    override suspend fun rescheduleAll() {
        val enabled = settings.getReminderEnabled().first()
        val future = repository.getScheduledFutureAfter(System.currentTimeMillis())
        future.forEach { record ->
            if (enabled) schedule(record) else cancel(record.id)
        }
    }

    private fun buildPendingIntent(recordId: Long): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, RECEIVER_CLASS)
            .setData(Uri.parse("vaccine://reminder/$recordId"))
            .putExtra(EXTRA_VACCINE_ID, recordId)
        return PendingIntent.getBroadcast(
            context,
            (RC_BASE + recordId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_VACCINE_ID = "vaccine_id"
        const val REMINDER_HOUR_OF_DAY = 9
        private const val RC_BASE = 5000L
        private const val RECEIVER_CLASS = "com.babytracker.receiver.VaccineReminderReceiver"
        private const val TAG = "VaccineReminderManager"
    }
}
```

> The `(RC_BASE + recordId).toInt()` request code plus the unique `data` URI keep each pending intent distinct. The URI is what actually disambiguates the `PendingIntent` (request code alone can collide after int truncation for very large ids); both together are belt-and-suspenders.

- [ ] **Step 2:** In `NotificationSchedulerModule`, **replace** the plan-2 no-op binding with `@Binds @Singleton abstract fun bindVaccineReminderScheduler(impl: VaccineReminderManager): VaccineReminderScheduler`, and delete `NoOpVaccineReminderScheduler.kt`.
- [ ] **Step 3: Test** the pure `computeTriggerAtMs(scheduledMs, leadDays, nowMs, zone)` (no Android `AlarmManager` needed): (a) normal case — fires at 09:00, `leadDays` before the scheduled date; (b) lead window already passed but shot still future — fires at the **next 09:00 strictly after now**, never immediately; (c) the only remaining 09:00 lands at/after the scheduled instant — returns **null**; (d) scheduled is now/past — returns **null**. Mock `VaccineSettingsRepository`/`VaccineRepository` for `rescheduleAll` (enabled arms each future record; disabled cancels each).
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.manager.VaccineReminderManagerTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(vaccine): add VaccineReminderManager and replace no-op scheduler binding`

---

### Task 3: Notification channel + builder + app-startup wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-pt-rBR/strings.xml`

- [ ] **Step 1:** Add `createVaccineReminderNotificationChannel(context)` (default importance) to `NotificationHelper`, mirroring `createStashExpirationNotificationChannel`, plus a `showVaccineReminder(context, vaccineName, scheduledDate)` builder tinted with the Indigo accent (`VaccineIndigo`) — mirror `showStashExpiration`. Tapping it opens the vaccine history (intent to `Routes.VACCINE_HISTORY`, matching how other notifications open their screens). Catch `SecurityException` if `POST_NOTIFICATIONS` is denied (same as stash).
- [ ] **Step 2: Create the channel at startup — REQUIRED.** Channels are not auto-created; `BabyTrackerApp.onCreate()` creates every channel (e.g. `NotificationHelper.createStashExpirationNotificationChannel(this)`). Without this, an O+ reminder posts to a missing channel and **never displays** even though the alarm fired. Add `NotificationHelper.createVaccineReminderNotificationChannel(this)` alongside the other `create*Channel(this)` calls in `onCreate()`.
- [ ] **Step 3: Re-arm on cold start.** Add a `reconcileVaccineReminders()` to `BabyTrackerApp` (mirror `reconcileStashExpirationAlarm`): inject `VaccineReminderScheduler` + `VaccineSettingsRepository`, and from `onCreate()` `appScope.launch { runCatching { if (vaccineSettings.getReminderEnabled().first()) vaccineReminderScheduler.rescheduleAll() } }`. This covers the OS dropping alarms without a `BOOT_COMPLETED` (e.g. after force-stop), matching the stash pattern.
- [ ] **Step 4:** Add strings (both locales): channel name/description, `vaccine_reminder_title` (e.g. "Vaccine coming up"), `vaccine_reminder_body` with `%1$s` (name) and `%2$s` (formatted date) — e.g. "%1$s is scheduled for %2$s". Format the date via the existing `util/DateTimeExt.kt` helpers.
- [ ] **Step 5: Commit** `feat(vaccine): add vaccine reminder channel, builder, and app-startup wiring`

---

### Task 4: Fire receiver + boot receiver

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/VaccineReminderReceiver.kt`
- Create: `app/src/main/java/com/babytracker/receiver/VaccineReminderBootReceiver.kt`

- [ ] **Step 1: `VaccineReminderReceiver`** (`@AndroidEntryPoint`, `goAsync()` + timeout pattern from `StashExpirationReceiver`, but **one-shot** — no re-arm). Reads `EXTRA_VACCINE_ID`, re-queries `repository.getById(id)`; posts the notification only if the record still exists, is `SCHEDULED`, and reminders are enabled (suppresses stale notifications for since-administered/deleted records).

```kotlin
internal suspend fun handle(context: Context) {
    if (!settings.getReminderEnabled().first()) return
    val id = pendingId
    val record = repository.getById(id) ?: return
    if (record.status != VaccineStatus.SCHEDULED) return
    val scheduled = record.scheduledDate ?: return
    runCatching {
        NotificationHelper.showVaccineReminder(context, record.name, scheduled)
    }.onFailure { if (it is SecurityException) Log.w(TAG, "POST_NOTIFICATIONS denied", it) else throw it }
}
```

(inject `VaccineRepository` + `VaccineSettingsRepository`; read the id from the intent in `onReceive` and pass it to `handle`)

- [ ] **Step 2: `VaccineReminderBootReceiver`** (mirror `StashExpirationBootReceiver`): handles `ACTION_BOOT_COMPLETED`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`; if reminders enabled, calls `scheduler.rescheduleAll()` (inject `VaccineReminderScheduler` + `VaccineSettingsRepository`).
- [ ] **Step 3: Test** — `VaccineReminderReceiver.handle` suppression: given a record that is `ADMINISTERED` (or missing), no notification is attempted; given `SCHEDULED` + enabled, the builder is invoked. Boot receiver `shouldHandle` action filtering. Use MockK; the `NotificationHelper` static call can be verified via a thin seam or by asserting the repository/settings reads (follow how stash receiver tests are structured, if any).
- [ ] **Step 4: Commit** `feat(vaccine): add vaccine reminder fire and boot receivers`

---

### Task 5: Manifest registration

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1:** Register both receivers (mirror the `PredictiveSleepReceiver` / `PredictiveSleepBootReceiver` / `StashExpiration*` entries):

```xml
<receiver
    android:name=".receiver.VaccineReminderReceiver"
    android:exported="false" />

<receiver
    android:name=".receiver.VaccineReminderBootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 2:** Confirm `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />` is already present (it is, for predictive sleep / stash). Do **not** add `SCHEDULE_EXACT_ALARM` — alarms are inexact.
- [ ] **Step 3: Commit** `feat(vaccine): register vaccine reminder receivers in the manifest`

---

### Task 6: Settings UI section

**Files:**
- Modify: the settings screen (`ui/settings/SettingsScreen.kt`) + its ViewModel, OR add a dedicated route — follow how the nap-reminder / predictive-feed settings are surfaced.

- [ ] **Step 1:** Add a **"Vaccine reminders"** section: an enable toggle bound to `getReminderEnabled`/`setReminderEnabled`, and a days-before selector (1 / 3 / 7 / 14) bound to `getReminderLeadDays`/`setReminderLeadDays`, disabled when the toggle is off. Mirror the sleep nap-reminder section's composition.
- [ ] **Step 2:** When either value changes, call `VaccineReminderScheduler.rescheduleAll()` so existing scheduled vaccines pick up the new setting (arm on enable / re-time on lead change / cancel on disable). Do this from the settings ViewModel (inject the scheduler) after persisting.
- [ ] **Step 3:** Add strings (both locales): section title, toggle label, lead-days option labels (e.g. "%1$d days before"). Use a plural/format string as the app does elsewhere.
- [ ] **Step 4:** Test the settings ViewModel: toggling/lead-change persists and triggers `rescheduleAll()`.
- [ ] **Step 5: Commit** `feat(vaccine): add vaccine reminder settings section`

---

## Acceptance Criteria

- A scheduled future vaccine arms an inexact alarm at `(scheduled date − leadDays)` ~09:00 local; if that window already passed, it uses the next 09:00 strictly after now (never an immediate or post-shot ping), and arms nothing when no window remains; administered/overdue records arm nothing.
- Editing the date, marking-administered, or deleting a vaccine re-arms/cancels its specific alarm (via plan-2 use cases calling the now-real manager).
- Toggling the setting off cancels pending reminders; toggling on / changing lead-days re-arms all future scheduled vaccines.
- Reminders survive reboot / time change via the boot receiver, and cold start (force-stop) via `BabyTrackerApp.reconcileVaccineReminders()`.
- The `vaccine_reminders` channel is created at app startup, so fired reminders actually display on Android O+.
- The fire receiver suppresses notifications for records that were administered or deleted before firing.
- No `SCHEDULE_EXACT_ALARM` permission is required; `RECEIVE_BOOT_COMPLETED` already declared.
- `./gradlew test` passes (settings allowlist, trigger-time math, receiver suppression).

## Self-Review Notes

- The plan-2 no-op binding is replaced here, so plans 2/4/5 already call the scheduler — no use-case edits needed in this plan.
- Trigger-time logic is extracted into a pure function for unit testing without Android `AlarmManager` (the manager method itself is thin glue).
- Per-record `PendingIntent` uniqueness uses both a `vaccine://reminder/<id>` data URI and a per-id request code; the URI is the authoritative discriminator (request-code int truncation could collide on huge ids).
- `rescheduleAll()` enumerates via `getScheduledFutureAfter(now)` (plan 1 DAO) — there is no Android API to list pending alarms, so cancel-on-disable iterates the known future records. Records already cancelled individually (delete/mark) won't reappear.
- Inexact alarms can be delayed under Doze; acceptable for a multi-day-ahead reminder and re-armed on boot. The schedule is always visible in-app regardless.
