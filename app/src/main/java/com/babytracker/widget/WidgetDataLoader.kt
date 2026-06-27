package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
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
        settings = entryPoint.settingsRepository(),
        partnerCache = entryPoint.partnerWidgetCache(),
        scheduler = entryPoint.widgetRefreshScheduler(),
        baby = entryPoint.babyRepository(),
        feed = entryPoint.breastfeedingRepository(),
        sleep = entryPoint.sleepRepository(),
    )
}

internal suspend fun loadWidgetData(
    settings: SettingsRepository,
    partnerCache: PartnerWidgetCacheImpl,
    scheduler: WidgetRefreshScheduler,
    baby: BabyRepository,
    feed: BreastfeedingRepository,
    sleep: SleepRepository,
): WidgetData = runCatching {
    // One Preferences read covers app mode, share code, and enabled features for this render.
    val prefs = settings.getWidgetPreferences()
    if (prefs.appMode == AppMode.PARTNER) {
        // PARTNER: never touch local repos or the network. Read the worker-populated cache; on a
        // miss, kick off a one-time refresh (no network on this thread) and render EMPTY for now.
        val shareCode = prefs.shareCode ?: return@runCatching WidgetData.EMPTY
        partnerCache.read(shareCode) ?: run {
            scheduler.scheduleImmediateRefresh()
            WidgetData.EMPTY
        }
    } else {
        val features = prefs.enabledFeatures
        val babyProfile = baby.getBabyProfile().first()
        val lastFeed = feed.getLastSession()
        val latestSleep = sleep.getLatestRecord()
        toWidgetData(
            babyName = babyProfile?.name,
            lastFeed = lastFeed,
            latestSleep = latestSleep,
            feedEnabled = AppFeature.BREASTFEEDING in features,
            sleepEnabled = AppFeature.SLEEP in features,
        )
    }
}.getOrElse { error ->
    if (error is CancellationException) throw error
    Log.w(TAG, "Widget data load failed; rendering EMPTY", error)
    WidgetData.EMPTY
}
