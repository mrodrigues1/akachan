package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class StashExpirationBootReceiver : BroadcastReceiver() {

    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var scheduler: StashExpirationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle() }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle() {
        if (!inventorySettings.getExpirationEnabled().first()) return
        if (!inventorySettings.getExpirationNotifEnabled().first()) return
        val notifTime = inventorySettings.getExpirationNotifTimeMinutes().first()
        scheduler.scheduleDaily(notifTime)
        Log.d(TAG, "Restored stash expiration alarm at minute=$notifTime")
    }

    companion object {
        private const val TAG = "StashExpirationBoot"
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
