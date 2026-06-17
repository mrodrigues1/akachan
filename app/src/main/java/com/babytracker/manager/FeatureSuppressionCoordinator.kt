package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cancels (and re-arms) the alarm-based reminders that have no reconcile loop of their own when
 * their feature is toggled. The predictive coordinators self-cancel via their own combine, so they
 * are not handled here. Nap reminders are event-driven, so a re-enable is honored at the next nap
 * end (no retroactive re-arm). Stash expiration is re-armed on re-enable from its inventory settings.
 */
@Singleton
class FeatureSuppressionCoordinator @Inject constructor(
    private val featureToggleRepository: FeatureToggleRepository,
    private val napReminderScheduler: NapReminderScheduler,
    private val stashExpirationScheduler: StashExpirationScheduler,
    private val inventorySettings: InventorySettingsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    fun start() {
        applicationScope.launch {
            var previous: Set<AppFeature>? = null
            featureToggleRepository.getEnabledFeatures().collect { current ->
                val prev = previous
                if (prev != null) {
                    if (AppFeature.SLEEP in prev && AppFeature.SLEEP !in current) {
                        napReminderScheduler.cancel()
                    }
                    if (AppFeature.INVENTORY in prev && AppFeature.INVENTORY !in current) {
                        stashExpirationScheduler.cancel()
                    }
                    if (AppFeature.INVENTORY !in prev && AppFeature.INVENTORY in current) {
                        rescheduleStash()
                    }
                }
                previous = current
            }
        }
    }

    private suspend fun rescheduleStash() {
        if (
            inventorySettings.getExpirationEnabled().first() &&
            inventorySettings.getExpirationNotifEnabled().first()
        ) {
            stashExpirationScheduler.scheduleDaily(inventorySettings.getExpirationNotifTimeMinutes().first())
        }
    }
}
