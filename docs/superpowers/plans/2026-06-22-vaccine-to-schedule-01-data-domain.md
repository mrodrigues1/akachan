# Vaccine To-Schedule — Plan 01: Data & Domain Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `TO_SCHEDULE` vaccine status to the domain model and data layer so later plans can persist and query to-schedule doses.

**Architecture:** `VaccineStatus` gains a third value, stored as a String in the existing `vaccines.status` column (no migration). A new `isPastTarget` domain predicate flags to-schedule doses whose target day has passed. A new DAO + repository query returns future to-schedule records for boot re-arming.

**Tech Stack:** Kotlin 2.3.20, Room 2.8.4, JUnit 5, MockK, Robolectric (androidTest uses AndroidJUnit4 + in-memory Room).

## Global Constraints

- DB stays at **version 15**, backup at **v6** — status is a String, a new value needs no migration.
- `toVaccineStatusSafe()` default MUST stay `ADMINISTERED` (unknown/old/partner values never fabricate a reminder).
- No Mapper classes — entity↔domain via extension functions.
- Day-granular comparisons use `Instant.atZone(zone).toLocalDate()` (see existing `isOverdue`).
- Run `./gradlew test --tests "<class>"` for unit changes; `connectedAndroidTest` for DAO changes (emulator). Full `./gradlew test` before the final commit.

---

### Task 1: Add `TO_SCHEDULE` to `VaccineStatus`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/VaccineStatus.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/VaccineStatusTest.kt` (create)

