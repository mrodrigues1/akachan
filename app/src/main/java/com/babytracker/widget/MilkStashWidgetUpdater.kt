package com.babytracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface MilkStashWidgetUpdater {
    suspend fun updateAll()
}

@Singleton
class GlanceMilkStashWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : MilkStashWidgetUpdater {
    override suspend fun updateAll() {
        MilkStashWidget().updateAll(context)
    }
}
