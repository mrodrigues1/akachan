package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Read-side collaborators that produce the partner snapshot payload. Bundled as one injected
 * dependency so [SyncToFirestoreUseCase] stays within the constructor parameter budget.
 */
data class SnapshotSources @Inject constructor(
    val baby: BabyRepository,
    val breastfeeding: BreastfeedingRepository,
    val sleep: SleepRepository,
    val inventory: InventoryRepository,
    val bottleFeeds: BottleFeedRepository,
    val diaper: DiaperRepository,
    val predictSleepWindow: PredictSleepWindowUseCase,
    val growth: GrowthRepository,
    val milestones: MilestoneRepository,
    val doctorVisit: DoctorVisitRepository,
)

private const val SNAPSHOT_SYNC_LIMIT = 20

/**
 * Assembles the full partner [ShareSnapshot] from the current local data. Shared by
 * [GenerateShareCodeUseCase] (initial share) and [SyncToFirestoreUseCase] (full re-sync), which
 * built byte-for-byte identical snapshots before this extraction.
 */
internal suspend fun buildShareSnapshot(
    sources: SnapshotSources,
    sleepSettings: SleepSettingsRepository,
    now: Instant,
): ShareSnapshot {
    val baby = sources.baby.getBabyProfile().first()
    val sessions = sources.breastfeeding.getRecentSessions(SNAPSHOT_SYNC_LIMIT)
    val sleepRecords = sources.sleep.getRecentRecords(SNAPSHOT_SYNC_LIMIT)
    val summary = sources.inventory.currentSummary()
    val activeBags = sources.inventory.getActiveBags().first()
    val bottleFeeds = sources.bottleFeeds.getRecent(SNAPSHOT_SYNC_LIMIT)
    val diapers = sources.diaper.getRecent(SNAPSHOT_SYNC_LIMIT)
    val growth = sources.growth.getAllMeasurements().first().latestPerType()
    val milestones = sources.milestones.getMilestones().first()
    val doctorVisits = sources.doctorVisit.getRecentVisits(SNAPSHOT_SYNC_LIMIT)
    val updatedAtMs = now.toEpochMilli()
    return ShareSnapshot(
        lastSyncAt = now,
        baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
        sessions = sessions.map { it.toSnapshot() },
        sleepRecords = sleepRecords.map { it.toSnapshot() },
        bottleFeeds = bottleFeeds.map { it.toSnapshot() },
        inventoryTotalMl = summary.totalMl,
        inventoryBagCount = summary.bagCount,
        inventoryUpdatedAt = updatedAtMs,
        milkBags = activeBags.map { it.toSnapshot() },
        sleepPrediction = currentSleepPrediction(sources, sleepSettings, updatedAtMs),
        growth = growth.map { it.toSnapshot() },
        milestones = milestones.map { it.toSnapshot() },
        diapers = diapers.map { it.toSnapshot() },
        doctorVisits = doctorVisits.map { it.toSnapshot() },
    )
}

/**
 * Builds the synced sleep-window prediction, or null when predictive sleep is disabled. Shared by
 * every sync path that touches session or sleep state, so a stale prediction never lingers.
 */
internal suspend fun currentSleepPrediction(
    sources: SnapshotSources,
    sleepSettings: SleepSettingsRepository,
    generatedAtMs: Long,
): SleepPredictionSnapshot? =
    if (sleepSettings.getPredictiveSleepEnabled().first()) {
        sources.predictSleepWindow().first().toSnapshot(generatedAt = generatedAtMs)
    } else {
        null
    }
