# Doctor Visit Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-207

**Goal:** Add configurable "remind me N days before" notifications for upcoming visits — a DataStore-backed settings repository, an inexact `AlarmManager` scheduler, fire + boot receivers, a notification channel, the settings UI section, and the Hilt rebind that replaces plan 2's no-op scheduler.

**Architecture:** Mirrors the shipped Vaccine reminder wiring (`manager/VaccineReminderManager.kt`, `receiver/VaccineReminder*Receiver.kt`). A single global lead time (`{1,3,7,14}` days, default `1`) + on/off toggle in DataStore. Each upcoming visit schedules an inexact `setAndAllowWhileIdle` alarm at `(visit date @ local 09:00) − leadDays`. The fire receiver re-validates the visit before posting; the boot receiver re-arms all upcoming alarms.

**Tech Stack:** Kotlin, Hilt, DataStore 1.1.1, AlarmManager, WorkManager-free (direct alarms like vaccine), Coroutines/Flow; JUnit 5 + MockK + Robolectric.

## Global Constraints

- Inexact alarms only (`setAndAllowWhileIdle`) — **no** `SCHEDULE_EXACT_ALARM` permission.
- `RECEIVE_BOOT_COMPLETED` is already declared (vaccine/predictive-sleep) — reuse, do not redeclare.
- Lead-days allowlist `setOf(1, 3, 7, 14)`, default `1`, sanitized at the repository boundary.
- Reuse the existing `NotificationHelper` channel-creation pattern; tint Slate.
- All user-facing strings in en + pt-BR.

**Dependencies:** Plan 1 (data — `getUpcomingVisitsAfter`), Plan 2 (use cases — they already call the scheduler interface). This plan supplies the real implementation and re-points the binding.

**Suggested implementation branch:** `feat/doctor-visit-reminders`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `DoctorVisitSettingsRepository` (+ impl)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/DoctorVisitSettingsRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/DoctorVisitSettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt` (bind)
- Test: `app/src/test/java/com/babytracker/data/repository/DoctorVisitSettingsRepositoryImplTest.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface DoctorVisitSettingsRepository {
    fun getReminderEnabled(): Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
    fun getReminderLeadDays(): Flow<Int>
    suspend fun setReminderLeadDays(days: Int)

    companion object {
        val ALLOWED_LEAD_DAYS = setOf(1, 3, 7, 14)
        const val DEFAULT_LEAD_DAYS = 1
    }
}
```

- [ ] **Step 2: Impl** (DataStore-backed; mirror the Vaccine settings repo — search `rg "vaccine_reminder_enabled" -g "*.kt"` to copy the exact DataStore wiring). Keys: `doctor_visit_reminder_enabled`, `doctor_visit_reminder_lead_days`. `setReminderLeadDays` clamps to the allowlist (fallback to default); `getReminderLeadDays` sanitizes reads too.

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository.Companion.ALLOWED_LEAD_DAYS
import com.babytracker.domain.repository.DoctorVisitSettingsRepository.Companion.DEFAULT_LEAD_DAYS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DoctorVisitSettingsRepository {
    private val enabledKey = booleanPreferencesKey("doctor_visit_reminder_enabled")
    private val leadDaysKey = intPreferencesKey("doctor_visit_reminder_lead_days")

    override fun getReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[enabledKey] ?: false }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override fun getReminderLeadDays(): Flow<Int> =
        dataStore.data.map { prefs ->
            val raw = prefs[leadDaysKey] ?: DEFAULT_LEAD_DAYS
            if (raw in ALLOWED_LEAD_DAYS) raw else DEFAULT_LEAD_DAYS
        }

    override suspend fun setReminderLeadDays(days: Int) {
        val sanitized = if (days in ALLOWED_LEAD_DAYS) days else DEFAULT_LEAD_DAYS
        dataStore.edit { it[leadDaysKey] = sanitized }
    }
}
```

- [ ] **Step 3: Bind** in `RepositoryModule` (`@Binds @Singleton`).
- [ ] **Step 4: Test** allowlist sanitization (write 5 → reads default; write 7 → reads 7; default when unset is 1; enabled default false). Use a test `DataStore` or a `FakeDataStore` per the vaccine settings test.
- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.data.repository.DoctorVisitSettingsRepositoryImplTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(doctor-visit): add reminder settings repository`

---

### Task 2: Notification channel + `showDoctorVisitReminder`

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/NotificationHelper.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`

