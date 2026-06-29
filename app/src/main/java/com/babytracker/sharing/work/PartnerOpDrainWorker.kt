package com.babytracker.sharing.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babytracker.sharing.usecase.ProcessFeedOpsUseCase
import com.babytracker.sharing.usecase.ProcessSleepOpsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "PartnerOpDrainWorker"

/**
 * Periodic background drain of the partner op inboxes on the primary device. The foreground
 * collectors (MainActivity) only run while the app is STARTED, so without this a partner's
 * start/stop sits unapplied in Firestore until the primary opens the app. Each [drainOnce] no-ops
 * unless this device is the primary with an active share code, so the worker is cheap otherwise.
 *
 * Latency is bounded by WorkManager's 15-minute periodic floor (and Doze can stretch it further) —
 * this is the "eventually" backstop, not an instant-sync path.
 */
@HiltWorker
class PartnerOpDrainWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processSleepOps: ProcessSleepOpsUseCase,
    private val processFeedOps: ProcessFeedOpsUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            processSleepOps.drainOnce()
            processFeedOps.drainOnce()
            Result.success()
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            Log.w(TAG, "Partner op drain failed; will retry", t)
            Result.retry()
        }

    companion object {
        const val UNIQUE_NAME = "partner_op_drain"
    }
}
