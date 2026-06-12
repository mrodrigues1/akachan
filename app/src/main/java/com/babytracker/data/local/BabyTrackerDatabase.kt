package com.babytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babytracker.data.local.converter.Converters
import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.dao.BabyProfileDao
import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.dao.SleepRecommendationDao
import com.babytracker.data.local.entity.BabyEventEntity
import com.babytracker.data.local.entity.BabyProfileEntity
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity

@Database(
    entities = [
        BreastfeedingEntity::class,
        SleepEntity::class,
        PumpingEntity::class,
        MilkBagEntity::class,
        BabyProfileEntity::class,
        BabyEventEntity::class,
        SleepRecommendationEntity::class,
        SleepRecommendationFeedbackEntity::class,
        BottleFeedEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BabyTrackerDatabase : RoomDatabase() {
    abstract fun breastfeedingDao(): BreastfeedingDao
    abstract fun sleepDao(): SleepDao
    abstract fun pumpingDao(): PumpingDao
    abstract fun milkBagDao(): MilkBagDao
    abstract fun babyProfileDao(): BabyProfileDao
    abstract fun babyEventDao(): BabyEventDao
    abstract fun sleepRecommendationDao(): SleepRecommendationDao
    abstract fun bottleFeedDao(): BottleFeedDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE breastfeeding_sessions ADD COLUMN paused_at INTEGER DEFAULT NULL"
        )
        database.execSQL(
            "ALTER TABLE breastfeeding_sessions ADD COLUMN paused_duration_ms INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
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
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_pumping_sessions_start_time ON pumping_sessions(start_time DESC)"
        )
        database.execSQL(
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
        database.execSQL("CREATE INDEX IF NOT EXISTS index_milk_bags_used_at ON milk_bags(used_at)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_milk_bags_collection_date ON milk_bags(collection_date)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_milk_bags_source_session_id ON milk_bags(source_session_id)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Repair any pre-existing duplicate active rows before installing the
        // single-active-row invariant: keep only the newest active row (latest
        // start_time, then highest id) per table and stamp the survivor's
        // start_time as end_time on every other active row so closed rows
        // retain a plausible, non-negative duration.
        database.execSQL(
            """
            UPDATE breastfeeding_sessions
            SET end_time = (
                SELECT kept.start_time
                FROM breastfeeding_sessions AS kept
                WHERE kept.end_time IS NULL
                ORDER BY kept.start_time DESC, kept.id DESC
                LIMIT 1
            )
            WHERE end_time IS NULL
              AND id NOT IN (
                  SELECT kept.id
                  FROM breastfeeding_sessions AS kept
                  WHERE kept.end_time IS NULL
                  ORDER BY kept.start_time DESC, kept.id DESC
                  LIMIT 1
              )
            """.trimIndent()
        )
        database.execSQL(
            """
            UPDATE sleep_records
            SET end_time = (
                SELECT kept.start_time
                FROM sleep_records AS kept
                WHERE kept.end_time IS NULL
                ORDER BY kept.start_time DESC, kept.id DESC
                LIMIT 1
            )
            WHERE end_time IS NULL
              AND id NOT IN (
                  SELECT kept.id
                  FROM sleep_records AS kept
                  WHERE kept.end_time IS NULL
                  ORDER BY kept.start_time DESC, kept.id DESC
                  LIMIT 1
              )
            """.trimIndent()
        )

        // pumping_sessions active-row invariant is out of scope for this migration.
        database.installActiveSessionInvariantTriggers()
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS babies (
                id INTEGER NOT NULL,
                date_of_birth INTEGER,
                due_date INTEGER,
                due_date_user_provided INTEGER NOT NULL DEFAULT 0,
                home_timezone_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            "ALTER TABLE sleep_records ADD COLUMN timezone_id TEXT"
        )
        database.execSQL(
            """
            UPDATE sleep_records
            SET sleep_type = CASE
                WHEN sleep_type = 'Nap' THEN 'NAP'
                WHEN sleep_type = 'Night Sleep' THEN 'NIGHT_SLEEP'
                ELSE sleep_type
            END
            WHERE sleep_type IN ('Nap', 'Night Sleep')
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
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
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_baby_events_timestamp ON baby_events(timestamp)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_baby_events_event_type ON baby_events(event_type)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_recommendations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                anchor_sleep_id INTEGER NOT NULL,
                generated_at INTEGER NOT NULL,
                recommendation_type TEXT NOT NULL,
                window_start INTEGER NOT NULL,
                window_end INTEGER NOT NULL,
                best_estimate INTEGER NOT NULL,
                confidence TEXT NOT NULL,
                lifecycle TEXT NOT NULL,
                algorithm_version TEXT NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_generated_at ON sleep_recommendations(generated_at)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_anchor_sleep_id ON sleep_recommendations(anchor_sleep_id)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendations_recommendation_type ON sleep_recommendations(recommendation_type)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sleep_recommendations_anchor_sleep_id_recommendation_type_algorithm_version ON sleep_recommendations(anchor_sleep_id, recommendation_type, algorithm_version)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_recommendation_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recommendation_id INTEGER NOT NULL,
                actual_sleep_record_id INTEGER,
                error_minutes INTEGER,
                outcome TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendation_feedback_recommendation_id ON sleep_recommendation_feedback(recommendation_id)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sleep_recommendation_feedback_actual_sleep_record_id ON sleep_recommendation_feedback(actual_sleep_record_id)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sleep_recommendation_feedback_recommendation_id_outcome ON sleep_recommendation_feedback(recommendation_id, outcome)"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bottle_feeds (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                volume_ml INTEGER NOT NULL,
                type TEXT NOT NULL,
                linked_milk_bag_id INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(linked_milk_bag_id) REFERENCES milk_bags(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bottle_feeds_timestamp ON bottle_feeds(timestamp)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bottle_feeds_linked_milk_bag_id ON bottle_feeds(linked_milk_bag_id)"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE bottle_feeds ADD COLUMN client_id TEXT NOT NULL DEFAULT ''"
        )
        database.execSQL(
            "UPDATE bottle_feeds SET client_id = lower(hex(randomblob(16)))"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bottle_feeds_client_id ON bottle_feeds(client_id)"
        )
        database.execSQL(
            "ALTER TABLE bottle_feeds ADD COLUMN author TEXT NOT NULL DEFAULT 'OWNER'"
        )
    }
}

// Applied in both MIGRATION_3_4 (upgrades) and the RoomDatabase.Callback.onCreate
// (fresh installs) so the invariant is present regardless of install path.
// Triggers (not partial unique indexes) are used so Room's schema validator does not
// see unexpected index entries — Room ignores triggers during schema comparison.
fun SupportSQLiteDatabase.installActiveSessionInvariantTriggers() {
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS trg_one_active_feed
        BEFORE INSERT ON breastfeeding_sessions
        WHEN NEW.end_time IS NULL
        BEGIN
          SELECT RAISE(ABORT, 'only one active breastfeeding session allowed')
          WHERE (SELECT COUNT(*) FROM breastfeeding_sessions WHERE end_time IS NULL) > 0;
        END
        """.trimIndent()
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS trg_one_active_sleep
        BEFORE INSERT ON sleep_records
        WHEN NEW.end_time IS NULL
        BEGIN
          SELECT RAISE(ABORT, 'only one active sleep record allowed')
          WHERE (SELECT COUNT(*) FROM sleep_records WHERE end_time IS NULL) > 0;
        END
        """.trimIndent()
    )
}
