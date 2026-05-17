package com.babytracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BabyTrackerDatabase::class.java,
    )

    @Test
    fun migrate2To3_createsPumpingAndMilkBagsTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO breastfeeding_sessions(start_time, starting_side, paused_duration_ms)" +
                    " VALUES(1000, 'LEFT', 0)"
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            db.execSQL(
                "INSERT INTO pumping_sessions(start_time, breast, paused_duration_ms)" +
                    " VALUES(2000, 'BOTH', 0)"
            )
            db.execSQL(
                "INSERT INTO milk_bags(collection_date, volume_ml, created_at)" +
                    " VALUES(2000, 100, 2000)"
            )
            db.query("SELECT count(*) FROM pumping_sessions").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 1)
            }
            assertNotNull(db)
        }
    }
}
