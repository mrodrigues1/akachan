package com.babytracker.di

import com.babytracker.data.repository.FeedSettingsRepositoryImpl
import com.babytracker.domain.repository.FeedSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedSettingsModule {

    @Binds
    @Singleton
    abstract fun bindFeedSettingsRepository(
        impl: FeedSettingsRepositoryImpl,
    ): FeedSettingsRepository
}
