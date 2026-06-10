package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.repository.InventoryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

private const val TAG = "MilkStashWidget"

data class MilkStashWidgetData(
    val totalMl: Int,
    val bagCount: Int,
) {
    companion object {
        val EMPTY = MilkStashWidgetData(totalMl = 0, bagCount = 0)
    }
}

suspend fun loadMilkStashWidgetData(context: Context): MilkStashWidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        MilkStashWidgetEntryPoint::class.java,
    )
    return loadMilkStashWidgetData(entryPoint.inventoryRepository())
}

internal suspend fun loadMilkStashWidgetData(
    inventoryRepository: InventoryRepository,
): MilkStashWidgetData = runCatching {
    val summary = inventoryRepository.currentSummary()
    MilkStashWidgetData(totalMl = summary.totalMl, bagCount = summary.bagCount)
}.getOrElse { error ->
    if (error is CancellationException) throw error
    Log.w(TAG, "Milk stash widget data load failed; rendering EMPTY", error)
    MilkStashWidgetData.EMPTY
}
