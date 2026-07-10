package com.babytracker.sharing.data.firebase

import android.util.Log
import com.babytracker.di.ApplicationScope
import com.babytracker.sharing.domain.model.SleepOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * De-duplicates the partner's own sleep-op Firestore listener across the dashboard and the sleep
 * history screen. Each distinct (share code, author uid) gets exactly ONE cold [observeSleepOps]
 * listener, wrapped with the capped-backoff retry that used to live in `PartnerSleepViewModel` and
 * shared via [shareIn] with [SharingStarted.WhileSubscribed]: the underlying listener attaches on the
 * first collector and detaches [STOP_TIMEOUT_MS] after the last one leaves, so a screen sitting in the
 * backstack no longer pins a listener and two live screens share one instead of opening two.
 *
 * Keyed per (code, uid) rather than a single shared flow because the share code changes on
 * pairing/unpairing: a new code mints a new entry with its own listener while the old entry's listener
 * is torn down once its subscribers drain. Codes in flight are few (one active pairing at a time), so
 * the map does not grow unbounded.
 *
 * Retry is baked into the shared upstream on purpose: [shareIn] does not deliver upstream failures to
 * subscribers, so a throwing upstream would permanently kill the shared coroutine and silently freeze
 * every overlay it feeds. The capped backoff keeps the single shared listener alive across transient
 * Firestore errors exactly as the per-view-model `retryWhen` did before — the interval is unchanged.
 */
@Singleton
class SharedSleepOpStream @Inject constructor(
    private val service: FirestoreSharingService,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val streams = ConcurrentHashMap<Key, Flow<List<SleepOp>>>()

    /** The shared, retry-wrapped stream of the author's own sleep ops for [code]. */
    fun observe(code: String, authorUid: String): Flow<List<SleepOp>> =
        streams.computeIfAbsent(Key(code, authorUid)) {
            service.observeSleepOps(code, authorUid)
                .retryWhen { cause, attempt ->
                    // A transient Firestore listener error must not kill the shared stream — the
                    // optimistic overlays it feeds would freeze. Re-subscribe with capped backoff.
                    Log.w(TAG, "shared sleep op listener error (attempt $attempt); retrying", cause)
                    delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                    true
                }
                .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
        }

    private data class Key(val code: String, val authorUid: String)

    private companion object {
        const val TAG = "SharedSleepOpStream"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
