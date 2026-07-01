package com.babytracker.util

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TickerFlowTest {

    @Test
    fun `emits immediately on collection`() = runTest {
        tickerFlow(periodMs = 60_000L).test {
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps ticking after the initial emission`() = runTest {
        // Short real period: subsequent ticks are delayed on Dispatchers.Default (wall clock),
        // deliberately outside the test scheduler's virtual time.
        tickerFlow(periodMs = 10L).test {
            awaitItem()
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
