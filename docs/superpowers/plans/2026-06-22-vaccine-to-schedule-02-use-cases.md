# Vaccine To-Schedule — Plan 02: Use Cases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teach the vaccine use cases the `TO_SCHEDULE` lifecycle: add a to-schedule dose (target date required + future), flip it to scheduled in one tap, and allow marking it given directly.

**Architecture:** A to-schedule dose stores its target date in `scheduledDate`. `AddVaccineRecordUseCase` gains a `TO_SCHEDULE` branch with the same future-date guard as `SCHEDULED`. A new `MarkVaccineScheduledUseCase` flips status only. `MarkVaccineAdministeredUseCase` broadens its transition guard. `EditVaccineRecordUseCase` handles the new exhaustive `when` branch.

**Tech Stack:** Kotlin, Hilt, Coroutines, JUnit 5, MockK.

## Global Constraints

- A to-schedule **target date is required and must be in the future at creation** (`date.isAfter(now())`), mirroring `SCHEDULED`.
- Use cases stay single-responsibility with `suspend operator fun invoke(...)`; no `Result<>` wrappers — let `require()` throw `IllegalArgumentException`.
- Reminder scheduling is always **best-effort** (`runCatching { reminderScheduler.schedule(...) }`) so a scheduler failure never fails the local write.
- The injected clock is `now: () -> Instant`; never call `Instant.now()` directly.
- Depends on Plan 01 (`VaccineStatus.TO_SCHEDULE`).

---

### Task 1: `AddVaccineRecordUseCase` — TO_SCHEDULE branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCaseTest.kt` (add cases)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`; `VaccineReminderScheduler.schedule(record)`.
- Produces: `invoke(name, doseLabel, status = TO_SCHEDULE, date, notes)` persists `scheduledDate = date` (target), requires future date, arms the reminder. Signature unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `invoke with TO_SCHEDULE persists target date and arms reminder`() = runTest {
    val future = fixedNow.plus(10, ChronoUnit.DAYS)
    val slot = slot<VaccineRecord>()
    coEvery { repository.insert(capture(slot)) } returns 42L

    val id = useCase("MMR", null, VaccineStatus.TO_SCHEDULE, future, null)

    assertEquals(42L, id)
    assertEquals(VaccineStatus.TO_SCHEDULE, slot.captured.status)
    assertEquals(future, slot.captured.scheduledDate)
    assertNull(slot.captured.administeredDate)
    coVerify { reminderScheduler.schedule(match { it.id == 42L && it.status == VaccineStatus.TO_SCHEDULE }) }
}

@Test
fun `invoke with TO_SCHEDULE rejects a non-future target date`() = runTest {
    val past = fixedNow.minus(1, ChronoUnit.DAYS)
    assertThrows(IllegalArgumentException::class.java) {
        runBlocking { useCase("MMR", null, VaccineStatus.TO_SCHEDULE, past, null) }
    }
    coVerify(exactly = 0) { repository.insert(any()) }
}
```

Reuse the test class's existing `fixedNow`, `repository`, `reminderScheduler`, and `useCase` setup. Ensure imports: `java.time.temporal.ChronoUnit`, `io.mockk.slot`, `kotlinx.coroutines.runBlocking`, `org.junit.jupiter.api.Assertions.*`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCaseTest"`
Expected: FAIL — the `when(status)` has no `TO_SCHEDULE` branch (compile error), or behavior mismatch.

- [ ] **Step 3: Write minimal implementation**

In `AddVaccineRecordUseCase.kt`, add a `TO_SCHEDULE` branch to the `when (status)` (place it before `SCHEDULED`):

