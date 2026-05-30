LINEAR_ISSUE: AKA-61

# Atomic Session Repository Ops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add transactional "start if none" and "stop active" operations for breastfeeding and sleep so tile toggles can perform one atomic DB operation instead of open-coded read-then-write races.

**Architecture:** Put atomic operations in DAO `@Transaction` default methods and expose them through repository interfaces. The DAO methods read active state inside the same Room transaction, insert or update only when appropriate, and return compact results that the tile layer can branch on without relying on exceptions for the common path. If a cross-process writer wins between the transaction's active read and insert, the database invariant from Plan 1 may reject the insert; `startIfNone` catches that expected constraint loser and returns `null` after re-reading active state.

**Tech Stack:** Kotlin, Room DAO `@Transaction`, Coroutines, Flow `.first()`, JUnit 5 + MockK repository tests, Android DAO tests with real Room.

---

## Dependencies

- Depends on Plan 1 / `AKA-60` for the global database invariant and constraint-tolerant insert behavior.

## Suggested Branch

`feat/atomic-session-repository-ops`

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt` | Add transactional active start/stop helpers. | Modify |
| `app/src/main/java/com/babytracker/data/local/dao/SleepDao.kt` | Add transactional active start/stop helpers. | Modify |
| `app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt` | Expose `startSessionIfNone` and `stopActiveSession`. | Modify |
| `app/src/main/java/com/babytracker/domain/repository/SleepRepository.kt` | Expose `startRecordIfNone` and `stopActiveRecord`. | Modify |
| `app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt` | Delegate repository operations to DAO transactions and map entities. | Modify |
| `app/src/main/java/com/babytracker/data/repository/SleepRepositoryImpl.kt` | Same for sleep. | Modify |
| `app/src/androidTest/java/com/babytracker/data/local/dao/BreastfeedingDaoTest.kt` | Add real Room transaction tests. | Modify |
| `app/src/androidTest/java/com/babytracker/data/local/dao/SleepDaoTest.kt` | Add real Room transaction tests. | Modify |
| `app/src/test/java/com/babytracker/data/repository/BreastfeedingRepositoryImplTest.kt` | Add repository delegation and mapping tests. | Modify |
| `app/src/test/java/com/babytracker/data/repository/SleepRepositoryImplTest.kt` | Add repository delegation and mapping tests. | Modify |

## Implementation Tasks

### Task 1: Define repository API

- [ ] Add to `BreastfeedingRepository`:

```kotlin
suspend fun startSessionIfNone(session: BreastfeedingSession): Long?
suspend fun stopActiveSession(endTime: Instant): Boolean
```

- [ ] Add to `SleepRepository`:

```kotlin
suspend fun startRecordIfNone(record: SleepRecord): Long?
suspend fun stopActiveRecord(endTime: Instant): Boolean
```

- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.BreastfeedingRepositoryImplTest" --tests "com.babytracker.data.repository.SleepRepositoryImplTest" -PfastTests
```

Expected before implementation: compile failures in repository implementations.

### Task 2: Implement breastfeeding DAO transactions

- [ ] In `BreastfeedingDao.kt`, import `android.database.sqlite.SQLiteConstraintException` and `androidx.room.Transaction`.
- [ ] Add a suspend query for a one-shot active read:

```kotlin
@Query("SELECT * FROM breastfeeding_sessions WHERE end_time IS NULL LIMIT 1")
suspend fun getActiveSessionOnce(): BreastfeedingEntity?
```

- [ ] Add a transaction that inserts only when no active session exists:

```kotlin
@Transaction
suspend fun startSessionIfNone(entity: BreastfeedingEntity): Long? {
    if (getActiveSessionOnce() != null) return null
    return try {
        insertSession(entity)
    } catch (e: SQLiteConstraintException) {
        if (getActiveSessionOnce() != null) null else throw e
    }
}
```

- [ ] Add a query and transaction for stopping the active row:

```kotlin
@Query("UPDATE breastfeeding_sessions SET end_time = :endTime WHERE id = :id")
suspend fun markSessionEnded(id: Long, endTime: Long)

@Transaction
suspend fun stopActiveSession(endTime: Long): Boolean {
    val active = getActiveSessionOnce() ?: return false
    markSessionEnded(active.id, endTime)
    return true
}
```

### Task 3: Implement sleep DAO transactions

- [ ] In `SleepDao.kt`, import `android.database.sqlite.SQLiteConstraintException` and `androidx.room.Transaction`.
- [ ] Add:

```kotlin
@Query("SELECT * FROM sleep_records WHERE end_time IS NULL ORDER BY start_time DESC, id DESC LIMIT 1")
suspend fun getActiveRecordOnce(): SleepEntity?

@Transaction
suspend fun startRecordIfNone(entity: SleepEntity): Long? {
    if (getActiveRecordOnce() != null) return null
    return try {
        insertRecord(entity)
    } catch (e: SQLiteConstraintException) {
        if (getActiveRecordOnce() != null) null else throw e
    }
}

@Query("UPDATE sleep_records SET end_time = :endTime WHERE id = :id")
suspend fun markRecordEnded(id: Long, endTime: Long)

@Transaction
suspend fun stopActiveRecord(endTime: Long): Boolean {
    val active = getActiveRecordOnce() ?: return false
    markRecordEnded(active.id, endTime)
    return true
}
```

### Task 4: Wire repositories

- [ ] In `BreastfeedingRepositoryImpl`, implement:

```kotlin
override suspend fun startSessionIfNone(session: BreastfeedingSession): Long? =
    dao.startSessionIfNone(session.toEntity())

override suspend fun stopActiveSession(endTime: Instant): Boolean =
    dao.stopActiveSession(endTime.toEpochMilli())
```

- [ ] In `SleepRepositoryImpl`, implement:

```kotlin
override suspend fun startRecordIfNone(record: SleepRecord): Long? =
    dao.startRecordIfNone(record.toEntity())

override suspend fun stopActiveRecord(endTime: Instant): Boolean =
    dao.stopActiveRecord(endTime.toEpochMilli())
```

### Task 5: Add transaction tests

- [ ] In `BreastfeedingDaoTest`, add tests for:
  - no active row -> `startSessionIfNone` inserts and returns id;
  - existing active row -> returns `null` and leaves one active row;
  - invariant-loser race -> a second active insert attempt returns `null`, not an exception;
  - active row -> `stopActiveSession` returns `true` and sets `end_time`;
  - no active row -> `stopActiveSession` returns `false`.
- [ ] In `SleepDaoTest`, add the equivalent five tests.
- [ ] Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.data.local.dao.BreastfeedingDaoTest,com.babytracker.data.local.dao.SleepDaoTest
```

Expected after implementation: pass.

### Task 6: Add repository tests

- [ ] In `BreastfeedingRepositoryImplTest`, verify `startSessionIfNone` converts the domain session to an entity and returns the DAO id/null.
- [ ] Verify `stopActiveSession` passes epoch millis to the DAO and returns the DAO boolean.
- [ ] In `SleepRepositoryImplTest`, add the same delegation tests.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.BreastfeedingRepositoryImplTest" --tests "com.babytracker.data.repository.SleepRepositoryImplTest" -PfastTests
```

Expected after implementation: pass.

## Acceptance Criteria

- Both repositories expose nullable start-if-none operations and boolean stop-active operations.
- DAO transactions prevent tile-path check-then-act races in a single database transaction.
- Cross-process insert losers caused by the database invariant return `null` from start-if-none instead of throwing.
- Operations are idempotent for already-active and already-inactive states.
- Targeted DAO Android tests and repository JVM tests pass.
