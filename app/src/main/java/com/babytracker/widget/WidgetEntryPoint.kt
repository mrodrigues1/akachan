package com.babytracker.widget

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun babyRepository(): BabyRepository
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
    fun settingsRepository(): SettingsRepository
    fun partnerWidgetCache(): PartnerWidgetCacheImpl
    fun widgetRefreshScheduler(): WidgetRefreshScheduler
}
