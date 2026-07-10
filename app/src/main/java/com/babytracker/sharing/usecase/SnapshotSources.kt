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
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
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
    val sharedSleepPrediction: SharedSleepPredictionStream,
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
 *
 * Reads from [SharedSleepPredictionStream] rather than cold-starting PredictSleepWindowUseCase: the
 * always-on predictive-notification coordinator keeps that shared pipeline hot with replay = 1, so a
 * sync fired on any feed/sleep write gets the current prediction from the replay cache instantly
 * instead of spinning up four Room flows + the ticker + a full feature extraction on every write. On
 * the cold edge where a sync beats the first-ever emission (or the coordinator momentarily holds no
 * subscriber under WhileSubscribed), `.first()` simply becomes the subscriber that starts the
 * pipeline — the same cost as before, now deduplicated across concurrent consumers.
 *
 * Tradeoff: the cold flow used to issue its query *after* the local write, so it always saw post-write
 * state. The replay cache instead reflects whatever the shared pipeline last computed, and that pipeline
 * reacts to the write's Room invalidation asynchronously (on Dispatchers.Default). A write-triggered
 * sync can therefore race the recompute and publish the pre-write prediction. In practice the several
 * suspending DataStore/Room reads that precede this call (mode, share code, recent records, the enabled
 * flag) yield the dispatcher long enough for the recompute to land, and any residual one-write lag
 * self-heals on the next sync — while the sleep records in the same snapshot are always read fresh.
 * Forcing strict freshness would mean re-introducing the per-write cold start this optimization removes,
 * so the bounded, self-healing staleness is accepted deliberately (issue #764).
 */
internal suspend fun currentSleepPrediction(
    sources: SnapshotSources,
    sleepSettings: SleepSettingsRepository,
    generatedAtMs: Long,
): SleepPredictionSnapshot? =
    if (sleepSettings.getPredictiveSleepEnabled().first()) {
        sources.sharedSleepPrediction.observe().first().toSnapshot(generatedAt = generatedAtMs)
    } else {
        null
    }
