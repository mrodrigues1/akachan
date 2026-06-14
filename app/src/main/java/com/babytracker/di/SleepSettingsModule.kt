package com.babytracker.di

import com.babytracker.data.repository.SleepSettingsRepositoryImpl
import com.babytracker.domain.repository.SleepSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepSettingsModule {

    @Binds
    @Singleton
    abstract fun bindSleepSettingsRepository(
        impl: SleepSettingsRepositoryImpl,
    ): SleepSettingsRepository

    // NapReminderScheduler / SleepNotificationScheduler are already bound in
    // NotificationSchedulerModule — not duplicated here.
}
