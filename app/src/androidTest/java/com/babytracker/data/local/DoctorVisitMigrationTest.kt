package com.babytracker.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "doctor-visit-migration-test"

@RunWith(AndroidJUnit4::class)
class DoctorVisitMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BabyTrackerDatabase::class.java,
    )

    @Test
    fun migrate14To15_roomSchemaValidationPasses() {
        helper.createDatabase(TEST_DB, 14).close()
        // Throws if the migrated schema differs from entity-generated schema 15.
        helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15).close()
    }

    @Test
    fun migrate14To15_createsDoctorVisitTables_acceptsInserts() {
        helper.createDatabase(TEST_DB, 14).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        db.execSQL(
            "INSERT INTO doctor_visits (date, provider_name, notes, snapshot_label, snapshot_created_at, created_at)" +
                " VALUES (5000, 'Dr. Tanaka', 'Bring chart', NULL, NULL, 1000)",
        )
        db.execSQL(
            "INSERT INTO visit_questions (text, answered, visit_id, created_at)" +
                " VALUES ('Is the rash normal?', 0, NULL, 2000)",
        )
        db.execSQL(
            "INSERT INTO visit_questions (text, answered, visit_id, created_at)" +
                " VALUES ('Sleep regression?', 1, 1, 3000)",
        )

        val visits: Cursor = db.query("SELECT count(*) FROM doctor_visits")
        visits.moveToFirst()
        assertEquals(1, visits.getInt(0))
        visits.close()

        val inbox: Cursor = db.query("SELECT count(*) FROM visit_questions WHERE visit_id IS NULL")
        inbox.moveToFirst()
        assertEquals(1, inbox.getInt(0))
        inbox.close()

        db.close()
    }
}
