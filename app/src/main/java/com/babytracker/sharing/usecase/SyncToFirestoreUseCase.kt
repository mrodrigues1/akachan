package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.toSnapshot
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SyncToFirestoreUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val sources: SnapshotSources,
    private val now: () -> Instant = Instant::now,
) {
    enum class SyncType {
        FULL, SESSIONS, SLEEP_RECORDS, INVENTORY, BOTTLE_FEEDS, BOTTLE_FEEDS_AND_INVENTORY, DIAPERS
    }

    suspend operator fun invoke(syncType: SyncType = SyncType.FULL) {
        if (settingsRepository.getAppMode().first() != AppMode.PRIMARY) return
        val code = ShareCode(settingsRepository.getShareCode().first() ?: return)
        when (syncType) {
            SyncType.FULL -> syncFull(code)
            SyncType.SESSIONS -> syncSessions(code)
            SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
            SyncType.INVENTORY -> syncInventory(code)
            SyncType.BOTTLE_FEEDS -> syncBottleFeeds(code)
            SyncType.BOTTLE_FEEDS_AND_INVENTORY -> syncBottleFeedsAndInventory(code)
            SyncType.DIAPERS -> syncDiapers(code)
        }
    }

    private suspend fun syncFull(code: ShareCode) {
        service.syncFullSnapshot(
            code.value,
            buildShareSnapshot(sources, sleepSettingsRepository, now()),
        )
    }

    private suspend fun syncSessions(code: ShareCode) {
        val sessions = sources.breastfeeding.getRecentSessions(SYNC_LIMIT)
        service.syncSessions(
            code.value,
            sessions.map { it.toSnapshot() },
            currentSleepPrediction(sources, sleepSettingsRepository, now().toEpochMilli()),
        )
    }

    private suspend fun syncSleepRecords(code: ShareCode) {
        val sleepRecords = sources.sleep.getRecentRecords(SYNC_LIMIT)
        service.syncSleepRecords(
            code.value,
            sleepRecords.map { it.toSnapshot() },
            currentSleepPrediction(sources, sleepSettingsRepository, now().toEpochMilli()),
        )
    }

    private suspend fun syncInventory(code: ShareCode) {
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        service.syncInventory(
            code.value,
            InventorySnapshotFields(summary.totalMl, summary.bagCount, now().toEpochMilli()),
            activeBags.map { it.toSnapshot() },
        )
    }

    private suspend fun syncBottleFeeds(code: ShareCode) {
        val feeds = sources.bottleFeeds.getRecent(SYNC_LIMIT)
        service.syncBottleFeeds(code.value, feeds.map { it.toSnapshot() })
    }

    // A bottle-feed edit/delete/op touches both the feed list and inventory (a consumed bag). Pushing
    // them as one merged write halves the Firestore round-trips versus separate BOTTLE_FEEDS +
    // INVENTORY syncs; the written fields are identical.
    private suspend fun syncBottleFeedsAndInventory(code: ShareCode) {
        val feeds = sources.bottleFeeds.getRecent(SYNC_LIMIT)
        val summary = sources.inventory.currentSummary()
        val activeBags = sources.inventory.getActiveBags().first()
        service.syncBottleFeedsAndInventory(
            code.value,
            feeds.map { it.toSnapshot() },
            InventorySnapshotFields(summary.totalMl, summary.bagCount, now().toEpochMilli()),
            activeBags.map { it.toSnapshot() },
        )
    }

    private suspend fun syncDiapers(code: ShareCode) {
        val diapers = sources.diaper.getRecent(SYNC_LIMIT)
        service.syncDiapers(code.value, diapers.map { it.toSnapshot() })
    }

    companion object {
        private const val SYNC_LIMIT = 20
    }
}
