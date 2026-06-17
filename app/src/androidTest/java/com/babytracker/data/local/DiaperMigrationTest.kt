package com.babytracker.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "diaper-migration-test"

@RunWith(AndroidJUnit4::class)
class DiaperMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BabyTrackerDatabase::class.java,
    )

    @Test
    fun migrate12To13_roomSchemaValidationPasses() {
        helper.createDatabase(TEST_DB, 12).close()
        // Throws if the migrated schema differs from entity-generated schema 13.
        helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13).close()
    }

    @Test
    fun migrate12To13_createsDiaperChangesTable_acceptsInserts() {
        helper.createDatabase(TEST_DB, 12).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.execSQL(
            "INSERT INTO diaper_changes (timestamp, type, notes, created_at)" +
                " VALUES (1000, 'WET', 'first', 1000)",
        )
        db.execSQL(
            "INSERT INTO diaper_changes (timestamp, type, notes, created_at)" +
                " VALUES (2000, 'BOTH', NULL, 2000)",
        )

        val cursor: Cursor = db.query("SELECT count(*) FROM diaper_changes")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()
        db.close()
    }
}
