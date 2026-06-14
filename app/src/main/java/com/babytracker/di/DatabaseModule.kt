package com.babytracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.MIGRATION_1_2
import com.babytracker.data.local.MIGRATION_2_3
import com.babytracker.data.local.MIGRATION_3_4
import com.babytracker.data.local.MIGRATION_4_5
import com.babytracker.data.local.MIGRATION_5_6
import com.babytracker.data.local.MIGRATION_6_7
import com.babytracker.data.local.MIGRATION_7_8
import com.babytracker.data.local.MIGRATION_8_9
import com.babytracker.data.local.MIGRATION_9_10
import com.babytracker.data.local.MIGRATION_10_11
import com.babytracker.data.local.installActiveSessionInvariantTriggers
import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.dao.BabyProfileDao
import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.GrowthMeasurementDao
import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.dao.SleepRecommendationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BabyTrackerDatabase =
        Room.databaseBuilder(
            context,
            BabyTrackerDatabase::class.java,
            "baby_tracker_db",
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.installActiveSessionInvariantTriggers()
                }
            })
            .build()

    @Provides
    fun provideBreastfeedingDao(database: BabyTrackerDatabase): BreastfeedingDao =
        database.breastfeedingDao()

    @Provides
    fun provideSleepDao(database: BabyTrackerDatabase): SleepDao = database.sleepDao()

    @Provides
    fun providePumpingDao(database: BabyTrackerDatabase): PumpingDao = database.pumpingDao()

    @Provides
    fun provideMilkBagDao(database: BabyTrackerDatabase): MilkBagDao = database.milkBagDao()

    @Provides
    fun provideBabyProfileDao(database: BabyTrackerDatabase): BabyProfileDao = database.babyProfileDao()

    @Provides
    fun provideBabyEventDao(database: BabyTrackerDatabase): BabyEventDao = database.babyEventDao()

    @Provides
    fun provideSleepRecommendationDao(database: BabyTrackerDatabase): SleepRecommendationDao =
        database.sleepRecommendationDao()

    @Provides
    fun provideBottleFeedDao(database: BabyTrackerDatabase): BottleFeedDao = database.bottleFeedDao()

    @Provides
    fun provideGrowthMeasurementDao(database: BabyTrackerDatabase): GrowthMeasurementDao =
        database.growthMeasurementDao()

    @Provides
    fun provideMilestoneDao(database: BabyTrackerDatabase): MilestoneDao = database.milestoneDao()

    @Provides
    fun provideNowProvider(): () -> Instant = Instant::now
}
