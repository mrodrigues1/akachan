package com.babytracker.di

import com.babytracker.widget.GlanceWidgetUpdater
import com.babytracker.widget.PartnerWidgetCache
import com.babytracker.widget.PartnerWidgetCacheImpl
import com.babytracker.widget.WidgetUpdater
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
    abstract fun bindPartnerWidgetCache(impl: PartnerWidgetCacheImpl): PartnerWidgetCache
}
