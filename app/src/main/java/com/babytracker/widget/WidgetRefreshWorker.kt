package com.babytracker.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "WidgetRefreshWorker"

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching { updater.updateAll() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { t ->
                    if (t is CancellationException) throw t
                    Log.w(TAG, "Periodic widget refresh failed; will retry", t)
                    Result.retry()
                },
            )

    companion object {
        const val UNIQUE_NAME = "baby_widget_refresh"
    }
}
