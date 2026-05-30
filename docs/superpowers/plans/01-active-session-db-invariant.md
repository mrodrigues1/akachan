LINEAR_ISSUE: AKA-60

# Active Session DB Invariant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce "at most one active breastfeeding session and one active sleep record" at the database boundary so tile, app, widget, notification, import, and debug paths cannot create duplicate active rows.

**Architecture:** Add a Room migration from the current database version 3 to version 4. The migration first repairs any pre-existing duplicate active rows, keeping the newest active row per table and closing older active rows at the kept row's `start_time`, then installs the invariant. Prefer partial unique indexes on a constant expression, but verify Room schema validation with `MigrationTestHelper`; if validation rejects unmodeled partial indexes, use equivalent `BEFORE INSERT` triggers because Room does not validate triggers. Repository insert methods catch `SQLiteConstraintException`, re-read active/latest state, and treat the violation as "already active" for active rows instead of crashing.

**Tech Stack:** Kotlin, Room, SQLite partial indexes or triggers, `MigrationTestHelper`, JUnit 4 Android tests, JUnit 5 repository tests, Coroutines.

---

## Dependencies

None. This is the foundation for later tile work.

## Suggested Branch

`feat/active-session-db-invariant`

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt` | Room database version and migrations. Current version is already `3`; add `MIGRATION_3_4` and bump to `4`. | Modify |
| `app/src/main/java/com/babytracker/di/DatabaseModule.kt` | Register the new migration in the Room builder. | Modify |
| `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/4.json` | Exported schema after migration. | Create via Room schema export |
| `app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt` | Convert duplicate active insert constraint into no-op/read-current behavior. | Modify |
| `app/src/main/java/com/babytracker/data/repository/SleepRepositoryImpl.kt` | Same for sleep records. | Modify |
| `app/src/test/java/com/babytracker/data/repository/BreastfeedingRepositoryImplTest.kt` | Unit coverage for constraint handling. | Modify |
| `app/src/test/java/com/babytracker/data/repository/SleepRepositoryImplTest.kt` | Unit coverage for constraint handling. | Modify |
| `app/src/androidTest/java/com/babytracker/data/local/ActiveSessionInvariantMigrationTest.kt` | Migration and invariant coverage with a real Room database. | Create |

## Duplicate Repair Rule

Before adding the invariant, repair v3 data that already violates it:

- Breastfeeding: among rows where `end_time IS NULL`, keep the row with the highest `start_time`; break ties by highest `id`.
- Sleep: same rule on `sleep_records`.
- For every other active row in that table, set `end_time` to the kept row's `start_time`.
- If an older active row has a `start_time` greater than the computed close time because of corrupt clock data, set `end_time` to that row's own `start_time` so the record becomes zero-duration rather than invalid.
- Do not delete rows; preserving a closed historical record is less destructive and keeps export/import history intact.

## Implementation Tasks

### Task 1: Add failing migration tests

- [ ] Create `app/src/androidTest/java/com/babytracker/data/local/ActiveSessionInvariantMigrationTest.kt`.
- [ ] Add a `MigrationTestHelper` test that creates a version 3 database with `breastfeeding_sessions`, inserts one active row, migrates to version 4, and verifies a second active insert fails with `SQLiteConstraintException`.
- [ ] Add the same test for `sleep_records`.
- [ ] Add a v3 fixture with two active breastfeeding rows. After migration, assert exactly one row remains active, it is the newest row by `start_time, id`, and the older row's `end_time` is non-null.
- [ ] Add the same duplicate-repair fixture for sleep records.
- [ ] Add survival checks for existing completed rows and the existing version 3 pumping/milk tables.
- [ ] Add tests that insert one active row and one completed row, then attempt to update the completed row back to `end_time = NULL`; assert the update is rejected for both breastfeeding and sleep when the trigger fallback is selected.
- [ ] Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.data.local.ActiveSessionInvariantMigrationTest
```

Expected before implementation: compile failure because version 4 migration does not exist.

### Task 2: Add `MIGRATION_3_4`

- [ ] In `BabyTrackerDatabase.kt`, bump `@Database(version = 4)`.
- [ ] Add `MIGRATION_3_4` after `MIGRATION_2_3`.
- [ ] First repair duplicate active rows. Use SQL that deterministically closes all active rows except the newest active row in each table before adding any index or trigger:

