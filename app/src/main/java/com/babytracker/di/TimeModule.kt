package com.babytracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    // Deliberately unscoped: a @Singleton ZoneId freezes the zone at process start, so a
    // Provider<ZoneId> could never observe a device timezone change (AKACHAN-306).
    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()
}
