package com.babytracker.sharing.usecase

import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import javax.inject.Inject

/**
 * The single write-then-sync seam for tracker writes: persist a record, then push the partner
 * snapshot. Sync is best-effort — a partner-sync failure must never fail the local write — and a
 * no-op unless this device is the sharing PRIMARY. Callers hand over the write and a [SyncType];
 * failure semantics, mode gating, and any future replication (e.g. an outbox) are decided here,
 * once.
 */
class SyncedWrite @Inject constructor(
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun <T> invoke(syncType: SyncType, write: suspend () -> T): T {
        val result = write()
        sync(syncType)
        return result
    }

    /**
     * Best-effort sync without a bracketed write, for flows where the sync must trail other side
     * effects (notification scheduling, UI state) so an offline sync can't delay them. Returns
     * whether the sync succeeded, for callers that surface a "saved locally" hint.
     */
    suspend fun sync(syncType: SyncType = SyncType.FULL): Boolean =
        runCatching { syncToFirestore(syncType) }.isSuccess
}
