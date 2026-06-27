package com.babytracker.di

import com.babytracker.widget.GlanceMilkStashWidgetUpdater
import com.babytracker.widget.GlanceWidgetUpdater
import com.babytracker.widget.MilkStashWidgetUpdater
import com.babytracker.widget.WidgetRefreshScheduler
import com.babytracker.widget.WidgetUpdater
import com.babytracker.widget.WorkManagerWidgetRefreshScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    @Binds
    @Singleton
    abstract fun bindMilkStashWidgetUpdater(impl: GlanceMilkStashWidgetUpdater): MilkStashWidgetUpdater

    @Binds
    @Singleton
    abstract fun bindWidgetRefreshScheduler(impl: WorkManagerWidgetRefreshScheduler): WidgetRefreshScheduler
}
