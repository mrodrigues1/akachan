package com.babytracker.debug

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
import com.babytracker.sharing.usecase.SnapshotSources
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Builds a partner [ShareSnapshot] from the locally-seeded data so the partner dashboard shows
 * realistic content offline (debug only — served by FetchPartnerDataUseCase when the placeholder
 * share code is active). Mirrors SyncToFirestoreUseCase.syncFull but skips the sleep prediction
 * (nullable; the dashboard renders fine without it) to avoid the settings/context coupling.
 */
class DebugPartnerSnapshotBuilder @Inject constructor(
    private val sources: SnapshotSources,
) {
    suspend fun build(): ShareSnapshot {
        val baby = sources.baby.getBabyProfile().first()
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        val nowMs = Instant.now().toEpochMilli()
        return ShareSnapshot(
            lastSyncAt = Instant.ofEpochMilli(nowMs),
            baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
            sessions = sources.breastfeeding.getRecentSessions(LIMIT).map { it.toSnapshot() },
            sleepRecords = sources.sleep.getRecentRecords(LIMIT).map { it.toSnapshot() },
            bottleFeeds = sources.bottleFeeds.getRecent(LIMIT).map { it.toSnapshot() },
            inventoryTotalMl = summary.totalMl,
            inventoryBagCount = summary.bagCount,
            inventoryUpdatedAt = nowMs,
            milkBags = activeBags.map { it.toSnapshot() },
            growth = sources.growth.getAllMeasurements().first().map { it.toSnapshot() },
            milestones = sources.milestones.getMilestones().first().map { it.toSnapshot() },
            diapers = sources.diaper.getRecent(LIMIT).map { it.toSnapshot() },
            doctorVisits = sources.doctorVisit.getRecentVisits(LIMIT).map { it.toSnapshot() },
        )
    }

    private companion object {
        const val LIMIT = 20
    }
}
