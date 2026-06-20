# Doctor Visit Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-201

**Goal:** Add the persistence foundation for the Doctor Visit Log — two domain models (`DoctorVisit`, `VisitQuestion`) plus a Home-tile summary, Room entities + mapping, a single DAO covering both tables, one repository, DI wiring, and a database migration to v15.

**Architecture:** A visit is a point-in-time event (no duration), so this mirrors the `Vaccine` / `Diaper` data layer. The feature has **two** related entities in one bounded context: `doctor_visits` and `visit_questions`. A question carries a nullable `visit_id` FK (`NULL` = inbox; set = attached to that visit) — modeled as a plain nullable column with no Room foreign-key constraint, because deletion detaches (sets `visit_id = NULL`) rather than cascades, and that behavior lives in the use-case layer (plan 2). One `DoctorVisitRepository` exposes both visit and question operations. Pure-Kotlin domain, extension-function mapping (no Mapper class), additive `CREATE TABLE` migration with no active-row trigger.

**Tech Stack:** Kotlin 2.3.20, Room 2.8.4, Hilt 2.59, Coroutines/Flow, JUnit 5 + MockK + Turbine (unit), Robolectric/Room in-memory + `MigrationTestHelper` (DB tests).

## Global Constraints

- Min SDK 26, JVM 17, KSP only (no KAPT).
- Domain models are pure Kotlin — zero framework imports.
- No Mapper classes — use `toDomain()` / `toEntity()` extension functions.
- No `Result<T>` wrappers — let exceptions propagate / use nullable returns.
- Store timestamps as epoch-ms `Long` in Room; `java.time.Instant` in domain.

**Dependencies:** None. This is the base plan; plans 2–9 depend on it.

**Suggested implementation branch:** `feat/doctor-visit-data-layer`

**Project convention:** Implement first, then tests (no strict TDD — per `CLAUDE.md`). Commit after each task. Do not run `ktlintFormat`/`detekt` manually — the pre-commit hook runs them.

---

### Task 1: `DoctorVisit` domain model + helpers

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/DoctorVisit.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/DoctorVisitTest.kt`

- [ ] **Step 1: Create the model + pure helpers** (zero framework imports beyond `java.time`).

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class DoctorVisit(
    val id: Long = 0,
    val date: Instant,
    val providerName: String? = null,
    val notes: String? = null,
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Instant? = null,
    val createdAt: Instant,
)

fun DoctorVisit.isUpcoming(now: Instant): Boolean = date.isAfter(now)

fun DoctorVisit.hasSnapshot(): Boolean = snapshotLabel != null
```

