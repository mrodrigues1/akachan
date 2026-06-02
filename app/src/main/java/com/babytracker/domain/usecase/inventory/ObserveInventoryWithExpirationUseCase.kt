package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Combines the active-bags Flow with the expiration settings and an externally-driven
 * date Flow, emitting each bag paired with its computed [ExpirationStatus].
 *
 * The date is injected as a Flow (rather than calling [LocalDate.now] inside the transform)
 * so the caller can push a fresh date on resume; otherwise statuses would go stale past
 * midnight because [combine] only re-runs when an upstream Flow emits.
 *
 * When the feature is disabled, every bag maps to [ExpirationStatus.NONE] and no date math runs.
 */
class ObserveInventoryWithExpirationUseCase @Inject constructor(
    private val getInventory: GetInventoryUseCase,
    private val settings: InventorySettingsRepository,
) {
    operator fun invoke(dateFlow: Flow<LocalDate>): Flow<List<MilkBagWithExpiration>> =
        combine(
            getInventory(),
            settings.getExpirationEnabled(),
            settings.getExpirationDays(),
            dateFlow,
        ) { bags, enabled, days, today ->
            if (!enabled) {
                bags.map { MilkBagWithExpiration(it, ExpirationStatus.NONE) }
            } else {
                bags.map { bag ->
                    val expiryDate = bag.collectionDate
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .plusDays(days.toLong())
                    val status = when {
                        expiryDate.isAfter(today.plusDays(1)) -> ExpirationStatus.NONE
                        expiryDate.isAfter(today) -> ExpirationStatus.EXPIRING_SOON
                        else -> ExpirationStatus.EXPIRING_OR_EXPIRED
                    }
                    MilkBagWithExpiration(bag, status)
                }
            }
        }
}
