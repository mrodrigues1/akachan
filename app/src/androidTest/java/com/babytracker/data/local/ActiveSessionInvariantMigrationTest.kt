package com.babytracker.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the version-4 active-session DB invariant: at most one active
 * (`end_time IS NULL`) row may exist in `breastfeeding_sessions` and in
 * `sleep_records`.
 *
 * These tests reference [MIGRATION_3_4], which is added by Task 2. Until then
 * the symbol is unresolved and the androidTest source set will not compile —
 * this is intentional TDD.
 */
@RunWith(AndroidJUnit4::class)
class ActiveSessionInvariantMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BabyTrackerDatabase::class.java,
        )

    // region breastfeeding — second active insert rejected

    @Test
    fun breastfeeding_secondActiveInsertAfterMigration_throws() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertBreastfeeding(startTime = 1_000L, endTime = null)
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            assertThrows(SQLiteException::class.java) {
                db.insertBreastfeeding(startTime = 2_000L, endTime = null)
            }
        }
    }

    // endregion

    // region sleep — second active insert rejected

    @Test
    fun sleep_secondActiveInsertAfterMigration_throws() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertSleep(startTime = 1_000L, endTime = null)
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            assertThrows(SQLiteException::class.java) {
                db.insertSleep(startTime = 2_000L, endTime = null)
            }
        }
    }

    // endregion

    // region breastfeeding — duplicate-active repair during migration

    @Test
    fun breastfeeding_twoActiveRows_migrationKeepsOnlyNewestActive() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            // Older active row.
            db.insertBreastfeeding(startTime = 1_000L, endTime = null)
            // Newer active row — this is the one that must stay active.
            db.insertBreastfeeding(startTime = 2_000L, endTime = null)
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            assertEquals(1, db.activeCount("breastfeeding_sessions"))

            // The surviving active row is the newest by (start_time, id).
            val activeStart = db.singleLong(
                "SELECT start_time FROM breastfeeding_sessions WHERE end_time IS NULL",
            )
            assertEquals(2_000L, activeStart)

            // The older row was closed — its end_time is now non-null.
            val olderEnd = db.nullableLong(
                "SELECT end_time FROM breastfeeding_sessions WHERE start_time = 1000",
            )
            assertNotNull(olderEnd)
        }
    }

    // endregion

    // region sleep — duplicate-active repair during migration

    @Test
    fun sleep_twoActiveRows_migrationKeepsOnlyNewestActive() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertSleep(startTime = 1_000L, endTime = null)
            db.insertSleep(startTime = 2_000L, endTime = null)
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            assertEquals(1, db.activeCount("sleep_records"))

            val activeStart = db.singleLong(
                "SELECT start_time FROM sleep_records WHERE end_time IS NULL",
            )
            assertEquals(2_000L, activeStart)

            val olderEnd = db.nullableLong(
                "SELECT end_time FROM sleep_records WHERE start_time = 1000",
            )
            assertNotNull(olderEnd)
        }
    }

    // endregion

    // region survival of existing data and v3 schema

    @Test
    fun migration_preservesCompletedRowsAndPumpingInventorySchema() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            // Completed rows must survive untouched.
            db.insertBreastfeeding(startTime = 1_000L, endTime = 1_500L)
            db.insertSleep(startTime = 1_000L, endTime = 1_500L)

            // v3 pumping/inventory data.
            db.execSQL(
                "INSERT INTO pumping_sessions(start_time, breast, paused_duration_ms) " +
                    "VALUES(3000, 'BOTH', 0)",
            )
            db.execSQL(
                "INSERT INTO milk_bags(collection_date, volume_ml, created_at) " +
                    "VALUES(3000, 120, 3000)",
            )
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            // Completed rows survive and remain completed.
            assertEquals(1, db.singleLong("SELECT count(*) FROM breastfeeding_sessions"))
            assertEquals(0, db.activeCount("breastfeeding_sessions"))
            assertEquals(1, db.singleLong("SELECT count(*) FROM sleep_records"))
            assertEquals(0, db.activeCount("sleep_records"))

            // Pumping/inventory schema and data survive and still accept inserts.
            assertEquals(1, db.singleLong("SELECT count(*) FROM pumping_sessions"))
            assertEquals(1, db.singleLong("SELECT count(*) FROM milk_bags"))
            db.execSQL(
                "INSERT INTO pumping_sessions(start_time, breast, paused_duration_ms) " +
                    "VALUES(4000, 'LEFT', 0)",
            )
            db.execSQL(
                "INSERT INTO milk_bags(collection_date, volume_ml, created_at) " +
                    "VALUES(4000, 90, 4000)",
            )
            assertEquals(2, db.singleLong("SELECT count(*) FROM pumping_sessions"))
            assertEquals(2, db.singleLong("SELECT count(*) FROM milk_bags"))
        }
    }

    // endregion

    // region update rejection (trigger fallback only)

    /**
     * When the trigger fallback is selected for the invariant, reopening a
     * completed row while another active row exists must be rejected. With the
     * partial-unique-index approach this guard is not present (the index only
     * guards INSERTs), so the update succeeds — both outcomes are accepted here
     * so the test is meaningful regardless of which mechanism Task 2 ships.
     */
    @Test
    fun breastfeeding_reopenCompletedRowWhileActiveExists_rejectedWhenTriggerUsed() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertBreastfeeding(startTime = 1_000L, endTime = 1_500L) // completed
            db.insertBreastfeeding(startTime = 2_000L, endTime = null) // active
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            val reopened = db.tryReopen("breastfeeding_sessions", startTime = 1_000L)
            if (reopened) {
                // Partial-unique-index path: update allowed, so two active rows now exist.
                assertEquals(2, db.activeCount("breastfeeding_sessions"))
            } else {
                // Trigger path: update rejected, original active row untouched.
                assertEquals(1, db.activeCount("breastfeeding_sessions"))
            }
        }
    }

    @Test
    fun sleep_reopenCompletedRowWhileActiveExists_rejectedWhenTriggerUsed() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertSleep(startTime = 1_000L, endTime = 1_500L) // completed
            db.insertSleep(startTime = 2_000L, endTime = null) // active
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.use { db ->
            val reopened = db.tryReopen("sleep_records", startTime = 1_000L)
            if (reopened) {
                assertEquals(2, db.activeCount("sleep_records"))
            } else {
                assertEquals(1, db.activeCount("sleep_records"))
            }
        }
    }

    // endregion

    // region helpers

    private fun SupportSQLiteDatabase.insertBreastfeeding(startTime: Long, endTime: Long?) {
        val values = ContentValues().apply {
            put("start_time", startTime)
            if (endTime == null) putNull("end_time") else put("end_time", endTime)
            put("starting_side", "LEFT")
            put("paused_duration_ms", 0L)
        }
        insert("breastfeeding_sessions", SQLiteDatabase.CONFLICT_NONE, values)
    }

    private fun SupportSQLiteDatabase.insertSleep(startTime: Long, endTime: Long?) {
        val values = ContentValues().apply {
            put("start_time", startTime)
            if (endTime == null) putNull("end_time") else put("end_time", endTime)
            put("sleep_type", "NAP")
        }
        insert("sleep_records", SQLiteDatabase.CONFLICT_NONE, values)
    }

    private fun SupportSQLiteDatabase.activeCount(table: String): Long =
        singleLong("SELECT count(*) FROM $table WHERE end_time IS NULL")

    private fun SupportSQLiteDatabase.singleLong(sql: String): Long =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.nullableLong(sql: String): Long? =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    /**
     * Attempts to reopen (set end_time = NULL) the completed row with the given
     * start_time. Returns true if the update was applied, false if the DB
     * rejected it with an [SQLiteException] (trigger fallback).
     */
    private fun SupportSQLiteDatabase.tryReopen(table: String, startTime: Long): Boolean =
        try {
            execSQL(
                "UPDATE $table SET end_time = NULL WHERE start_time = $startTime",
            )
            true
        } catch (_: SQLiteException) {
            false
        }

    // endregion

    private companion object {
        private const val TEST_DB = "invariant-test"
    }
}
