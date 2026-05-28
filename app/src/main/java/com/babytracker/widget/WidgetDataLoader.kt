package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.data.toWidgetData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "BabyWidget"

suspend fun loadWidgetData(context: Context): WidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    return loadWidgetData(
        baby = entryPoint.babyRepository(),
        feed = entryPoint.breastfeedingRepository(),
        sleep = entryPoint.sleepRepository(),
    )
}

internal suspend fun loadWidgetData(
    baby: BabyRepository,
    feed: BreastfeedingRepository,
    sleep: SleepRepository,
): WidgetData = runCatching {
    val babyProfile = baby.getBabyProfile().first()
    val lastFeed = feed.getLastSession()
    val latestSleep = sleep.getLatestRecord()

    toWidgetData(
        babyName = babyProfile?.name,
        lastFeed = lastFeed,
        latestSleep = latestSleep,
    )
}.getOrElse { error ->
    if (error is CancellationException) throw error
    Log.w(TAG, "Widget data load failed; rendering EMPTY", error)
    WidgetData.EMPTY
}