- [ ] **Step 1: Add the channel + post method** (mirror `showVaccineReminder`). Channel id `doctor_visit_reminders`, default importance. Title/body from strings, tinted with the Slate accent (`DoctorSlate`).

```kotlin
// in NotificationHelper:
const val CHANNEL_DOCTOR_VISIT_REMINDERS = "doctor_visit_reminders"

fun showDoctorVisitReminder(providerName: String?, dateLabel: String, leadDays: Int) {
    // build channel (if not created), build notification with R.string.doctor_visit_reminder_title
    // and R.string.doctor_visit_reminder_body (args: providerName-or-generic, leadDays), tint DoctorSlate.
}
```

- [ ] **Step 2: Strings**

```xml
<!-- values/strings.xml -->
<string name="doctor_visit_reminder_channel">Doctor visit reminders</string>
<string name="doctor_visit_reminder_title">Upcoming doctor visit</string>
<string name="doctor_visit_reminder_body">%1$s in %2$d days</string>
<string name="doctor_visit_reminder_body_generic">Doctor visit in %1$d days</string>
```

```xml
<!-- values-pt-rBR/strings.xml -->
<string name="doctor_visit_reminder_channel">Lembretes de consulta</string>
<string name="doctor_visit_reminder_title">Consulta médica próxima</string>
<string name="doctor_visit_reminder_body">%1$s em %2$d dias</string>
<string name="doctor_visit_reminder_body_generic">Consulta médica em %1$d dias</string>
```

- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add reminder notification channel + helper`

---

### Task 3: `DoctorVisitReminderManager` (real scheduler)

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/DoctorVisitReminderManager.kt`
- Test: `app/src/test/java/com/babytracker/manager/DoctorVisitReminderManagerTest.kt`

- [ ] **Step 1: Implement `DoctorVisitReminderScheduler`** (the interface from plan 2). Mirror `VaccineReminderManager`. Trigger-time math: take the visit `date`, set it to local 09:00 on that calendar day, subtract `leadDays`. If the result is in the past but the visit date is still future, fire at the next ~09:00 (or `now + small delta`); if the visit date itself is past, do not schedule. Request code derived from `visitId`. Action `com.babytracker.DOCTOR_VISIT_REMINDER_FIRE`, extras `visitId`.

```kotlin
package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.receiver.DoctorVisitReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DoctorVisitRepository,
    private val settings: DoctorVisitSettingsRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DoctorVisitReminderScheduler {

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun schedule(visit: DoctorVisit) {
        if (!settings.getReminderEnabled().first()) return
        val now = Instant.now()
        if (!visit.date.isAfter(now)) return
        val leadDays = settings.getReminderLeadDays().first().toLong()
        val triggerAt = computeTriggerAt(visit.date, leadDays, now)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(visit.id))
    }

    override fun cancel(visitId: Long) {
        alarmManager.cancel(pendingIntent(visitId))
    }

    override suspend fun rescheduleAll() {
        val upcoming = repository.getUpcomingVisitsAfter(Instant.now().toEpochMilli())
        upcoming.forEach { cancel(it.id) }
        if (!settings.getReminderEnabled().first()) return
        upcoming.forEach { schedule(it) }
    }

    private fun computeTriggerAt(date: Instant, leadDays: Long, now: Instant): Long {
        val at9 = date.atZone(zoneId).toLocalDate().atTime(LocalTime.of(9, 0)).atZone(zoneId).toInstant()
        val target = at9.minusSeconds(leadDays * 86_400)
        return if (target.isAfter(now)) target.toEpochMilli() else now.plusSeconds(60).toEpochMilli()
    }

    private fun pendingIntent(visitId: Long): PendingIntent {
        val intent = Intent(context, DoctorVisitReminderReceiver::class.java).apply {
            action = "com.babytracker.DOCTOR_VISIT_REMINDER_FIRE"
            putExtra("visitId", visitId)
        }
        return PendingIntent.getBroadcast(
            context, visitId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

> Confirm `VaccineReminderManager`'s exact request-code derivation, `PendingIntent` flags, and `RTC_WAKEUP` vs `RTC` choice, and match them.

- [ ] **Step 2: Test** `computeTriggerAt` (lead-days subtraction, 09:00 alignment, past-trigger → now+delta), and that `schedule` is a no-op when disabled or when the visit is in the past. Use MockK for settings/repository (`coEvery { settings.getReminderEnabled() } returns flowOf(true)`), and inject a fixed `ZoneId`. AlarmManager interaction can be verified via Robolectric `ShadowAlarmManager` (mirror the vaccine manager test).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.manager.DoctorVisitReminderManagerTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisitReminderManager`

