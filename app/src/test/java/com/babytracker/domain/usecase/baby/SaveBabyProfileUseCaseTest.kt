package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.LocalDate

class SaveBabyProfileUseCaseTest {

    private lateinit var repository: BabyRepository
    private lateinit var bootstrap: BootstrapBabyProfileUseCase
    private lateinit var useCase: SaveBabyProfileUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        bootstrap = mockk(relaxed = true)
        useCase = SaveBabyProfileUseCase(repository, bootstrap)
    }

    @Test
    fun invokeValidBabySavesToRepository() = runTest {
        val baby = Baby("Luna", LocalDate.now(), listOf(AllergyType.CMPA), null)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby)

        coVerify(exactly = 1) { repository.saveBabyProfile(baby) }
        coVerify(exactly = 1) { bootstrap.invoke() }
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
    fun invokeWithDueDatePropagatesBootstrapFailureBeforeMarkingComplete() {
        val baby = Baby("Luna", LocalDate.now())
        val dueDate = LocalDate.now().plusWeeks(3)
        coEvery { bootstrap.invoke(dueDate, baby.birthDate) } throws IOException("disk full")

        assertThrows<IOException> {
            runBlocking { useCase(baby, dueDate) }
        }

        // saveBabyProfile (which flips onboarding_complete) must not run once the due date failed.
        coVerify(exactly = 0) { repository.saveBabyProfile(any()) }
    }

    @Test
    fun invokeWithDueDatePersistsDueDateBeforeSavingBaby() = runTest {
        val baby = Baby("Luna", LocalDate.now())
        val dueDate = LocalDate.now().plusWeeks(3)
        coJustRun { repository.saveBabyProfile(baby) }

        useCase(baby, dueDate)

        coVerifyOrder {
            bootstrap.invoke(dueDate, baby.birthDate)
            repository.saveBabyProfile(baby)
        }
    }

    @Test
    fun invokeWithoutDueDateSwallowsBootstrapFailure() = runTest {
        val baby = Baby("Luna", LocalDate.now())
        coJustRun { repository.saveBabyProfile(baby) }
        coEvery { bootstrap.invoke() } throws IOException("disk full")

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
