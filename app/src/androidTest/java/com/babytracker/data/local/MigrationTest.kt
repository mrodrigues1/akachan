package com.babytracker.data.local

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate4To5_createsBabiesTable_addsTimezoneId_normalizesLegacySleepType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-v4-to-v5"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(V4DatabaseCallback())
                    .build()
            )
            val db = helper.writableDatabase

            db.execSQL("INSERT INTO sleep_records(start_time, sleep_type) VALUES(1000, 'NAP')")
            db.execSQL("INSERT INTO sleep_records(start_time, sleep_type) VALUES(2000, 'Nap')")
            db.execSQL("INSERT INTO sleep_records(start_time, sleep_type) VALUES(3000, 'Night Sleep')")

            MIGRATION_4_5.migrate(db)

            db.execSQL(
                "INSERT INTO babies(id, created_at, updated_at) VALUES(1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})"
            )
            val babiesCursor: Cursor = db.query("SELECT count(*) FROM babies")
            babiesCursor.moveToFirst()
            assertEquals(1, babiesCursor.getInt(0))
            babiesCursor.close()

            db.execSQL("UPDATE sleep_records SET timezone_id = 'UTC' WHERE start_time = 1000")

            val typeCursor: Cursor = db.query(
                "SELECT sleep_type FROM sleep_records ORDER BY start_time ASC"
            )
            typeCursor.moveToFirst()
            assertEquals("NAP", typeCursor.getString(0))
            typeCursor.moveToNext()
            assertEquals("NAP", typeCursor.getString(0))
            typeCursor.moveToNext()
            assertEquals("NIGHT_SLEEP", typeCursor.getString(0))
            typeCursor.close()

            db.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate2To3_createsPumpingAndMilkBagsTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-v2-to-v3"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(V2DatabaseCallback())
                    .build()
            )
            val db = helper.writableDatabase

            // Seed a v2 row to verify data survives migration
            db.execSQL(
                "INSERT INTO breastfeeding_sessions(start_time, starting_side, paused_duration_ms)" +
                    " VALUES(1000, 'LEFT', 0)"
            )

            MIGRATION_2_3.migrate(db)

            // Verify new tables accept inserts
            db.execSQL(
                "INSERT INTO pumping_sessions(start_time, breast, paused_duration_ms)" +
                    " VALUES(2000, 'BOTH', 0)"
            )
            db.execSQL(
                "INSERT INTO milk_bags(collection_date, volume_ml, created_at)" +
                    " VALUES(2000, 100, 2000)"
            )

            val pumpingCursor: Cursor = db.query("SELECT count(*) FROM pumping_sessions")
            pumpingCursor.moveToFirst()
            assertEquals(1, pumpingCursor.getInt(0))
            pumpingCursor.close()

            // Existing v2 data must survive
            val breastfeedingCursor: Cursor =
                db.query("SELECT count(*) FROM breastfeeding_sessions")
            breastfeedingCursor.moveToFirst()
            assertEquals(1, breastfeedingCursor.getInt(0))
            breastfeedingCursor.close()

            db.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}

private class V4DatabaseCallback : SupportSQLiteOpenHelper.Callback(4) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                sleep_type TEXT NOT NULL,
                notes TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS breastfeeding_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                starting_side TEXT NOT NULL,
                switch_time INTEGER,
                notes TEXT,
                paused_at INTEGER DEFAULT NULL,
                paused_duration_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

private class V2DatabaseCallback : SupportSQLiteOpenHelper.Callback(2) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE breastfeeding_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "start_time INTEGER NOT NULL, " +
                "end_time INTEGER, " +
                "starting_side TEXT NOT NULL, " +
                "switch_time INTEGER, " +
                "notes TEXT, " +
                "paused_at INTEGER DEFAULT NULL, " +
                "paused_duration_ms INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE sleep_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "start_time INTEGER NOT NULL, " +
                "end_time INTEGER, " +
                "sleep_type TEXT NOT NULL, " +
                "notes TEXT)"
        )
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
