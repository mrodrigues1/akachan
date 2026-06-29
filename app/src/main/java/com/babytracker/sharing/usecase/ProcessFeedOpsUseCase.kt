package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PartnerFeedNotificationManager
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.ShareCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

// Singleton: holds the cross-batch [syncPending] obligation, which must survive activity recreation.
@Singleton
class ProcessFeedOpsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val service: FirestoreSharingService,
    private val applyFeedOp: ApplyFeedOpUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val partnerFeedNotifier: PartnerFeedNotificationManager,
) {
    /**
     * True when Room changed but the follow-up snapshot push has not succeeded yet.
     * Instance-scoped rather than batch-scoped: after a batch exhausts its retries, its ops stay
     * in Firestore and may re-enter a later batch as idempotent no-op re-applies — the owed sync
     * must survive until both snapshots are pushed, or that batch would skip the push and delete
     * the ops while the shared snapshot is still stale. Not persisted: across process death the
     * obligation is covered by the next owner-side edit's own sync.
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
                    service.observeFeedOps(code.value)
                        .retryWhen { cause, attempt ->
                            Log.w(TAG, "feed op listener error (attempt $attempt); retrying", cause)
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

    /**
     * One-shot drain for the background worker: fetches the current pending ops once from the
     * server and runs them through the same [processBatchWithRetry] path as the live collector.
     * Server-sourced on purpose — the listener's first emission is cache-first and could miss the
     * very op a partner just wrote. No-op unless this device is the primary with an active share code.
     */
    suspend fun drainOnce() {
        val mode = settingsRepository.getAppMode().first()
        val code = settingsRepository.getShareCode().first() ?: return
        if (mode != AppMode.PRIMARY) return
        val ops = service.getFeedOps(code)
        if (ops.isNotEmpty()) processBatchWithRetry(ShareCode(code), ops)
    }

    /**
     * Retries a failed batch in place: the Firestore snapshot listener only re-emits when
     * data changes, so waiting for the next emission would leave failed ops stuck unapplied.
     */
    private suspend fun processBatchWithRetry(code: ShareCode, ops: List<FeedOp>) {
        val sortedOps = ops.sortedBy { it.createdAtMs }
        // Keyed by entryClientId so retries don't double-count: a re-applied create reports a null
        // consumedBagId, so each consuming feed is recorded exactly once across all attempts.
        val consumedBagByEntry = linkedMapOf<String, Long>()
        repeat(MAX_BATCH_ATTEMPTS) { attempt ->
            runCatching {
                sortedOps.forEach { op ->
                    val result = applyFeedOp(op)
                    if (result.roomChanged) syncPending = true
                    result.consumedBagId?.let { consumedBagByEntry[op.entryClientId] = it }
                }
                if (syncPending) {
                    // One merged write instead of back-to-back BOTTLE_FEEDS + INVENTORY round-trips.
                    syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS_AND_INVENTORY)
                    syncPending = false
                }
                // Always delete: leaving invalid/duplicate ops in place would re-trigger the batch forever.
                service.deleteFeedOps(code.value, ops.map { it.opId })
                // Coalesced, after the batch fully succeeds. Wrapped so a notifier failure never
                // re-triggers the (already deleted) batch.
                if (consumedBagByEntry.isNotEmpty()) {
                    runCatching { partnerFeedNotifier.notifyStashConsumed(consumedBagByEntry.values.toList()) }
                        .onFailure { Log.w(TAG, "stash-consumed notification failed", it) }
                }
            }
                .onSuccess { return }
                .onFailure { cause ->
                    if (attempt == MAX_BATCH_ATTEMPTS - 1) {
                        Log.w(TAG, "feed op batch failed after $MAX_BATCH_ATTEMPTS attempts; giving up until next emission", cause)
                    } else {
                        Log.w(TAG, "feed op batch failed (attempt ${attempt + 1}); retrying", cause)
                        delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                    }
                }
        }
    }

    private companion object {
        const val TAG = "ProcessFeedOps"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
        const val MAX_BATCH_ATTEMPTS = 3
    }
}
