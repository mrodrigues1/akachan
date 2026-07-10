package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepPredictionState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SharedSleepPredictionStreamTest {

    @Test
    fun `shares a single upstream pipeline across concurrent collectors`() = runTest(UnconfinedTestDispatcher()) {
        val attachCount = AtomicInteger(0)
        val predictSleepWindow = mockk<PredictSleepWindowUseCase>()
        every { predictSleepWindow() } returns flow {
            attachCount.incrementAndGet()
            emit(SleepPredictionState.CurrentlySleeping)
            awaitCancellation()
        }
        val stream = SharedSleepPredictionStream(predictSleepWindow, backgroundScope)

        // UnconfinedTestDispatcher makes each collector subscribe eagerly, so both are active before
        // the assertion; shareIn must have attached the upstream exactly once.
        val first = backgroundScope.launch { stream.observe().collect {} }
        val second = backgroundScope.launch { stream.observe().collect {} }

        assertEquals(1, attachCount.get())
        first.cancel()
        second.cancel()
    }

    @Test
    fun `observe emits the predicted state`() = runTest {
        val predictSleepWindow = mockk<PredictSleepWindowUseCase>()
        every { predictSleepWindow() } returns flow {
            emit(SleepPredictionState.CurrentlySleeping)
            awaitCancellation()
        }
        val stream = SharedSleepPredictionStream(predictSleepWindow, backgroundScope)

        assertEquals(SleepPredictionState.CurrentlySleeping, stream.observe().first())
    }
}
