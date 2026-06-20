package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AddVisitQuestionUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var useCase: AddVisitQuestionUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = AddVisitQuestionUseCase(repository)
    }

    @Test
    fun `trims and inserts inbox question`() = runTest {
        val captured = slot<VisitQuestion>()
        coEvery { repository.insertQuestion(capture(captured)) } returns 5
        val id = useCase("  Is fever normal?  ", now = Instant.ofEpochMilli(100))
        assertEquals(5, id)
        assertEquals("Is fever normal?", captured.captured.text)
        assertEquals(null, captured.captured.visitId)
    }

    @Test
    fun `blank text throws`() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { useCase("   ") } }
    }
}
