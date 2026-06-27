package com.babytracker.di

import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.DoctorVisitReminderManager
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.manager.NapReminderManager
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.manager.NotificationPermissionCheckerImpl
import com.babytracker.manager.NotificationScheduler
import com.babytracker.manager.PartnerFeedNotificationManager
import com.babytracker.manager.PartnerFeedNotifier
import com.babytracker.manager.PartnerSleepNotificationManager
import com.babytracker.manager.PartnerSleepNotifier
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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindNotificationScheduler(impl: BreastfeedingNotificationManager): NotificationScheduler

    @Binds
    @Singleton
    abstract fun bindSleepNotificationScheduler(impl: SleepNotificationManager): SleepNotificationScheduler

    @Binds
    @Singleton
    abstract fun bindPredictiveFeedScheduler(impl: PredictiveFeedSchedulerImpl): PredictiveFeedScheduler

    @Binds
    @Singleton
    abstract fun bindPredictiveSleepScheduler(impl: PredictiveSleepSchedulerImpl): PredictiveSleepScheduler
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

    @Binds
    @Singleton
    abstract fun bindPartnerSleepNotifier(impl: PartnerSleepNotificationManager): PartnerSleepNotifier
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaccineReminderSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindVaccineReminderScheduler(impl: VaccineReminderManager): VaccineReminderScheduler
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DoctorVisitReminderSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindDoctorVisitReminderScheduler(
        impl: DoctorVisitReminderManager,
    ): DoctorVisitReminderScheduler
}
