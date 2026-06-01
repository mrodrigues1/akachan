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
        runCatching {
            updateWidget(context, glanceId)
        }.onFailure { t ->
            if (t is CancellationException) throw t
            BabyWidget.refreshingInstances.remove(glanceId)
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
    }
}