```sql
UPDATE breastfeeding_sessions
SET end_time = CASE
    WHEN start_time > (
        SELECT kept.start_time
        FROM breastfeeding_sessions AS kept
        WHERE kept.end_time IS NULL
        ORDER BY kept.start_time DESC, kept.id DESC
        LIMIT 1
    ) THEN start_time
    ELSE (
        SELECT kept.start_time
        FROM breastfeeding_sessions AS kept
        WHERE kept.end_time IS NULL
        ORDER BY kept.start_time DESC, kept.id DESC
        LIMIT 1
    )
END
WHERE end_time IS NULL
  AND id NOT IN (
      SELECT kept.id
      FROM breastfeeding_sessions AS kept
      WHERE kept.end_time IS NULL
      ORDER BY kept.start_time DESC, kept.id DESC
      LIMIT 1
  )
```

Use the same shape for `sleep_records`.

- [ ] Then attempt the partial unique index implementation:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_feed
            ON breastfeeding_sessions(1)
            WHERE end_time IS NULL
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_sleep
            ON sleep_records(1)
            WHERE end_time IS NULL
            """.trimIndent()
        )
    }
}
```

- [ ] Register `MIGRATION_3_4` in `DatabaseModule.kt` alongside existing migrations.
- [ ] Run the migration test. If Room schema validation fails because the partial indexes are not modeled in generated `TableInfo`, keep the duplicate-repair SQL and replace only the invariant install SQL with equivalent insert and update triggers. The fallback must guard both ways a row can become active: `INSERT` with `NEW.end_time IS NULL` and `UPDATE OF end_time` that changes a completed row back to active.

```sql
CREATE TRIGGER IF NOT EXISTS trg_one_active_feed
BEFORE INSERT ON breastfeeding_sessions
WHEN NEW.end_time IS NULL
BEGIN
    SELECT RAISE(ABORT, 'active breastfeeding session already exists')
    WHERE EXISTS (SELECT 1 FROM breastfeeding_sessions WHERE end_time IS NULL);
END
```

Add the matching update trigger:

```sql
CREATE TRIGGER IF NOT EXISTS trg_one_active_feed_update
BEFORE UPDATE OF end_time ON breastfeeding_sessions
WHEN NEW.end_time IS NULL
BEGIN
    SELECT RAISE(ABORT, 'active breastfeeding session already exists')
    WHERE EXISTS (
        SELECT 1
        FROM breastfeeding_sessions
        WHERE end_time IS NULL
          AND id != OLD.id
    );
END
```

Add the analogous insert and update triggers for `sleep_records`.

### Task 3: Export and verify schema

- [ ] Run the schema-exporting unit test task:

```bash
./gradlew :app:assembleDebug
```

- [ ] Confirm `app/schemas/com.babytracker.data.local.BabyTrackerDatabase/4.json` exists and contains the new version.
- [ ] Commit only the database, migration registration, schema, and migration test changes for this task if implementing manually.

### Task 4: Make active inserts constraint-tolerant

- [ ] In `BreastfeedingRepositoryImpl.insertSession`, catch `SQLiteConstraintException`.
- [ ] If the attempted session has `endTime == null`, re-read `dao.getActiveSession().first()` and return the existing active id when present.
- [ ] If no active row exists after the violation, rethrow; that indicates an unrelated constraint failure.
- [ ] In `SleepRepositoryImpl.insertRecord`, use the same pattern for `record.endTime == null`, re-reading `dao.getLatestRecord()` and only swallowing the violation if the latest record is still in progress.
- [ ] Keep completed/manual backfill inserts strict: any constraint exception for a completed row must propagate.

### Task 5: Add repository tests for constraint conversion

- [ ] Extend `BreastfeedingRepositoryImplTest` with a test where `dao.insertSession(active)` throws `SQLiteConstraintException` and `dao.getActiveSession()` emits an existing active entity; assert the repository returns the existing id.
- [ ] Add a breastfeeding test where the active re-read returns `null`; assert the exception is rethrown.
- [ ] Extend `SleepRepositoryImplTest` with analogous tests for `insertRecord`.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.BreastfeedingRepositoryImplTest" --tests "com.babytracker.data.repository.SleepRepositoryImplTest" -PfastTests
```

Expected after implementation: pass.

### Task 6: Verify mixed-path invariant

- [ ] Add an Android test that inserts an active row through the plain DAO method, then attempts a second active insert through the repository path and verifies it does not create another active row.
- [ ] Add the sleep equivalent.
- [ ] Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.data.local.ActiveSessionInvariantMigrationTest
```

Expected after implementation: pass.

## Acceptance Criteria

- Room database version is `4`; `MIGRATION_3_4` is registered and covered.
- A second active breastfeeding row and a second active sleep row are rejected by the database.
- Updating a completed breastfeeding or sleep row back to active is rejected when another active row exists.
- Existing completed records and existing v3 pumping/inventory schema survive migration.
- Pre-existing duplicate active rows are repaired deterministically before the invariant is installed.
- Active-session insert races are converted into "already active" behavior for active rows.
- Completed row insert failures still surface as errors.
- Targeted repository tests and migration/invariant Android tests pass.