```kotlin
VaccineStatus.TO_SCHEDULE -> {
    // A to-schedule dose has a known target date but no booked appointment yet. The target is
    // stored in scheduledDate (status distinguishes the two) and, like a scheduled dose, must be
    // in the future so a reminder window exists.
    require(date.isAfter(now())) { "Scheduled date must be in the future" }
    VaccineRecord(
        name = trimmedName,
        doseLabel = doseLabel?.takeIf { it.isNotBlank() },
        status = status,
        scheduledDate = date,
        notes = notes?.takeIf { it.isNotBlank() },
        createdAt = now(),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCase.kt app/src/test/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCaseTest.kt
git commit -m "feat(vaccine): add to-schedule branch to AddVaccineRecordUseCase"
```

---

### Task 2: `MarkVaccineScheduledUseCase` (new) — one-tap TO_SCHEDULE → SCHEDULED

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/MarkVaccineScheduledUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/MarkVaccineScheduledUseCaseTest.kt` (create)

**Interfaces:**
- Consumes: `VaccineRepository.getById`/`update`; `VaccineReminderScheduler.schedule`; `VaccineStatus`.
- Produces: `MarkVaccineScheduledUseCase` with `suspend operator fun invoke(id: Long)` — if the record is `TO_SCHEDULE`, write `status = SCHEDULED` (date unchanged) and re-arm the reminder (now under the appointment lead). No-op otherwise. (Consumed by Plan 05.)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MarkVaccineScheduledUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var reminderScheduler: VaccineReminderScheduler
    private lateinit var useCase: MarkVaccineScheduledUseCase

    private val target = Instant.parse("2026-07-01T00:00:00Z")
    private val toSchedule = VaccineRecord(
        id = 5, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = target, createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
        useCase = MarkVaccineScheduledUseCase(repository, reminderScheduler)
    }

    @Test
    fun `invoke flips to-schedule to scheduled keeping the date and re-arms`() = runTest {
        coEvery { repository.getById(5) } returns toSchedule
        val slot = slot<VaccineRecord>()
        coEvery { repository.update(capture(slot)) } returns Unit

        useCase(5)

        assertEquals(VaccineStatus.SCHEDULED, slot.captured.status)
        assertEquals(target, slot.captured.scheduledDate)
        coVerify { reminderScheduler.schedule(match { it.status == VaccineStatus.SCHEDULED }) }
    }

    @Test
    fun `invoke is a no-op for a non-to-schedule record`() = runTest {
        coEvery { repository.getById(5) } returns toSchedule.copy(status = VaccineStatus.SCHEDULED)
        useCase(5)
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `invoke is a no-op for a missing record`() = runTest {
        coEvery { repository.getById(5) } returns null
        useCase(5)
        coVerify(exactly = 0) { repository.update(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCaseTest"`
Expected: FAIL — `MarkVaccineScheduledUseCase` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

/**
 * Flips a TO_SCHEDULE dose to SCHEDULED in one tap, leaving its date untouched (the target date
 * becomes the appointment date). Guards on the current status so it is idempotent under double-taps
 * and a no-op for already-scheduled / administered / missing records. Re-arms the reminder so it now
 * fires under the appointment lead instead of the to-schedule lead.
 */
class MarkVaccineScheduledUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        val existing = repository.getById(id) ?: return
        if (existing.status != VaccineStatus.TO_SCHEDULE) return
        val updated = existing.copy(status = VaccineStatus.SCHEDULED)
        repository.update(updated)
        // Best-effort: re-arming must never fail the local flip.
        runCatching { reminderScheduler.schedule(updated) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/vaccine/MarkVaccineScheduledUseCase.kt app/src/test/java/com/babytracker/domain/usecase/vaccine/MarkVaccineScheduledUseCaseTest.kt
git commit -m "feat(vaccine): add MarkVaccineScheduledUseCase for one-tap booking"
```

---

### Task 3: `MarkVaccineAdministeredUseCase` — allow direct TO_SCHEDULE → ADMINISTERED

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCaseTest.kt` (add case)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`.
- Produces: `invoke(id, administeredDate)` now transitions records whose status is `SCHEDULED` **or** `TO_SCHEDULE`. Signature unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `invoke marks a to-schedule dose given directly`() = runTest {
    val toSchedule = VaccineRecord(
        id = 9, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-07-01T00:00:00Z"), createdAt = fixedNow,
    )
    coEvery { repository.getById(9) } returns toSchedule
    val slot = slot<VaccineRecord>()
    coEvery { repository.update(capture(slot)) } returns Unit

    useCase(9, fixedNow)

    assertEquals(VaccineStatus.ADMINISTERED, slot.captured.status)
    assertEquals(fixedNow, slot.captured.administeredDate)
    coVerify { reminderScheduler.cancel(9) }
}
```

Reuse the class's existing `fixedNow`, `repository`, `reminderScheduler`, `useCase`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCaseTest"`
Expected: FAIL — current guard only transitions `SCHEDULED`, so `update` is never called.

- [ ] **Step 3: Write minimal implementation**

In `MarkVaccineAdministeredUseCase.kt`, broaden the guard:

```kotlin
// Transition from either pending state. Guarding against an already-administered record keeps this
// idempotent under double-taps / stale UI and prevents overwriting the original administeredDate.
if (existing.status == VaccineStatus.SCHEDULED || existing.status == VaccineStatus.TO_SCHEDULE) {
    repository.update(
        existing.copy(status = VaccineStatus.ADMINISTERED, administeredDate = administeredDate),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCase.kt app/src/test/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCaseTest.kt
git commit -m "feat(vaccine): allow marking a to-schedule dose given directly"
```

---

### Task 4: `EditVaccineRecordUseCase` — TO_SCHEDULE branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCaseTest.kt` (add case)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`.
- Produces: editing a `TO_SCHEDULE` record requires a non-null target (`scheduledDate`), normalizes, persists, re-arms. Closes the exhaustive `when` so the module compiles.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `invoke persists a to-schedule edit and re-arms reminder`() = runTest {
    val record = VaccineRecord(
        id = 3, name = "  MMR ", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = Instant.parse("2026-07-01T00:00:00Z"), createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )
    val slot = slot<VaccineRecord>()
    coEvery { repository.update(capture(slot)) } returns Unit

    useCase(record)

    assertEquals("MMR", slot.captured.name)
    assertEquals(VaccineStatus.TO_SCHEDULE, slot.captured.status)
    coVerify { reminderScheduler.schedule(any()) }
}

@Test
fun `invoke rejects a to-schedule edit with no target date`() = runTest {
    val record = VaccineRecord(
        id = 3, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = null, createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )
    assertThrows(IllegalArgumentException::class.java) { runBlocking { useCase(record) } }
}
```

Reuse the class's existing `repository`, `reminderScheduler`, `useCase`. Imports: `kotlinx.coroutines.runBlocking`, `org.junit.jupiter.api.Assertions.assertThrows`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCaseTest"`
Expected: FAIL — `when(record.status)` is non-exhaustive (compile error) without a `TO_SCHEDULE` branch.

- [ ] **Step 3: Write minimal implementation**

In `EditVaccineRecordUseCase.kt`, add a branch to the `when (record.status)`:

```kotlin
VaccineStatus.TO_SCHEDULE -> require(record.scheduledDate != null) {
    "Scheduled date is required"
}
```

(Edit does not re-validate the future-date rule — consistent with the existing `SCHEDULED` edit branch, which only requires the date be present. `reminderScheduler.schedule` no-ops if the date is past.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCase.kt app/src/test/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCaseTest.kt
git commit -m "feat(vaccine): handle to-schedule status in EditVaccineRecordUseCase"
```

---

## Self-Review

- **Spec coverage:** add (Task 1), one-tap schedule (Task 2), direct mark-given (Task 3), edit (Task 4). Undo for both mark-scheduled and direct-mark-given reuses the existing `UndoMarkVaccineAdministeredUseCase` (generic write-back-then-reschedule) — wired in Plan 05, no new class needed.
- **Placeholder scan:** none.
- **Types:** `MarkVaccineScheduledUseCase.invoke(id: Long)` matches the signature Plan 05 injects.
