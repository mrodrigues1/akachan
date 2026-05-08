package com.babytracker.domain.usecase.sleep

import app.cash.turbine.test
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetSleepHistoryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: GetSleepHistoryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetSleepHistoryUseCase(repository)
    }

    @Test
    fun invokeDelegatesToRepositoryGetAllRecords() = runTest {
        every { repository.getAllRecords() } returns flowOf(emptyList())

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getAllRecords() }
    }

    @Test
    fun invokeEmitsRecordsFromRepository() = runTest {
        val record = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NAP)
        every { repository.getAllRecords() } returns flowOf(listOf(record))

        useCase().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            awaitComplete()
        }
    }

    @Test
    fun invokeEmptyFlowEmitsEmpty() = runTest {
        every { repository.getAllRecords() } returns flowOf(emptyList())

        useCase().test {
            val items = awaitItem()
            assertEquals(0, items.size)
            awaitComplete()
        }
    }

    @Test
    fun invokeMultipleEmissionsAllPropagated() = runTest {
        val record1 = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NAP)
        val record2 = SleepRecord(id = 2L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
        val mutableFlow = MutableStateFlow(listOf(record1))
        every { repository.getAllRecords() } returns mutableFlow

        useCase().test {
            assertEquals(1, awaitItem().size)
            mutableFlow.value = listOf(record1, record2)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
