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
import com.babytracker.data.local.MIGRATION_11_12
import com.babytracker.data.local.MIGRATION_12_13
import com.babytracker.data.local.MIGRATION_13_14
import com.babytracker.data.local.MIGRATION_14_15
import com.babytracker.data.local.MIGRATION_15_16
import com.babytracker.data.local.installActiveSessionInvariantTriggers
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
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.installActiveSessionInvariantTriggers()
                }
            })
            .build()

    @Provides
    fun provideNowProvider(): () -> Instant = Instant::now
}
