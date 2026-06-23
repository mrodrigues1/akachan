package com.babytracker.domain.model

import com.babytracker.sharing.domain.model.AppMode

/**
 * Snapshot of the preference values a widget render needs, read from the shared DataStore in a
 * single pass. Collapses the previously separate `getAppMode()`/`getShareCode()`/
 * `getEnabledFeatures()`/`getVolumeUnit()` flow collections (each a full Preferences read) into one.
 */
data class WidgetPreferences(
    val appMode: AppMode,
    val shareCode: String?,
    val enabledFeatures: Set<AppFeature>,
    val volumeUnit: VolumeUnit,
)
