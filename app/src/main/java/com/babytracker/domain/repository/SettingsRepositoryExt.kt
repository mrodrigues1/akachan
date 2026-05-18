package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import com.babytracker.domain.model.BreastfeedingNotificationScheduleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun SettingsRepository.getBreastfeedingActiveNotificationSettings(): Flow<BreastfeedingActiveNotificationSettings> =
    combine(
        getMaxTotalFeedMinutes(),
        getRichNotificationsEnabled()
    ) { maxTotalFeedMinutes, richNotificationsEnabled ->
        BreastfeedingActiveNotificationSettings(
            maxTotalFeedMinutes = maxTotalFeedMinutes,
            richNotificationsEnabled = richNotificationsEnabled
        )
    }

fun SettingsRepository.getBreastfeedingNotificationScheduleSettings(): Flow<BreastfeedingNotificationScheduleSettings> =
    combine(
        getMaxPerBreastMinutes(),
        getMaxTotalFeedMinutes()
    ) { maxPerBreastMinutes, maxTotalFeedMinutes ->
        BreastfeedingNotificationScheduleSettings(
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalFeedMinutes = maxTotalFeedMinutes
        )
    }
