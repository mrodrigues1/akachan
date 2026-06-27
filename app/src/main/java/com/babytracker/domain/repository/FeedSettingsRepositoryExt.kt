package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the feed-owned max-total-feed limit with the globally-owned rich-notifications toggle.
 * Rich notifications stay on [SettingsRepository] (app-wide), so it is passed in here.
 */
fun FeedSettingsRepository.getBreastfeedingActiveNotificationSettings(
    settingsRepository: SettingsRepository,
): Flow<BreastfeedingActiveNotificationSettings> =
    combine(
        getMaxTotalFeedMinutes(),
        settingsRepository.getRichNotificationsEnabled(),
    ) { maxTotalFeedMinutes, richNotificationsEnabled ->
        BreastfeedingActiveNotificationSettings(
            maxTotalFeedMinutes = maxTotalFeedMinutes,
            richNotificationsEnabled = richNotificationsEnabled,
        )
    }
