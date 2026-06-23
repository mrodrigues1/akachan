package com.babytracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BabyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BabyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueRefreshWorker(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // The periodic refresh is scheduled in onEnabled (kept via ExistingPeriodicWorkPolicy.KEEP),
        // so re-enqueuing here — onUpdate fires on every host refresh/resize — only wastes a
        // PeriodicWorkRequest build + WorkManager IPC on the main thread.
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context)
            .cancelUniqueWork(WidgetRefreshWorker.UNIQUE_NAME)
    }

    private fun enqueueRefreshWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
