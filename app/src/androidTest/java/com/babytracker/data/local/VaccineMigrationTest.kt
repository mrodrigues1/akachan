package com.babytracker.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "vaccine-migration-test"

@RunWith(AndroidJUnit4::class)
class VaccineMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BabyTrackerDatabase::class.java,
    )

    @Test
    fun migrate13To14_roomSchemaValidationPasses() {
        helper.createDatabase(TEST_DB, 13).close()
        // Throws if the migrated schema differs from entity-generated schema 14.
        helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14).close()
    }

    @Test
    fun migrate13To14_createsVaccinesTable_acceptsInserts() {
        helper.createDatabase(TEST_DB, 13).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        db.execSQL(
            "INSERT INTO vaccines (name, dose_label, status, scheduled_date, administered_date, notes, created_at)" +
                " VALUES ('BCG', '1st dose', 'SCHEDULED', 5000, NULL, NULL, 1000)",
        )
        db.execSQL(
            "INSERT INTO vaccines (name, dose_label, status, scheduled_date, administered_date, notes, created_at)" +
                " VALUES ('MMR', NULL, 'ADMINISTERED', NULL, 9000, 'left thigh', 8000)",
        )

        val cursor: Cursor = db.query("SELECT count(*) FROM vaccines")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()
        db.close()
    }
}
