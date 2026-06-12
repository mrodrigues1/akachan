package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.ShareCode
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

class ProcessFeedOpsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sharingRepository: SharingRepository,
    private val applyFeedOp: ApplyFeedOpUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
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
                    sharingRepository.observeFeedOps(code)
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
     * Retries a failed batch in place: the Firestore snapshot listener only re-emits when
     * data changes, so waiting for the next emission would leave failed ops stuck unapplied.
     */
    private suspend fun processBatchWithRetry(code: ShareCode, ops: List<FeedOp>) {
        repeat(MAX_BATCH_ATTEMPTS) { attempt ->
            runCatching { processBatch(code, ops) }
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

    private suspend fun processBatch(code: ShareCode, ops: List<FeedOp>) {
        ops.sortedBy { it.createdAtMs }.forEach { applyFeedOp(it) }
        syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS)
        syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY)
        sharingRepository.deleteFeedOps(code, ops.map { it.opId })
    }

    private companion object {
        const val TAG = "ProcessFeedOps"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
        const val MAX_BATCH_ATTEMPTS = 3
    }
}
