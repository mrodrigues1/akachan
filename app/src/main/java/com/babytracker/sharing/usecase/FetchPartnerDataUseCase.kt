package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugPartnerSnapshotBuilder
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.google.firebase.FirebaseException
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FetchPartnerDataUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    // Lazy: only resolved on the debug placeholder-code path, never in release fetches.
    private val debugSnapshotBuilder: Lazy<DebugPartnerSnapshotBuilder>,
) {
    /** Fetches the snapshot for the currently stored share code (used by the dashboard). */
    suspend operator fun invoke(): ShareSnapshot {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        return invoke(code)
    }

    /**
     * Fetches the snapshot for an explicit [code]. Callers that already hold the code (e.g. the
     * widget refresh worker) use this so the returned snapshot provably belongs to [code] — the
     * use case never re-reads settings, so a reconnect mid-fetch can't make the fetched data and
     * the caller's code diverge and end up cached under another primary's key.
     */
    suspend operator fun invoke(code: ShareCode): ShareSnapshot {
        // Debug offline partner mode: serve a snapshot of the locally-seeded data instead of Firebase.
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return debugSnapshotBuilder.get().build()
        }
        try {
            return fetchConnectedSnapshot(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PartnerAccessRevokedException) {
            throw e
        } catch (e: FirebaseException) {
            throw PartnerDataFetchException("Could not load partner data", e)
        }
    }

    private suspend fun fetchConnectedSnapshot(code: ShareCode): ShareSnapshot {
        val uid = service.signInAnonymously()
        if (!service.isPartnerConnected(code.value, uid)) {
            // Conditional clear: if the user already reconnected to a different primary, the stored
            // code no longer matches and this is a no-op; the newer connection is preserved.
            settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
            throw PartnerAccessRevokedException("Partner access revoked")
        }
        return fetchSnapshotOrRevoked(code)
    }

    private suspend fun fetchSnapshotOrRevoked(code: ShareCode): ShareSnapshot {
        // Guard against the TOCTOU window between isPartnerConnected and fetchSnapshot: if the
        // primary deletes the share document after the connectivity check passes, fetchSnapshot
        // throws IllegalStateException("No data in share document ..."). Treat that as a confirmed
        // revoke so the worker clears the cache instead of keeping stale data and retrying.
        return try {
            service.fetchSnapshot(code.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
            throw PartnerAccessRevokedException("Share document missing or invalid: ${e.message}", e)
        }
    }
}