**Interfaces:**
- Produces: `VaccineStatus.TO_SCHEDULE`; `String.toVaccineStatusOrNull()` returns it for `"TO_SCHEDULE"`; `String.toVaccineStatusSafe()` still defaults to `ADMINISTERED`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VaccineStatusTest {
    @Test
    fun `toVaccineStatusOrNull maps every enum name`() {
        assertEquals(VaccineStatus.SCHEDULED, "SCHEDULED".toVaccineStatusOrNull())
        assertEquals(VaccineStatus.ADMINISTERED, "ADMINISTERED".toVaccineStatusOrNull())
        assertEquals(VaccineStatus.TO_SCHEDULE, "TO_SCHEDULE".toVaccineStatusOrNull())
    }

    @Test
    fun `toVaccineStatusOrNull returns null for an unknown value`() {
        assertNull("GARBAGE".toVaccineStatusOrNull())
    }

    @Test
    fun `toVaccineStatusSafe still defaults unknown to ADMINISTERED`() {
        assertEquals(VaccineStatus.ADMINISTERED, "GARBAGE".toVaccineStatusSafe())
        assertEquals(VaccineStatus.TO_SCHEDULE, "TO_SCHEDULE".toVaccineStatusSafe())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.model.VaccineStatusTest"`
Expected: FAIL — `TO_SCHEDULE` unresolved (compile error).

- [ ] **Step 3: Write minimal implementation**

Replace the body of `VaccineStatus.kt` with:

```kotlin
package com.babytracker.domain.model

enum class VaccineStatus { TO_SCHEDULE, SCHEDULED, ADMINISTERED }

fun String.toVaccineStatusOrNull(): VaccineStatus? = when (this) {
    VaccineStatus.TO_SCHEDULE.name -> VaccineStatus.TO_SCHEDULE
    VaccineStatus.SCHEDULED.name -> VaccineStatus.SCHEDULED
    VaccineStatus.ADMINISTERED.name -> VaccineStatus.ADMINISTERED
    else -> null
}

// Defaults to ADMINISTERED: an unknown/corrupt stored value is treated as a logged shot
// rather than a pending schedule, so it never fabricates a future reminder.
fun String.toVaccineStatusSafe(): VaccineStatus = toVaccineStatusOrNull() ?: VaccineStatus.ADMINISTERED
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.model.VaccineStatusTest"`
Expected: PASS. (Other modules may now fail to compile at exhaustive `when(status)` sites — those are fixed in their own plans; this task only needs its own test to pass. If the test module won't compile because of an unrelated exhaustive `when`, proceed — those sites belong to later plans and are listed in the spec.)

> Note: enum ordering is `TO_SCHEDULE, SCHEDULED, ADMINISTERED` (lifecycle order). Persistence uses `.name` (not `.ordinal`), so order is cosmetic and safe to choose for readability.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/VaccineStatus.kt app/src/test/java/com/babytracker/domain/model/VaccineStatusTest.kt
git commit -m "feat(vaccine): add TO_SCHEDULE vaccine status"
```

---

### Task 2: Add `isPastTarget` domain predicate

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/VaccineRecord.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/VaccineRecordTest.kt` (add cases)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE` (Task 1).
- Produces: `fun VaccineRecord.isPastTarget(now: Instant, zone: ZoneId): Boolean` — true only when `status == TO_SCHEDULE` and the target (`scheduledDate`) calendar day is strictly before today. Mirrors `isOverdue`'s day-granularity but for to-schedule.

- [ ] **Step 1: Write the failing test**

Add to `VaccineRecordTest.kt`:

```kotlin
@Test
fun `isPastTarget is true when a to-schedule target day is wholly in the past`() {
    val zone = ZoneId.of("UTC")
    val now = Instant.parse("2026-06-22T10:00:00Z")
    val record = VaccineRecord(
        name = "MMR",
        status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-06-21T00:00:00Z"),
        createdAt = now,
    )
    assertTrue(record.isPastTarget(now, zone))
}

@Test
fun `isPastTarget is false for today, future, and non-to-schedule records`() {
    val zone = ZoneId.of("UTC")
    val now = Instant.parse("2026-06-22T10:00:00Z")
    val base = VaccineRecord(name = "MMR", status = VaccineStatus.TO_SCHEDULE, createdAt = now)

    assertFalse(base.copy(scheduledDate = Instant.parse("2026-06-22T23:00:00Z")).isPastTarget(now, zone))
    assertFalse(base.copy(scheduledDate = Instant.parse("2026-06-30T00:00:00Z")).isPastTarget(now, zone))
    assertFalse(base.copy(scheduledDate = null).isPastTarget(now, zone))
    assertFalse(
        base.copy(status = VaccineStatus.SCHEDULED, scheduledDate = Instant.parse("2026-06-01T00:00:00Z"))
            .isPastTarget(now, zone),
    )
}
```

Ensure these imports exist in the test file: `java.time.Instant`, `java.time.ZoneId`, and JUnit 5 `assertTrue`/`assertFalse` from `org.junit.jupiter.api.Assertions`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.model.VaccineRecordTest"`
Expected: FAIL — `isPastTarget` unresolved.

- [ ] **Step 3: Write minimal implementation**

Append to `VaccineRecord.kt` (after `isOverdue`):

```kotlin
/**
 * Past-target is the to-schedule analogue of [isOverdue]: a to-schedule dose whose target calendar
 * day is wholly in the past. Used only for in-section flagging on the lists — to-schedule doses never
 * drive the dashboard hero, so this is intentionally separate from [isOverdue] (which stays
 * scheduled-only).
 */
fun VaccineRecord.isPastTarget(now: Instant, zone: ZoneId): Boolean =
    status == VaccineStatus.TO_SCHEDULE &&
        scheduledDate != null &&
        scheduledDate.atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.model.VaccineRecordTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/VaccineRecord.kt app/src/test/java/com/babytracker/domain/model/VaccineRecordTest.kt
git commit -m "feat(vaccine): add isPastTarget predicate for to-schedule doses"
```

---

### Task 3: Add `getToScheduleFutureAfter` DAO + repository query

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/dao/VaccineDao.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/VaccineRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/VaccineRepositoryImpl.kt`
- Test (unit): `app/src/test/java/com/babytracker/data/repository/VaccineRepositoryImplTest.kt` (add case)
- Test (instrumented): `app/src/androidTest/java/com/babytracker/data/local/dao/VaccineDaoTest.kt` (add case)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE` (Task 1).
- Produces:
  - `VaccineDao.getToScheduleFutureAfter(nowMs: Long): List<VaccineEntity>` — rows with `status = 'TO_SCHEDULE' AND scheduled_date IS NOT NULL AND scheduled_date > :nowMs`, ascending by `scheduled_date`.
  - `VaccineRepository.getToScheduleFutureAfter(nowMs: Long): List<VaccineRecord>` (consumed by Plan 03's `rescheduleAll`).

- [ ] **Step 1: Write the failing unit test**

Add to `VaccineRepositoryImplTest.kt` (matches the existing MockK style — mock the dao, verify delegation + mapping):

```kotlin
@Test
fun `getToScheduleFutureAfter maps dao rows to domain`() = runTest {
    val entity = VaccineEntity(
        id = 7,
        name = "MMR",
        status = "TO_SCHEDULE",
        scheduledDate = 1_900_000_000_000,
        createdAt = 1_700_000_000_000,
    )
    coEvery { dao.getToScheduleFutureAfter(any()) } returns listOf(entity)

    val result = repository.getToScheduleFutureAfter(1_800_000_000_000)

    assertEquals(1, result.size)
    assertEquals(VaccineStatus.TO_SCHEDULE, result.first().status)
    coVerify { dao.getToScheduleFutureAfter(1_800_000_000_000) }
}
```

Confirm the test file imports `com.babytracker.data.local.entity.VaccineEntity`, `com.babytracker.domain.model.VaccineStatus`, MockK `coEvery`/`coVerify`, and `kotlinx.coroutines.test.runTest`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.data.repository.VaccineRepositoryImplTest"`
Expected: FAIL — `getToScheduleFutureAfter` unresolved on `VaccineDao`/repository.

- [ ] **Step 3: Write minimal implementation**

In `VaccineDao.kt`, add after `getScheduledFutureAfter`:

```kotlin
@Query(
    "SELECT * FROM vaccines WHERE status = 'TO_SCHEDULE' AND scheduled_date IS NOT NULL " +
        "AND scheduled_date > :nowMs ORDER BY scheduled_date ASC",
)
suspend fun getToScheduleFutureAfter(nowMs: Long): List<VaccineEntity>
```

In `VaccineRepository.kt`, add to the interface after `getScheduledFutureAfter`:

```kotlin
suspend fun getToScheduleFutureAfter(nowMs: Long): List<VaccineRecord>
```

In `VaccineRepositoryImpl.kt`, add after the `getScheduledFutureAfter` override:

```kotlin
override suspend fun getToScheduleFutureAfter(nowMs: Long): List<VaccineRecord> =
    dao.getToScheduleFutureAfter(nowMs).map { it.toDomain() }
```

- [ ] **Step 4: Run unit test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.data.repository.VaccineRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 5: Add the instrumented DAO test**

Add to `VaccineDaoTest.kt` (AndroidJUnit4 + in-memory Room, mirroring the existing `getScheduledFutureAfter` test):

```kotlin
@Test
fun getToScheduleFutureAfter_returnsOnlyFutureToScheduleRows() = runTest {
    val now = 1_800_000_000_000L
    dao.insert(VaccineEntity(name = "Future TS", status = "TO_SCHEDULE", scheduledDate = now + 86_400_000, createdAt = now))
    dao.insert(VaccineEntity(name = "Past TS", status = "TO_SCHEDULE", scheduledDate = now - 86_400_000, createdAt = now))
    dao.insert(VaccineEntity(name = "Scheduled", status = "SCHEDULED", scheduledDate = now + 86_400_000, createdAt = now))

    val result = dao.getToScheduleFutureAfter(now)

    assertEquals(1, result.size)
    assertEquals("Future TS", result.first().name)
}
```

- [ ] **Step 6: Run the instrumented test**

Run (emulator/device required): `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.dao.VaccineDaoTest"`
Expected: PASS. (Check `adb devices` first; an emulator is usually already running.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/dao/VaccineDao.kt app/src/main/java/com/babytracker/domain/repository/VaccineRepository.kt app/src/main/java/com/babytracker/data/repository/VaccineRepositoryImpl.kt app/src/test/java/com/babytracker/data/repository/VaccineRepositoryImplTest.kt app/src/androidTest/java/com/babytracker/data/local/dao/VaccineDaoTest.kt
git commit -m "feat(vaccine): query future to-schedule doses for boot re-arm"
```

---

## Self-Review

- **Spec coverage:** enum + mappers (Task 1), `isPastTarget` (Task 2), DAO/repo future query for boot re-arm (Task 3). Entity/DB unchanged — confirmed no migration needed.
- **Downstream compile breaks** at exhaustive `when(status)` (EditVaccineRecordUseCase, ValidateBackupUseCase, sheet label) are intentional and handled in Plans 02/04/06.
- **Types:** `getToScheduleFutureAfter(nowMs: Long)` identical across DAO/repo/impl.
