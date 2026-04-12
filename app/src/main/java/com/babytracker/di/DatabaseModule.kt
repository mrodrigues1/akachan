package com.babytracker.di

import android.content.Context
import androidx.room.Room
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.MIGRATION_1_2
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.SleepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            "baby_tracker_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideBreastfeedingDao(database: BabyTrackerDatabase): BreastfeedingDao =
        database.breastfeedingDao()

    @Provides
    fun provideSleepDao(database: BabyTrackerDatabase): SleepDao =
        database.sleepDao()
}
