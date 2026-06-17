# Vaccine Use Cases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-195

**Goal:** Add the domain use cases for adding (scheduled or administered), marking-administered, editing, deleting, and observing vaccine records, plus a summary use case that feeds the Home tile. Introduce the `VaccineReminderScheduler` abstraction the mutating use cases call (a no-op binding until plan 6 wires the real manager).

**Architecture:** Single-responsibility use cases with `suspend operator fun invoke(...)` (or `operator fun invoke()` for the Flow observers), each depending only on the `VaccineRepository` interface plus the `VaccineReminderScheduler` interface (for the mutating ones). Mirrors the `diaper` use-case package, with the scheduler collaborator playing the same role `SyncToFirestoreUseCase` plays for diaper. The scheduled→administered transition and the status↔date invariants are enforced here, not by Room. Time/zone come from the existing injected `() -> Instant` and `ZoneId` providers.

**Tech Stack:** Kotlin, Coroutines/Flow, Hilt, JUnit 5 + MockK + Turbine.

**Dependencies:** Plan 1 (`feat/vaccine-data-layer`) — needs `VaccineRepository`, `VaccineRecord`, `VaccineStatus`.

**Suggested implementation branch:** `feat/vaccine-use-cases`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `VaccineReminderScheduler` abstraction + no-op binding

The mutating use cases must (re)arm/cancel reminders. To keep them testable and let plans land independently, introduce the interface here with a **no-op** implementation; plan 6 replaces the binding with the real `VaccineReminderManager`. This mirrors how `SleepNotificationScheduler` is an interface in `manager/`.

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/VaccineReminderScheduler.kt`
- Create: `app/src/main/java/com/babytracker/manager/NoOpVaccineReminderScheduler.kt`
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`

- [ ] **Step 1: Interface** (pure — no Android imports; `schedule` is idempotent: implementations cancel any existing alarm for the record id before arming a new one)

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.VaccineRecord

interface VaccineReminderScheduler {
    /** Arms (or re-arms) a reminder for a SCHEDULED future record; a no-op for other states. */
    suspend fun schedule(record: VaccineRecord)
    /** Cancels any pending reminder for this record id. */
    fun cancel(recordId: Long)
    /** Cancels all and re-arms from currently-scheduled future records (boot / settings change / import). */
    suspend fun rescheduleAll()
}
```

- [ ] **Step 2: No-op impl**

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.VaccineRecord
import javax.inject.Inject

class NoOpVaccineReminderScheduler @Inject constructor() : VaccineReminderScheduler {
    override suspend fun schedule(record: VaccineRecord) = Unit
    override fun cancel(recordId: Long) = Unit
    override suspend fun rescheduleAll() = Unit
}
```

- [ ] **Step 3: Bind it** in `NotificationSchedulerModule` (this binding is **replaced** in plan 6):

```kotlin
@Binds
@Singleton
abstract fun bindVaccineReminderScheduler(impl: NoOpVaccineReminderScheduler): VaccineReminderScheduler
```

