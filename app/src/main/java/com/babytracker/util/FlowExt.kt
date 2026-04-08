package com.babytracker.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

fun <T> Flow<T>.catchAndLog(): Flow<T> = catch { e ->
    android.util.Log.e("FlowExt", "Error in flow", e)
}
