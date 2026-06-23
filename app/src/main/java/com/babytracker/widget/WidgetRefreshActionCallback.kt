package com.babytracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val REFRESH_FEEDBACK_MS = 450L

class WidgetRefreshActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = WidgetRefreshActionHandler().refresh(context, glanceId)
}

internal class WidgetRefreshActionHandler(
    private val updateWidget: suspend (Context, GlanceId) -> Unit = { context, glanceId ->
        BabyWidget().update(context, glanceId)
    },
    private val schedulerProvider: (Context) -> WidgetRefreshScheduler = { context ->
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).widgetRefreshScheduler()
    },
) {
    suspend fun refresh(context: Context, glanceId: GlanceId) {
        BabyWidget.refreshingInstances.add(glanceId)
        // finally guarantees the instance is evicted even if the coroutine is cancelled between the
        // add and the explicit happy-path remove below — otherwise a cancelled refresh would leave a
        // stale GlanceId pinned in the process-wide set. The happy-path remove stays where it is so
        // the final updateWidget still renders the cleared (non-refreshing) state; the finally remove
        // is then an idempotent no-op.
        try {
            runCatching {
                updateWidget(context, glanceId)
            }.onFailure { t ->
                if (t is CancellationException) throw t
                return
            }
            delay(REFRESH_FEEDBACK_MS)
            runCatching {
                schedulerProvider(context).scheduleImmediateRefresh()
            }.onFailure { t ->
                if (t is CancellationException) throw t
            }
            BabyWidget.refreshingInstances.remove(glanceId)
            runCatching {
                updateWidget(context, glanceId)
            }.onFailure { t ->
                if (t is CancellationException) throw t
            }
        } finally {
            BabyWidget.refreshingInstances.remove(glanceId)
        }
    }
}