---

### Task 4: Fire + boot receivers

**Files:**
- Create: `app/src/main/java/com/babytracker/receiver/DoctorVisitReminderReceiver.kt`
- Create: `app/src/main/java/com/babytracker/receiver/DoctorVisitReminderBootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Fire receiver** (`@AndroidEntryPoint`, mirror `VaccineReminderReceiver`): on the fire action, read `visitId`, `goAsync()`, fetch the visit, and post only if it still exists and is still upcoming; format the date label and lead days for `showDoctorVisitReminder`.
- [ ] **Step 2: Boot receiver** (`@AndroidEntryPoint`, mirror the vaccine/predictive-sleep boot receiver): on `BOOT_COMPLETED` / `TIME_SET`, `goAsync()` + call `scheduler.rescheduleAll()`.
- [ ] **Step 3: Manifest** — register both receivers (the fire receiver needs no intent filter beyond being targeted by the explicit `PendingIntent`; the boot receiver needs the `BOOT_COMPLETED` + `TIME_SET` intent filter). Copy the vaccine receiver `<receiver>` blocks and rename.
- [ ] **Step 4: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 5: Commit** `feat(doctor-visit): add reminder fire + boot receivers`

---

### Task 5: Re-point the Hilt binding to the real manager

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`
- Delete (optional): `app/src/main/java/com/babytracker/manager/NoOpDoctorVisitReminderScheduler.kt`

- [ ] **Step 1: Change the binding** so `DoctorVisitReminderScheduler` → `DoctorVisitReminderManager` (was `NoOpDoctorVisitReminderScheduler` from plan 2). Remove the no-op binding; delete the no-op class if nothing else references it.

```kotlin
@Binds
@Singleton
abstract fun bindDoctorVisitReminderScheduler(impl: DoctorVisitReminderManager): DoctorVisitReminderScheduler
```

- [ ] **Step 2: Build + run unit tests** `./gradlew test` — expect PASS (the use-case tests from plan 2 still pass; they mock the interface).
- [ ] **Step 3: Commit** `feat(doctor-visit): bind real reminder manager`

---

### Task 6: Settings UI section

**Files:**
- Modify: the settings screen (search `rg "vaccine_reminder" -g "*.kt"` to find where the vaccine reminder section renders) + its ViewModel.

- [ ] **Step 1: Add a "Visit reminders" section** mirroring the vaccine reminder section: an on/off `Switch` bound to `getReminderEnabled`/`setReminderEnabled`, and a days-before selector (`1/3/7/14`) bound to `getReminderLeadDays`/`setReminderLeadDays`, shown only when enabled. After either change, call `DoctorVisitReminderScheduler.rescheduleAll()` (inject the scheduler into the settings ViewModel, as the vaccine section does).
- [ ] **Step 2: Strings**

```xml
<!-- values/strings.xml -->
<string name="settings_doctor_visit_reminders">Visit reminders</string>
<string name="settings_doctor_visit_reminders_summary">Notify before an upcoming visit</string>
<string name="settings_doctor_visit_lead_days">Remind me %1$d days before</string>
```

```xml
<!-- values-pt-rBR/strings.xml -->
<string name="settings_doctor_visit_reminders">Lembretes de consulta</string>
<string name="settings_doctor_visit_reminders_summary">Avisar antes de uma consulta</string>
<string name="settings_doctor_visit_lead_days">Lembrar %1$d dias antes</string>
```

- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): add reminder settings section`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- Toggling reminders on + setting a lead time arms inexact alarms for all upcoming visits.
- Adding/editing/deleting a visit (plan 2 use cases) now schedules/cancels real alarms.
- The fire receiver suppresses notifications for deleted / no-longer-upcoming visits.
- The boot receiver re-arms alarms after reboot.
- Lead-days values outside `{1,3,7,14}` are sanitized to the default.
- Strings in en + pt-BR.

## Self-Review Notes

- Spec coverage: settings repo + allowlist, scheduler/manager, fire + boot receivers, manifest, channel + helper, settings UI, Hilt rebind — all present.
- This plan replaces plan 2's no-op binding; the use-case unit tests (which mock the interface) are unaffected.
- Inexact alarms (no exact-alarm permission); `RECEIVE_BOOT_COMPLETED` already declared.
- The implementer must match `VaccineReminderManager`'s request-code + `PendingIntent` flag conventions to avoid alarm-collision bugs across sections.
