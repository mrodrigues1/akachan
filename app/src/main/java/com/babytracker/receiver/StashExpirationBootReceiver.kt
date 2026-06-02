package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class StashExpirationBootReceiver : BroadcastReceiver() {

    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var scheduler: StashExpirationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val failure = runCatching {
                    withTimeout(TIMEOUT_MS) { handle() }
                }.exceptionOrNull()
                when (failure) {
                    null -> Unit
                    is TimeoutCancellationException -> Log.e(TAG, "onReceive timed out", failure)
                    is CancellationException -> throw failure
                    else -> Log.e(TAG, "onReceive failed", failure)
                }
            } finally {
                result.finish()
            }
        }
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
        private const val TIMEOUT_MS = 10_000L
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
