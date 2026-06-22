# Vaccine To-Schedule — Plan 03: Reminders & Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire a "time to book" nudge for to-schedule doses at `targetDate − toScheduleLead`, using a new separate lead setting, distinct notification copy, and boot re-arming.

**Architecture:** A new `getToScheduleLeadDays`/`setToScheduleLeadDays` pair (allowed `{7,14,30}`, default 14) joins the existing vaccine settings. `VaccineReminderManager.schedule` branches on status to pick the lead; `rescheduleAll` re-arms both scheduled and to-schedule future doses; `VaccineReminderReceiver` accepts to-schedule and shows distinct copy via a new `isToSchedule` flag on `VaccineNotificationHelper.show`.

**Tech Stack:** Kotlin, DataStore 1.1.1, AlarmManager, Hilt, JUnit 5, MockK.

## Global Constraints

- One **shared** master enable toggle (`reminderEnabled`) gates both reminder types.
- To-schedule lead: allowed set `{7, 14, 30}`, default **14**, key `vaccine_to_schedule_lead_days`. Sanitize out-of-set values to the default (mirror `getReminderLeadDays`).
- Reuse the pure `computeReminderTriggerAtMs(targetMs, leadDays, nowMs, zone)` — do not reimplement timing.
- All new user-facing strings added to **both** `values/strings.xml` and `values-pt-rBR/strings.xml`.
- Depends on Plan 01 (enum, `getToScheduleFutureAfter`) and Plan 02 (use cases that call `schedule`).

---

### Task 1: Add the to-schedule lead setting

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/VaccineSettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/VaccineSettingsRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/VaccineSettingsRepositoryImplTest.kt` (add cases)

**Interfaces:**
- Produces: `VaccineSettingsRepository.getToScheduleLeadDays(): Flow<Int>` / `setToScheduleLeadDays(days: Int)`. Consumed by Plan 03 Task 2 (manager) and Plan 04 (settings screen).

- [ ] **Step 1: Write the failing test**

Add to `VaccineSettingsRepositoryImplTest.kt` (mirror the existing lead-days tests):

```kotlin
@Test
fun `to-schedule lead defaults to 14`() = runTest {
    assertEquals(14, repository.getToScheduleLeadDays().first())
}

@Test
fun `set then get to-schedule lead round-trips an allowed value`() = runTest {
    repository.setToScheduleLeadDays(30)
    assertEquals(30, repository.getToScheduleLeadDays().first())
}

