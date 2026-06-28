package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugPartnerSnapshotBuilder
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.observePartnerConnected
import com.babytracker.sharing.data.firebase.observeSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

/**
 * The live equivalent of [FetchPartnerDataUseCase]: streams the share snapshot via Firestore listeners
 * so the primary's changes appear within ~1s. A second cheap listener on the partner's own doc detects
 * a server-confirmed disconnect. Local partner state is cleared ONLY on a server-confirmed loss of
 * access — a cache-origin absence (cold offline start) is held as [Access.Pending] until the server
 * corrects it, so a missing cached snapshot never erases a valid partner's data.
 */
class ObservePartnerDataUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    // Lazy: only resolved on the debug placeholder-code path, never in release observation.
    private val debugSnapshotBuilder: Lazy<DebugPartnerSnapshotBuilder>,
) {
    /** Observes the snapshot for the currently stored share code (used by the dashboard). */
    operator fun invoke(): Flow<ShareSnapshot> = flow {
        val codeValue = settingsRepository.getShareCode().first() ?: error("No share code")
        emitAll(invoke(ShareCode(codeValue)))
    }

    operator fun invoke(code: ShareCode): Flow<ShareSnapshot> {
        // Debug offline partner mode: serve a snapshot of the locally-seeded data instead of Firebase.
        // build() is suspend, so it must run inside the flow builder rather than flowOf(...).
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return flow { emit(debugSnapshotBuilder.get().build()) }
        }
        return flow {
            // signInAnonymously() lives inside the flow so an auth/network failure routes through
            // .catch instead of crashing the collector.
            val uid = service.signInAnonymously()
            emitAll(
                combine(
                    service.observeSnapshot(code.value),
                    service.observePartnerConnected(code.value, uid),
                ) { snap, conn -> snap to conn }
                    .transform { (snap, conn) ->
                        when {
                            // Present data + connected — show it (cached data is fine, offline-first).
                            snap.data != null && conn.connected -> emit(snap.data)
                            // Server-CONFIRMED absence/disconnect — the only state that clears DataStore.
                            (snap.data == null && !snap.fromCache) || (!conn.connected && !conn.fromCache) -> {
                                settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
                                throw PartnerAccessRevokedException("Partner disconnected")
                            }
                            // Cache-origin absence/disconnect (cold offline start): await the server emission.
                            else -> Unit
                        }
                    },
            )
        }.catch { error ->
            // Transient drops do not error the flow (the SDK serves cache + auto-reconnects), so a thrown
            // error is genuinely terminal. Re-throw revoke; wrap everything else for the VM's error branch.
            if (error is PartnerAccessRevokedException) throw error
            throw PartnerDataFetchException("Could not load partner data", error)
        }
    }
}
