package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class SaveBabyProfileUseCaseTest {

    private lateinit var repository: BabyRepository
    private lateinit var useCase: SaveBabyProfileUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SaveBabyProfileUseCase(repository)
    }

    @Test
    fun invokeValidBabySavesToRepository() = runTest {
        val baby = Baby("Luna", LocalDate.now(), listOf(AllergyType.CMPA), null)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }

    @Test
    fun invokeBlankNameThrowsIllegalArgument() {
        val baby = Baby("  ", LocalDate.now())
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(baby) }
        }
    }

    @Test
    fun invokeFutureBirthDateThrowsIllegalArgument() {
        val baby = Baby("Luna", LocalDate.now().plusDays(1))
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(baby) }
        }
    }

    @Test
    fun invokeNoAllergiesSavesEmptyList() = runTest {
        val baby = Baby("Luna", LocalDate.now(), emptyList(), null)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }

    @Test
    fun invokeOtherAllergyWithNoteSavesNote() = runTest {
        val baby = Baby("Luna", LocalDate.now(), listOf(AllergyType.OTHER), "Shellfish")
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }
}
