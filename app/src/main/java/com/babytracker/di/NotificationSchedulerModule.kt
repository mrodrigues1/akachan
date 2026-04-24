package com.babytracker.di

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NotificationScheduler
import com.babytracker.manager.SleepNotificationManager
import com.babytracker.manager.SleepNotificationScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationSchedulerModule {

    @Provides
    @Singleton
    fun provideNotificationScheduler(
        @ApplicationContext context: Context
    ): NotificationScheduler = BreastfeedingNotificationManager(context)

    @Provides
    @Singleton
    fun provideSleepNotificationScheduler(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): SleepNotificationScheduler = SleepNotificationManager(context, settingsRepository)
}
