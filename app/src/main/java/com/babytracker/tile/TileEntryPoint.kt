package com.babytracker.tile

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileEntryPoint {
    fun babyRepository(): BabyRepository
    fun settingsRepository(): SettingsRepository
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
    fun tileToggleHandler(): TileToggleHandler
    fun clock(): Clock
}
