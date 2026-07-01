package com.babytracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Emits [Unit] immediately, then every [periodMs]. Shared by the ViewModels that re-render an
 * elapsed-time label once a minute.
 *
 * The initial emit runs on the collecting coroutine (Main/testDispatcher) so that
 * StandardTestDispatcher.advanceUntilIdle() sees it and terminates correctly. Subsequent ticks are
 * delayed on Dispatchers.Default to keep the infinite loop off the test scheduler.
 */
fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
    emit(Unit)
    emitAll(
        flow {
            while (true) {
                delay(periodMs)
                emit(Unit)
            }
        }.flowOn(Dispatchers.Default),
    )
}
