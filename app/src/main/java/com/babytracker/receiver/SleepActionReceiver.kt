package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.manager.SleepSessionController
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SleepActionReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionController: SleepSessionController

    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
        private const val TAG = "SleepActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithTimeout(TAG) { handle(intent) }
    }

    internal suspend fun handle(intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        if (action == ACTION_STOP) {
            sessionController.stop(sessionId)
        }
    }
}
