package com.babytracker.domain.usecase.inventory

import app.cash.turbine.test
import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventorySettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObserveInventoryWithExpirationUseCaseTest {

    private lateinit var getInventory: GetInventoryUseCase
    private lateinit var settings: InventorySettingsRepository
    private lateinit var useCase: ObserveInventoryWithExpirationUseCase

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 6, 2)
    private val days = 4

    @BeforeEach
    fun setup() {
        getInventory = mockk()
        settings = mockk()
        useCase = ObserveInventoryWithExpirationUseCase(getInventory, settings)
        every { settings.getExpirationDays() } returns flowOf(days)
    }

    private fun bagExpiringOn(expiryDate: LocalDate): MilkBag {
        val collection = expiryDate.minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        return MilkBag(id = 1, collectionDate = collection, volumeMl = 100, createdAt = Instant.now())
    }

    @Test
    fun `feature disabled maps every bag to NONE`() = runTest {
        every { getInventory() } returns flowOf(
            listOf(bagExpiringOn(today.minusDays(10)), bagExpiringOn(today)),
        )
        every { settings.getExpirationEnabled() } returns flowOf(false)

        useCase(flowOf(today)).test {
            val result = awaitItem()
            assertEquals(listOf(ExpirationStatus.NONE, ExpirationStatus.NONE), result.map { it.status })
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring today is EXPIRING_OR_EXPIRED`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today)))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expired yesterday is EXPIRING_OR_EXPIRED`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.minusDays(1))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring tomorrow is EXPIRING_SOON`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.plusDays(1))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_SOON, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring in two days is NONE`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.plusDays(2))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.NONE, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `empty bag list emits empty list`() = runTest {
        every { getInventory() } returns flowOf(emptyList())
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `recomputes when date flow emits a new day`() = runTest {
        val expiry = today
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(expiry)))
        every { settings.getExpirationEnabled() } returns flowOf(true)
        val dateFlow = MutableStateFlow(expiry.minusDays(1))

        useCase(dateFlow).test {
            assertEquals(ExpirationStatus.EXPIRING_SOON, awaitItem().first().status)
            dateFlow.value = expiry
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
        }
    }
}
