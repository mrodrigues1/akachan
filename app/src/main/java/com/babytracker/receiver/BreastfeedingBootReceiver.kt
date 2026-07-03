package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingBootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BreastfeedingRepository

    @Inject lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle(context) }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle(context: Context) {
        val session = repository.getActiveSession().first() ?: return
        NotificationHelper.createBreastfeedingNotificationChannel(context)
        notificationCoordinator.restoreActiveSession(session)
        Log.d(TAG, "Restored active breastfeeding session ${session.id}")
    }

    companion object {
        private const val TAG = "BreastfeedingBoot"
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
