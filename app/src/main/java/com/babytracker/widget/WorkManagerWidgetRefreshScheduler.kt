package com.babytracker.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerWidgetRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefreshScheduler {

    override fun scheduleImmediateRefresh() {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // Unique name distinct from the periodic UNIQUE_NAME; KEEP dedupes bursts (e.g. the 60s
        // sync timer repeatedly hitting an empty cache) into a single pending refresh.
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val IMMEDIATE_UNIQUE_NAME = "baby_widget_refresh_now"
    }
}
