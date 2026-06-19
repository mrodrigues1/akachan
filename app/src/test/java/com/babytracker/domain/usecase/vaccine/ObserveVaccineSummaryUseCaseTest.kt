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

class ObserveVaccineSummaryUseCaseTest {
    private val now = Instant.ofEpochMilli(10_000)

    @Test
    fun `picks soonest future and counts overdue and administered`() = runTest {
        val observe = mockk<ObserveVaccineRecordsUseCase>()
        every { observe() } returns flowOf(
            listOf(
                VaccineRecord(
                    id = 1,
                    name = "Far",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = Instant.ofEpochMilli(30_000),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 2,
                    name = "Soon",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = Instant.ofEpochMilli(20_000),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 3,
                    name = "Overdue",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = Instant.ofEpochMilli(5_000),
                    createdAt = now,
                ),
                VaccineRecord(
                    id = 4,
                    name = "Done",
                    status = VaccineStatus.ADMINISTERED,
                    administeredDate = Instant.ofEpochMilli(1_000),
                    createdAt = now,
                ),
            ),
        )
        ObserveVaccineSummaryUseCase(observe) { now }().test {
            val s = awaitItem()
            assertEquals("Soon", s.nextUpcoming?.name)
            assertEquals(2, s.upcomingCount)
            assertEquals(1, s.overdueCount)
            assertEquals(1, s.administeredCount)
            assertEquals("Done", s.lastAdministered?.name)
            awaitComplete()
        }
    }
}
