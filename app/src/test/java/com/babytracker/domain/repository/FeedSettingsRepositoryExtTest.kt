package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeedSettingsRepositoryExtTest {

    private lateinit var feedSettingsRepository: FeedSettingsRepository
    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setup() {
        feedSettingsRepository = mockk()
        settingsRepository = mockk()
        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns flowOf(15)
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flowOf(30)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)
    }

    @Test
    fun `getBreastfeedingActiveNotificationSettings combines active notification values`() = runTest {
        val settings = feedSettingsRepository
            .getBreastfeedingActiveNotificationSettings(settingsRepository)
            .first()

        assertEquals(
            BreastfeedingActiveNotificationSettings(
                maxTotalFeedMinutes = 30,
                richNotificationsEnabled = false
            ),
            settings
        )
    }
}
