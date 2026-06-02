package com.babytracker.di

import com.babytracker.data.repository.InventorySettingsRepositoryImpl
import com.babytracker.domain.repository.InventorySettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventorySettingsModule {

    @Binds
    @Singleton
    abstract fun bindInventorySettingsRepository(
        impl: InventorySettingsRepositoryImpl,
    ): InventorySettingsRepository
}