@Test
fun `to-schedule lead sanitizes an out-of-set value to the default`() = runTest {
    repository.setToScheduleLeadDays(5)
    assertEquals(14, repository.getToScheduleLeadDays().first())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.data.repository.VaccineSettingsRepositoryImplTest"`
Expected: FAIL — methods unresolved.

- [ ] **Step 3: Write minimal implementation**

In `VaccineSettingsRepository.kt`, add to the interface:

```kotlin
fun getToScheduleLeadDays(): Flow<Int>
suspend fun setToScheduleLeadDays(days: Int)
```

In `VaccineSettingsRepositoryImpl.kt`, extend the `companion object`:

```kotlin
val TO_SCHEDULE_LEAD_DAYS = intPreferencesKey("vaccine_to_schedule_lead_days")
const val DEFAULT_TO_SCHEDULE_LEAD_DAYS = 14
val ALLOWED_TO_SCHEDULE_LEAD_DAYS = setOf(7, 14, 30)
```

and add the implementations:

```kotlin
override fun getToScheduleLeadDays(): Flow<Int> =
    dataStore.data.map { prefs ->
        val stored = prefs[TO_SCHEDULE_LEAD_DAYS] ?: DEFAULT_TO_SCHEDULE_LEAD_DAYS
        if (stored in ALLOWED_TO_SCHEDULE_LEAD_DAYS) stored else DEFAULT_TO_SCHEDULE_LEAD_DAYS
    }

override suspend fun setToScheduleLeadDays(days: Int) {
    val sanitized = if (days in ALLOWED_TO_SCHEDULE_LEAD_DAYS) days else DEFAULT_TO_SCHEDULE_LEAD_DAYS
    dataStore.edit { it[TO_SCHEDULE_LEAD_DAYS] = sanitized }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.data.repository.VaccineSettingsRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/VaccineSettingsRepository.kt app/src/main/java/com/babytracker/data/repository/VaccineSettingsRepositoryImpl.kt app/src/test/java/com/babytracker/data/repository/VaccineSettingsRepositoryImplTest.kt
git commit -m "feat(vaccine): add to-schedule reminder lead setting"
```

---

### Task 2: `VaccineReminderManager` — lead branch + boot re-arm

**Files:**
- Modify: `app/src/main/java/com/babytracker/manager/VaccineReminderManager.kt`
- Test: `app/src/test/java/com/babytracker/manager/VaccineReminderManagerTest.kt` (add cases)

**Interfaces:**
- Consumes: `VaccineSettingsRepository.getToScheduleLeadDays` (Task 1); `VaccineRepository.getToScheduleFutureAfter` (Plan 01).
- Produces: `schedule(record)` arms a to-schedule reminder using the to-schedule lead; `rescheduleAll()` re-arms both record sets.

- [ ] **Step 1: Write the failing tests**

Refactor the test's `setup()` to retain the AlarmManager and settings/repository mocks as fields, then add the branch tests. Replace the existing `setup()` with:

```kotlin
private val zone = ZoneId.of("UTC")
private lateinit var manager: VaccineReminderManager
private lateinit var alarmManager: AlarmManager
private lateinit var settings: VaccineSettingsRepository
private lateinit var repository: VaccineRepository

@BeforeEach
fun setup() {
    val context = mockk<Context>(relaxed = true)
    alarmManager = mockk(relaxed = true)
    every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
    settings = mockk(relaxed = true)
    repository = mockk(relaxed = true)
    manager = VaccineReminderManager(context = context, settings = settings, repository = repository)
}
```

Add these tests (keep the four existing `computeTriggerAtMs` tests unchanged):

```kotlin
@Test
fun `schedule uses the to-schedule lead for a to-schedule record`() = runTest {
    every { settings.getReminderEnabled() } returns flowOf(true)
    every { settings.getToScheduleLeadDays() } returns flowOf(14)
    val record = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.ofEpochMilli(at(2030, 1, 1, 12)), createdAt = Instant.ofEpochMilli(1),
    )

    manager.schedule(record)

    verify { settings.getToScheduleLeadDays() }
    verify(exactly = 0) { settings.getReminderLeadDays() }
    verify { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
}

@Test
fun `schedule does nothing for an administered record`() = runTest {
    val record = VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.ADMINISTERED,
        administeredDate = Instant.ofEpochMilli(1), createdAt = Instant.ofEpochMilli(1),
    )
    manager.schedule(record)
    verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
}

@Test
fun `rescheduleAll arms both scheduled and to-schedule future doses`() = runTest {
    every { settings.getReminderEnabled() } returns flowOf(true)
    every { settings.getReminderLeadDays() } returns flowOf(7)
    every { settings.getToScheduleLeadDays() } returns flowOf(14)
    coEvery { repository.getScheduledFutureAfter(any()) } returns listOf(
        VaccineRecord(id = 1, name = "S", status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(at(2030, 1, 1, 12)), createdAt = Instant.ofEpochMilli(1)),
    )
    coEvery { repository.getToScheduleFutureAfter(any()) } returns listOf(
        VaccineRecord(id = 2, name = "T", status = VaccineStatus.TO_SCHEDULE,
            scheduledDate = Instant.ofEpochMilli(at(2030, 2, 1, 12)), createdAt = Instant.ofEpochMilli(1)),
    )

    manager.rescheduleAll()

    coVerify { repository.getScheduledFutureAfter(any()) }
    coVerify { repository.getToScheduleFutureAfter(any()) }
}
```

Add imports: `com.babytracker.domain.model.VaccineRecord`, `com.babytracker.domain.model.VaccineStatus`, `io.mockk.coEvery`, `io.mockk.coVerify`, `io.mockk.every`, `io.mockk.verify`, `kotlinx.coroutines.flow.flowOf`, `kotlinx.coroutines.test.runTest`, `java.time.Instant`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.manager.VaccineReminderManagerTest"`
Expected: FAIL — `getToScheduleLeadDays`/`getToScheduleFutureAfter` not wired into the manager.

- [ ] **Step 3: Write minimal implementation**

In `VaccineReminderManager.kt`, replace `schedule` with the status-branching version:

```kotlin
override suspend fun schedule(record: VaccineRecord) {
    // Always clear any prior alarm for this id (idempotent re-arm).
    cancel(record.id)
    val leadDays = when (record.status) {
        VaccineStatus.SCHEDULED -> settings.getReminderLeadDays().first()
        VaccineStatus.TO_SCHEDULE -> settings.getToScheduleLeadDays().first()
        VaccineStatus.ADMINISTERED -> return // a logged shot never carries a reminder
    }
    val target = record.scheduledDate ?: return
    if (!settings.getReminderEnabled().first()) return
    val triggerAtMs = computeTriggerAtMs(
        scheduledMs = target.toEpochMilli(),
        leadDays = leadDays,
        nowMs = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
    ) ?: return // no sensible reminder window before the target

    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMs,
        buildPendingIntent(record.id),
    )
    Log.d(TAG, "Scheduled vaccine reminder id=${record.id} status=${record.status} at $triggerAtMs")
}
```

Replace `rescheduleAll` with:

```kotlin
override suspend fun rescheduleAll() {
    val enabled = settings.getReminderEnabled().first()
    val nowMs = System.currentTimeMillis()
    val records = repository.getScheduledFutureAfter(nowMs) + repository.getToScheduleFutureAfter(nowMs)
    records.forEach { record ->
        if (enabled) schedule(record) else cancel(record.id)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.manager.VaccineReminderManagerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/manager/VaccineReminderManager.kt app/src/test/java/com/babytracker/manager/VaccineReminderManagerTest.kt
git commit -m "feat(vaccine): arm to-schedule nudges with their own lead and on boot"
```

---

### Task 3: Notification copy + receiver branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/VaccineNotificationHelper.kt`
- Modify: `app/src/main/java/com/babytracker/receiver/VaccineReminderReceiver.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-pt-rBR/strings.xml`
- Test: `app/src/test/java/com/babytracker/receiver/VaccineReminderReceiverTest.kt` (update + add)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`.
- Produces: `VaccineNotificationHelper.show(context, vaccineName, date, isToSchedule: Boolean = false)`; receiver notifies for `SCHEDULED` and `TO_SCHEDULE` with the right copy.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, after `vaccine_reminder_body` (line ~1191):

```xml
<string name="vaccine_to_schedule_reminder_title">Time to book a vaccine</string>
<string name="vaccine_to_schedule_reminder_body">%1$s — book the appointment (target %2$s)</string>
```

In `app/src/main/res/values-pt-rBR/strings.xml`, in the matching spot:

```xml
<string name="vaccine_to_schedule_reminder_title">Hora de agendar uma vacina</string>
<string name="vaccine_to_schedule_reminder_body">%1$s — marque a consulta (meta %2$s)</string>
```

- [ ] **Step 2: Write the failing test**

Update `VaccineReminderReceiverTest.kt`:
1. Change every `VaccineNotificationHelper.show(any(), any(), any())` (the `every {}` stub and all `verify {}` calls) to four args: `show(any(), any(), any(), any())`.
2. Change the scheduled assertion to `verify(exactly = 1) { VaccineNotificationHelper.show(context, "BCG", scheduled, false) }`.
3. Add:

```kotlin
@Test
fun `notifies for a to-schedule record with the book copy`() = runTest {
    val target = Instant.ofEpochMilli(50_000)
    every { settings.getReminderEnabled() } returns flowOf(true)
    coEvery { repository.getById(1) } returns VaccineRecord(
        id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = target, createdAt = Instant.ofEpochMilli(1),
    )
    receiver.handle(context, 1)
    verify(exactly = 1) { VaccineNotificationHelper.show(context, "MMR", target, true) }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.receiver.VaccineReminderReceiverTest"`
Expected: FAIL — `show` has no 4th param; receiver rejects TO_SCHEDULE.

- [ ] **Step 4: Write minimal implementation**

In `VaccineNotificationHelper.kt`, change `show` to accept the flag and branch the copy:

```kotlin
fun show(context: Context, vaccineName: String, scheduledDate: Instant, isToSchedule: Boolean = false) {
    val dateLabel = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        .format(scheduledDate.atZone(ZoneId.systemDefault()).toLocalDate())
    val accent = NotificationHelper.resolveAccent(context, VaccineIndigo, VaccineIndigoDark)
    val titleRes = if (isToSchedule) R.string.vaccine_to_schedule_reminder_title else R.string.vaccine_reminder_title
    val bodyRes = if (isToSchedule) R.string.vaccine_to_schedule_reminder_body else R.string.vaccine_reminder_body
    val title = context.getString(titleRes)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notif_limit)
        .setColor(accent)
        .setColorized(false)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentTitle(title)
        .setContentText(context.getString(bodyRes, vaccineName, dateLabel))
        .setTicker(title)
        .setAutoCancel(true)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(tapPendingIntent(context))
        .build()
    context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    Log.d(TAG, "show posted (name=$vaccineName, toSchedule=$isToSchedule)")
}
```

In `VaccineReminderReceiver.kt`, replace the guard + show call in `handle`:

```kotlin
val record = repository.getById(id) ?: return
if (record.status != VaccineStatus.SCHEDULED && record.status != VaccineStatus.TO_SCHEDULE) return
val target = record.scheduledDate ?: return
runCatching {
    VaccineNotificationHelper.show(
        context, record.name, target, isToSchedule = record.status == VaccineStatus.TO_SCHEDULE,
    )
}.onFailure { if (it is SecurityException) Log.w(TAG, "POST_NOTIFICATIONS denied", it) else throw it }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.receiver.VaccineReminderReceiverTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/util/VaccineNotificationHelper.kt app/src/main/java/com/babytracker/receiver/VaccineReminderReceiver.kt app/src/main/res/values/strings.xml app/src/main/res/values-pt-rBR/strings.xml app/src/test/java/com/babytracker/receiver/VaccineReminderReceiverTest.kt
git commit -m "feat(vaccine): show a distinct book-it reminder for to-schedule doses"
```

---

## Self-Review

- **Spec coverage:** separate lead setting (Task 1), lead branch + boot re-arm (Task 2), distinct copy + receiver acceptance (Task 3). Shared master toggle preserved (read inside `schedule`/`rescheduleAll`).
- **Placeholder scan:** none.
- **Types:** `getToScheduleLeadDays(): Flow<Int>` and `show(..., isToSchedule: Boolean = false)` consistent across producer/consumer. `VaccineReminderBootReceiver` calls `rescheduleAll()` (unchanged signature) so it picks up to-schedule re-arm for free — no edit needed there.
