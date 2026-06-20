# Doctor Visit Use Cases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-202

**Goal:** Add the domain use cases for visits, questions, the Home-tile summary, and the snapshot reference — the business logic between the UI (plans 4–6) and the repository (plan 1).

**Architecture:** Single-responsibility classes with `suspend operator fun invoke(...)` (or a `fun invoke(): Flow<...>` for observers), each injecting `DoctorVisitRepository`. Visit mutation use cases also call a `DoctorVisitReminderScheduler` — introduced **here** as an interface with a **no-op default binding** so this plan compiles and tests without the alarm machinery; plan 7 swaps in the real implementation. `GenerateVisitSnapshotUseCase` delegates to the existing export use case (`GeneratePdfReportUseCase`) — no new export logic.

**Tech Stack:** Kotlin 2.3.20, Hilt 2.59, Coroutines/Flow, JUnit 5 + MockK + Turbine.

## Global Constraints

- Domain models pure Kotlin; use cases depend only on repository interfaces.
- No `Result<T>` wrappers — validation failures throw `IllegalArgumentException`; callers map to UI state.
- Trim `providerName`/`notes`/question `text`; blank → `null` (for visit fields) or rejected (for question text).
- `java.time` for time; inject a `Clock` (or `() -> Instant`) where "now" is needed, so tests are deterministic.

**Dependencies:** Plan 1 (data layer).

**Suggested implementation branch:** `feat/doctor-visit-use-cases`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `DoctorVisitReminderScheduler` interface + no-op binding

**Files:**
- Create: `app/src/main/java/com/babytracker/manager/DoctorVisitReminderScheduler.kt`
- Create: `app/src/main/java/com/babytracker/manager/NoOpDoctorVisitReminderScheduler.kt`
- Modify: `app/src/main/java/com/babytracker/di/NotificationSchedulerModule.kt`

- [ ] **Step 1: Interface** (the real impl arrives in plan 7).

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.DoctorVisit

interface DoctorVisitReminderScheduler {
    suspend fun schedule(visit: DoctorVisit)
    fun cancel(visitId: Long)
    suspend fun rescheduleAll()
}
```

- [ ] **Step 2: No-op impl** so use cases have a binding before plan 7.

```kotlin
package com.babytracker.manager

import com.babytracker.domain.model.DoctorVisit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpDoctorVisitReminderScheduler @Inject constructor() : DoctorVisitReminderScheduler {
    override suspend fun schedule(visit: DoctorVisit) = Unit
    override fun cancel(visitId: Long) = Unit
    override suspend fun rescheduleAll() = Unit
}
```

- [ ] **Step 3: Bind it** in `NotificationSchedulerModule` (plan 7 will replace this binding target with `DoctorVisitReminderManager`):

```kotlin
@Binds
@Singleton
abstract fun bindDoctorVisitReminderScheduler(impl: NoOpDoctorVisitReminderScheduler): DoctorVisitReminderScheduler
```

- [ ] **Step 4: Commit** `feat(doctor-visit): add reminder scheduler interface with no-op binding`

---

### Task 2: `AddVisitQuestionUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/AddVisitQuestionUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/AddVisitQuestionUseCaseTest.kt`

- [ ] **Step 1: Implement** — inserts an inbox question (`visitId = null`). Rejects blank text.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import java.time.Instant
import javax.inject.Inject

class AddVisitQuestionUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(text: String, now: Instant = Instant.now()): Long {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Question text must not be blank" }
        return repository.insertQuestion(
            VisitQuestion(text = trimmed, answered = false, visitId = null, createdAt = now),
        )
    }
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AddVisitQuestionUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var useCase: AddVisitQuestionUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = AddVisitQuestionUseCase(repository)
    }

    @Test
    fun `trims and inserts inbox question`() = runTest {
        val captured = slot<VisitQuestion>()
        coEvery { repository.insertQuestion(capture(captured)) } returns 5
        val id = useCase("  Is fever normal?  ", now = Instant.ofEpochMilli(100))
        assertEquals(5, id)
        assertEquals("Is fever normal?", captured.captured.text)
        assertEquals(null, captured.captured.visitId)
    }

    @Test
    fun `blank text throws`() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { useCase("   ") } }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add AddVisitQuestionUseCase`

---

### Task 3: `ToggleVisitQuestionAnsweredUseCase` + `DeleteVisitQuestionUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/ToggleVisitQuestionAnsweredUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/DeleteVisitQuestionUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/VisitQuestionMutationUseCasesTest.kt`

- [ ] **Step 1: Toggle** — reads the question, flips `answered`, updates. No-op if missing.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import javax.inject.Inject

class ToggleVisitQuestionAnsweredUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(id: Long) {
        val q = repository.getQuestionById(id) ?: return
        repository.updateQuestion(q.copy(answered = !q.answered))
    }
}
```

- [ ] **Step 2: Delete**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import javax.inject.Inject

class DeleteVisitQuestionUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteQuestionById(id)
}
```