(check whether the module is an `abstract class` with `@Binds` or an `object` with `@Provides`; if `@Provides`-only, add `@Provides @Singleton fun provideVaccineReminderScheduler(impl: NoOpVaccineReminderScheduler): VaccineReminderScheduler = impl` instead, matching the file's existing style)

- [ ] **Step 4: Commit** `feat(vaccine): add VaccineReminderScheduler abstraction with no-op binding`

---

### Task 2: `VaccineSummary` model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/VaccineSummary.kt`

- [ ] **Step 1: Create the model**

```kotlin
package com.babytracker.domain.model

data class VaccineSummary(
    val nextUpcoming: VaccineRecord? = null,
    val upcomingCount: Int = 0,
    val overdueCount: Int = 0,
    val administeredCount: Int = 0,
    val lastAdministered: VaccineRecord? = null,
) {
    val hasAny: Boolean get() = nextUpcoming != null || administeredCount > 0 || overdueCount > 0
}
```

- [ ] **Step 2: Commit** `feat(vaccine): add VaccineSummary model`

---

### Task 3: `AddVaccineRecordUseCase`

Creates either a `SCHEDULED` entry (future date) or an `ADMINISTERED` entry (logged shot). Validation branches on status; the single `date` argument is interpreted per status. After insert, it asks the scheduler to arm a reminder (a no-op for administered entries — the scheduler ignores non-scheduled/non-future records).

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/AddVaccineRecordUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class AddVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        name: String,
        doseLabel: String?,
        status: VaccineStatus,
        date: Instant,
        notes: String? = null,
    ): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Vaccine name is required" }
        val record = when (status) {
            VaccineStatus.ADMINISTERED -> {
                require(!date.isAfter(now())) { "Administered date cannot be in the future" }
                VaccineRecord(
                    name = trimmedName,
                    doseLabel = doseLabel?.takeIf { it.isNotBlank() },
                    status = status,
                    administeredDate = date,
                    notes = notes?.takeIf { it.isNotBlank() },
                    createdAt = now(),
                )
            }
            VaccineStatus.SCHEDULED -> {
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
        }
        val id = repository.insert(record)
        reminderScheduler.schedule(record.copy(id = id))
        return id
    }
}
```

- [ ] **Step 2: Test** (administered rejects future date; scheduled is allowed a future date; scheduler asked to arm; blank name rejected)

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

class AddVaccineRecordUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: AddVaccineRecordUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = AddVaccineRecordUseCase(repository, scheduler) { fixedNow }
    }

    @Test
    fun `schedules future vaccine and arms reminder`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { repository.insert(capture(captured)) } returns 5
        val id = useCase("  MMR ", "1st dose", VaccineStatus.SCHEDULED, Instant.ofEpochMilli(50_000), "  ")
        assertEquals(5, id)
        assertEquals("MMR", captured.captured.name)
        assertEquals(VaccineStatus.SCHEDULED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(50_000), captured.captured.scheduledDate)
        assertEquals(null, captured.captured.notes)
        coVerify { scheduler.schedule(match { it.id == 5L }) }
    }

    @Test
    fun `administered rejects future date`() = runTest {
        val error = runCatching {
            useCase("MMR", null, VaccineStatus.ADMINISTERED, Instant.ofEpochMilli(20_000))
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `blank name rejected`() = runTest {
        val error = runCatching {
            useCase("   ", null, VaccineStatus.ADMINISTERED, fixedNow)
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `scheduled rejects non-future date`() = runTest {
        val error = runCatching {
            useCase("MMR", null, VaccineStatus.SCHEDULED, fixedNow) // date == now, not future
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add AddVaccineRecordUseCase`

---

### Task 4: `MarkVaccineAdministeredUseCase`

Flips a `SCHEDULED` entry to `ADMINISTERED` (sets `administeredDate`, default now; retains `scheduledDate` for history), and cancels its pending reminder. A missing id is a silent no-op.

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/MarkVaccineAdministeredUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class MarkVaccineAdministeredUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(id: Long, administeredDate: Instant = now()) {
        require(!administeredDate.isAfter(now())) { "Administered date cannot be in the future" }
        val existing = repository.getById(id) ?: return
        repository.update(
            existing.copy(status = VaccineStatus.ADMINISTERED, administeredDate = administeredDate),
        )
        reminderScheduler.cancel(id)
    }
}
```

- [ ] **Step 2: Test** (retains scheduledDate; cancels reminder; missing id no-ops)

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

class MarkVaccineAdministeredUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: MarkVaccineAdministeredUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = MarkVaccineAdministeredUseCase(repository, scheduler) { fixedNow }
    }

    @Test
    fun `flips to administered retaining scheduled date and cancels reminder`() = runTest {
        coEvery { repository.getById(3) } returns VaccineRecord(
            id = 3, name = "BCG", status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000), createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit
        useCase(3, Instant.ofEpochMilli(8_000))
        assertEquals(VaccineStatus.ADMINISTERED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(8_000), captured.captured.administeredDate)
        assertEquals(Instant.ofEpochMilli(5_000), captured.captured.scheduledDate)
        coVerify { scheduler.cancel(3) }
    }

    @Test
    fun `missing id is a no-op`() = runTest {
        coEvery { repository.getById(99) } returns null
        useCase(99)
        coVerify(exactly = 0) { repository.update(any()) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add MarkVaccineAdministeredUseCase`

