package com.babytracker.di

import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.dao.BabyProfileDao
import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.dao.DoctorVisitDao
import com.babytracker.data.local.dao.GrowthMeasurementDao
import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.dao.SleepRecommendationDao
import com.babytracker.data.local.dao.VaccineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

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
    fun provideDiaperDao(database: BabyTrackerDatabase): DiaperDao = database.diaperDao()

    @Provides
    fun provideVaccineDao(database: BabyTrackerDatabase): VaccineDao = database.vaccineDao()

    @Provides
    fun provideDoctorVisitDao(database: BabyTrackerDatabase): DoctorVisitDao = database.doctorVisitDao()
}
