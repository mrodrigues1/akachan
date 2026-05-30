package com.babytracker.di

import android.content.Context
import androidx.room.Room
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.MIGRATION_1_2
import com.babytracker.data.local.MIGRATION_2_3
import com.babytracker.data.local.MIGRATION_3_4
import com.babytracker.data.local.installActiveSessionInvariantTriggers
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
    fun provideNowProvider(): () -> Instant = Instant::now
}
