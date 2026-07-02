package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.util.FireDecision
import com.babytracker.util.decideFire
import com.babytracker.util.showPredictiveSleepReminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveSleepReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sleepSettingsRepository: SleepSettingsRepository
    @Inject lateinit var sleepRecommendationRepository: SleepRecommendationRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val bestEstimateMs = intent.getLongExtra(EXTRA_BEST_ESTIMATE_MS, 0L)
        if (bestEstimateMs <= 0L) {
            Log.w(TAG, "Missing bestEstimate; dropping fire")
            return
        }
        val recommendationId = intent.getLongExtra(EXTRA_RECOMMENDATION_ID, 0L)
        // Re-read feature flag and quiet hours at fire time to handle inexact-alarm delivery
        // that may land inside a quiet window scheduled after the alarm was set.
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val enabled = sleepSettingsRepository.getPredictiveSleepEnabled().first()
                if (!enabled) {
                    Log.d(TAG, "Feature disabled at fire time; dropping")
                    return@launch
                }
                val quietStart = settingsRepository.getQuietHoursStartMinute().first()
                val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
                val decision = decideFire(
                    now = Instant.now(),
                    bestEstimate = Instant.ofEpochMilli(bestEstimateMs),
                    quietStartMinute = quietStart,
                    quietEndMinute = quietEnd,
                )
                when (decision) {
                    FireDecision.Stale -> Log.i(TAG, "Prediction stale; dropping fire")
                    FireDecision.QuietHours -> Log.d(TAG, "Fire time inside quiet hours; suppressing notification")
                    FireDecision.Fire -> {
                        showPredictiveSleepReminder(context = context, bestEstimateMs = bestEstimateMs)
                        if (recommendationId > 0L) {
                            runCatching {
                                sleepRecommendationRepository.updateLifecycle(recommendationId, RecommendationLifecycle.FIRED)
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
            } finally {
                result?.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_SLEEP_FIRE"
        const val EXTRA_BEST_ESTIMATE_MS = "best_estimate_ms"
        const val EXTRA_RECOMMENDATION_ID = "recommendation_id"
        const val REQUEST_CODE_PREDICTIVE_SLEEP = 1005
        private const val TAG = "PredictiveSleepRx"
    }
}
