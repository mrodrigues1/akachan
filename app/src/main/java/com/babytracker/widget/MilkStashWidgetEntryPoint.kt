package com.babytracker.widget

import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MilkStashWidgetEntryPoint {
    fun inventoryRepository(): InventoryRepository
    fun settingsRepository(): SettingsRepository
}
