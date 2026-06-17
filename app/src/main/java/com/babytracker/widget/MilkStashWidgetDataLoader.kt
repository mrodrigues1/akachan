package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "MilkStashWidget"

data class MilkStashWidgetData(
    val totalMl: Int,
    val bagCount: Int,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val inventoryEnabled: Boolean = true,
) {
    companion object {
        val EMPTY = MilkStashWidgetData(totalMl = 0, bagCount = 0)

        /** Rendered when the Inventory feature is turned off (visibility-only; data is untouched). */
        val DISABLED = MilkStashWidgetData(totalMl = 0, bagCount = 0, inventoryEnabled = false)
    }
}

suspend fun loadMilkStashWidgetData(context: Context): MilkStashWidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        MilkStashWidgetEntryPoint::class.java,
    )
    return loadMilkStashWidgetData(
        entryPoint.inventoryRepository(),
        entryPoint.settingsRepository(),
        entryPoint.featureToggleRepository(),
    )
}

internal suspend fun loadMilkStashWidgetData(
    inventoryRepository: InventoryRepository,
    settingsRepository: SettingsRepository,
    featureToggle: FeatureToggleRepository,
): MilkStashWidgetData = runCatching {
    if (AppFeature.INVENTORY !in featureToggle.getEnabledFeatures().first()) {
        return@runCatching MilkStashWidgetData.DISABLED
    }
    val summary = inventoryRepository.currentSummary()
    val unit = settingsRepository.getVolumeUnit().first()
    MilkStashWidgetData(totalMl = summary.totalMl, bagCount = summary.bagCount, volumeUnit = unit)
}.getOrElse { error ->
    if (error is CancellationException) throw error
    Log.w(TAG, "Milk stash widget data load failed; rendering EMPTY", error)
    MilkStashWidgetData.EMPTY
}
