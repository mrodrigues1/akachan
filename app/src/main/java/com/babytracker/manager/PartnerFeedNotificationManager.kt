package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerFeedNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
) : PartnerFeedNotifier {

    override suspend fun notifyStashConsumed(consumedBagIds: List<Long>) {
        if (consumedBagIds.isEmpty()) return
        if (!settingsRepository.getPartnerFeedStashNotificationsEnabled().first()) return
        // The consumed bags were just marked used, not deleted, so getById still resolves them.
        // A null (e.g. bag deleted between apply and lookup) contributes 0 mL.
        val totalMl = consumedBagIds.sumOf { id -> inventoryRepository.getById(id)?.volumeMl ?: 0 }
        if (totalMl <= 0) return
        NotificationHelper.showPartnerStashConsumed(
            context = context,
            feedCount = consumedBagIds.size,
            totalMl = totalMl,
        )
    }
}
