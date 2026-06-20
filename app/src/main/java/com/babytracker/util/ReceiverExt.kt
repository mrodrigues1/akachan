package com.babytracker.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

const val RECEIVER_DEFAULT_TIMEOUT_MS = 10_000L

/** Mutable-content, immutable-target PendingIntent flags shared by every reminder PendingIntent. */
val PENDING_INTENT_IMMUTABLE_UPDATE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/**
 * Run [block] off the main thread for a [BroadcastReceiver], holding the broadcast alive via
 * `goAsync()` until it finishes or [timeoutMs] elapses. Timeouts/failures are logged under [tag];
 * structural cancellation is rethrown. Shared by the reminder receivers to avoid duplicating the
 * `goAsync` + `withTimeout` + exception-mapping scaffold in every `onReceive`.
 */
fun BroadcastReceiver.goAsyncWithTimeout(
    tag: String,
    timeoutMs: Long = RECEIVER_DEFAULT_TIMEOUT_MS,
    block: suspend () -> Unit,
) {
    val result = goAsync()
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        try {
            val failure = runCatching { withTimeout(timeoutMs) { block() } }.exceptionOrNull()
            when (failure) {
                null -> Unit
                is TimeoutCancellationException -> Log.e(tag, "onReceive timed out", failure)
                is CancellationException -> throw failure
                else -> Log.e(tag, "onReceive failed", failure)
            }
        } finally {
            result.finish()
        }
    }
}
