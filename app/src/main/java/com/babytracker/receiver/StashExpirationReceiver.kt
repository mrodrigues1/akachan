package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class StashExpirationReceiver : BroadcastReceiver() {

    @Inject lateinit var milkBagDao: MilkBagDao
    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var scheduler: StashExpirationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithTimeout(TAG) { handle(context) }
    }

    internal suspend fun handle(context: Context) {
        var notifTime = DEFAULT_NOTIF_TIME_MINUTES
        var shouldRearm = true

        try {
            val failure = runCatching {
                if (!inventorySettings.getExpirationEnabled().first()) {
                    shouldRearm = false
                    Log.d(TAG, "Stash expiration disabled; not re-arming")
                } else if (!inventorySettings.getExpirationNotifEnabled().first()) {
                    shouldRearm = false
                    Log.d(TAG, "Stash expiration notifications disabled; not re-arming")
                } else {
                    notifTime = inventorySettings.getExpirationNotifTimeMinutes().first()
                    val expirationDays = inventorySettings.getExpirationDays().first()
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val expiringToday = milkBagDao.getAllBagsOnce()
                        .filter { it.usedAt == null }
                        .filter { bag ->
                            val expiryDate = Instant.ofEpochMilli(bag.collectionDate)
                                .atZone(zone)
                                .toLocalDate()
                                .plusDays(expirationDays.toLong())
                            expiryDate == today
                        }

                    if (expiringToday.isNotEmpty()) {
                        try {
                            NotificationHelper.showStashExpiration(
                                context = context,
                                count = expiringToday.size,
                                totalMl = expiringToday.sumOf { it.volumeMl },
                            )
                        } catch (e: SecurityException) {
                            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping stash expiration notification", e)
                        }
                    }
                }
            }.exceptionOrNull()
            when (failure) {
                null -> Unit
                is CancellationException -> throw failure
                else -> Log.w(TAG, "Stash expiration handling failed; re-arming defensively", failure)
            }
        } finally {
            if (shouldRearm) {
                runCatching { scheduler.scheduleDaily(notifTime) }
                    .onFailure { Log.e(TAG, "Stash expiration re-arm failed", it) }
            }
        }
    }

    private companion object {
        const val DEFAULT_NOTIF_TIME_MINUTES = 480
        const val TAG = "StashExpirationReceiver"
    }
}