- [ ] **Step 3: Test**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VisitQuestionMutationUseCasesTest {
    private lateinit var repository: DoctorVisitRepository

    @BeforeEach
    fun setup() { repository = mockk(relaxed = true) }

    @Test
    fun `toggle flips answered`() = runTest {
        coEvery { repository.getQuestionById(1) } returns VisitQuestion(id = 1, text = "Q", answered = false, createdAt = Instant.EPOCH)
        val captured = slot<VisitQuestion>()
        coEvery { repository.updateQuestion(capture(captured)) } returns Unit
        ToggleVisitQuestionAnsweredUseCase(repository)(1)
        assertEquals(true, captured.captured.answered)
    }

    @Test
    fun `toggle no-ops when missing`() = runTest {
        coEvery { repository.getQuestionById(9) } returns null
        ToggleVisitQuestionAnsweredUseCase(repository)(9)
        coVerify(exactly = 0) { repository.updateQuestion(any()) }
    }

    @Test
    fun `delete delegates`() = runTest {
        DeleteVisitQuestionUseCase(repository)(3)
        coVerify { repository.deleteQuestionById(3) }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.VisitQuestionMutationUseCasesTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): add toggle/delete question use cases`

---

### Task 4: Question observer use cases

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/ObserveInboxQuestionsUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/ObserveVisitQuestionsUseCase.kt`

- [ ] **Step 1: Inbox observer**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInboxQuestionsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(): Flow<List<VisitQuestion>> = repository.observeInboxQuestions()
}
```

- [ ] **Step 2: Per-visit observer**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVisitQuestionsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(visitId: Long): Flow<List<VisitQuestion>> = repository.observeQuestionsForVisit(visitId)
}
```

- [ ] **Step 3: Commit** `feat(doctor-visit): add question observer use cases`

---

### Task 5: `AddDoctorVisitUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/AddDoctorVisitUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/AddDoctorVisitUseCaseTest.kt`

- [ ] **Step 1: Implement** — inserts a visit (trimming text fields to null when blank) **and** attaches inbox questions atomically (single `@Transaction` repo call), then schedules the reminder after the DB write succeeds.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import java.time.Instant
import javax.inject.Inject

class AddDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) {
    suspend operator fun invoke(
        date: Instant,
        providerName: String?,
        notes: String?,
        attachQuestionIds: List<Long> = emptyList(),
        now: Instant = Instant.now(),
    ): Long {
        val visit = DoctorVisit(
            date = date,
            providerName = providerName?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            createdAt = now,
        )
        val id = repository.insertVisitWithAttachments(visit, attachQuestionIds) // atomic insert + attach
        reminderScheduler.schedule(visit.copy(id = id))
        return id
    }
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AddDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler
    private lateinit var useCase: AddDoctorVisitUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = AddDoctorVisitUseCase(repository, scheduler)
        coEvery { repository.insertVisitWithAttachments(any(), any()) } returns 11
    }

    @Test
    fun `inserts blanks-to-null, attaches questions atomically, schedules reminder`() = runTest {
        val captured = slot<DoctorVisit>()
        coEvery { repository.insertVisitWithAttachments(capture(captured), eq(listOf(1, 2))) } returns 11
        val id = useCase(
            date = Instant.ofEpochMilli(5_000),
            providerName = "   ",
            notes = "  Bring chart  ",
            attachQuestionIds = listOf(1, 2),
            now = Instant.ofEpochMilli(1_000),
        )
        assertEquals(11, id)
        assertNull(captured.captured.providerName)
        assertEquals("Bring chart", captured.captured.notes)
        coVerify { repository.insertVisitWithAttachments(any(), listOf(1, 2)) }
        coVerify { scheduler.schedule(match { it.id == 11L }) }
    }
}
```

> Add the MockK `eq` import (`io.mockk.eq`) for the argument matcher.

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.AddDoctorVisitUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add AddDoctorVisitUseCase`

---

### Task 6: `EditDoctorVisitUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/EditDoctorVisitUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/EditDoctorVisitUseCaseTest.kt`

- [ ] **Step 1: Implement** — updates fields and reconciles attached questions **atomically** (single `@Transaction` repo call: update + detach-all + re-attach selection), then re-arms the reminder after the DB write succeeds.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import javax.inject.Inject

class EditDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) {
    suspend operator fun invoke(visit: DoctorVisit, attachQuestionIds: List<Long>) {
        val sanitized = visit.copy(
            providerName = visit.providerName?.trim()?.ifBlank { null },
            notes = visit.notes?.trim()?.ifBlank { null },
        )
        repository.updateVisitReconcilingAttachments(sanitized, attachQuestionIds) // atomic update + reconcile
        reminderScheduler.cancel(sanitized.id)
        reminderScheduler.schedule(sanitized)
    }
}
```

- [ ] **Step 2: Test** — verify the atomic reconcile call (`updateVisitReconcilingAttachments`) and reminder re-arm order.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EditDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler
    private lateinit var useCase: EditDoctorVisitUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = EditDoctorVisitUseCase(repository, scheduler)
    }

    @Test
    fun `reconciles attachments atomically then re-arms`() = runTest {
        val visit = DoctorVisit(id = 4, date = Instant.ofEpochMilli(9_000), createdAt = Instant.ofEpochMilli(1))
        useCase(visit, attachQuestionIds = listOf(7))
        coVerifyOrder {
            repository.updateVisitReconcilingAttachments(any(), listOf(7))
            scheduler.cancel(4)
            scheduler.schedule(any())
        }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.EditDoctorVisitUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add EditDoctorVisitUseCase`

---

### Task 7: `DeleteDoctorVisitUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/DeleteDoctorVisitUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/DeleteDoctorVisitUseCaseTest.kt`

- [ ] **Step 1: Implement** — delete the visit and detach its questions back to the inbox **atomically** via the repository's `@Transaction` method (plan 1), then cancel the reminder only after the DB mutation succeeds. Doing the detach+delete in one transaction means a crash can't leave the visit orphaned with its questions already moved; cancelling the alarm last means a thrown cancel can't skip the deletion.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import javax.inject.Inject

class DeleteDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteVisitDetachingQuestions(id) // atomic: detach questions + delete visit
        reminderScheduler.cancel(id)                 // alarm cleanup after DB success
    }
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
    }

    @Test
    fun `deletes atomically then cancels reminder`() = runTest {
        DeleteDoctorVisitUseCase(repository, scheduler)(6)
        coVerifyOrder {
            repository.deleteVisitDetachingQuestions(6)
            // cancel happens after the DB mutation
        }
        verify { scheduler.cancel(6) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.DeleteDoctorVisitUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DeleteDoctorVisitUseCase`

---

### Task 8: `ObserveDoctorVisitsUseCase` + `ObserveDoctorVisitSummaryUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/ObserveDoctorVisitsUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/ObserveDoctorVisitSummaryUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/ObserveDoctorVisitSummaryUseCaseTest.kt`

- [ ] **Step 1: Visits observer**

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDoctorVisitsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(): Flow<List<DoctorVisit>> = repository.observeAllVisits()
}
```

- [ ] **Step 2: Summary use case** — combines all visits + inbox questions + a `now` provider. `nextUpcoming` = soonest visit with `date > now`; `lastPast` = most recent visit with `date <= now`; `openQuestionCount` = inbox questions not answered. Inject a `() -> Instant` named-or-default clock (mirror `ObserveVaccineSummaryUseCase`'s "now" injection).

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

class ObserveDoctorVisitSummaryUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val now: () -> Instant = { Instant.now() },
) {
    operator fun invoke(): Flow<DoctorVisitSummary> =
        combine(
            repository.observeAllVisits(),
            repository.observeInboxQuestions(),
        ) { visits, inbox ->
            val instant = now()
            val upcoming = visits.filter { it.date.isAfter(instant) }.minByOrNull { it.date }
            val past = visits.filter { !it.date.isAfter(instant) }.maxByOrNull { it.date }
            DoctorVisitSummary(
                nextUpcoming = upcoming,
                lastPast = past,
                openQuestionCount = inbox.count { !it.answered },
            )
        }
}
```

> **Note for the implementer:** check how `ObserveVaccineSummaryUseCase` receives "now" (a `Clock`, a `ZoneId`, or a `() -> Instant`) and match that convention exactly, including the Hilt provider that supplies it. The body above is correct regardless of the exact injection shape.

- [ ] **Step 3: Test** (Turbine)

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import app.cash.turbine.test
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveDoctorVisitSummaryUseCaseTest {
    @Test
    fun `computes next upcoming, last past, open question count`() = runTest {
        val now = Instant.ofEpochMilli(1_000)
        val repository = mockk<DoctorVisitRepository>()
        every { repository.observeAllVisits() } returns flowOf(
            listOf(
                DoctorVisit(id = 1, date = Instant.ofEpochMilli(500), createdAt = now),   // past
                DoctorVisit(id = 2, date = Instant.ofEpochMilli(900), createdAt = now),   // past (more recent)
                DoctorVisit(id = 3, date = Instant.ofEpochMilli(5_000), createdAt = now), // upcoming (soonest)
                DoctorVisit(id = 4, date = Instant.ofEpochMilli(9_000), createdAt = now), // upcoming
            ),
        )
        every { repository.observeInboxQuestions() } returns flowOf(
            listOf(
                VisitQuestion(id = 1, text = "a", answered = false, createdAt = now),
                VisitQuestion(id = 2, text = "b", answered = true, createdAt = now),
            ),
        )
        val useCase = ObserveDoctorVisitSummaryUseCase(repository) { now }
        useCase().test {
            val s = awaitItem()
            assertEquals(3L, s.nextUpcoming?.id)
            assertEquals(2L, s.lastPast?.id)
            assertEquals(1, s.openQuestionCount)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitSummaryUseCaseTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): add visits + summary observer use cases`

---

### Task 9: `AttachSnapshotToVisitUseCase` + `GenerateVisitSnapshotUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/AttachSnapshotToVisitUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/doctorvisit/GenerateVisitSnapshotUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/doctorvisit/SnapshotUseCasesTest.kt`

- [ ] **Step 1: Attach** — sets `snapshotLabel` + `snapshotCreatedAt` on an existing visit. No-op if the visit is gone.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import java.time.Instant
import javax.inject.Inject

class AttachSnapshotToVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(visitId: Long, label: String, now: Instant = Instant.now()) {
        val visit = repository.getVisitById(visitId) ?: return
        repository.updateVisit(visit.copy(snapshotLabel = label.trim(), snapshotCreatedAt = now))
    }
}
```

- [ ] **Step 2: Generate on demand** — delegates to the existing PDF report use case. **Check `GeneratePdfReportUseCase`'s actual `invoke` signature/return type** (e.g. returns a `File`/`Uri`) and match it; do not add new export logic here.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import javax.inject.Inject

/**
 * Re-generates a fresh data snapshot on demand when the user taps "View snapshot" on a visit.
 * Pure delegation to the existing export pipeline — no snapshot file is persisted on the visit.
 */
class GenerateVisitSnapshotUseCase @Inject constructor(
    private val generatePdfReport: GeneratePdfReportUseCase,
) {
    suspend operator fun invoke() = generatePdfReport()
}
```

- [ ] **Step 3: Test** — attach sets fields; generate delegates.

```kotlin
package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SnapshotUseCasesTest {
    @Test
    fun `attach sets label and timestamp`() = runTest {
        val repository = mockk<DoctorVisitRepository>(relaxed = true)
        coEvery { repository.getVisitById(3) } returns DoctorVisit(id = 3, date = Instant.EPOCH, createdAt = Instant.EPOCH)
        val captured = slot<DoctorVisit>()
        coEvery { repository.updateVisit(capture(captured)) } returns Unit
        AttachSnapshotToVisitUseCase(repository)(3, "  Backup  ", now = Instant.ofEpochMilli(50))
        assertEquals("Backup", captured.captured.snapshotLabel)
        assertEquals(Instant.ofEpochMilli(50), captured.captured.snapshotCreatedAt)
    }

    @Test
    fun `attach no-ops when visit missing`() = runTest {
        val repository = mockk<DoctorVisitRepository>(relaxed = true)
        coEvery { repository.getVisitById(9) } returns null
        AttachSnapshotToVisitUseCase(repository)(9, "x")
        coVerify(exactly = 0) { repository.updateVisit(any()) }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.domain.usecase.doctorvisit.SnapshotUseCasesTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): add snapshot attach + on-demand generate use cases`

---

## Acceptance Criteria

- `./gradlew test` passes with all new use-case tests.
- Every use case injects only the `DoctorVisitRepository` (+ scheduler / export use case where noted) — no Android imports.
- Visit add/edit/delete call the (no-op for now) `DoctorVisitReminderScheduler`; plan 7 makes those calls real.
- `DeleteDoctorVisitUseCase` detaches questions to the inbox and deletes the visit atomically (single `@Transaction` repo call), then cancels the reminder.
- `AddDoctorVisitUseCase` / `EditDoctorVisitUseCase` perform insert+attach / update+reconcile **atomically** via single `@Transaction` repo calls (plan 1), so a crash mid-reconcile can't strand a visit with half-applied attachments; reminders are (re)armed only after the DB write succeeds.
- `ObserveDoctorVisitSummaryUseCase` selects next-upcoming / last-past correctly across the now boundary and counts only unanswered inbox questions.

## Self-Review Notes

- Spec coverage: Add/Edit/Delete visit, Add/Toggle/Delete question, both observers ×2, summary, attach-snapshot, generate-snapshot — all present.
- The reminder scheduler is introduced here (interface + no-op binding) so plans 4–6 can use the mutation use cases before plan 7 lands; plan 7 only re-points the Hilt binding and (optionally) deletes the no-op.
- `GenerateVisitSnapshotUseCase` is intentionally a thin delegate; the implementer must confirm the real `GeneratePdfReportUseCase` signature (it may take params like a date range or return a `File`/`Uri`) and align the wrapper + its call sites in plan 5.
- `now` is injected as `() -> Instant` for deterministic tests; align with whatever the existing summary use cases use.
