package com.babytracker.di

import com.babytracker.data.repository.BabyEventRepositoryImpl
import com.babytracker.data.repository.BabyProfileRepositoryImpl
import com.babytracker.data.repository.BabyRepositoryImpl
import com.babytracker.data.repository.BreastfeedingRepositoryImpl
import com.babytracker.data.repository.InventoryRepositoryImpl
import com.babytracker.data.repository.PumpingRepositoryImpl
import com.babytracker.data.repository.SleepRecommendationRepositoryImpl
import com.babytracker.data.repository.SettingsRepositoryImpl
import com.babytracker.data.repository.SleepRepositoryImpl
import com.babytracker.domain.repository.BabyEventRepository
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBabyEventRepository(impl: BabyEventRepositoryImpl): BabyEventRepository

    @Binds
    @Singleton
    abstract fun bindBreastfeedingRepository(impl: BreastfeedingRepositoryImpl): BreastfeedingRepository

    @Binds
    @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository

    @Binds
    @Singleton
    abstract fun bindBabyRepository(impl: BabyRepositoryImpl): BabyRepository

    @Binds
    @Singleton
    abstract fun bindBabyProfileRepository(impl: BabyProfileRepositoryImpl): BabyProfileRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPumpingRepository(impl: PumpingRepositoryImpl): PumpingRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindSleepRecommendationRepository(
        impl: SleepRecommendationRepositoryImpl,
    ): SleepRecommendationRepository
}
