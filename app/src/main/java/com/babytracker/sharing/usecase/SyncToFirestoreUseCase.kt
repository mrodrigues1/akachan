package com.babytracker.sharing.usecase

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.toSnapshot
import com.babytracker.sharing.domain.model.toSnapshotFields
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.util.resolve
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SyncToFirestoreUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val sources: SnapshotSources,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant = Instant::now,
) {
    enum class SyncType {
        FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY, BOTTLE_FEEDS, BOTTLE_FEEDS_AND_INVENTORY, DIAPERS
    }

    suspend operator fun invoke(syncType: SyncType = SyncType.FULL) {
        if (settingsRepository.getAppMode().first() != AppMode.PRIMARY) return
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        when (syncType) {
            SyncType.FULL -> syncFull(code)
            SyncType.SESSIONS -> syncSessions(code)
            SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
            SyncType.BABY -> syncBaby(code)
            SyncType.INVENTORY -> syncInventory(code)
            SyncType.BOTTLE_FEEDS -> syncBottleFeeds(code)
            SyncType.BOTTLE_FEEDS_AND_INVENTORY -> syncBottleFeedsAndInventory(code)
            SyncType.DIAPERS -> syncDiapers(code)
        }
    }

    private suspend fun syncFull(code: ShareCode) {
        val baby = sources.baby.getBabyProfile().first()
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        val bottleFeeds = sources.bottleFeeds.getRecent(SYNC_LIMIT)
        val diapers = sources.diaper.getRecent(SYNC_LIMIT)
        val growth = sources.growth.getAllMeasurements().first().latestPerType()
        val milestones = sources.milestones.getMilestones().first()
        val doctorVisits = sources.doctorVisit.getRecentVisits(SYNC_LIMIT)
        val updatedAtMs = now().toEpochMilli()
        val prediction = currentPrediction(updatedAtMs)
        sharingRepository.syncFullSnapshot(
            code,
            ShareSnapshot(
                lastSyncAt = Instant.ofEpochMilli(updatedAtMs),
                baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
                sessions = sessions.map { it.toSnapshot() },
                sleepRecords = sleepRecords.map { it.toSnapshot() },
                bottleFeeds = bottleFeeds.map { it.toSnapshot() },
                inventoryTotalMl = summary.totalMl,
                inventoryBagCount = summary.bagCount,
                inventoryUpdatedAt = updatedAtMs,
                milkBags = activeBags.map { it.toSnapshot() },
                sleepPrediction = prediction,
                growth = growth.map { it.toSnapshot() },
                milestones = milestones.map { it.toSnapshot() },
                diapers = diapers.map { it.toSnapshot() },
                doctorVisits = doctorVisits.map { it.toSnapshot() },
            ),
        )
    }

    private suspend fun syncSessions(code: ShareCode) {
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        sharingRepository.syncSessions(
            code,
            sessions.map { it.toSnapshot() },
            currentPrediction(now().toEpochMilli()),
        )
    }

    private suspend fun syncSleepRecords(code: ShareCode) {
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        sharingRepository.syncSleepRecords(
            code,
            sleepRecords.map { it.toSnapshot() },
            currentPrediction(now().toEpochMilli()),
        )
    }

    // The synced prediction depends on session and sleep state, so every sync that touches either
    // must refresh it — otherwise a stale AFTER_ACTIVE_FEED/CURRENTLY_SLEEPING lingers in Firestore
    // until the next full sync.
    private suspend fun currentPrediction(generatedAtMs: Long): SleepPredictionSnapshot? =
        if (sleepSettingsRepository.getPredictiveSleepEnabled().first()) {
            sources.predictSleepWindow().first().toSnapshot(
                generatedAt = generatedAtMs,
                resolveReason = { it.resolve(appContext) },
                feedPromptText = appContext.getString(R.string.sleep_feed_prompt),
            )
        } else {
            null
        }

    private suspend fun syncBaby(code: ShareCode) {
        val baby = sources.baby.getBabyProfile().first()
        sharingRepository.syncBaby(code, baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()))
    }

    private suspend fun syncInventory(code: ShareCode) {
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        sharingRepository.syncInventory(
            code,
            summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
            activeBags.map { it.toSnapshot() },
        )
    }

    private suspend fun syncBottleFeeds(code: ShareCode) {
        val feeds = sources.bottleFeeds.getRecent(SYNC_LIMIT)
        sharingRepository.syncBottleFeeds(code, feeds.map { it.toSnapshot() })
    }

    // A bottle-feed edit/delete/op touches both the feed list and inventory (a consumed bag). Pushing
    // them as one merged write halves the Firestore round-trips versus separate BOTTLE_FEEDS +
    // INVENTORY syncs; the written fields are identical.
    private suspend fun syncBottleFeedsAndInventory(code: ShareCode) {
        val feeds = sources.bottleFeeds.getRecent(SYNC_LIMIT)
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        sharingRepository.syncBottleFeedsAndInventory(
            code,
            feeds.map { it.toSnapshot() },
            summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
            activeBags.map { it.toSnapshot() },
        )
    }

    private suspend fun syncDiapers(code: ShareCode) {
        val diapers = sources.diaper.getRecent(SYNC_LIMIT)
        sharingRepository.syncDiapers(code, diapers.map { it.toSnapshot() })
    }

    companion object {
        private const val SYNC_LIMIT = 20
    }
}
