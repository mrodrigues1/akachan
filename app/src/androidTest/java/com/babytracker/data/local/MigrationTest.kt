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

    @Test
    fun migrate6To7_createsSleepRecommendationTables_andUniquenessIsEnforced() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-v6-to-v7"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(V6DatabaseCallback())
                    .build()
            )
            val db = helper.writableDatabase

            // Seed a v6 row to verify pre-existing data survives
            db.execSQL(
                "INSERT INTO baby_events(timestamp, event_type, created_at) VALUES(1000, 'FEVER', 1000)"
            )

            MIGRATION_6_7.migrate(db)

            // Pre-existing data survives
            val eventsCursor: Cursor = db.query("SELECT count(*) FROM baby_events")
            eventsCursor.moveToFirst()
            assertEquals(1, eventsCursor.getInt(0))
            eventsCursor.close()

            // sleep_recommendations accepts inserts
            db.execSQL(
                "INSERT INTO sleep_recommendations(anchor_sleep_id, generated_at, recommendation_type, window_start, window_end, best_estimate, confidence, lifecycle, algorithm_version)" +
                    " VALUES(1, 1000, 'NAP', 2000, 3000, 2500, 'MEDIUM', 'GENERATED', 'v1')"
            )
            val recCursor: Cursor = db.query("SELECT count(*) FROM sleep_recommendations")
            recCursor.moveToFirst()
            assertEquals(1, recCursor.getInt(0))
            recCursor.close()

            // Unique constraint on (anchor_sleep_id, recommendation_type, algorithm_version) — second insert is silently ignored
            db.execSQL(
                "INSERT OR IGNORE INTO sleep_recommendations(anchor_sleep_id, generated_at, recommendation_type, window_start, window_end, best_estimate, confidence, lifecycle, algorithm_version)" +
                    " VALUES(1, 9999, 'NAP', 2000, 3000, 2500, 'HIGH', 'GENERATED', 'v1')"
            )
            val recCursorAfterDup: Cursor = db.query("SELECT count(*) FROM sleep_recommendations")
            recCursorAfterDup.moveToFirst()
            assertEquals(1, recCursorAfterDup.getInt(0))
            recCursorAfterDup.close()

            // sleep_recommendation_feedback accepts inserts
            db.execSQL(
                "INSERT INTO sleep_recommendation_feedback(recommendation_id, outcome, created_at)" +
                    " VALUES(1, 'ACTED_IN_WINDOW', 5000)"
            )
            val fbCursor: Cursor = db.query("SELECT count(*) FROM sleep_recommendation_feedback")
            fbCursor.moveToFirst()
            assertEquals(1, fbCursor.getInt(0))
            fbCursor.close()

            // Unique constraint on (recommendation_id, outcome) — duplicate is silently ignored
            db.execSQL(
                "INSERT OR IGNORE INTO sleep_recommendation_feedback(recommendation_id, outcome, created_at)" +
                    " VALUES(1, 'ACTED_IN_WINDOW', 9999)"
            )
            val fbCursorAfterDup: Cursor = db.query("SELECT count(*) FROM sleep_recommendation_feedback")
            fbCursorAfterDup.moveToFirst()
            assertEquals(1, fbCursorAfterDup.getInt(0))
            fbCursorAfterDup.close()

            db.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate7To8_createsBottleFeedsTable_andFkSetsNullOnBagDelete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-v7-to-v8"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(V7DatabaseCallback())
                    .build()
            )
            val db = helper.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON")

            // Seed a milk bag so the FK target exists.
            db.execSQL("INSERT INTO milk_bags(id, collection_date, volume_ml, created_at) VALUES(1, 1000, 100, 1000)")

            MIGRATION_7_8.migrate(db)

            // bottle_feeds accepts inserts, including a linked bag.
            db.execSQL(
                "INSERT INTO bottle_feeds(timestamp, volume_ml, type, linked_milk_bag_id, created_at)" +
                    " VALUES(2000, 120, 'BREAST_MILK', 1, 2000)"
            )
            val feedCursor: Cursor = db.query("SELECT count(*) FROM bottle_feeds")
            feedCursor.moveToFirst()
            assertEquals(1, feedCursor.getInt(0))
            feedCursor.close()

            // Deleting the bag nulls the link (ON DELETE SET NULL), feed row survives.
            db.execSQL("DELETE FROM milk_bags WHERE id = 1")
            val linkCursor: Cursor = db.query(
                "SELECT linked_milk_bag_id FROM bottle_feeds"
            )
            linkCursor.moveToFirst()
            assertEquals(true, linkCursor.isNull(0))
            linkCursor.close()

            db.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}

private class V7DatabaseCallback : SupportSQLiteOpenHelper.Callback(7) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pumping_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                breast TEXT NOT NULL,
                volume_ml INTEGER,
                notes TEXT,
                paused_at INTEGER,
                paused_duration_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS milk_bags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                collection_date INTEGER NOT NULL,
                volume_ml INTEGER NOT NULL,
                source_session_id INTEGER,
                used_at INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(source_session_id) REFERENCES pumping_sessions(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

private class V6DatabaseCallback : SupportSQLiteOpenHelper.Callback(6) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS baby_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                intensity INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
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
