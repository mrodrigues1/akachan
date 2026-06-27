package com.babytracker.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.data.toWidgetData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "WidgetRefreshWorker"

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
    private val settings: SettingsRepository,
    private val fetchPartnerData: FetchPartnerDataUseCase,
    private val partnerCache: PartnerWidgetCacheImpl,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            if (settings.getAppMode().first() == AppMode.PARTNER) {
                refreshPartner()
            } else {
                updater.updateAll()
                Result.success()
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            BabyWidget.refreshingInstances.clear()
            Log.w(TAG, "Periodic widget refresh failed; will retry", t)
            Result.retry()
        }

    private suspend fun refreshPartner(): Result {
        val shareCode = settings.getShareCode().first()
        if (shareCode == null) {
            // Partner mode but no code yet — nothing to fetch; the render path already shows EMPTY.
            updater.updateAll()
            return Result.success()
        }
        val code = ShareCode(shareCode)
        return runCatching { fetchPartnerData(code) }.fold(
            onSuccess = { snapshot ->
                // Guard: re-read the active share code before writing. If the user reconnected to
                // a different primary mid-fetch, the stored code changed and we must not cache
                // this snapshot under the old code. The render path will see a cache-miss for the
                // new code and schedule a fresh refresh after updateAll() triggers a re-render.
                if (settings.getShareCode().first() == shareCode) {
                    partnerCache.save(shareCode, toWidgetData(snapshot))
                }
                updater.updateAll()
                Result.success()
            },
            onFailure = { t ->
                when {
                    t is CancellationException -> throw t
                    // Confirmed revoke (use case already cleared the matching share state):
                    // drop only the cache tagged with the code we tried. No retry. A concurrent
                    // reconnect to a different primary keeps its own freshly-saved cache.
                    t is PartnerAccessRevokedException -> {
                        partnerCache.clear(shareCode)
                        updater.updateAll()
                        Result.success()
                    }
                    // Any other failure (network / sign-in / bug): keep the stale cache, re-render
                    // it, and retry. A misclassified error never empties it.
                    else -> {
                        Log.w(TAG, "Partner snapshot refresh failed; will retry", t)
                        updater.updateAll()
                        Result.retry()
                    }
                }
            },
        )
    }

    companion object {
        const val UNIQUE_NAME = "baby_widget_refresh"
    }
}
