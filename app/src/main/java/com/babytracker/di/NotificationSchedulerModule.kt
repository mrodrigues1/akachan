package com.babytracker.di

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.manager.NapReminderManager
import com.babytracker.manager.NoOpDoctorVisitReminderScheduler
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.manager.NotificationPermissionCheckerImpl
import com.babytracker.manager.NotificationScheduler
import com.babytracker.manager.PartnerFeedNotificationManager
import com.babytracker.manager.PartnerFeedNotifier
import com.babytracker.manager.PredictiveFeedScheduler
import com.babytracker.manager.PredictiveFeedSchedulerImpl
import com.babytracker.manager.PredictiveSleepScheduler
import com.babytracker.manager.PredictiveSleepSchedulerImpl
import com.babytracker.manager.SleepNotificationManager
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.manager.VaccineReminderManager
import com.babytracker.manager.VaccineReminderScheduler
import dagger.Binds
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

    @Provides
    @Singleton
    fun providePredictiveFeedScheduler(
        @ApplicationContext context: Context
    ): PredictiveFeedScheduler = PredictiveFeedSchedulerImpl(context)

    @Provides
    @Singleton
    fun providePredictiveSleepScheduler(
        @ApplicationContext context: Context,
    ): PredictiveSleepScheduler = PredictiveSleepSchedulerImpl(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPermissionCheckerModule {

    @Binds
    @Singleton
    abstract fun bindNotificationPermissionChecker(
        impl: NotificationPermissionCheckerImpl
    ): NotificationPermissionChecker
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NapReminderModule {

    @Binds
    @Singleton
    abstract fun bindNapReminderScheduler(impl: NapReminderManager): NapReminderScheduler
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PartnerFeedNotifierModule {

    @Binds
    @Singleton
    abstract fun bindPartnerFeedNotifier(impl: PartnerFeedNotificationManager): PartnerFeedNotifier
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaccineReminderSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindVaccineReminderScheduler(impl: VaccineReminderManager): VaccineReminderScheduler
}

// Plan 7 replaces NoOpDoctorVisitReminderScheduler with the real DoctorVisitReminderManager here.
@Module
@InstallIn(SingletonComponent::class)
abstract class DoctorVisitReminderSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindDoctorVisitReminderScheduler(
        impl: NoOpDoctorVisitReminderScheduler,
    ): DoctorVisitReminderScheduler
}
