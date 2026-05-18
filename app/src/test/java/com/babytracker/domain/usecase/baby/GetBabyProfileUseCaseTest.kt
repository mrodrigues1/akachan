package com.babytracker.domain.usecase.baby

import app.cash.turbine.test
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GetBabyProfileUseCaseTest {

    private lateinit var repository: BabyRepository
    private lateinit var useCase: GetBabyProfileUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetBabyProfileUseCase(repository)
    }

    @Test
    fun invokeDelegatesToRepositoryGetBabyProfile() = runTest {
        every { repository.getBabyProfile() } returns flowOf(null)

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getBabyProfile() }
    }

    @Test
    fun invokeEmitsBabyFromRepository() = runTest {
        val baby = Baby(name = "Luna", birthDate = LocalDate.of(2025, 1, 1))
        every { repository.getBabyProfile() } returns flowOf(baby)

        useCase().test {
            val result = awaitItem()
            assertEquals("Luna", result?.name)
            awaitComplete()
        }
    }

    @Test
    fun invokeEmitsNullWhenRepositoryReturnsNull() = runTest {
        every { repository.getBabyProfile() } returns flowOf(null)

        useCase().test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
    }

    @Test
    fun invokeMultipleEmissionsAllPropagated() = runTest {
        val baby = Baby(name = "Luna", birthDate = LocalDate.of(2025, 1, 1))
        val mutableFlow = MutableStateFlow<Baby?>(null)
        every { repository.getBabyProfile() } returns mutableFlow

        useCase().test {
            assertNull(awaitItem())
            mutableFlow.value = baby
            assertEquals("Luna", awaitItem()?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
