package com.babytracker.di

import android.content.Context
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NotificationScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object NotificationSchedulerModule {

    @Provides
    @ViewModelScoped
    fun provideNotificationScheduler(
        @ApplicationContext context: Context
    ): NotificationScheduler {
        return BreastfeedingNotificationManager(context)
    }
}
