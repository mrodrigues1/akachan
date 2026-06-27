package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.StashNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "main partner" notification when partner-authored bottle feeds consume stash milk.
 *
 * Called on the primary device after a batch of partner feed ops applies. Gates on the user setting,
 * resolves the consumed bag volumes, and posts a single coalesced notification.
 */
@Singleton
class PartnerFeedNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * @param consumedBagIds bag ids freshly consumed by partner feeds in the just-applied batch.
     *   Empty list is a no-op. Each id corresponds to one partner bottle feed.
     */
    suspend fun notifyStashConsumed(consumedBagIds: List<Long>) {
        if (consumedBagIds.isEmpty()) return
        if (!settingsRepository.getPartnerFeedStashNotificationsEnabled().first()) return
        // The consumed bags were just marked used, not deleted, so they still resolve. A single
        // aggregate sum avoids loading a MilkBag object per id; ids missing from the table (e.g. a
        // bag deleted between apply and lookup) contribute 0 mL, as before. Non-empty by the guard above.
        val totalMl = inventoryRepository.sumVolumeForIds(consumedBagIds)
        if (totalMl <= 0) return
        StashNotificationHelper.showPartnerStashConsumed(
            context = context,
            feedCount = consumedBagIds.size,
            totalMl = totalMl,
        )
    }
}
