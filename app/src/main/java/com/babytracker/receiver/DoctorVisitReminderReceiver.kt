package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.manager.DoctorVisitReminderManager
import com.babytracker.util.DoctorVisitNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class DoctorVisitReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: DoctorVisitRepository

    @Inject lateinit var settings: DoctorVisitSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DoctorVisitReminderManager.EXTRA_VISIT_ID, -1L)
        if (id < 0) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val failure = runCatching {
                    withTimeout(TIMEOUT_MS) { handle(context, id) }
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

    // One-shot: no re-arm. Re-queries the visit so a since-deleted / moved-to-past visit (or a
    // disabled setting) suppresses a now-stale notification.
    internal suspend fun handle(context: Context, id: Long) {
        if (!settings.getReminderEnabled().first()) return
        val visit = repository.getVisitById(id) ?: return
        if (!visit.date.isAfter(Instant.now())) return
        runCatching {
            DoctorVisitNotificationHelper.show(context, visit.providerName, visit.date)
        }.onFailure { if (it is SecurityException) Log.w(TAG, "POST_NOTIFICATIONS denied", it) else throw it }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val TAG = "DoctorVisitReminderReceiver"
    }
}
