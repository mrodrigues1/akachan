package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.VaccineReminderManager
import com.babytracker.util.VaccineNotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class VaccineReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: VaccineRepository
    @Inject lateinit var settings: VaccineSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(VaccineReminderManager.EXTRA_VACCINE_ID, -1L)
        if (id < 0) return
        goAsyncWithTimeout(TAG) { handle(context, id) }
    }

    // One-shot: no re-arm. Re-queries the record so a since-administered/deleted vaccine (or a
    // disabled setting) suppresses a now-stale notification.
    internal suspend fun handle(context: Context, id: Long) {
        if (!settings.getReminderEnabled().first()) return
        val record = repository.getById(id) ?: return
        if (record.status != VaccineStatus.SCHEDULED) return
        val scheduled = record.scheduledDate ?: return
        runCatching {
            VaccineNotificationHelper.show(context, record.name, scheduled)
        }.onFailure { if (it is SecurityException) Log.w(TAG, "POST_NOTIFICATIONS denied", it) else throw it }
    }

    private companion object {
        const val TAG = "VaccineReminderReceiver"
    }
}
