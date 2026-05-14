package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import com.babytracker.domain.model.BreastfeedingNotificationScheduleSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRepositoryExtTest {

    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(15)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(30)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)
    }

    @Test
    fun `getBreastfeedingActiveNotificationSettings combines active notification values`() = runTest {
        val settings = settingsRepository.getBreastfeedingActiveNotificationSettings().first()

        assertEquals(
            BreastfeedingActiveNotificationSettings(
                maxTotalFeedMinutes = 30,
                richNotificationsEnabled = false
            ),
            settings
        )
    }

    @Test
    fun `getBreastfeedingNotificationScheduleSettings combines schedule values`() = runTest {
        val settings = settingsRepository.getBreastfeedingNotificationScheduleSettings().first()

        assertEquals(
            BreastfeedingNotificationScheduleSettings(
                maxPerBreastMinutes = 15,
                maxTotalFeedMinutes = 30
            ),
            settings
        )
    }
}
