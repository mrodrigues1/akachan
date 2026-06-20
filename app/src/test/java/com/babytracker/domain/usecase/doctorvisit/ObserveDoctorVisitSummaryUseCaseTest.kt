package com.babytracker.domain.usecase.doctorvisit

import app.cash.turbine.test
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveDoctorVisitSummaryUseCaseTest {
    @Test
    fun `computes next upcoming, last past, open question count`() = runTest {
        val now = Instant.ofEpochMilli(1_000)
        val repository = mockk<DoctorVisitRepository>()
        every { repository.observeAllVisits() } returns flowOf(
            listOf(
                DoctorVisit(id = 1, date = Instant.ofEpochMilli(500), createdAt = now), // past
                DoctorVisit(id = 2, date = Instant.ofEpochMilli(900), createdAt = now), // past (more recent)
                DoctorVisit(id = 3, date = Instant.ofEpochMilli(5_000), createdAt = now), // upcoming (soonest)
                DoctorVisit(id = 4, date = Instant.ofEpochMilli(9_000), createdAt = now), // upcoming
            ),
        )
        every { repository.observeInboxQuestions() } returns flowOf(
            listOf(
                VisitQuestion(id = 1, text = "a", answered = false, createdAt = now),
                VisitQuestion(id = 2, text = "b", answered = true, createdAt = now),
            ),
        )
        val useCase = ObserveDoctorVisitSummaryUseCase(repository) { now }
        useCase().test {
            val s = awaitItem()
            assertEquals(3L, s.nextUpcoming?.id)
            assertEquals(2L, s.lastPast?.id)
            assertEquals(1, s.openQuestionCount)
            awaitComplete()
        }
    }
}
