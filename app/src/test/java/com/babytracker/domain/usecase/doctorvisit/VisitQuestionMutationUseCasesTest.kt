package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VisitQuestionMutationUseCasesTest {
    private lateinit var repository: DoctorVisitRepository

    @BeforeEach
    fun setup() { repository = mockk(relaxed = true) }

    @Test
    fun `toggle flips answered`() = runTest {
        coEvery { repository.getQuestionById(1) } returns VisitQuestion(id = 1, text = "Q", answered = false, createdAt = Instant.EPOCH)
        val captured = slot<VisitQuestion>()
        coEvery { repository.updateQuestion(capture(captured)) } returns Unit
        ToggleVisitQuestionAnsweredUseCase(repository)(1)
        assertEquals(true, captured.captured.answered)
    }

    @Test
    fun `toggle no-ops when missing`() = runTest {
        coEvery { repository.getQuestionById(9) } returns null
        ToggleVisitQuestionAnsweredUseCase(repository)(9)
        coVerify(exactly = 0) { repository.updateQuestion(any()) }
    }
}
