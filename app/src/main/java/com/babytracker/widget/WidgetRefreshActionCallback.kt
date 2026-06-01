package com.babytracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

class WidgetRefreshActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        BabyWidget.refreshingInstances.add(glanceId)
        runCatching {
            BabyWidget().update(context, glanceId)
        }.onFailure { t ->
            if (t is CancellationException) throw t
            BabyWidget.refreshingInstances.remove(glanceId)
            return
        }
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).widgetRefreshScheduler().scheduleImmediateRefresh()
    }
}