---

### Task 5: `EditVaccineRecordUseCase`

Validates the edited record (non-blank name; status↔date invariant; administered date not in future), normalizes optional fields, updates, then re-arms the reminder (the idempotent `schedule()` cancels any old alarm and arms a new one only if still scheduled+future).

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/EditVaccineRecordUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class EditVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(record: VaccineRecord) {
        require(record.name.isNotBlank()) { "Vaccine name is required" }
        when (record.status) {
            VaccineStatus.ADMINISTERED -> require(
                record.administeredDate != null && !record.administeredDate.isAfter(now()),
            ) { "Administered date is required and cannot be in the future" }
            VaccineStatus.SCHEDULED -> require(record.scheduledDate != null) {
                "Scheduled date is required"
            }
        }
        val normalized = record.copy(
            name = record.name.trim(),
            doseLabel = record.doseLabel?.takeIf { it.isNotBlank() },
            notes = record.notes?.takeIf { it.isNotBlank() },
        )
        repository.update(normalized)
        reminderScheduler.schedule(normalized)
    }
}
```

- [ ] **Step 2: Test** — covers: administered without date rejected; scheduled without date rejected; valid edit normalizes notes + re-arms. (Follow the `AddVaccineRecordUseCaseTest` structure.)
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add EditVaccineRecordUseCase`

---

### Task 6: `DeleteVaccineRecordUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/DeleteVaccineRecordUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/DeleteVaccineRecordUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

class DeleteVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteById(id)
        reminderScheduler.cancel(id)
    }
}
```

- [ ] **Step 2: Test** (delegates to repository + cancels reminder)

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteVaccineRecordUseCaseTest {
    @Test
    fun `deletes and cancels reminder`() = runTest {
        val repository = mockk<VaccineRepository>(relaxed = true)
        val scheduler = mockk<VaccineReminderScheduler>(relaxed = true)
        DeleteVaccineRecordUseCase(repository, scheduler)(5)
        coVerify { repository.deleteById(5) }
        coVerify { scheduler.cancel(5) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add DeleteVaccineRecordUseCase`

---

### Task 7: `ObserveVaccineRecordsUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/ObserveVaccineRecordsUseCase.kt`

- [ ] **Step 1: Implement** (mirrors `ObserveDiaperChangesUseCase`)

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVaccineRecordsUseCase @Inject constructor(
    private val repository: VaccineRepository,
) {
    operator fun invoke(): Flow<List<VaccineRecord>> = repository.observeAll()
}
```

- [ ] **Step 2: Commit** `feat(vaccine): add ObserveVaccineRecordsUseCase`

---

### Task 8: `ObserveVaccineSummaryUseCase`

Computes the Home-tile summary: the soonest future scheduled record, plus upcoming/overdue/administered counts. Overdue = scheduled with a past date.

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/vaccine/ObserveVaccineSummaryUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/vaccine/ObserveVaccineSummaryUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.model.VaccineSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ObserveVaccineSummaryUseCase @Inject constructor(
    private val observeVaccineRecords: ObserveVaccineRecordsUseCase,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<VaccineSummary> =
        observeVaccineRecords().map { records ->
            val current = now()
            val scheduled = records.filter {
                it.status == VaccineStatus.SCHEDULED && it.scheduledDate != null
            }
            val future = scheduled.filter { !it.scheduledDate!!.isBefore(current) }
            val overdue = scheduled.filter { it.scheduledDate!!.isBefore(current) }
            val administered = records.filter { it.status == VaccineStatus.ADMINISTERED }
            VaccineSummary(
                nextUpcoming = future.minByOrNull { it.scheduledDate!! },
                upcomingCount = future.size,
                overdueCount = overdue.size,
                administeredCount = administered.size,
                lastAdministered = administered.maxByOrNull { it.administeredDate ?: it.createdAt },
            )
        }
}
```

