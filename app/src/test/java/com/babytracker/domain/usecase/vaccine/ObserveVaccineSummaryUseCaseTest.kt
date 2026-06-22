package com.babytracker.domain.usecase.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ObserveVaccineSummaryUseCaseTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-21T12:00:00Z")
    private fun days(offset: Long) = now.plusSeconds(offset * 86_400)

    @Test
    fun `picks soonest future and counts overdue and administered`() = runTest {
        val observe = mockk<ObserveVaccineRecordsUseCase>()
        every { observe() } returns flowOf(
            listOf(
                VaccineRecord(
                    id = 1,
                    name = "Far",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = days(30),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 2,
                    name = "Soon",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = days(5),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 3,
                    name = "Overdue",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = days(-3),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 4,
                    name = "Done",
                    status = VaccineStatus.ADMINISTERED,
                    administeredDate = days(-2),
                    createdAt = now,
                ),
            ),
        )
        ObserveVaccineSummaryUseCase(observe, zone) { now }().test {
            val s = awaitItem()
            assertEquals("Soon", s.nextUpcoming?.name)
            assertEquals(2, s.upcomingCount)
            assertEquals(1, s.overdueCount)
            assertEquals(1, s.administeredCount)
            assertEquals("Done", s.lastAdministered?.name)
            awaitComplete()
        }
    }

    @Test
    fun `dose scheduled earlier today is upcoming, not overdue`() = runTest {
        val observe = mockk<ObserveVaccineRecordsUseCase>()
        every { observe() } returns flowOf(
            listOf(
                VaccineRecord(
                    id = 1,
                    name = "Hib",
                    status = VaccineStatus.SCHEDULED,
                    // Earlier the same calendar day — must not register as overdue on the tile.
                    scheduledDate = now.minusSeconds(3_600),
                    createdAt = now,
                ),
            ),
        )
        ObserveVaccineSummaryUseCase(observe, zone) { now }().test {
            val s = awaitItem()
            assertEquals(0, s.overdueCount)
            assertEquals(1, s.upcomingCount)
            assertEquals("Hib", s.nextUpcoming?.name)
            awaitComplete()
        }
    }
}
