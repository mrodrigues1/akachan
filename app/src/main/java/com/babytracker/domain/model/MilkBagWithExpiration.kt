package com.babytracker.domain.model

/**
 * A milk stash bag paired with its computed expiration status.
 * Produced by ObserveInventoryWithExpirationUseCase and consumed by the inventory UI.
 */
data class MilkBagWithExpiration(
    val bag: MilkBag,
    val status: ExpirationStatus,
)
