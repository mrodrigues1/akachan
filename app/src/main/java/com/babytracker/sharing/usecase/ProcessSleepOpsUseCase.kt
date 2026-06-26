package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PartnerSleepNotifier
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

// Singleton: holds the cross-batch [syncPending] obligation, which must survive activity recreation.
@Singleton
class ProcessSleepOpsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sharingRepository: SharingRepository,
    private val applySleepOp: ApplySleepOpUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val partnerSleepNotifier: PartnerSleepNotifier,
) {
    /**
     * True when Room changed but the follow-up snapshot push has not succeeded yet. Instance-scoped
     * (see [ProcessFeedOpsUseCase]) so the owed re-publish survives batch retries and re-entries.
     */
    private var syncPending = false

    /** Suspends while collecting; cancel via caller scope. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() {
        combine(
            settingsRepository.getAppMode(),
            settingsRepository.getShareCode(),
        ) { mode, code -> if (mode == AppMode.PRIMARY && code != null) ShareCode(code) else null }
            .distinctUntilChanged()
            .flatMapLatest { code ->
                if (code == null) {
                    flowOf(null)
                } else {
                    sharingRepository.observeSleepOps(code)
                        .retryWhen { cause, attempt ->
                            Log.w(TAG, "sleep op listener error (attempt $attempt); retrying", cause)
                            delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                            true
                        }
                        .map { ops -> code to ops }
                }
            }
            .collect { batch ->
                if (batch == null) return@collect
                val (code, ops) = batch
                if (ops.isEmpty()) return@collect
                processBatchWithRetry(code, ops)
            }
    }

    private suspend fun processBatchWithRetry(code: ShareCode, ops: List<SleepOp>) {
        val sortedOps = ops.sortedBy { it.createdAtMs }
        repeat(MAX_BATCH_ATTEMPTS) { attempt ->
            // Rebuilt per attempt so a partial-then-retried batch never double-counts notifications:
            // re-applied ops report no notification, so each real change is surfaced exactly once.
            val notifications = mutableListOf<PartnerSleepNotification>()
            runCatching {
                sortedOps.forEach { op ->
                    val result = applySleepOp(op)
                    if (result.roomChanged) syncPending = true
                    result.notification?.let { notifications += it }
                }
                if (syncPending) {
                    syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS)
                    syncPending = false
                }
                // Always delete: leaving invalid/duplicate ops in place would re-trigger the batch forever.
                sharingRepository.deleteSleepOps(code, ops.map { it.opId })
                // After the batch fully succeeds; a notifier failure never re-triggers the deleted batch.
                if (notifications.isNotEmpty()) {
                    runCatching { partnerSleepNotifier.notifySleepChanges(notifications) }
                        .onFailure { Log.w(TAG, "partner sleep notification failed", it) }
                }
            }
                .onSuccess { return }
                .onFailure { cause ->
                    if (attempt == MAX_BATCH_ATTEMPTS - 1) {
                        Log.w(TAG, "sleep op batch failed after $MAX_BATCH_ATTEMPTS attempts; giving up until next emission", cause)
                    } else {
                        Log.w(TAG, "sleep op batch failed (attempt ${attempt + 1}); retrying", cause)
                        delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                    }
                }
        }
    }

    private companion object {
        const val TAG = "ProcessSleepOps"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
        const val MAX_BATCH_ATTEMPTS = 3
    }
}