- [ ] **Step 2: Write the test**

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DoctorVisitTest {
    private val now = Instant.ofEpochMilli(10_000)
    private val base = DoctorVisit(date = now, createdAt = now)

    @Test
    fun `isUpcoming true only when date after now`() {
        assertTrue(base.copy(date = Instant.ofEpochMilli(20_000)).isUpcoming(now))
        assertFalse(base.copy(date = Instant.ofEpochMilli(5_000)).isUpcoming(now))
        assertFalse(base.isUpcoming(now)) // equal is not "after"
    }

    @Test
    fun `hasSnapshot reflects snapshotLabel presence`() {
        assertFalse(base.hasSnapshot())
        assertTrue(base.copy(snapshotLabel = "Backup 2026-06-20").hasSnapshot())
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.model.DoctorVisitTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisit model and helpers`

---

### Task 2: `VisitQuestion` domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/VisitQuestion.kt`

- [ ] **Step 1: Create the model** (plain data class; `visitId == null` means inbox).

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class VisitQuestion(
    val id: Long = 0,
    val text: String,
    val answered: Boolean = false,
    val visitId: Long? = null,
    val createdAt: Instant,
)
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add VisitQuestion model`

---

### Task 3: `DoctorVisitSummary` model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/DoctorVisitSummary.kt`

- [ ] **Step 1: Create the Home-tile summary** (consumed by the summary use case in plan 2; placed here so the data layer owns the shape).

```kotlin
package com.babytracker.domain.model

data class DoctorVisitSummary(
    val nextUpcoming: DoctorVisit? = null,
    val lastPast: DoctorVisit? = null,
    val openQuestionCount: Int = 0,
) {
    val hasAny: Boolean
        get() = nextUpcoming != null || lastPast != null || openQuestionCount > 0
}
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add DoctorVisitSummary model`

---

### Task 4: `DoctorVisitEntity` + mapping

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/DoctorVisitEntity.kt`
- Test: `app/src/test/java/com/babytracker/data/local/entity/DoctorVisitEntityMappingTest.kt`

- [ ] **Step 1: Create entity + mapping** (mirrors `VaccineEntity.kt`). Index on `date` for history ordering / upcoming filters.

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.DoctorVisit
import java.time.Instant

@Entity(
    tableName = "doctor_visits",
    indices = [Index(value = ["date"])],
)
data class DoctorVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "provider_name") val providerName: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "snapshot_label") val snapshotLabel: String? = null,
    @ColumnInfo(name = "snapshot_created_at") val snapshotCreatedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun DoctorVisitEntity.toDomain(): DoctorVisit = DoctorVisit(
    id = id,
    date = Instant.ofEpochMilli(date),
    providerName = providerName,
    notes = notes,
    snapshotLabel = snapshotLabel,
    snapshotCreatedAt = snapshotCreatedAt?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun DoctorVisit.toEntity(): DoctorVisitEntity = DoctorVisitEntity(
    id = id,
    date = date.toEpochMilli(),
    providerName = providerName,
    notes = notes,
    snapshotLabel = snapshotLabel,
    snapshotCreatedAt = snapshotCreatedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
)
```

- [ ] **Step 2: Write round-trip test**

```kotlin
package com.babytracker.data.local.entity

import com.babytracker.domain.model.DoctorVisit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DoctorVisitEntityMappingTest {
    @Test
    fun `full visit round trips`() {
        val visit = DoctorVisit(
            id = 7,
            date = Instant.ofEpochMilli(5_000),
            providerName = "Dr. Tanaka",
            notes = "Bring growth chart",
            snapshotLabel = "Backup 2026-06-20",
            snapshotCreatedAt = Instant.ofEpochMilli(4_000),
            createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(visit, visit.toEntity().toDomain())
    }

    @Test
    fun `minimal visit round trips with nulls`() {
        val visit = DoctorVisit(id = 2, date = Instant.ofEpochMilli(9_000), createdAt = Instant.ofEpochMilli(8_000))
        assertEquals(visit, visit.toEntity().toDomain())
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.data.local.entity.DoctorVisitEntityMappingTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add DoctorVisitEntity and mapping`

---

### Task 5: `VisitQuestionEntity` + mapping

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/entity/VisitQuestionEntity.kt`
- Test: `app/src/test/java/com/babytracker/data/local/entity/VisitQuestionEntityMappingTest.kt`

- [ ] **Step 1: Create entity + mapping.** Index on `visit_id` (inbox filter + per-visit lookups). `answered` is stored as `Int` 0/1 — Room maps `Boolean` automatically, but keep the column nullable-free with a default.

```kotlin
package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.VisitQuestion
import java.time.Instant

@Entity(
    tableName = "visit_questions",
    indices = [Index(value = ["visit_id"])],
)
data class VisitQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "answered") val answered: Boolean = false,
    @ColumnInfo(name = "visit_id") val visitId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun VisitQuestionEntity.toDomain(): VisitQuestion = VisitQuestion(
    id = id,
    text = text,
    answered = answered,
    visitId = visitId,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun VisitQuestion.toEntity(): VisitQuestionEntity = VisitQuestionEntity(
    id = id,
    text = text,
    answered = answered,
    visitId = visitId,
    createdAt = createdAt.toEpochMilli(),
)
```

- [ ] **Step 2: Write round-trip test**

```kotlin
package com.babytracker.data.local.entity

import com.babytracker.domain.model.VisitQuestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class VisitQuestionEntityMappingTest {
    @Test
    fun `inbox question round trips`() {
        val q = VisitQuestion(id = 1, text = "Is the rash normal?", answered = false, visitId = null, createdAt = Instant.ofEpochMilli(100))
        assertEquals(q, q.toEntity().toDomain())
    }

    @Test
    fun `attached answered question round trips`() {
        val q = VisitQuestion(id = 2, text = "Sleep regression?", answered = true, visitId = 9, createdAt = Instant.ofEpochMilli(200))
        assertEquals(q, q.toEntity().toDomain())
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.data.local.entity.VisitQuestionEntityMappingTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): add VisitQuestionEntity and mapping`

---

### Task 6: `DoctorVisitDao`

**Files:**
- Create: `app/src/main/java/com/babytracker/data/local/dao/DoctorVisitDao.kt`

- [ ] **Step 1: Create the DAO** covering both tables. `getUpcomingVisitsAfter` is the one-shot read the reminder boot-rescheduler (plan 7) uses; `getAllVisitsOnce` / `getAllQuestionsOnce` are consumed by plan 8 (backup) and plan 9 (partner snapshot). `attachQuestions` / `detachQuestionsForVisit` are bulk updates driving the inbox↔visit relationship.

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorVisitDao {
    // ── Visits ──
    @Query("SELECT * FROM doctor_visits ORDER BY date DESC")
    fun observeAllVisits(): Flow<List<DoctorVisitEntity>>

    @Query("SELECT * FROM doctor_visits WHERE id = :id LIMIT 1")
    suspend fun getVisitById(id: Long): DoctorVisitEntity?

    @Query("SELECT * FROM doctor_visits ORDER BY date ASC")
    suspend fun getAllVisitsOnce(): List<DoctorVisitEntity>

    @Query("SELECT * FROM doctor_visits WHERE date > :nowMs ORDER BY date ASC")
    suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisitEntity>

    @Insert
    suspend fun insertVisit(entity: DoctorVisitEntity): Long

    @Update
    suspend fun updateVisit(entity: DoctorVisitEntity)

    @Query("DELETE FROM doctor_visits WHERE id = :id")
    suspend fun deleteVisitById(id: Long): Int

    // ── Questions ──
    @Query("SELECT * FROM visit_questions WHERE visit_id IS NULL ORDER BY created_at DESC")
    fun observeInboxQuestions(): Flow<List<VisitQuestionEntity>>

    @Query("SELECT * FROM visit_questions WHERE visit_id = :visitId ORDER BY created_at ASC")
    fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestionEntity>>

    @Query("SELECT * FROM visit_questions ORDER BY created_at ASC")
    suspend fun getAllQuestionsOnce(): List<VisitQuestionEntity>

    @Query("SELECT * FROM visit_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: Long): VisitQuestionEntity?

    @Insert
    suspend fun insertQuestion(entity: VisitQuestionEntity): Long

    @Update
    suspend fun updateQuestion(entity: VisitQuestionEntity)

    @Query("DELETE FROM visit_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long): Int

    @Query("UPDATE visit_questions SET visit_id = :visitId WHERE id IN (:ids)")
    suspend fun attachQuestions(ids: List<Long>, visitId: Long)

    @Query("UPDATE visit_questions SET visit_id = NULL WHERE visit_id = :visitId")
    suspend fun detachQuestionsForVisit(visitId: Long)

    /**
     * Atomic delete: detach this visit's questions back to the inbox and delete the visit in
     * one DB transaction. The integrity boundary for the FK-less relationship — without this,
     * a crash between detach and delete could orphan the visit with its questions already moved.
     */
    @Transaction
    suspend fun deleteVisitDetachingQuestions(visitId: Long) {
        detachQuestionsForVisit(visitId)
        deleteVisitById(visitId)
    }

    /** Atomic create: insert the visit, then attach the selected questions in one transaction. */
    @Transaction
    suspend fun insertVisitWithAttachments(entity: DoctorVisitEntity, questionIds: List<Long>): Long {
        val id = insertVisit(entity)
        if (questionIds.isNotEmpty()) attachQuestions(questionIds, id)
        return id
    }

    /** Atomic edit: update the visit and reconcile attachments (detach all, re-attach selection). */
    @Transaction
    suspend fun updateVisitReconcilingAttachments(entity: DoctorVisitEntity, questionIds: List<Long>) {
        updateVisit(entity)
        detachQuestionsForVisit(entity.id)
        if (questionIds.isNotEmpty()) attachQuestions(questionIds, entity.id)
    }
}
```

> `@Transaction` on a default interface method is supported by Room 2.8.4; the writes in each method commit or roll back together — so a crash mid-reconcile can never leave a saved visit with half-applied attachments.

- [ ] **Step 2: Commit** `feat(doctor-visit): add DoctorVisitDao`

---

### Task 7: `DoctorVisitRepository` interface + impl

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/DoctorVisitRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/DoctorVisitRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/DoctorVisitRepositoryImplTest.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import kotlinx.coroutines.flow.Flow

interface DoctorVisitRepository {
    // Visits
    fun observeAllVisits(): Flow<List<DoctorVisit>>
    suspend fun getVisitById(id: Long): DoctorVisit?
    suspend fun getAllVisitsOnce(): List<DoctorVisit>
    suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisit>
    suspend fun insertVisit(visit: DoctorVisit): Long
    suspend fun updateVisit(visit: DoctorVisit)
    suspend fun deleteVisitById(id: Long)
    suspend fun deleteVisitDetachingQuestions(id: Long)
    suspend fun insertVisitWithAttachments(visit: DoctorVisit, questionIds: List<Long>): Long
    suspend fun updateVisitReconcilingAttachments(visit: DoctorVisit, questionIds: List<Long>)

    // Questions
    fun observeInboxQuestions(): Flow<List<VisitQuestion>>
    fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestion>>
    suspend fun getAllQuestionsOnce(): List<VisitQuestion>
    suspend fun getQuestionById(id: Long): VisitQuestion?
    suspend fun insertQuestion(question: VisitQuestion): Long
    suspend fun updateQuestion(question: VisitQuestion)
    suspend fun deleteQuestionById(id: Long)
    suspend fun attachQuestions(ids: List<Long>, visitId: Long)
    suspend fun detachQuestionsForVisit(visitId: Long)
}
```

- [ ] **Step 2: Impl** (mirrors `VaccineRepositoryImpl.kt`)

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.DoctorVisitDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitRepositoryImpl @Inject constructor(
    private val dao: DoctorVisitDao,
) : DoctorVisitRepository {
    override fun observeAllVisits(): Flow<List<DoctorVisit>> =
        dao.observeAllVisits().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getVisitById(id: Long): DoctorVisit? = dao.getVisitById(id)?.toDomain()

    override suspend fun getAllVisitsOnce(): List<DoctorVisit> = dao.getAllVisitsOnce().map { it.toDomain() }

    override suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisit> =
        dao.getUpcomingVisitsAfter(nowMs).map { it.toDomain() }

    override suspend fun insertVisit(visit: DoctorVisit): Long = dao.insertVisit(visit.toEntity())

    override suspend fun updateVisit(visit: DoctorVisit) = dao.updateVisit(visit.toEntity())

    override suspend fun deleteVisitById(id: Long) {
        dao.deleteVisitById(id)
    }

    override suspend fun deleteVisitDetachingQuestions(id: Long) {
        dao.deleteVisitDetachingQuestions(id)
    }

    override suspend fun insertVisitWithAttachments(visit: DoctorVisit, questionIds: List<Long>): Long =
        dao.insertVisitWithAttachments(visit.toEntity(), questionIds)

    override suspend fun updateVisitReconcilingAttachments(visit: DoctorVisit, questionIds: List<Long>) =
        dao.updateVisitReconcilingAttachments(visit.toEntity(), questionIds)

    override fun observeInboxQuestions(): Flow<List<VisitQuestion>> =
        dao.observeInboxQuestions().map { rows -> rows.map { it.toDomain() } }

    override fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestion>> =
        dao.observeQuestionsForVisit(visitId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAllQuestionsOnce(): List<VisitQuestion> = dao.getAllQuestionsOnce().map { it.toDomain() }

    override suspend fun getQuestionById(id: Long): VisitQuestion? = dao.getQuestionById(id)?.toDomain()

    override suspend fun insertQuestion(question: VisitQuestion): Long = dao.insertQuestion(question.toEntity())

    override suspend fun updateQuestion(question: VisitQuestion) = dao.updateQuestion(question.toEntity())

    override suspend fun deleteQuestionById(id: Long) {
        dao.deleteQuestionById(id)
    }

    override suspend fun attachQuestions(ids: List<Long>, visitId: Long) {
        if (ids.isNotEmpty()) dao.attachQuestions(ids, visitId)
    }

    override suspend fun detachQuestionsForVisit(visitId: Long) = dao.detachQuestionsForVisit(visitId)
}
```

- [ ] **Step 3: Test** (MockK over the DAO; Turbine for the Flow)

```kotlin
package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.DoctorVisitDao
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import com.babytracker.domain.model.DoctorVisit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DoctorVisitRepositoryImplTest {
    private lateinit var dao: DoctorVisitDao
    private lateinit var repository: DoctorVisitRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = DoctorVisitRepositoryImpl(dao)
    }

    @Test
    fun `observeAllVisits maps entities to domain`() = runTest {
        every { dao.observeAllVisits() } returns flowOf(
            listOf(DoctorVisitEntity(id = 1, date = 10, providerName = "Dr A", createdAt = 5)),
        )
        repository.observeAllVisits().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Dr A", list.first().providerName)
            awaitComplete()
        }
    }

    @Test
    fun `observeInboxQuestions maps entities to domain`() = runTest {
        every { dao.observeInboxQuestions() } returns flowOf(
            listOf(VisitQuestionEntity(id = 1, text = "Q", visitId = null, createdAt = 5)),
        )
        repository.observeInboxQuestions().test {
            assertEquals("Q", awaitItem().first().text)
            awaitComplete()
        }
    }

    @Test
    fun `insertVisit converts to entity`() = runTest {
        val captured = slot<DoctorVisitEntity>()
        coEvery { dao.insertVisit(capture(captured)) } returns 42
        val id = repository.insertVisit(
            DoctorVisit(date = Instant.ofEpochMilli(10), providerName = "Dr B", createdAt = Instant.ofEpochMilli(5)),
        )
        assertEquals(42, id)
        assertEquals(10L, captured.captured.date)
        assertEquals("Dr B", captured.captured.providerName)
    }

    @Test
    fun `attachQuestions skips empty list`() = runTest {
        repository.attachQuestions(emptyList(), 9)
        coVerify(exactly = 0) { dao.attachQuestions(any(), any()) }
        repository.attachQuestions(listOf(1, 2), 9)
        coVerify { dao.attachQuestions(listOf(1, 2), 9) }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.data.repository.DoctorVisitRepositoryImplTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): add DoctorVisitRepository and impl`

---

### Task 8: Database migration to v15 + DAO accessor

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`

- [ ] **Step 1: Register both entities, bump version, add the DAO accessor.** In the `@Database` annotation add `DoctorVisitEntity::class` and `VisitQuestionEntity::class` to `entities`, change `version = 14` to `version = 15`, and add to the abstract class:

```kotlin
abstract fun doctorVisitDao(): DoctorVisitDao
```

Add imports for `DoctorVisitEntity`, `VisitQuestionEntity`, and `DoctorVisitDao`.

- [ ] **Step 2: Add the migration** (place after `MIGRATION_13_14`; additive `CREATE TABLE` ×2, no trigger). Verify the `database` parameter name matches the existing migrations in this file.

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS doctor_visits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date INTEGER NOT NULL,
                provider_name TEXT,
                notes TEXT,
                snapshot_label TEXT,
                snapshot_created_at INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_visits_date ON doctor_visits(date)")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS visit_questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                text TEXT NOT NULL,
                answered INTEGER NOT NULL DEFAULT 0,
                visit_id INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_visit_questions_visit_id ON visit_questions(visit_id)")
    }
}
```

- [ ] **Step 3: Regenerate + commit the exported schema.** Run `./gradlew :app:compileDebugKotlin` (or `build`) so Room writes `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/15.json`. Commit this generated file alongside the migration — `MigrationTestHelper` and CI read it. **Confirm** the generated `answered` column default and index names exactly match the migration SQL above; if Room's exported schema differs (e.g. no `DEFAULT 0`), align the migration to the generated schema so the validation passes.
- [ ] **Step 4: Commit** `feat(doctor-visit): add doctor_visits and visit_questions migration v15`

---

### Task 9: Hilt wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`

- [ ] **Step 1:** In `DatabaseModule`, add `MIGRATION_14_15` to the `.addMigrations(...)` list (with its import), and add the DAO provider:

```kotlin
@Provides
fun provideDoctorVisitDao(database: BabyTrackerDatabase): DoctorVisitDao = database.doctorVisitDao()
```

(add `import com.babytracker.data.local.dao.DoctorVisitDao` and `import com.babytracker.data.local.MIGRATION_14_15`)

- [ ] **Step 2:** In `RepositoryModule`, add the binding:

```kotlin
@Binds
@Singleton
abstract fun bindDoctorVisitRepository(impl: DoctorVisitRepositoryImpl): DoctorVisitRepository
```

(add imports for `DoctorVisitRepositoryImpl` and `DoctorVisitRepository`)

- [ ] **Step 3: Commit** `feat(doctor-visit): wire DoctorVisit DAO and repository into Hilt`

---

### Task 10: DAO + migration instrumentation tests

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/data/local/dao/DoctorVisitDaoTest.kt`
- Create: `app/src/androidTest/java/com/babytracker/data/local/DoctorVisitMigrationTest.kt`

Follow the existing DAO/migration test patterns in `app/src/androidTest/java/com/babytracker/data/local/` (Room `inMemoryDatabaseBuilder` for DAO tests; `MigrationTestHelper` with the exported schema for the migration test).

- [ ] **Step 1: DAO test** — exercise: `observeAllVisits` orders by date DESC; `getUpcomingVisitsAfter(now)` returns only `date > now`; `observeInboxQuestions` returns only `visit_id IS NULL`; `attachQuestions` moves rows out of the inbox and into `observeQuestionsForVisit`; `detachQuestionsForVisit` returns them to the inbox; `deleteVisitById` returns 1.

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorVisitDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: DoctorVisitDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.doctorVisitDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun visitOrderingAndUpcomingFilter() = runTest {
        dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        dao.insertVisit(DoctorVisitEntity(date = 5_000, createdAt = 1))
        val all = dao.observeAllVisits().first()
        assertEquals(listOf(5_000L, 100L), all.map { it.date }) // DESC
        val upcoming = dao.getUpcomingVisitsAfter(1_000)
        assertEquals(listOf(5_000L), upcoming.map { it.date })
    }

    @Test
    fun questionInboxAttachDetach() = runTest {
        val visitId = dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        val q1 = dao.insertQuestion(VisitQuestionEntity(text = "A", visitId = null, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "B", visitId = null, createdAt = 2))
        assertEquals(2, dao.observeInboxQuestions().first().size)

        dao.attachQuestions(listOf(q1), visitId)
        assertEquals(1, dao.observeInboxQuestions().first().size)
        assertEquals(listOf("A"), dao.observeQuestionsForVisit(visitId).first().map { it.text })

        dao.detachQuestionsForVisit(visitId)
        assertEquals(2, dao.observeInboxQuestions().first().size)
    }

    @Test
    fun deleteVisitDetachingQuestionsIsAtomic() = runTest {
        val visitId = dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "A", visitId = visitId, createdAt = 1))
        dao.deleteVisitDetachingQuestions(visitId)
        assertEquals(null, dao.getVisitById(visitId))      // visit gone
        assertEquals(1, dao.observeInboxQuestions().first().size) // question back in inbox, not deleted
    }
}
```

- [ ] **Step 2: Migration test** — build the DB at v14 with `MigrationTestHelper`, run `MIGRATION_14_15`, assert both `doctor_visits` and `visit_questions` are queryable. Mirror the closest existing migration test (e.g. the one covering `MIGRATION_13_14`).
- [ ] **Step 3: Run** the instrumentation tests on an emulator: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.dao.DoctorVisitDaoTest"` (and the migration test). Expect PASS. If no emulator is available, note it and rely on `./gradlew test` for the unit layer.
- [ ] **Step 4: Commit** `test(doctor-visit): add DoctorVisitDao and migration tests`

---

## Acceptance Criteria

- `./gradlew build` succeeds (Room schema for v15 is exported under `app/schemas/`).
- `./gradlew test` passes, including the new unit tests.
- DAO + migration instrumentation tests pass on an emulator.
- `doctor_visits` and `visit_questions` tables exist at schema v15 with the `date` and `visit_id` indices; fresh installs and v14→v15 upgrades both produce the tables.
- No active-row trigger is added.
- `DoctorVisitRepository` is injectable wherever a constructor requests it.

## Self-Review Notes

- Spec coverage: `DoctorVisit` + `isUpcoming`/`hasSnapshot`, `VisitQuestion`, `DoctorVisitSummary`, both entities + mapping, DAO (incl. `getUpcomingVisitsAfter` for plan 7 and `getAll*Once` for plans 8/9, plus the attach/detach bulk updates), repository, DI, migration v15 — all present.
- `visit_id` is a plain nullable column, not a Room FK: deletion detaches rather than cascades. Because there is no FK, the `@Transaction deleteVisitDetachingQuestions` is the **integrity boundary** — detach + delete commit atomically so a crash mid-delete can't orphan the visit with its questions already moved. Plan 2's delete use case calls this single repository method (then cancels the reminder after the DB transaction succeeds).
- `attachQuestions` is a no-op for an empty id list (guarded in the repo) so the SQL `IN ()` edge case never executes.
- `getUpcomingVisitsAfter`/timestamps use epoch-ms `Long` at the DAO boundary; the repository passes through.