- [ ] **Step 2: Test** (Turbine; verify next-upcoming is the soonest future, overdue counted separately, administered counted)

```kotlin
package com.babytracker.domain.usecase.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveVaccineSummaryUseCaseTest {
    private val now = Instant.ofEpochMilli(10_000)

    @Test
    fun `picks soonest future and counts overdue and administered`() = runTest {
        val observe = mockk<ObserveVaccineRecordsUseCase>()
        every { observe() } returns flowOf(
            listOf(
                VaccineRecord(id = 1, name = "Far", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(30_000), createdAt = now),
                VaccineRecord(id = 2, name = "Soon", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(20_000), createdAt = now),
                VaccineRecord(id = 3, name = "Overdue", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(5_000), createdAt = now),
                VaccineRecord(id = 4, name = "Done", status = VaccineStatus.ADMINISTERED, administeredDate = Instant.ofEpochMilli(1_000), createdAt = now),
            ),
        )
        ObserveVaccineSummaryUseCase(observe) { now }().test {
            val s = awaitItem()
            assertEquals("Soon", s.nextUpcoming?.name)
            assertEquals(2, s.upcomingCount)
            assertEquals(1, s.overdueCount)
            assertEquals(1, s.administeredCount)
            assertEquals("Done", s.lastAdministered?.name)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.vaccine.ObserveVaccineSummaryUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(vaccine): add ObserveVaccineSummaryUseCase`

---

## Acceptance Criteria

- `./gradlew test` passes, including all new vaccine use-case tests.
- All use cases inject only the `VaccineRepository` and `VaccineReminderScheduler` interfaces (plus `() -> Instant` where needed); no Android or Firebase imports.
- Add rejects blank names and future administered dates but allows future scheduled dates.
- Mark-administered retains `scheduledDate`, sets `administeredDate`, cancels the reminder; missing id no-ops.
- Edit enforces the status↔date invariant and re-arms the reminder.
- Delete cancels the reminder.
- The app compiles and runs with the no-op scheduler (reminders simply do not fire until plan 6).

## Self-Review Notes

- Spec coverage: Add / MarkAdministered / Edit / Delete / Observe / ObserveSummary + `VaccineSummary` — all present.
- The `VaccineReminderScheduler` interface is injected into the mutating use cases exactly as `SyncToFirestoreUseCase` is injected into the diaper use cases — side-effecting collaborator, mocked in tests. Plan 6 only swaps the DI binding (no use-case edits).
- `schedule()` is defined as idempotent (cancel-then-arm) so Edit can call it unconditionally without leaking stale alarms; the real manager (plan 6) honors this.
- The `() -> Instant` / `ZoneId` providers already exist (used by `ObserveTodayDiaperSummaryUseCase`), so no new DI providers are required.
- **Future-date invariant:** *Add* requires a `SCHEDULED` date strictly after `now()` (you don't schedule something for the past — if it already happened, log it administered). *Edit* deliberately does **not** re-require future for `SCHEDULED` (only `scheduledDate != null`): an "overdue" record is a scheduled vaccine whose date has since passed, and the parent must still be able to edit it (fix the name, or push the date out). Overdue is a runtime-derived state (`isOverdue`), not a creation-time one.
- `lastAdministered` (most recent administered by `administeredDate ?: createdAt`) feeds the Home tile's fallback before the empty state (plan 4), matching the spec.
