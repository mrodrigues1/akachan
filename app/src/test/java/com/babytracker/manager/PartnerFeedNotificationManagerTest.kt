package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PartnerFeedNotificationManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var manager: PartnerFeedNotificationManager

    @BeforeEach
    fun setup() {
        inventoryRepository = mockk()
        settingsRepository = mockk()
        manager = PartnerFeedNotificationManager(context, inventoryRepository, settingsRepository)
        mockkObject(NotificationHelper)
        every { NotificationHelper.showPartnerStashConsumed(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun `posts coalesced notification with bag count and summed volume`() = runTest {
        every { settingsRepository.getPartnerFeedStashNotificationsEnabled() } returns flowOf(true)
        coEvery { inventoryRepository.sumVolumeForIds(listOf(11L, 22L)) } returns 450

        manager.notifyStashConsumed(listOf(11L, 22L))

        coVerify(exactly = 1) { NotificationHelper.showPartnerStashConsumed(context, 2, 450) }
    }

    @Test
    fun `does nothing when the toggle is off`() = runTest {
        every { settingsRepository.getPartnerFeedStashNotificationsEnabled() } returns flowOf(false)

        manager.notifyStashConsumed(listOf(11L))

        coVerify(exactly = 0) { inventoryRepository.sumVolumeForIds(any()) }
        coVerify(exactly = 0) { NotificationHelper.showPartnerStashConsumed(any(), any(), any()) }
    }

    @Test
    fun `empty list is a no-op without reading the toggle`() = runTest {
        manager.notifyStashConsumed(emptyList())

        coVerify(exactly = 0) { settingsRepository.getPartnerFeedStashNotificationsEnabled() }
        coVerify(exactly = 0) { NotificationHelper.showPartnerStashConsumed(any(), any(), any()) }
    }

    @Test
    fun `does not post when consumed bags resolve to zero volume`() = runTest {
        every { settingsRepository.getPartnerFeedStashNotificationsEnabled() } returns flowOf(true)
        coEvery { inventoryRepository.sumVolumeForIds(any()) } returns 0

        manager.notifyStashConsumed(listOf(11L))

        coVerify(exactly = 0) { NotificationHelper.showPartnerStashConsumed(any(), any(), any()) }
    }
}
