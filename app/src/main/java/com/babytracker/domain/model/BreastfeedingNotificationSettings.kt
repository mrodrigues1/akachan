package com.babytracker.domain.model

data class BreastfeedingActiveNotificationSettings(
    val maxTotalFeedMinutes: Int,
    val richNotificationsEnabled: Boolean
)

data class BreastfeedingNotificationScheduleSettings(
    val maxPerBreastMinutes: Int,
    val maxTotalFeedMinutes: Int
)
