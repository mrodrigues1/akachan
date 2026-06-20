package com.babytracker.receiver

import android.content.Context
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.StashNotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StashExpirationReceiverTest {

    private lateinit var milkBagDao: MilkBagDao
    private lateinit var inventorySettings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler
    private lateinit var receiver: StashExpirationReceiver

    private val context = mockk<Context>(relaxed = true)
    private val zone = ZoneId.systemDefault()

    @BeforeEach
    fun setup() {
        milkBagDao = mockk()
        inventorySettings = mockk()
        scheduler = mockk(relaxed = true)
        receiver = StashExpirationReceiver().apply {
            this.milkBagDao = this@StashExpirationReceiverTest.milkBagDao
            this.inventorySettings = this@StashExpirationReceiverTest.inventorySettings
            this.scheduler = this@StashExpirationReceiverTest.scheduler
        }
        mockkObject(StashNotificationHelper)
        every { StashNotificationHelper.showStashExpiration(any(), any(), any()) } returns Unit
        every { inventorySettings.getExpirationNotifTimeMinutes() } returns flowOf(NOTIF_TIME_MINUTES)
        every { inventorySettings.getExpirationDays() } returns flowOf(EXPIRATION_DAYS)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(StashNotificationHelper)
    }

    @Test
    fun `posts notification for bags expiring exactly today and re-arms`() = runTest {
        val today = LocalDate.now(zone)
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { milkBagDao.getAllBagsOnce() } returns listOf(
            bagExpiringOn(today, volumeMl = 100),
            bagExpiringOn(today, volumeMl = 50),
            bagExpiringOn(today.plusDays(1), volumeMl = 999),
            bagExpiringOn(today.minusDays(1), volumeMl = 999),
            bagExpiringOn(today, volumeMl = 999, usedAt = 1L),
        )

        receiver.handle(context)

        verify(exactly = 1) { StashNotificationHelper.showStashExpiration(context, 2, 150) }
        verify(exactly = 1) { scheduler.scheduleDaily(NOTIF_TIME_MINUTES) }
    }

    @Test
    fun `does not post when no bag expires today but still re-arms`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { milkBagDao.getAllBagsOnce() } returns listOf(
            bagExpiringOn(LocalDate.now(zone).plusDays(2), volumeMl = 100),
        )

        receiver.handle(context)

        verify(exactly = 0) { StashNotificationHelper.showStashExpiration(any(), any(), any()) }
        verify(exactly = 1) { scheduler.scheduleDaily(NOTIF_TIME_MINUTES) }
    }

    @Test
    fun `bails and does not re-arm when master toggle disabled`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(false)

        receiver.handle(context)

        coVerify(exactly = 0) { milkBagDao.getAllBagsOnce() }
        verify(exactly = 0) { inventorySettings.getExpirationNotifEnabled() }
        verify(exactly = 0) { StashNotificationHelper.showStashExpiration(any(), any(), any()) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `bails and does not re-arm when notification toggle disabled`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(false)

        receiver.handle(context)

        coVerify(exactly = 0) { milkBagDao.getAllBagsOnce() }
        verify(exactly = 0) { inventorySettings.getExpirationNotifTimeMinutes() }
        verify(exactly = 0) { StashNotificationHelper.showStashExpiration(any(), any(), any()) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `re-arms even when bag query throws`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { milkBagDao.getAllBagsOnce() } throws RuntimeException("db error")

        receiver.handle(context)

        verify(exactly = 1) { scheduler.scheduleDaily(NOTIF_TIME_MINUTES) }
        verify(exactly = 0) { StashNotificationHelper.showStashExpiration(any(), any(), any()) }
    }

    @Test
    fun `re-arms with default time when toggle read throws`() = runTest {
        every { inventorySettings.getExpirationEnabled() } throws RuntimeException("datastore error")

        receiver.handle(context)

        verify(exactly = 1) { scheduler.scheduleDaily(DEFAULT_NOTIF_TIME_MINUTES) }
        verify(exactly = 0) { StashNotificationHelper.showStashExpiration(any(), any(), any()) }
    }

    @Test
    fun `re-arms with default time when notification time read throws`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifTimeMinutes() } throws RuntimeException("datastore error")

        receiver.handle(context)

        verify(exactly = 1) { scheduler.scheduleDaily(DEFAULT_NOTIF_TIME_MINUTES) }
        coVerify(exactly = 0) { milkBagDao.getAllBagsOnce() }
    }

    @Test
    fun `disabled master toggle short-circuits before later read can throw`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(false)
        every { inventorySettings.getExpirationNotifEnabled() } throws RuntimeException("must not be read")

        receiver.handle(context)

        verify(exactly = 0) { inventorySettings.getExpirationNotifEnabled() }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `disabled notification toggle short-circuits before notification time read can throw`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(false)
        every { inventorySettings.getExpirationNotifTimeMinutes() } throws RuntimeException("must not be read")

        receiver.handle(context)

        verify(exactly = 0) { inventorySettings.getExpirationNotifTimeMinutes() }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `does not throw when scheduler re-arm fails`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { milkBagDao.getAllBagsOnce() } returns listOf(
            bagExpiringOn(LocalDate.now(zone), volumeMl = 100),
        )
        every { scheduler.scheduleDaily(any()) } throws RuntimeException("AlarmManager failure")

        receiver.handle(context)

        verify(exactly = 1) { scheduler.scheduleDaily(NOTIF_TIME_MINUTES) }
        verify(exactly = 1) { StashNotificationHelper.showStashExpiration(context, 1, 100) }
    }

    @Test
    fun `re-arms when notification post is denied`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        coEvery { milkBagDao.getAllBagsOnce() } returns listOf(
            bagExpiringOn(LocalDate.now(zone), volumeMl = 100),
        )
        every {
            StashNotificationHelper.showStashExpiration(any(), any(), any())
        } throws SecurityException("POST_NOTIFICATIONS denied")

        receiver.handle(context)

        verify(exactly = 1) { StashNotificationHelper.showStashExpiration(context, 1, 100) }
        verify(exactly = 1) { scheduler.scheduleDaily(NOTIF_TIME_MINUTES) }
    }

    private fun bagExpiringOn(
        expiryDate: LocalDate,
        volumeMl: Int,
        usedAt: Long? = null,
    ): MilkBagEntity {
        val collectionDate = expiryDate.minusDays(EXPIRATION_DAYS.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return MilkBagEntity(
            collectionDate = collectionDate,
            volumeMl = volumeMl,
            usedAt = usedAt,
            createdAt = Instant.now().toEpochMilli(),
        )
    }

    private companion object {
        const val EXPIRATION_DAYS = 4
        const val NOTIF_TIME_MINUTES = 540
        const val DEFAULT_NOTIF_TIME_MINUTES = 480
    }
}
