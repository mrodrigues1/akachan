package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `invoke_validBaby_savesToRepository`() = runTest {
        val baby = Baby("Luna", LocalDate.now(), listOf(AllergyType.CMPA), null)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }

    @Test
    fun `invoke_blankName_throwsIllegalArgument`() = runTest {
        val baby = Baby("  ", LocalDate.now())
        coJustRun { repository.saveBabyProfile(any()) }

        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(baby) }
        }
    }

    @Test
    fun `invoke_futureBirthDate_throwsIllegalArgument`() = runTest {
        val baby = Baby("Luna", LocalDate.now().plusDays(1))
        coJustRun { repository.saveBabyProfile(any()) }

        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(baby) }
        }
    }

    @Test
    fun `invoke_noAllergies_savesEmptyList`() = runTest {
        val baby = Baby("Luna", LocalDate.now(), emptyList(), null)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }

    @Test
    fun `invoke_otherAllergyWithNote_savesNote`() = runTest {
        val baby = Baby("Luna", LocalDate.now(), listOf(AllergyType.OTHER), "Shellfish")
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
    }
}
