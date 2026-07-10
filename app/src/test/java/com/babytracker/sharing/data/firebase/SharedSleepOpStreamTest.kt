package com.babytracker.sharing.data.firebase

import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SharedSleepOpStreamTest {
    private val service: FirestoreSharingService = mockk()

    private val op = SleepOp(
        opId = "op-1", action = SleepOpAction.START, entryClientId = "cid",
        authorUid = "uid", createdAtMs = 1_000L, startTimeMs = 1_000L, sleepType = SleepType.NAP,
    )

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `two concurrent collectors share a single upstream listener`() = runTest {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        val attachCount = AtomicInteger(0)
        every { service.observeSleepOps("CODE", "uid") } returns flow {
            attachCount.incrementAndGet()
            emit(listOf(op))
            awaitCancellation()
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val stream = SharedSleepOpStream(service, scope)

        val a = scope.launch { stream.observe("CODE", "uid").collect {} }
        val b = scope.launch { stream.observe("CODE", "uid").collect {} }

        assertEquals(1, attachCount.get())
        a.cancel()
        b.cancel()
        scope.cancel()
    }

    @Test
    fun `the shared listener detaches after the last subscriber leaves`() = runTest {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        val activeListeners = AtomicInteger(0)
        every { service.observeSleepOps("CODE", "uid") } returns flow {
            activeListeners.incrementAndGet()
            try {
                emit(listOf(op))
                awaitCancellation()
            } finally {
                activeListeners.decrementAndGet()
            }
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val stream = SharedSleepOpStream(service, scope)

        val job = scope.launch { stream.observe("CODE", "uid").collect {} }
        assertEquals(1, activeListeners.get())

        job.cancel()
        // Past WhileSubscribed's 5s stop timeout the shared upstream is cancelled.
        advanceTimeBy(5_001)
        assertEquals(0, activeListeners.get())
        scope.cancel()
    }

    @Test
    fun `a transient upstream error is retried on the shared stream`() = runTest {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        var attempts = 0
        every { service.observeSleepOps("CODE", "uid") } returns flow {
            attempts++
            if (attempts == 1) throw IOException("listener boom")
            emit(listOf(op))
            awaitCancellation()
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val stream = SharedSleepOpStream(service, scope)

        val received = mutableListOf<List<SleepOp>>()
        val job = scope.launch { stream.observe("CODE", "uid").collect { received += it } }
        // First attempt threw; retryWhen re-subscribes after the capped backoff.
        advanceTimeBy(5_001)

        assertTrue(attempts >= 2)
        assertEquals(listOf(op), received.last())
        job.cancel()
        scope.cancel()
    }
}
